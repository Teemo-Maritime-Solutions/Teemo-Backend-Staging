"""Synthetic checks of endpoint conditioning, vessel leakage and frozen registration."""
from copy import deepcopy
from dataclasses import asdict
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import MagicMock, patch

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from build_enc_pilot import mesh
from build_graph import xyz
from enc_snapshot import LAYERS_V2
from od_corridor_model import OdObjective, CONFIG
from od_experiment import HOLDOUTS, OBJECTIVES, COHORT, PURPOSE, verify, code_hashes, dependencies
from acquire_od_holdout import acquire
from freeze_od_experiment import freeze
from regional_experiment import sha
from synthetic_fixtures import synthetic_enc_source
from test_regional_corridors import domain, GOOD, BAD
from validate_corridor_ais import evaluate_case
from validate_regional_ais import FROZEN_CONFIG, CRITERIA
from scipy.spatial import cKDTree
from scipy.sparse import csr_matrix


def track(key,obs,fold):
    return dict(trackKey=key,observations=obs,fold=fold,vesselGroup=str(fold),trainingStatus='USED')


class ConditioningTests(unittest.TestCase):
    def test_same_vessel_fold_excluded_across_all_source_dates(self):
        objective=OdObjective(domain(),[],0,[track('date1',GOOD,1),track('date2',GOOD,1),track('date3',GOOD,2)])
        objective.excluded_fold=1
        ids=objective.choose((GOOD[0][1],GOOD[-1][1]))
        self.assertTrue(ids)
        self.assertEqual('date3',objective.last_selection['trackKey'])
        self.assertTrue(all(objective.base.segments[i]['trackKey']=='date3' for i in ids))

    def test_never_reverse_or_mix_coherent_tracks(self):
        objective=OdObjective(domain(),[],0,[track('a',GOOD,0),track('b',GOOD,1)])
        ids=objective.choose((GOOD[0][1],GOOD[-1][1]))
        self.assertEqual({0,1},ids)
        self.assertEqual(set(),objective.choose((GOOD[-1][1],GOOD[0][1])))
        self.assertEqual('NO_MATCH_DISTANCE_OBJECTIVE',objective.last_selection['status'])

    def test_no_training_and_outside_radius_return_no_support(self):
        objective=OdObjective(domain(),[],0,[])
        self.assertEqual(set(),objective.choose((GOOD[0][1],GOOD[-1][1])))
        objective=OdObjective(domain(),[],0,[track('a',GOOD,0)])
        self.assertFalse(objective.choose(((-.009,.009),(.009,.009))))
        objective.excluded_fold=0
        self.assertFalse(objective.choose((GOOD[0][1],GOOD[-1][1])))

    def test_real_search_metrics_restrictions_and_no_match_distance_equivalence(self):
        source=synthetic_enc_source()
        source['layerRegistryVersion']=2
        for key,name in LAYERS_V2.items():
            source['layers'].setdefault(str(key),dict(metadata=dict(name='Harbor.'+name),objectIdField='OBJECTID',features=[]))
        nodes,edges,_,_,water=mesh(source,dict(id='SYNTHETIC'),domain().config)
        objective=OdObjective(water,edges,len(nodes),[track('a',GOOD,0)])
        points=[n['point'] for n in nodes]
        lookup={(e['fromNode'],e['toNode']):e for e in edges}
        tree=cKDTree(xyz(points))
        matrix=csr_matrix(([e['distanceM'] for e in edges],([e['fromNode'] for e in edges],[e['toNode'] for e in edges])),shape=(len(nodes),len(nodes)))
        before=deepcopy(edges)
        case=evaluate_case('test',GOOD,points,lookup,water,tree,matrix,objective)
        self.assertEqual('PHYSICAL_MODEL_MEASURED',case['status'])
        self.assertEqual('PASSAGE_NOT_EVALUATED',case['passageStatus'])
        self.assertTrue(case['trainingSegmentIndices'])
        self.assertTrue(case['unevaluatedRestrictionIds'])
        objective.excluded_fold=0
        unsupported=evaluate_case('test',GOOD,points,lookup,water,tree,matrix,objective)
        baseline=evaluate_case('test',GOOD,points,lookup,water,tree,matrix,None)
        self.assertAlmostEqual(baseline['predictedDistanceM'],unsupported['predictedDistanceM'],places=6)
        self.assertFalse(unsupported['trainingSegmentIndices'])
        bad=evaluate_case('bad',BAD,points,lookup,water,tree,matrix,objective)
        self.assertEqual('OBSERVATION_CHART_WATER_CONFLICT',bad['status'])
        self.assertEqual(before,edges)


