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
        clinit = next((m for m in cls.get_methods() if m.get_name() == "<clinit>"), None)
        if clinit and clinit.get_code():
            for index, ins in enumerate(clinit.get_instructions()):
                output = ins.get_output()
                if "DOWNLOAD_200_VIDEOS" in output or ins.get_name().startswith("invoke-direct"):
                    print(f"  {index:04d}: {ins.get_name():28s} {output}")

    print("\n=== STATIC LIST CONFIG CANDIDATES ===")
    config_candidates = []
    for cls_name, cls in class_map.items():
        list_fields = [field for field in cls.get_fields() if field.get_descriptor() == "Ljava/util/List;"]
        if len(list_fields) < 2:
            continue
        clinit = next((method for method in cls.get_methods() if method.get_name() == "<clinit>"), None)
        if clinit is None or clinit.get_code() is None:
            continue
        instructions = list(clinit.get_instructions())
        enum_refs = [
            index for index, ins in enumerate(instructions)
            if enum_desc and enum_desc in ins.get_output()
        ]
        list_writes = [
            index for index, ins in enumerate(instructions)
            if ins.get_name().startswith("sput-object") and "Ljava/util/List;" in ins.get_output()
        ]
        if not enum_refs and len(list_writes) < 2:
            continue
        config_candidates.append(cls_name)
        print(
            f"LIST-CANDIDATE {cls_name} dex={class_dex[cls_name]} "
            f"list_fields={[(f.get_name(), f.get_descriptor()) for f in list_fields]} "
            f"enum_refs={enum_refs} list_writes={list_writes}"
        )
        interesting: set[int] = set()
        for index in enum_refs + list_writes:
            interesting.update(range(max(0, index - 12), min(len(instructions), index + 12)))
        for index in sorted(interesting):
            ins = instructions[index]
            print(f"  {index:04d}: {ins.get_name():28s} {ins.get_output()}")
        print()

    print("\n=== STABLE OFFLINE CLASSES ===")
    for suffix in ("/OfflineModeSheetPageAssem;", "/OfflineModeListVM;"):
        for name in sorted(name for name in class_map if name.endswith(suffix)):
            print(name, "dex", class_dex[name])

    print("\n=== SUMMARY ===")
    print("missing descriptors:", missing)
    print("offline enum:", enum_desc)
    print("config candidates:", config_candidates)


if __name__ == "__main__":
    download_apk()
    analyze()
