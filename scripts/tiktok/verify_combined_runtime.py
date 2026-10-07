#!/usr/bin/env python3
"""Rebuild the user's 39-patch, two-source selection and inspect the merged DEX.

Pinned official patches exercise shared-extension collisions with BlueIT. This
verifies packaging and injection, not on-device rendering or performance.
"""
import argparse
import gc
import hashlib
import json
import os
from pathlib import Path
import subprocess
import zipfile

from run_experimental import output_identity

OFFICIAL_URL = 'https://github.com/MorpheApp/morphe-patches/releases/download/v1.46.0-dev.10/patches-1.46.0-dev.10.mpp'
OFFICIAL_SHA = '9ae6de8b454086b52b376f977eded84c37045eeabdc5339e1a75b870beb64c95'
APK_SHA = '8b5569f592a5534652ae460ef1d9e7f7394b5b7fdde44ae64f106d76767e2622'
EXTRA = ['Change installer source', 'Disable Play Store updates']


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def verify_dex(path):
    from loguru import logger
    logger.remove()
    from androguard.core.dex import DEX
    expected = {
        ('Lapp/morphe/extension/shared/Utils;', 'primeContext'): '(Landroid/content/Context;)V',
        ('Lapp/morphe/extension/tiktok/theme/ThemeCaptionRenderer;', 'beforeDraw'): '(Landroid/view/View;Landroid/text/Layout;)V',
        ('Lapp/morphe/extension/tiktok/theme/ThemeUiThread;', 'defer'): '(Landroid/view/View;Ljava/lang/Runnable;)Z',
    }
    found = []
    calls = []
    with zipfile.ZipFile(path) as apk:
        for entry in apk.namelist():
            if not (entry.startswith('classes') and entry.endswith('.dex')):
                continue
            dex = DEX(apk.read(entry))
            for cls in dex.get_classes():
                for method in cls.get_methods():
                    key = cls.get_name(), method.get_name()
                    if key in expected:
                        assert method.get_descriptor().replace(' ', '') == expected[key], key
                        assert method.get_code() is not None, key
                        found.append(key)
                    if (key == ('Lcom/ss/android/ugc/aweme/app/host/AwemeHostApplication;', 'attachBaseContext')
                            or method.get_name() == 'onDraw'):
                        for ins in method.get_instructions():
                            text = ins.get_output()
                            if 'Utils;->primeContext' in text or 'ThemeCaptionRenderer;->beforeDraw' in text:
                                calls.append({'owner': key[0], 'method': key[1], 'instruction': text})
            del dex
            gc.collect()
    assert len(found) == len(expected) and set(found) == set(expected), found
    assert sum('Utils;->primeContext' in c['instruction'] for c in calls) == 1, calls
    assert sum('ThemeCaptionRenderer;->beforeDraw' in c['instruction'] for c in calls) == 1, calls
    return {'concreteMethods': [list(k) for k in found], 'nativeCalls': calls}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('apk', 'bundle', 'cli', 'metadata', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--head', required=True)
    args = parser.parse_args()
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    assert sha(args.apk) == APK_SHA, 'Unexpected original APK'
    official = out / 'official.mpp'
    subprocess.run(['curl', '-fLsS', '--proto', '=https', '--proto-redir', '=https',
                    '--retry', '3', '--max-time', '180', OFFICIAL_URL, '-o', str(official)], check=True)
    assert sha(official) == OFFICIAL_SHA, 'Unexpected official bundle'
    metadata = json.loads(args.metadata.read_text(encoding='utf-8'))
    names = [p['name'] for p in metadata['patches']
             if 'com.zhiliaoapp.musically' in (p.get('compatiblePackages') or {})]
    assert len(names) == len(set(names)) == 37
    result_path, patched = out / 'result.json', out / 'patched.apk'
    command = ['java', '-Xmx6g', '-jar', str(args.cli.resolve()), 'patch', '--exclusive', '--unsigned',
               '--result-file', str(result_path), '-p', str(official)]
    for name in EXTRA:
        command += ['-e', name]
    command += ['-p', str(args.bundle.resolve())]
    for name in names:
        command += ['-e', name]
    command += ['-o', str(patched), str(args.apk.resolve())]
    env = dict(os.environ, TIKTOK_FEATURE_HEAD=args.head)
    env.pop('TIKTOK_EXPERIMENTAL_PORTABLE', None)
    with (out / 'patch.log').open('w', encoding='utf-8') as log:
        subprocess.run(command, cwd=out, env=env, stdout=log, stderr=subprocess.STDOUT, check=True)
    result = json.loads(result_path.read_text(encoding='utf-8'))
    applied = [p['name'] for p in result['appliedPatches']]
    assert not result['failedPatches'] and len(applied) == 39 and set(applied) == set(names + EXTRA), result
    assert (result['packageName'], result['packageVersion']) == ('com.zhiliaoapp.musically', '47.1.3'), result
    steps = result['patchingSteps']
    assert len(steps) == 2 and {s['step'] for s in steps} == {'PATCHING', 'REBUILDING'} and all(s['success'] is True for s in steps), steps
    rebuilt = output_identity(patched)
    merged = verify_dex(patched)
    evidence = {'featureHead': args.head, 'officialSha256': OFFICIAL_SHA,
                'blueitSha256': sha(args.bundle), 'apkSha256': APK_SHA,
                'appliedPatches': applied, 'rebuilt': rebuilt, 'mergedDex': merged,
                'onDeviceVerified': False}
    (out / 'combined-evidence.json').write_text(json.dumps(evidence, indent=2) + '\n', encoding='utf-8')
    print('Combined 39-patch rebuild and merged DEX verified')


if __name__ == '__main__':
    main()
