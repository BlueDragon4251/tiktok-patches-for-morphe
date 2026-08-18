from __future__ import annotations

import glob
import hashlib
import html
import re
import sys
from collections import defaultdict

import requests
from bs4 import BeautifulSoup
from loguru import logger

logger.remove()
logger.add(sys.stderr, level="ERROR")

from androguard.core.apk import APK
from androguard.core.dex import DEX

PAGE_URL = "https://www.mediafire.com/file/sd2x6vnmv1ocnv7/com.zhiliaoapp.musically_46.4.3-2024604030_minAPI23(arm64-v8a,armeabi-v7a)(nodpi)_apkmirror.com.apk/file"
EXPECTED_SHA256 = "79062fb88d2eef8d6e11bbf766b4b40ee08d89cdc594ff26b549ccce7b50c4b2"
APK_PATH = "tiktok-46.4.3.apk"


def download_apk() -> None:
    headers = {
        "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/140 Safari/537.36"
    }
    page = requests.get(PAGE_URL, headers=headers, timeout=60)
    page.raise_for_status()
    soup = BeautifulSoup(page.text, "html.parser")
    button = soup.find(id="downloadButton")
    if button is None or not button.get("href"):
        raise RuntimeError("MediaFire direct download link was not found")
    direct = html.unescape(button["href"])

    digest = hashlib.sha256()
    size = 0
    with requests.get(direct, headers=headers, stream=True, timeout=(60, 180)) as response:
        response.raise_for_status()
        with open(APK_PATH, "wb") as fh:
            for chunk in response.iter_content(1024 * 1024):
                if chunk:
                    fh.write(chunk)
                    digest.update(chunk)
                    size += len(chunk)
    actual = digest.hexdigest()
    print("download bytes:", size)
    print("download sha256:", actual)
    if actual != EXPECTED_SHA256:
        raise RuntimeError(f"Unexpected APK SHA-256: {actual}")


def analyze() -> None:
    apk = APK(APK_PATH)
    print("=== APK ===")
    print("package:", apk.get_package())
    print("versionName:", apk.get_androidversion_name())
    print("versionCode:", apk.get_androidversion_code())

    class_map = {}
    class_dex = {}
    for dex_index, dex_bytes in enumerate(apk.get_all_dex(), start=1):
        dex = DEX(dex_bytes)
        for cls in dex.get_classes():
            class_map[cls.get_name()] = cls
            class_dex[cls.get_name()] = dex_index

    print("\n=== HARDCODED LX DESCRIPTORS USED BY TIKTOK PATCHES ===")
    source_descriptors: dict[str, set[str]] = defaultdict(set)
    for path in glob.glob("patches/src/main/kotlin/app/morphe/patches/tiktok/**/*.kt", recursive=True):
        text = open(path, encoding="utf-8").read()
        for desc in re.findall(r"LX/[A-Za-z0-9_$]+;", text):
            source_descriptors[desc].add(path)

    missing: list[str] = []
    for desc in sorted(source_descriptors):
        exists = desc in class_map
        print(("OK      " if exists else "MISSING ") + desc + " <- " + ", ".join(sorted(source_descriptors[desc])))
        if not exists:
            missing.append(desc)
    print(f"descriptor total={len(source_descriptors)} missing={len(missing)}")

    enum_desc = None
    for name, cls in class_map.items():
        names = {field.get_name() for field in cls.get_fields()}
        if "DOWNLOAD_200_VIDEOS" in names:
            enum_desc = name
            break

    print("\n=== OFFLINE ENUM ===")
    print("enum descriptor:", enum_desc)
    if enum_desc:
        cls = class_map[enum_desc]
        print("dex:", class_dex[enum_desc])
        print("fields:", [(f.get_name(), f.get_descriptor()) for f in cls.get_fields()])
        for method in cls.get_methods():
            if method.get_name() == "<clinit>" and method.get_code():
                for index, ins in enumerate(method.get_instructions()):
                    output = ins.get_output()
                    if "DOWNLOAD_200_VIDEOS" in output or ins.get_name().startswith("invoke-direct"):
                        print(f"  {index:04d}: {ins.get_name():28s} {output}")

    print("\n=== REFERENCES TO OFFLINE ENUM DOWNLOAD_* FIELDS ===")
    candidates: dict[tuple[str, str, str], list[int]] = defaultdict(list)
    if enum_desc:
        needle = enum_desc + "->DOWNLOAD_"
        for cls_name, cls in class_map.items():
            for method in cls.get_methods():
                code = method.get_code()
                if code is None:
                    continue
                instructions = list(method.get_instructions())
                for index, ins in enumerate(instructions):
                    if needle in ins.get_output():
                        candidates[(cls_name, method.get_name(), method.get_descriptor())].append(index)

        for (cls_name, method_name, method_desc), indexes in candidates.items():
            print(
                f"CANDIDATE {cls_name} dex={class_dex[cls_name]} "
                f"{method_name}{method_desc} refs={indexes}"
            )
            methods = [
                method
                for method in class_map[cls_name].get_methods()
                if method.get_name() == method_name and method.get_descriptor() == method_desc
            ]
            if not methods:
                continue
            instructions = list(methods[0].get_instructions())
            keep: set[int] = set()
            for index in indexes:
                keep.update(range(max(0, index - 10), min(len(instructions), index + 16)))
            for index in sorted(keep):
                ins = instructions[index]
                print(f"  {index:04d}: {ins.get_name():28s} {ins.get_output()}")
            print("  fields:", [(f.get_name(), f.get_descriptor()) for f in class_map[cls_name].get_fields()])

    print("\n=== STATIC LIST CONFIG CANDIDATES ===")
    for cls_name, cls in class_map.items():
        list_fields = [field for field in cls.get_fields() if field.get_descriptor() == "Ljava/util/List;"]
        if len(list_fields) < 2:
            continue
        clinit = next((method for method in cls.get_methods() if method.get_name() == "<clinit>"), None)
        if clinit is None or clinit.get_code() is None:
            continue
        instructions = list(clinit.get_instructions())
        enum_refs = [
            index
            for index, ins in enumerate(instructions)
            if enum_desc and enum_desc in ins.get_output()
        ]
        list_writes = [
            index
            for index, ins in enumerate(instructions)
            if ins.get_name().startswith("sput-object") and "Ljava/util/List;" in ins.get_output()
        ]
        if not enum_refs and len(list_writes) < 2:
            continue
        print(
            f"LIST-CANDIDATE {cls_name} dex={class_dex[cls_name]} "
            f"list_fields={[(f.get_name(), f.get_descriptor()) for f in list_fields]} "
            f"enum_refs={enum_refs} list_writes={list_writes}"
        )
        interesting: set[int] = set()
        for index in enum_refs + list_writes:
            interesting.update(range(max(0, index - 10), min(len(instructions), index + 10)))
        for index in sorted(interesting):
            ins = instructions[index]
            print(f"  {index:04d}: {ins.get_name():28s} {ins.get_output()}")

    print("\n=== STABLE OFFLINE CLASSES ===")
    for suffix in ("/OfflineModeSheetPageAssem;", "/OfflineModeListVM;"):
        for name in sorted(name for name in class_map if name.endswith(suffix)):
            print(name, "dex", class_dex[name])

    print("\n=== SUMMARY ===")
    print("missing descriptors:", missing)
    print("offline enum:", enum_desc)
    print("enum reference candidate count:", len(candidates))


if __name__ == "__main__":
    download_apk()
    analyze()
