#!/usr/bin/env python3
"""Reproducible, exhaustive source audit surface; not a semantic Kotlin parser."""
import argparse
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCOPES = ('patches/src/main/kotlin/app/morphe/patches/tiktok',
          'extensions/tiktok/src/main', 'extensions/tiktok/stub/src/main')
PATTERNS = {
    'fingerprint': r'\b(?:object|class|val|fun)\s+(\w*[Ff]ingerprint\w*)',
    'native-type': r'L(?:X|Y|com|android|java|kotlin|androidx)/[^\s";]+;|\b(?:X|Y)\.[A-Za-z0-9_$]+',
    'member': r'->[^\s"\n]+|\b(?:name|fieldName|methodName|definingClass|returnType|parameters)\s*(?:==|=|in)[^\n]+',
    'register': r'\b[vp]\d+\b|\b(?:register[A-G]|registerCount|startRegister|parameterTypes|parameters)\b[^\n]*',
    'instruction-selection': r'\b(?:indexOf\w*|first\w*|last\w*|single\w*|getInstruction|addInstructions?\w*|removeInstructions?|replaceInstructions?)\s*(?:[<(]|\{)[^\n]*',
    'control-flow': r'\b(?:RETURN\w*|IF_\w+|GOTO\w*|\w*SWITCH\w*|tryBlocks|exceptionHandlers)\b|\b(?:return-\w+|if-\w+|goto(?:/\w+)?)\b',
    'resource': r'0x7[fF][0-9a-fA-F]{6}|\b(?:getIdentifier|findViewById|resourceId|R\.\w+\.\w+|getResourceEntryName)\b[^\n]*',
    'reflection': r'\b(?:getDeclared\w+|getField|getMethod|Class\.forName|loadClass|setAccessible)\b[^\n]*',
    'version': r'\b\d{2}\.\d+\.\d+\b|AppCompatibilities\.tiktok\w*\([^\n]*',
    'literal': r'"(?:\\.|[^"\\\n])*"',
}


def classify(kind, text):
    if re.search(r'(?:LX/|LY/|\bX\.|\bY\.|\b[vp]\d+\b|0x7[fF])', text):
        return 'hard-coded'
    if kind == 'instruction-selection' and re.search(r'\b(?:first|last|indexOfFirst|indexOfLast)', text):
        return 'requires-cardinality-or-dataflow-review'
    if kind == 'version':
        return 'version-specific'
    if kind in ('native-type', 'member'):
        return 'stable-named-anchor' if not re.search(r'LIZ|LJ[A-Z]|LL[A-Z]', text) else 'hard-coded'
    return 'structural-or-semantic-evidence'


def build(root=ROOT):
    files = sorted(p for scope in SCOPES for p in (root / scope).rglob('*')
                   if p.suffix in ('.kt', '.java', '.xml'))
    files += [root / 'patches/src/main/kotlin/app/morphe/patches/shared/compat/AppCompatibilities.kt']
    records, patches = [], []
    for path in files:
        source = path.read_text()
        relative = path.relative_to(root).as_posix()
        declared = [{'symbol': m[1], 'name': m[2]} for m in re.finditer(
            r'val\s+(\w+)\s*=\s*(?:bytecodePatch|resourcePatch)\s*\(\s*name\s*=\s*"([^"]+)"', source)]
        for patch in declared:
            patches.append(dict(patch, source=relative,
                                dependencies=re.findall(r'dependsOn\(([\s\S]*?)\)', source)))
        findings = []
        for kind, pattern in PATTERNS.items():
            for match in re.finditer(pattern, source):
                value = match.group(0)
                findings.append({'kind': kind, 'line': source.count('\n', 0, match.start()) + 1,
                                 'text': value, 'category': classify(kind, value)})
        records.append({'source': relative, 'sha256': hashlib.sha256(source.encode()).hexdigest(),
                        'patches': declared, 'findings': sorted(findings, key=lambda r: (r['line'], r['kind'], r['text']))})
    return {'schema': 1, 'kind': 'source-audit', 'patches': sorted(patches, key=lambda r: r['name']),
            'files': records}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=ROOT / 'docs/tiktok-source-inventory.json')
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    result = json.dumps(build(), indent=2, ensure_ascii=False) + '\n'
    if args.check:
        if not args.output.exists() or args.output.read_text() != result:
            raise SystemExit('Source inventory is stale; run scripts/tiktok/inventory.py')
    else:
        args.output.write_text(result)
    print(f'Source inventory: {len(build()["patches"])} patches')


if __name__ == '__main__':
    main()
