#!/usr/bin/env python3
"""Compare an acceptance hook inventory with another APK, without patching that APK.

Only a unique structural match is reported as a relocation. Ambiguity and missing
hooks remain explicit. This report never changes the supported-version allowlist.
"""
import argparse
import hashlib
import json
import re
import zipfile
from collections import defaultdict
from pathlib import Path

OBFUSCATED = re.compile(r'L(?:X|Y)/[^;]+;|Lkotlin/jvm/internal/(?:A[^;]+);?')
MEMBER = re.compile(r'^(\[*L[^;]+;|\[+[ZBCSIJFD])->([^(: ]+)(.*)$')


def normalized_type(value):
    return OBFUSCATED.sub('L?;', value)


def normalized_member(owner, name, schema=2):
    if owner.startswith(('Landroid/', 'Ljava/', 'Ljavax/')): return name
    if schema == 2 and not re.fullmatch(r'L[A-Z0-9]+|[a-zA-Z]{1,2}|invoke\$[0-9]+', name): return name
    return '*' 


def method_tokens(method, schema=2):
    descriptor = method.get_descriptor().replace(' ', '')
    tokens = [normalized_type(descriptor[1:])]
    for instruction in method.get_instructions():
        if schema == 2 and instruction.get_name() == 'nop': continue
        detail = ''
        for operand in instruction.get_operands():
            if len(operand) < 3:
                continue
            value = str(operand[2])
            opcode = instruction.get_name()
            if opcode.startswith('const-string'):
                # Androguard's operand value is the actual string, without output quoting.
                detail = 's:' + value
            elif opcode.startswith(('invoke-', 'iget', 'iput', 'sget', 'sput')):
                match = MEMBER.match(value)
                if not match:
                    raise ValueError('Unsupported reference format: ' + value)
                owner, name, tail = match.groups()
                if opcode.startswith('invoke-'):
                    detail = 'm:' + normalized_type(owner) + '->' + normalized_member(owner, name, schema) + normalized_type(tail.replace(' ', ''))
                else:
                    detail = 'f:' + normalized_type(owner) + '->' + normalized_member(owner, name, schema) + ':' + normalized_type(tail.strip(' :'))
            elif value.startswith(('L', '[')):
                detail = 't:' + normalized_type(value)
            break
        tokens.append(normalized_opcode(instruction.get_name()) + ' ' + detail)
    return tokens


def normalized_opcode(name):
    return name.replace('fill-array-data-payload', 'array-payload').replace('/', '-').replace('_', '-')


def digest(tokens):
    # DEX uses UTF-16 strings. Join valid surrogate pairs and match the JVM's
    # UTF-8 replacement byte ('?') for isolated surrogates in obfuscated strings.
    value = '\n'.join(tokens).encode('utf-16-le', 'surrogatepass').decode('utf-16-le', 'surrogatepass')
    return hashlib.sha256(value.encode('utf-8', 'replace')).hexdigest()


def field_shape_parts(owner):
    parts = ['super:' + normalized_type(owner.get_superclassname() or '')]
    parts += sorted('interface:' + normalized_type(x) for x in owner.get_interfaces())
    parts += sorted('field:' + normalized_type(f.get_descriptor()) + ':' + str(f.get_access_flags()) for f in owner.get_fields())
    return parts


def owner_shape(owner, schema=2):
    parts = field_shape_parts(owner)
    parts += sorted('method:' + normalized_member(owner.get_name(), m.get_name(), schema) + ':' + str(m.get_access_flags()) + ':' + digest(method_tokens(m, schema)) for m in owner.get_methods())
    return digest(parts)


def same_identity(h, c):
    return (c['owner'],c['name'],c['descriptor']) == (h['owner'],h['name'],'('+''.join(h['parameters'])+')'+h['returns'])


def contextual_candidates(hook, candidates):
    context=hook.get('semanticContext',{})
    return [c for c in candidates if
        (not context.get('stableOwner') or c['owner']==context['stableOwner']) and
        (not context.get('stableName') or c['name']==context['stableName']) and
        (not context.get('ownerShapeSha256') or c.get('ownerShapeSha256')==context['ownerShapeSha256']) and
        (not context.get('returnTypeShapeSha256') or c.get('returnTypeShapeSha256')==context['returnTypeShapeSha256'])]


