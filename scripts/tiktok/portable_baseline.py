#!/usr/bin/env python3
"""Extract the portable hook index from a same-head acceptance report.

The full report remains the source of truth. This small index allows candidate
APK discovery without downloading an expiring GitHub Actions artifact.
"""
import argparse
import json
from pathlib import Path


def extract(report):
    if report.get('schema') != 2 or report.get('package') != 'com.zhiliaoapp.musically':
        raise ValueError('Expected a global TikTok schema-2 acceptance report')
    if not report.get('fixtureSha256') or not report.get('featureHead'):
        raise ValueError('Missing accepted APK identity or feature head')
    hooks = []
    for hook in report['fingerprints']:
        if hook.get('origin') == 'apk' and hook.get('fixtureContractValidated') is not True:
            raise ValueError(f"Unvalidated native baseline hook: {hook['hook']}")
        hooks.append({key: hook[key] for key in (
            'hook', 'status', 'required', 'owner', 'name', 'parameters', 'returns',
            'origin', 'semanticContext', 'structuralSha256', 'fixtureContractSha256',
            'fixtureContractValidated', 'selection', 'candidateCount', 'selector',
        ) if key in hook})
    if not hooks or not any(h.get('origin') == 'apk' for h in hooks):
        raise ValueError('Empty native hook baseline')
    return {key: report[key] for key in (
        'schema', 'normalization', 'package', 'version', 'versionCode',
        'fixtureSha256', 'featureHead',
    )} | {'fingerprints': hooks}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('report', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    result = extract(json.loads(args.report.read_text()))
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')


if __name__ == '__main__':
    main()
