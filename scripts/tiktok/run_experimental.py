#!/usr/bin/env python3
"""Try the complete catalog on an unqualified APK and discard partial output.

This records diagnostic evidence, never qualification for Morphe compatibility.
"""
import argparse
import hashlib
import json
import os
import re
import subprocess
import zipfile
from pathlib import Path
from fixtures import ROOT
from probe_latest import inspect


def blockers(metadata, result, report, expected, identity, head=None):
    package = identity['package']
    names = [p['name'] for p in metadata['patches'] if package in (p.get('compatiblePackages') or {})]
    if len(names) != len(set(names)) or set(names) != set(expected):
        return ['Generated catalog differs from the accepted complete catalog']
    problems = []
    if result is None:
        problems.append('Morphe produced no complete result file; inspect the patch log')
    else:
        applied = [p['name'] for p in result.get('appliedPatches', [])]
        if result.get('failedPatches') or len(applied) != len(set(applied)) or set(applied) != set(names):
            problems.append(f"Incomplete catalog: {len(applied)}/{len(names)} applied, {len(result.get('failedPatches', []))} failed")
        if (result.get('packageName'), result.get('packageVersion')) != (package, identity['version']):
            problems.append('Morphe reported a different APK package/version')
        steps = result.get('patchingSteps', [])
        if (len(steps) != 2 or {s.get('step') for s in steps} != {'PATCHING', 'REBUILDING'}
                or any(s.get('success') is not True for s in steps)):
            problems.append('Patching or APK rebuilding did not complete successfully')
    if not report:
        problems.append('No patch-time hook report')
    else:
        try:
            report_version_code = int(report.get('versionCode'))
        except (TypeError, ValueError):
            report_version_code = None
    if report and (report.get('schema'), report.get('fixtureSha256'), report.get('package'),
          report.get('version'), report_version_code, report.get('experimental')) != (
            2, identity['sha256'], package, identity['version'], identity['versionCode'], True):
        problems.append('Hook report is not for this exact experimental APK')
    elif report:
        if head is not None and report.get('featureHead') != head:
            problems.append('Hook report belongs to a different feature head')
        if not report.get('injections') or not report.get('fingerprints'):
            problems.append('Missing injection or fingerprint evidence')
        for hook in report.get('fingerprints', []):
            if hook.get('required') and (hook.get('status') != 'resolved' or
                  (hook.get('selection') == 'unique' and hook.get('candidateCount') != 1) or
                  (hook.get('origin') == 'apk' and hook.get('portableContractValidated') is not True)):
                problems.append('Unresolved native hook: ' + hook['hook'])
        validated = {
            hook['owner'] + '->' + hook['name'] + '(' + ''.join(hook['parameters']) + ')' + hook['returns']
            for hook in report['fingerprints']
            if hook.get('status') == 'resolved' and hook.get('required') and
            (hook.get('origin') == 'extension' or
             hook.get('origin') == 'apk' and hook.get('portableContractValidated') is True) and
            all(key in hook for key in ('owner', 'name', 'parameters', 'returns'))
        }
        for site in report.get('injections', []):
            if site.get('method') not in validated:
                problems.append('Injection has no validated hook: ' + str(site.get('method')))
    return problems


def output_identity(path):
    """Require a usable rebuilt APK before retaining the experimental output."""
    if not path.is_file() or path.stat().st_size == 0:
        raise ValueError('No patched APK was generated')
    try:
        with zipfile.ZipFile(path) as apk:
            if 'AndroidManifest.xml' not in apk.namelist() or 'classes.dex' not in apk.namelist():
                raise ValueError('Rebuilt APK is missing its manifest or primary DEX')
            if apk.testzip() is not None:
                raise ValueError('Rebuilt APK has a corrupt ZIP entry')
    except zipfile.BadZipFile as error:
        raise ValueError('Rebuilt APK is not a valid ZIP') from error
    with path.open('rb') as stream:
        sha = hashlib.file_digest(stream, 'sha256').hexdigest()
    return {'path': str(path), 'sha256': sha, 'sizeBytes': path.stat().st_size}


def read_result(path):
    if not path.is_file():
        return None, None
    try:
        result = json.loads(path.read_text())
        if not isinstance(result, dict):
            return None, 'Morphe result is not a JSON object'
        return result, None
    except (UnicodeError, json.JSONDecodeError) as error:
        return None, f'Morphe result is incomplete or invalid JSON: {error}'