class OdRegistrationTests(unittest.TestCase):
    def setUp(self):
        self.actual=dict(artifactSha256='G',chartSourceSha256='C',modelSha256='M',protocolSha256='P',
                         implementation={'acquire_od_holdout.py':'A'},dependencies={})
        self.model=dict(modelVersion='V',datasets=[dict(source=dict(sha256='T'))])
        self.record=dict(schemaVersion=1,purpose=PURPOSE,holdouts=HOLDOUTS,objectives=OBJECTIVES,
            parameters=asdict(FROZEN_CONFIG),cohortParameters=asdict(COHORT),criteria=CRITERIA,
            modelParameters=CONFIG,modelVersion='V',trainingSourceSha256s=['T'],
            createdAt='2026-09-21T00:00:00+00:00',**self.actual)
        self.source=dict(HOLDOUTS['september'],sha256='H',acquirerSha256='A',acquisitionStartedAt='2026-09-21T01:00:00+00:00')

    def test_binding_source_date_model_and_vessel_parameters(self):
        verify(self.record,self.actual,self.source,self.model)
        for key in (*self.actual,'parameters','cohortParameters','criteria','modelParameters','modelVersion','trainingSourceSha256s'):
            with self.assertRaises(ValueError): verify(dict(self.record,**{key:'CHANGED'}),self.actual,self.source,self.model)
        for changes in (dict(sha256='T'),dict(acquirerSha256='BAD'),dict(url='https://example.com'),
                        dict(acquisitionStartedAt='2000-01-01T00:00:00+00:00'),dict(acquisitionStartedAt='2026-09-21T01:00:00')):
            with self.assertRaises(ValueError): verify(self.record,self.actual,dict(self.source,**changes),self.model)

    def test_freeze_rejects_present_future_source_before_other_work(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            (root/HOLDOUTS['october']['filename']).touch()
            with patch('freeze_od_experiment.physical_context') as context:
                with self.assertRaisesRegex(ValueError,'already present'):
                    freeze(root/'g',root/'c',root/'m',root/'p',root/'v',root,root/'r',root/'b')
                context.assert_not_called()

    def test_download_success_receipt_binding_and_overwrite_rejection(self):
        import zstandard
        payload=zstandard.ZstdCompressor().compress(b'SYNTHETIC')
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            registration=root/'registration.json'
            record=dict(self.record,implementation=code_hashes(),dependencies=dependencies(),createdAt='2000-01-01T00:00:00+00:00')
            registration.write_text(json.dumps(record))
            response=MagicMock()
            response.url=HOLDOUTS['september']['url']
            response.headers={'Content-Length':str(len(payload))}
            response.read.return_value=payload
            response.__enter__.return_value=response
            with patch('acquire_od_holdout.build_opener') as opener:
                opener.return_value.open.return_value=response
                acquire('september',registration,root/'download')
                with self.assertRaises(ValueError): acquire('september',registration,root/'download')
                self.assertEqual(1,opener.call_count)
            receipt=json.loads((root/'download'/(HOLDOUTS['september']['filename']+'.receipt.json')).read_text())
            self.assertEqual(sha(registration),receipt['registrationSha256'])
            self.assertEqual(hashlib.sha256(payload).hexdigest(),receipt['sha256'])
            self.assertEqual(code_hashes()['acquire_od_holdout.py'],receipt['acquirerSha256'])

    def test_invalid_download_is_not_published(self):
        for payload,length in ((b'html','4'),(bytes.fromhex('28b52ffd'),'100'),(b'','400000001')):
            with tempfile.TemporaryDirectory() as tmp:
                root=Path(tmp)
                record=dict(self.record,implementation=code_hashes(),dependencies=dependencies(),createdAt='2000-01-01T00:00:00+00:00')
                registration=root/'registration.json'
                registration.write_text(json.dumps(record))
                response=MagicMock()
                response.url=HOLDOUTS['september']['url']
                response.headers={'Content-Length':length}
                response.read.return_value=payload
                response.__enter__.return_value=response
                with patch('acquire_od_holdout.build_opener') as opener:
                    opener.return_value.open.return_value=response
                    with self.assertRaises(ValueError): acquire('september',registration,root/'download')
                self.assertFalse((root/'download').exists())


if __name__=='__main__': unittest.main()
