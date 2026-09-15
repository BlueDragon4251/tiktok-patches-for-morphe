#!/usr/bin/env python3
"""Full catalog application and discovery for one exact fixture."""
import argparse
import json
import os
import subprocess
import sys
from pathlib import Path
from fixtures import ROOT, select, verify


def validate_catalog(metadata,result,fixture,expected):
    names=[p['name'] for p in metadata['patches'] if fixture['package'] in (p.get('compatiblePackages') or {})]
    if not names or set(names)!=set(expected) or len(names)!=len(set(names)): raise ValueError('Generated catalog differs from reviewed full catalog')
    applied=[p['name'] for p in result.get('appliedPatches',[])]
    if result.get('failedPatches') or len(applied)!=len(set(applied)) or set(applied)!=set(names): raise ValueError('Catalog failed, skipped or duplicated patches')
    if any(not s.get('success') for s in result.get('patchingSteps',[])): raise ValueError('APK patching/rebuilding step failed')
    return names


def validate_hooks(report,fixture,head):
    if report.get('schema')!=2 or report.get('featureHead')!=head or report.get('fixtureSha256')!=fixture['sha256']: raise ValueError('Hook report head or fixture mismatch')
    if (report.get('package'),report.get('version'),int(report.get('versionCode',0)))!=(fixture['package'],fixture['version'],fixture['versionCode']): raise ValueError('Hook APK identity mismatch')
    hooks=report.get('fingerprints',[])
    if not hooks or not report.get('injections'): raise ValueError('Empty hook/injection evidence')
    invalid=[h['hook'] for h in hooks if h.get('required') and (h.get('status')!='resolved' or (h.get('selection')=='unique' and h.get('candidateCount')!=1))]
    if invalid: raise ValueError('Mandatory hooks unresolved: '+', '.join(invalid))


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for arg in ('fixture','head'):p.add_argument('--'+arg,required=True)
    for arg in ('apk','bundle','cli','output'):p.add_argument('--'+arg,type=Path,required=True)
    p.add_argument('--metadata',type=Path,default=ROOT/'patches-list.json');a=p.parse_args();f=select(a.fixture)
    verify(f,a.apk);out=a.output.resolve();out.mkdir(parents=True,exist_ok=True)
    expected=[x['name'] for x in json.loads((ROOT/'fixtures/tiktok'/f['catalog']).read_text())['appliedPatches']]
    metadata=json.loads(a.metadata.read_text());names=[x['name'] for x in metadata['patches'] if f['package'] in (x.get('compatiblePackages') or {})]
    if not names or set(names)!=set(expected):raise SystemExit('Generated catalog does not match reviewed catalog')
    command=['java','-Xmx6g','-jar',str(a.cli.resolve()),'patch','-p',str(a.bundle.resolve()),'--continue-on-error','--exclusive','--unsigned','--result-file','morphe-full-result.json']
    if not f.get('selectable'):command.append('--force') # Candidate CI only, never selectable metadata.
    for name in names:command+=['-e',name]
    command+=['-o',str(out/'patched.apk'),str(a.apk.resolve())]
    (out/'fixture.json').write_text(json.dumps(dict(f,testedHead=a.head),indent=2));(out/'patches-list.json').write_text(json.dumps(metadata,indent=2))
    with (out/'morphe-full.log').open('w') as log:
        result=subprocess.run(command,cwd=out,env=dict(os.environ,TIKTOK_FEATURE_HEAD=a.head,TIKTOK_FIXTURE_SHA256=f['sha256']),stdout=log,stderr=subprocess.STDOUT)
    if result.returncode:raise SystemExit(f'Catalog application failed ({result.returncode}); see {out}/morphe-full.log')
    applied=json.loads((out/'morphe-full-result.json').read_text());validate_catalog(metadata,applied,f,expected)
    report=json.loads((out/'tiktok-hook-report.json').read_text());validate_hooks(report,f,a.head)
    subprocess.run([sys.executable,str(ROOT/'scripts/tiktok/rediscover_hooks.py'),str(out/'tiktok-hook-report.json'),str(a.apk.resolve()),'--verify-baseline','--output',str(out/'tiktok-hook-migration.json'),'--markdown',str(out/'tiktok-hook-migration.md')],check=True)
    (out/'qualification.json').write_text(json.dumps({'schema':1,'fixture':f['id'],'head':a.head,'package':f['package'],'version':f['version'],'sha256':f['sha256'],'catalogCount':len(names),'catalog':'passed','contracts':'passed','discovery':'passed','baselineSelfComparison':'passed'},indent=2))
    (out/'patched.apk').unlink(missing_ok=True)
    print(f"{f['id']}: {len(names)} patches; contracts and discovery passed on {a.head}")
if __name__=='__main__':main()