def observed_applied(path, result):
    """Recover an early, complete applied-patch array for diagnosis only.

    Morphe can stop serializing halfway through the later failed-patches array.
    This observation is never passed to blockers() as a successful result.
    """
    if result is not None:
        entries = result.get('appliedPatches')
    elif path.is_file():
        try:
            raw = path.read_text()
            match = re.search(r'"appliedPatches"\s*:\s*', raw)
            entries = json.JSONDecoder().raw_decode(raw, match.end())[0] if match else None
        except (UnicodeError, json.JSONDecodeError):
            return []
    else:
        return []
    if not isinstance(entries, list) or any(not isinstance(p, dict) or
                                           not isinstance(p.get('name'), str) for p in entries):
        return []
    return [p['name'] for p in entries]


def patch_failures(log):
    """Retain actionable patch failures even if Morphe fails to serialize its JSON."""
    failures = re.findall(r'^Caused by: [^\n]*PatchException: ([^\r\n]+)', log, re.MULTILINE)
    for match in re.finditer(r'^SEVERE: FAILED: ([^\r\n]+)\r?\n(.*?)(?=^SEVERE: FAILED: |\Z)',
                             log, re.MULTILINE | re.DOTALL):
        name, block = match.groups()
        before_stack = block.split('\n\tat', 1)[0]
        details = re.findall(r'^app\.morphe\.patcher\.patch\.PatchException: ([^\r\n]+)',
                             before_stack, re.MULTILINE)
        failures.append(f'{name}: {details[-1]}' if details else name)
    return list(dict.fromkeys(failures))


def main():
    p = argparse.ArgumentParser(description=__doc__)
    for name in ('apk', 'bundle', 'cli', 'output'):
        p.add_argument('--' + name, type=Path, required=True)
    p.add_argument('--head', required=True)
    p.add_argument('--metadata', type=Path, default=ROOT / 'patches-list.json')
    p.add_argument('--source', required=True)
    p.add_argument('--expected-sha', help='Optional SHA-256 to pin the downloaded input APK')
    a = p.parse_args()
    out = a.output.resolve()
    out.mkdir(parents=True, exist_ok=True)
    result_path, log_path, report_path, patched = (out / name for name in
        ('morphe-result.json', 'morphe.log', 'tiktok-hook-report.json', 'patched.apk'))
    # A reused output directory must never make a later failed attempt look successful.
    for path in (result_path, log_path, report_path, patched, out / 'experimental-result.json'):
        path.unlink(missing_ok=True)
    identity = inspect(a.apk, a.source, a.expected_sha)
    metadata = json.loads(a.metadata.read_text())
    accepted = json.loads((ROOT / 'fixtures/tiktok/46.7.3/accepted-catalog.json').read_text())
    expected = [patch['name'] for patch in accepted['appliedPatches']]
    names = [p['name'] for p in metadata['patches'] if identity['package'] in (p.get('compatiblePackages') or {})]
    command = ['java', '-Xmx6g', '-jar', str(a.cli.resolve()), 'patch', '-p', str(a.bundle.resolve()),
               '--force', '--continue-on-error', '--exclusive', '--unsigned', '--result-file', str(result_path)]
    for name in names:
        command += ['-e', name]
    command += ['-o', str(patched), str(a.apk.resolve())]
    problems = []
    rebuilt = None
    try:
        with log_path.open('w') as log:
            try:
                process = subprocess.run(command, cwd=out, stdout=log, stderr=subprocess.STDOUT,
                                         env=dict(os.environ, TIKTOK_EXPERIMENTAL_PORTABLE='1',
                                                  TIKTOK_FEATURE_HEAD=a.head))
            except OSError as error:
                process = None
                problems.append(f'Could not run Morphe: {error}')
        result, result_error = read_result(result_path)
        applied_observation = observed_applied(result_path, result)
        report, report_error = read_result(report_path)
        problems = blockers(metadata, result, report, expected, identity, a.head)
        for error in (result_error, report_error):
            if error:
                problems.append(error)
        failures = patch_failures(log_path.read_text(errors='replace'))
        problems.extend('Patch failed: ' + failure for failure in failures[:40])
        if len(failures) > 40:
            problems.append(f'{len(failures) - 40} additional distinct patch failures in morphe.log')
        if process is not None and process.returncode:
            problems.append(f'Morphe exited with code {process.returncode}')
        if not problems:
            try:
                rebuilt = output_identity(patched)
            except (OSError, ValueError) as error:
                problems.append(str(error))
    finally:
        # Morphe can produce a partial APK even when it reports failed patches.
        if rebuilt is None:
            patched.unlink(missing_ok=True)
    (out / 'experimental-result.json').write_text(json.dumps({
        'schema': 1, 'head': a.head, 'candidate': identity, 'qualified': False,
        'status': 'blocked' if problems else 'experimental-catalog-passed',
        'blockers': problems, 'expectedPatchCount': len(expected),
        'observedAppliedPatches': applied_observation, 'patchedApk': rebuilt,
    }, indent=2) + '\n')
    print(f"Experimental catalog: {len(expected)} patches; {len(problems)} blockers; never qualified")


if __name__ == '__main__':
    main()
