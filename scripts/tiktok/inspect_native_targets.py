#!/usr/bin/env python3
"""Record focused DEX evidence for changed native hooks without exporting APK bytes."""
import argparse
import hashlib
import json
import re
import zipfile
from pathlib import Path


def inspect(apk, targets, related_prefix):
    from androguard.core.dex import DEX
    from loguru import logger
    logger.remove()
    with apk.open('rb') as stream:
        sha = hashlib.file_digest(stream, 'sha256').hexdigest()
    result = {'apkSha256': sha, 'targets': sorted(targets), 'classes': [],
              'relatedClasses': [], 'references': []}
    with zipfile.ZipFile(apk) as archive:
        for dex_name in sorted(archive.namelist()):
            if not re.fullmatch(r'classes\d*\.dex', dex_name):
                continue
            dex = DEX(archive.read(dex_name))
            for owner in dex.get_classes():
                name = owner.get_name()
                methods = list(owner.get_methods())
                if name in targets:
                    result['classes'].append({
                        'dex': dex_name, 'type': name,
                        'accessFlags': owner.get_access_flags(),
                        'superclass': owner.get_superclassname(),
                        'interfaces': list(owner.get_interfaces()),
                        'fields': [f.get_name() + ':' + f.get_descriptor() for f in owner.get_fields()],
                        'methods': [{
                            'name': m.get_name(), 'descriptor': m.get_descriptor().replace(' ', ''),
                            'accessFlags': m.get_access_flags(),
                            'registers': m.get_code().get_registers_size() if m.get_code() else None,
                            'instructions': [i.get_name() + ' ' + i.get_output()
                                             for i in m.get_instructions()]
                            if m.get_name() in ('execute', 'onFail') and m.get_code() else None,
                        } for m in methods],
                    })
                if name.startswith(related_prefix) and (
                        any(term in name for term in ('Risk', 'Turing', 'Callback')) or
                        any(t in (owner.get_superclassname(), *owner.get_interfaces()) for t in targets)):
                    result['relatedClasses'].append({
                        'dex': dex_name, 'type': name,
                        'superclass': owner.get_superclassname(),
                        'interfaces': list(owner.get_interfaces()),
                        'methods': [m.get_name() + m.get_descriptor().replace(' ', '')
                                    for m in methods if m.get_name() in ('execute', 'onFail')],
                    })
                for method in methods:
                    if not method.get_code():
                        continue
                    for instruction in method.get_instructions():
                        output = instruction.get_output()
                        if any(target in output for target in targets):
                            result['references'].append({
                                'dex': dex_name, 'caller': name + '->' + method.get_name() +
                                method.get_descriptor().replace(' ', ''),
                                'instruction': instruction.get_name() + ' ' + output,
                            })
            del dex
    result['relatedClasses'].sort(key=lambda item: item['type'])
    result['references'].sort(key=lambda item: (item['caller'], item['instruction']))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    parser.add_argument('--target', action='append', required=True)
    parser.add_argument('--related-prefix', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    args.output.write_text(json.dumps(inspect(args.apk, set(args.target), args.related_prefix),
                                      indent=2) + '\n')


if __name__ == '__main__':
    main()
