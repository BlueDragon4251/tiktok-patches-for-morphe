#!/usr/bin/env python3
"""Fetch the pinned CLI embedding Patcher 2.0.1 for current Manager compatibility."""
import hashlib
import subprocess
from pathlib import Path
URL = 'https://github.com/MorpheApp/morphe-desktop/releases/download/v1.19.1/morphe-desktop-1.19.1-all.jar'
SHA256 = 'a51472252cd63b2eac635dbb5a154ff918bfc15268da89679feb3d760230c573'
if __name__ == '__main__':
    path = Path('morphe-modern.jar')
    subprocess.run(['curl', '-fLsS', '--proto', '=https', '--proto-redir', '=https', '--retry', '3',
                    '--max-time', '180', URL, '-o', str(path)], check=True)
    with path.open('rb') as stream:
        actual = hashlib.file_digest(stream, 'sha256').hexdigest()
    if actual != SHA256:
        raise SystemExit('Patcher 2.0.1 CLI SHA-256 mismatch')
