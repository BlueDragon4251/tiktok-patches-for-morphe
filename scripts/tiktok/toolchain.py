#!/usr/bin/env python3
"""Fetch a pinned, hash-checked CLI; upstream latest cannot silently change CI."""
import hashlib
import subprocess
from pathlib import Path
# Desktop 1.18.1 embeds Patcher 1.15.1, matching the reported Manager runtime.
URL='https://github.com/MorpheApp/morphe-desktop/releases/download/v1.18.1/morphe-desktop-1.18.1-all.jar'
SHA256='1b506ab5f03d16a2f65026d5e0e1910d01fc1e2152f21eaeb44ed2f30856597b'
if __name__=='__main__':
    path=Path('morphe-desktop.jar')
    subprocess.run(['curl','-fLsS','--retry','3','--max-time','120',URL,'-o',str(path)],check=True)
    with path.open('rb') as stream:actual=hashlib.file_digest(stream,'sha256').hexdigest()
    if actual!=SHA256:raise SystemExit('Morphe CLI SHA-256 mismatch')
