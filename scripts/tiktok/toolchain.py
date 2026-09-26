#!/usr/bin/env python3
"""Fetch a pinned, hash-checked CLI; upstream latest cannot silently change CI."""
import hashlib
import subprocess
from pathlib import Path
URL='https://github.com/MorpheApp/morphe-desktop/releases/download/v1.15.1/morphe-desktop-1.15.1-all.jar'
SHA256='6ae9954cd4e22e61055cf9ef6b0bbd25556d2092f354031828f824dc0f7364e1'
if __name__=='__main__':
    path=Path('morphe-desktop.jar')
    subprocess.run(['curl','-fLsS','--retry','3','--max-time','120',URL,'-o',str(path)],check=True)
    with path.open('rb') as stream:actual=hashlib.file_digest(stream,'sha256').hexdigest()
    if actual!=SHA256:raise SystemExit('Morphe CLI SHA-256 mismatch')
