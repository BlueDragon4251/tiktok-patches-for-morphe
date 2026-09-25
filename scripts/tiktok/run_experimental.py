#!/usr/bin/env python3
"""Try the complete catalog on an unqualified APK and discard partial output.

This records diagnostic evidence, never qualification for Morphe compatibility.
"""
import argparse
import json
import os
import subprocess
from pathlib import Path
from fixtures import ROOT
from probe_latest import inspect


def blockers(metadata, result, report, expected, identity, head=None):
    package = identity['package']
    names = [p['name'] for p in metadata['patches'] if package in (p.get('compatiblePackages') or {})]
    if len(names) != len(set(names)) or set(names) != set(expected):
        return ['Generated catalog differs from the accepted complete catalog']
    if not result:
        return ['Morphe produced no result file; inspect the patch log']
    applied = [p['name'] for p in result.get('appliedPatches', [])]
    problems = []
    if result.get('failedPatches') or len(applied) != len(set(applied)) or set(applied) != set(names):
        problems.append(f"Incomplete catalog: {len(applied)}/{len(names)} applied, {len(result.get('failedPatches', []))} failed")
    if (result.get('packageName'), result.get('packageVersion')) != (package, identity['version']):
        problems.append('Morphe reported a different APK package/version')
    steps = result.get('patchingSteps', [])
    if {s.get('step') for s in steps} != {'PATCHING', 'REBUILDING'} or any(not s.get('success') for s in steps):
        problems.append('Patching or APK rebuilding did not complete successfully')
    if not report:
        problems.append('No patch-time hook report')
    elif (report.get('fixtureSha256'), report.get('package'), report.get('version'), report.get('experimental')) != (
            identity['sha256'], package, identity['version'], True):
        problems.append('Hook report is not for this exact experimental APK')
    else:
        if head is not None and report.get('featureHead') != head:
            problems.append('Hook report belongs to a different feature head')
        if not report.get('injections') or not report.get('fingerprints'):
            problems.append('Missing injection or fingerprint evidence')
        for hook in report.get('fingerprints', []):
            if hook.get('required') and (hook.get('status') != 'resolved' or
                  (hook.get('selection') == 'unique' and hook.get('candidateCount') != 1) or
                  (hook.get('origin') == 'apk' and hook.get('portableContractValidated') is not True)):
                problems.append('Unresolved native hook: ' + hook['hook'])
    return problems


def main():
    p = argparse.ArgumentParser(description=__doc__)
    for name in ('apk', 'bundle', 'cli', 'output'):
        p.add_argument('--' + name, type=Path, required=True)
    p.add_argument('--head', required=True)
    p.add_argument('--metadata', type=Path, default=ROOT / 'patches-list.json')
    p.add_argument('--source', required=True)
    a = p.parse_args()
    out = a.output.resolve()
    out.mkdir(parents=True, exist_ok=True)
    identity = inspect(a.apk, a.source)
    metadata = json.loads(a.metadata.read_text())
    accepted = json.loads((ROOT / 'fixtures/tiktok/46.7.3/accepted-catalog.json').read_text())
    expected = [patch['name'] for patch in accepted['appliedPatches']]
    names = [p['name'] for p in metadata['patches'] if identity['package'] in (p.get('compatiblePackages') or {})]
    result_path, log_path, patched = out / 'morphe-result.json', out / 'morphe.log', out / 'patched.apk'
    command = ['java', '-Xmx6g', '-jar', str(a.cli.resolve()), 'patch', '-p', str(a.bundle.resolve()),
               '--force', '--continue-on-error', '--exclusive', '--unsigned', '--result-file', str(result_path)]
    for name in names:
        command += ['-e', name]
    command += ['-o', str(patched), str(a.apk.resolve())]
    problems = []
    try:
        with log_path.open('w') as log:
            process = subprocess.run(command, cwd=out, stdout=log, stderr=subprocess.STDOUT,
                                     env=dict(os.environ, TIKTOK_EXPERIMENTAL_PORTABLE='1',
                                              TIKTOK_FEATURE_HEAD=a.head))
        result = json.loads(result_path.read_text()) if result_path.is_file() else None
        report_path = out / 'tiktok-hook-report.json'
        report = json.loads(report_path.read_text()) if report_path.is_file() else None
        problems = blockers(metadata, result, report, expected, identity, a.head)
        if process.returncode:
            problems.append(f'Morphe exited with code {process.returncode}')
        if not patched.is_file() and not problems:
            problems.append('No patched APK was generated')
    finally:
        # Even a partially applied catalog may cause Morphe to produce an APK.
        patched.unlink(missing_ok=True)
    (out / 'experimental-result.json').write_text(json.dumps({
        'schema': 1, 'head': a.head, 'candidate': identity, 'qualified': False,
        'status': 'blocked' if problems else 'experimental-catalog-passed',
        'blockers': problems, 'expectedPatchCount': len(expected),
    }, indent=2) + '\n')
    print(f"Experimental catalog: {len(expected)} patches; {len(problems)} blockers; never qualified")


if __name__ == '__main__':
    main()
