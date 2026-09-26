#!/usr/bin/env python3
"""Verify actual successful same-head runs before exposing qualified versions."""
import json
import os
import subprocess
import io
import zipfile
from pathlib import Path
from fixtures import fixtures

# The historical device-confirmed baseline predates the matrix evidence format.
BASELINE = ('global-46.7.3', 'b9e96e64e94ac0f9ea229dd0ba743f1930121a8b6941cf9fd87191604da0129e',
            '232bf9c28134db0b9bf2f9e8cf4a3f8021681667', 34620836565, 34620836595)


def validate_run(run, head, kind):
    expected = '.github/workflows/pr_' + ('accept' if kind == 'acceptance' else 'discover') + '_tiktok_46_7_3.yml'
    if run.get('conclusion') != 'success' or run.get('status') != 'completed' or run.get('head_sha') != head or run.get('path') != expected:
        raise ValueError(f'{kind}: qualification is not a successful run on {head}')


def validate_evidence(evidence, fixture, head):
    expected = ('fixture','head','package','version','sha256')
    actual = (fixture['id'],head,fixture['package'],fixture['version'],fixture['sha256'])
    if tuple(evidence.get(key) for key in expected) != actual:
        raise ValueError(f"Qualification artifact identifies another APK/head: {evidence}")
    catalog = Path(__file__).resolve().parents[2]/'fixtures/tiktok'/fixture['catalog']
    if evidence.get('catalogCount') != len(json.loads(catalog.read_text())['appliedPatches']):
        raise ValueError('Qualification artifact lacks the complete patch catalog')
    if any(evidence.get(key) != 'passed' for key in ('catalog','contracts','discovery','baselineSelfComparison')):
        raise ValueError('Qualification artifact has incomplete contracts or discovery')


def artifact_evidence(repo, run_id, fixture, head, kind):
    listing = json.loads(subprocess.check_output(['gh','api',f'repos/{repo}/actions/runs/{run_id}/artifacts']))
    prefix = f"tiktok-{fixture['id']}-{kind}-{head}"
    artifacts = [a for a in listing.get('artifacts',[]) if a.get('name') == prefix and not a.get('expired')]
    if len(artifacts) != 1: raise ValueError(f'{kind}: expected one unexpired per-version evidence artifact on {head}')
    archive = subprocess.check_output(['gh','api',f"repos/{repo}/actions/artifacts/{artifacts[0]['id']}/zip"])
    with zipfile.ZipFile(io.BytesIO(archive)) as z:
        matches = [n for n in z.namelist() if n.endswith(f"{fixture['id']}/qualification.json")]
        if len(matches) != 1: raise ValueError(f'{kind}: expected one qualification.json in artifact')
        validate_evidence(json.loads(z.read(matches[0])),fixture,head)


def main():
    repo = os.environ.get('GITHUB_REPOSITORY', 'BlueDragon4251/tiktok-patches-for-morphe')
    for f in fixtures():
        if not f.get('selectable'): continue
        q = f['qualification']
        for kind in ('acceptance', 'discovery'):
            run = json.loads(subprocess.check_output(['gh', 'api', f"repos/{repo}/actions/runs/{q[kind+'Run']}"]))
            validate_run(run, q['head'], kind)
            if (f['id'],f['sha256'],q['head'],q['acceptanceRun'],q['discoveryRun']) != BASELINE:
                artifact_evidence(repo,q[kind+'Run'],f,q['head'],kind)
        print(f"{f['id']}: same-head exact-fixture qualification confirmed; this change still requires its own matrix")

if __name__ == '__main__': main()
