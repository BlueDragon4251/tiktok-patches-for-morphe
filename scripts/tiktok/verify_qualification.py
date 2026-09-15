#!/usr/bin/env python3
"""Verify actual successful same-head runs before exposing qualified versions."""
import json
import os
import subprocess
from fixtures import fixtures


def validate_run(run, head, kind):
    expected = '.github/workflows/pr_' + ('accept' if kind == 'acceptance' else 'discover') + '_tiktok_46_7_3.yml'
    if run.get('conclusion') != 'success' or run.get('status') != 'completed' or run.get('head_sha') != head or run.get('path') != expected:
        raise ValueError(f'{kind}: qualification is not a successful run on {head}')


def main():
    repo = os.environ.get('GITHUB_REPOSITORY', 'BlueDragon4251/tiktok-patches-for-morphe')
    for f in fixtures():
        if not f.get('selectable'): continue
        q = f['qualification']
        for kind in ('acceptance', 'discovery'):
            run = json.loads(subprocess.check_output(['gh', 'api', f"repos/{repo}/actions/runs/{q[kind+'Run']}"]))
            validate_run(run, q['head'], kind)
        print(f"{f['id']}: same-head historical qualification confirmed; this change still requires its own matrix")

if __name__ == '__main__': main()
