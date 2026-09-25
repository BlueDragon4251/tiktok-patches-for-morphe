#!/usr/bin/env python3
"""Record the identity of a newly downloaded original APK before discovery."""
import argparse
import hashlib
import json
from pathlib import Path


def inspect(apk_path, source_url, expected_sha=None):
    from loguru import logger
    logger.remove()
    from androguard.core.apk import APK
    with apk_path.open('rb') as stream:
        sha = hashlib.file_digest(stream, 'sha256').hexdigest()
    if expected_sha and sha != expected_sha.lower():
        raise ValueError(f'Candidate APK SHA-256 mismatch: expected {expected_sha}, got {sha}')
    apk = APK(str(apk_path))
    name, version, version_code = apk.get_package(), apk.get_androidversion_name(), apk.get_androidversion_code()
    if name != 'com.zhiliaoapp.musically' or not version or not version_code:
        raise ValueError(f'Not a global TikTok APK: {name} {version} ({version_code})')
    return {'source': source_url, 'package': name, 'version': version,
            'versionCode': int(version_code), 'sha256': sha,
            'sizeBytes': apk_path.stat().st_size, 'qualified': False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    parser.add_argument('--source', required=True)
    parser.add_argument('--expected-sha')
    parser.add_argument('--output', type=Path, default=Path('candidate-identity.json'))
    args = parser.parse_args()
    identity = inspect(args.apk, args.source, args.expected_sha)
    args.output.write_text(json.dumps(identity, indent=2) + '\n')
    print(f"Candidate {identity['version']} {identity['sha256']} ({identity['sizeBytes']} bytes); discovery only")


if __name__ == '__main__':
    main()
