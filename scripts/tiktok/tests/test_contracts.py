import copy
import json
import sys
import tempfile
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fixtures import fixtures, generated, select, verify
from rediscover_hooks import classify, normalized_member, normalized_type, normalized_opcode
from run_fixture import validate_catalog, validate_hooks
from verify_qualification import validate_run, validate_evidence

class DiscoveryTests(unittest.TestCase):
    def setUp(self):
        self.hook={'hook':'callback','owner':'LX/Old;','name':'onDoubleTap','parameters':['Landroid/view/MotionEvent;'],'returns':'Z','structuralSha256':'hash','semanticContext':{'stableName':'onDoubleTap','ownerShapeSha256':'family'}}
        self.candidate={'owner':'LX/New;','name':'onDoubleTap','descriptor':'(Landroid/view/MotionEvent;)Z','ownerShapeSha256':'family'}
    def test_relocation_ignores_obfuscated_owner(self):
        self.assertEqual('relocated',classify(self.hook,[self.candidate])[0])
        self.assertEqual(normalized_type('LX/Old;'),normalized_type('LX/New;'))
    def test_ambiguity_never_uses_first(self):
        other=dict(self.candidate,owner='LX/Other;')
        for candidates in ([self.candidate,other],[other,self.candidate]):self.assertEqual('ambiguous',classify(self.hook,candidates)[0])
    def test_missing_and_contract_change_are_distinct(self):
        self.assertEqual('missing',classify(self.hook,[])[0])
        self.assertEqual('contract-changed',classify(self.hook,[dict(self.candidate,ownerShapeSha256='other')])[0])
        self.assertEqual('contract-changed',classify(self.hook,[],[self.candidate])[0])
    def test_stable_named_anchors_survive_normalization(self):
        self.assertEqual('getShowType',normalized_member('Lcom/tiktok/ACLCommonShare;','getShowType'))
        self.assertEqual('*',normalized_member('LX/Changed;','LJII'))
        self.assertEqual('onDoubleTap',normalized_member('LX/Changed;','onDoubleTap'))
    def test_payload_names_match_the_jvm_parser(self):
        self.assertEqual('array-payload',normalized_opcode('fill-array-data-payload'))
    def test_fixture_lock_never_disambiguates_a_different_apk(self):
        h=dict(self.hook,fixtureContractValidated=True,fixtureContractSha256='contract')
        exact=dict(self.candidate,owner=h['owner'])
        reviewed={'sha256':'approved','methods':{'LX/Old;->onDoubleTap(Landroid/view/MotionEvent;)Z':'contract'}}
        self.assertEqual('resolved',classify(h,[self.candidate,exact],reviewed=reviewed,apk_sha='approved')[0])
        self.assertEqual('ambiguous',classify(h,[self.candidate,exact],reviewed=reviewed,apk_sha='other')[0])
        self.assertEqual('ambiguous',classify(dict(h,fixtureContractValidated=False),[self.candidate,exact],reviewed=reviewed,apk_sha='approved')[0])

class QualificationTests(unittest.TestCase):
    def test_same_version_different_sha_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            apk=Path(tmp)/'same-version.apk';apk.write_bytes(b'different bytes')
            with self.assertRaisesRegex(ValueError,'SHA-256 mismatch'):verify(select('global-46.7.3'),apk,metadata=False)
    def test_qualification_requires_all_evidence(self):
        for key in ('head','acceptanceRun','discoveryRun','runtime'):
            f=copy.deepcopy(select('global-46.7.3'));del f['qualification'][key]
            with tempfile.TemporaryDirectory() as tmp:
                p=Path(tmp)/'fixtures.json';p.write_text(json.dumps({'schema':1,'fixtures':[f]}))
                with self.assertRaises(ValueError):list(fixtures(p))
    def test_pending_failed_or_different_head_cannot_qualify(self):
        run={'conclusion':'success','status':'completed','head_sha':'head','path':'.github/workflows/pr_accept_tiktok_46_7_3.yml'}
        validate_run(run,'head','acceptance')
        for change in ({'head_sha':'old'},{'status':'in_progress'},{'conclusion':'failure'}):
            with self.assertRaises(ValueError):validate_run(dict(run,**change),'head','acceptance')
    def test_partial_catalog_cannot_pass(self):
        f=select('global-46.7.3');metadata={'patches':[{'name':n,'compatiblePackages':{f['package']:[f['version']]}} for n in ['A','B']]}
        result={'appliedPatches':[{'name':'A'}],'failedPatches':[]}
        with self.assertRaises(ValueError):validate_catalog(metadata,result,f,['A','B'])
        result['appliedPatches'].append({'name':'B'})
        with self.assertRaises(ValueError):validate_catalog(metadata,result,f,['A','B'])
        result.update(packageName=f['package'],packageVersion=f['version'],patchingSteps=[{'step':s,'success':True} for s in ['PATCHING','REBUILDING']])
        self.assertEqual(['A','B'],validate_catalog(metadata,result,f,['A','B']))
    def test_report_head_fixture_and_cardinality_are_required(self):
        f=select('global-46.7.3');report={'schema':2,'featureHead':'head','fixtureSha256':f['sha256'],'package':f['package'],'version':f['version'],'versionCode':f['versionCode'],'fingerprints':[{'hook':'a','required':True,'status':'resolved','selection':'unique','candidateCount':1}],'injections':[{}]}
        validate_hooks(report,f,'head')
        for change in ({'featureHead':'old'},{'fixtureSha256':'other'},{'fingerprints':[]}):
            with self.assertRaises(ValueError):validate_hooks(dict(report,**change),f,'head')
        report['fingerprints'][0]['candidateCount']=2
        with self.assertRaises(ValueError):validate_hooks(report,f,'head')
        report['fingerprints'][0].update(candidateCount=1,origin='apk',fixtureContractValidated=False)
        with self.assertRaises(ValueError):validate_hooks(report,f,'head')
        report['fingerprints'][0]['fixtureContractValidated']=True
        validate_hooks(report,f,'head')

    def test_new_version_evidence_cannot_reuse_a_sha_head_or_partial_catalog(self):
        f=select('global-46.7.3')
        count=len(json.loads((Path(__file__).resolve().parents[3]/'fixtures/tiktok'/f['catalog']).read_text())['appliedPatches'])
        evidence=dict(fixture=f['id'],head='head',package=f['package'],version=f['version'],sha256=f['sha256'],
                      catalogCount=count,catalog='passed',contracts='passed',discovery='passed',baselineSelfComparison='passed')
        validate_evidence(evidence,f,'head')
        for change in (dict(sha256='another apk'),dict(head='other head'),dict(catalogCount=count-1),dict(contracts='failed'),dict(baselineSelfComparison='missing')):
            with self.subTest(change=change),self.assertRaises(ValueError):validate_evidence(dict(evidence,**change),f,'head')

if __name__=='__main__':unittest.main()