def classify(hook, candidates, anchored=(), reviewed=None, apk_sha=None):
    if hook.get('origin')=='extension': return 'resolved',[]
    if not hook.get('structuralSha256'): return hook.get('status','missing'),[]
    selected=contextual_candidates(hook,candidates)
    if not selected: return ('contract-changed' if candidates or anchored else 'missing'),[]
    if len(selected)!=1:
        # This explicit lock is only valid for the reviewed APK's exact bytes.
        identity=hook['owner']+'->'+hook['name']+'('+''.join(hook['parameters'])+')'+hook['returns']
        if (reviewed and apk_sha==reviewed.get('sha256') and hook.get('fixtureContractValidated') is True
                and reviewed.get('methods',{}).get(identity)==hook.get('fixtureContractSha256')):
            exact=[c for c in selected if same_identity(hook,c)]
            if len(exact)==1:return 'resolved',exact
        return 'ambiguous',selected
    return ('resolved' if same_identity(hook,selected[0]) else 'relocated'),selected


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('baseline',type=Path);p.add_argument('apk',type=Path)
    p.add_argument('--output',type=Path,default=Path('tiktok-hook-migration.json'))
    p.add_argument('--markdown',type=Path,default=Path('tiktok-hook-migration.md'))
    p.add_argument('--verify-baseline',action='store_true');a=p.parse_args()
    report=json.loads(a.baseline.read_text());schema=report.get('schema')
    if schema not in (1,2):p.error('Unsupported report schema')
    wanted={h['structuralSha256'] for h in report['fingerprints'] if h.get('structuralSha256')}
    anchors={(h.get('semanticContext',{}).get('stableOwner'),h.get('semanticContext',{}).get('stableName')) for h in report['fingerprints']}
    from loguru import logger
    logger.remove()
    from androguard.core.dex import DEX
    from androguard.core.apk import APK
    with a.apk.open('rb') as stream:sha=hashlib.file_digest(stream,'sha256').hexdigest()
    apk=APK(str(a.apk))
    if a.verify_baseline:
        if report.get('fixtureSha256') and report['fixtureSha256']!=sha:raise SystemExit('Baseline fixture SHA-256 mismatch')
        if (apk.get_package(),apk.get_androidversion_name())!=(report['package'],report['version']):raise SystemExit('Baseline package/version mismatch')
    reviewed=None
    if a.verify_baseline and schema==2:
        contracts=Path(__file__).resolve().parents[2]/'patches/src/main/resources/tiktok-contracts'/f"{report['version']}.json"
        if contracts.is_file():reviewed=json.loads(contracts.read_text())
    found=defaultdict(list);anchored=defaultdict(list);return_shapes={}
    with zipfile.ZipFile(a.apk) as archive:
        for name in sorted(archive.namelist()):
            if not re.fullmatch(r'classes\d*\.dex',name):continue
            dex=DEX(archive.read(name))
            for owner in dex.get_classes():
                if schema==2:return_shapes[owner.get_name()]=digest(field_shape_parts(owner))
                shape=None
                for method in owner.get_methods():
                    if method.get_code() is None:continue
                    key=digest(method_tokens(method,schema));anchor=(method.get_class_name(),method.get_name())
                    if key not in wanted and anchor not in anchors:continue
                    if schema==2 and shape is None:shape=owner_shape(owner,schema)
                    candidate={'dex':name,'owner':method.get_class_name(),'name':method.get_name(),'descriptor':method.get_descriptor().replace(' ',''),'ownerShapeSha256':shape}
                    if key in wanted:found[key].append(candidate)
                    if anchor in anchors:anchored[anchor].append(candidate)
            del dex
    for candidates in list(found.values())+list(anchored.values()):
        for candidate in candidates:
            candidate['returnTypeShapeSha256']=return_shapes.get(candidate['descriptor'].split(')',1)[1])
    rows=[];invalid=[]
    for hook in report['fingerprints']:
        candidates=found.get(hook.get('structuralSha256'),[]);context=hook.get('semanticContext',{})
        state,selected=classify(hook,candidates,anchored.get((context.get('stableOwner'),context.get('stableName')),[]),reviewed,sha)
        pinned=state=='resolved' and len(contextual_candidates(hook,candidates))>1
        rows.append({'hook':hook['hook'],'required':hook.get('required',True),'status':state,
            'resolutionBasis':'verified-fixture-contract' if pinned else 'structural-context',
            'newApkInjectionApproved':False,'candidates':selected or candidates})
        if a.verify_baseline and hook.get('origin')!='extension':
            if schema==2 and hook.get('required',True) and state!='resolved':invalid.append(hook['hook'])
            elif hook.get('structuralSha256') and not any(same_identity(hook,c) for c in selected):invalid.append(hook['hook'])
    counts={state:sum(r['status']==state for r in rows) for state in sorted({r['status'] for r in rows})}
    a.output.write_text(json.dumps({'schema':2,'baselineSchema':schema,'apkSha256':sha,'package':apk.get_package(),'version':apk.get_androidversion_name(),'featureHead':report.get('featureHead'),'baselineSelfComparison':'failed' if invalid else ('passed' if a.verify_baseline else 'not-requested'),'summary':counts,'hooks':rows},indent=2))
    a.markdown.write_text('# TikTok hook migration\n\n'+f'Package: `{apk.get_package()}`; version: `{apk.get_androidversion_name()}`; SHA-256: `{sha}`.\n\n'+'Discovery does not activate compatibility. Changed or ambiguous contracts require review.\n\n| Hook | State | Candidates |\n|---|---|---|\n'+'\n'.join(f"| `{r['hook'].replace('|','/')} ` | {r['status']} | {len(r['candidates'])} |" for r in rows)+'\n')
    print(json.dumps(counts))
    if invalid:raise SystemExit('Baseline contracts did not uniquely rediscover: '+', '.join(invalid))


if __name__=='__main__':main()
