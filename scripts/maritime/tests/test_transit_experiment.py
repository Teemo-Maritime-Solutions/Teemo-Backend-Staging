"""Synthetic transit registration and end-to-end census/metric checks."""
from collections import Counter
from contextlib import ExitStack
from copy import deepcopy
from dataclasses import asdict
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import MagicMock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from build_enc_pilot import mesh
from regional_corridor_model import CorridorConfig, training_segments
from regional_experiment import sha
from test_regional_corridors import domain, GOOD, BAD
from synthetic_fixtures import synthetic_enc_source
from enc_snapshot import LAYERS_V2
from transit_experiment import HOLDOUTS, OBJECTIVES, COHORT, PURPOSE, verify, code_hashes, dependencies
from validate_regional_ais import FROZEN_CONFIG, CRITERIA
from validate_transit_ais import validate
from freeze_transit_experiment import freeze
from acquire_transit_holdout import acquire
from compare_transit_runs import paired


class TransitRegistrationTests(unittest.TestCase):
    def setUp(self):
        self.bindings = dict(artifactSha256='GRAPH', chartSourceSha256='CHART',
            modelSha256='MODEL', protocolSha256='PROTOCOL', dependencies={'python':'TEST'},
            implementation={'acquire_transit_holdout.py':'ACQUIRER'})
        self.model = dict(modelVersion='MODEL', trainingSource=dict(sha256='TRAINING'))
        self.record = dict(schemaVersion=1, purpose=PURPOSE, holdouts=HOLDOUTS,
            objectives=OBJECTIVES, parameters=asdict(FROZEN_CONFIG), criteria=deepcopy(CRITERIA),
            cohortParameters=asdict(COHORT), modelVersion='MODEL',
            createdAt='2026-09-16T01:00:00+00:00', **self.bindings)

    def source(self, month='july'):
        return dict(HOLDOUTS[month], sha256='HELDOUT', acquirerSha256='ACQUIRER',
                    acquisitionStartedAt='2026-09-16T02:00:00+00:00')

    def test_both_dates_and_objectives_are_bound(self):
        for month in HOLDOUTS:
            for objective in OBJECTIVES:
                verify(self.record, self.bindings, self.source(month), objective, self.model)

    def test_changed_cohort_threshold_code_environment_and_model_rejected(self):
        for key in (*self.bindings, 'cohortParameters', 'parameters', 'criteria', 'modelVersion', 'purpose'):
            changed = dict(self.record, **{key:'CHANGED'})
            with self.assertRaises(ValueError):
                verify(changed, self.bindings, self.source(), OBJECTIVES[1], self.model)

    def test_source_leakage_acquirer_chronology_and_switching_rejected(self):
        for change in (dict(sha256='TRAINING'), dict(acquirerSha256='CHANGED'),
                       dict(id='noaa-ais-regional-may-holdout'), dict(url='https://example.com'),
                       dict(acquisitionStartedAt='2026-09-16T00:00:00+00:00'),
                       dict(acquisitionStartedAt='2026-09-16T02:00:00')):
            with self.assertRaises(ValueError):
                verify(self.record, self.bindings, dict(self.source(), **change), OBJECTIVES[1], self.model)
        with self.assertRaises(ValueError):
            verify(self.record, self.bindings, self.source(), 'PICK_BEST', self.model)

    def test_freeze_rejects_existing_holdout_before_loading_artifacts(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / HOLDOUTS['august']['filename']).touch()
            with patch('freeze_transit_experiment.physical_context') as context:
                with self.assertRaisesRegex(ValueError, 'already present'):
                    freeze(root/'graph', root/'chart', root/'model', root/'protocol',
                           root/'geometry', root, root/'registration', root/'bundle')
                context.assert_not_called()

    def test_acquisition_rejects_mutated_registration_before_network(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            registration = root/'registration.json'
            registration.write_text(json.dumps(self.record))
            with patch('acquire_transit_holdout.build_opener') as opener:
                with self.assertRaises(ValueError): acquire('july', registration, root/'download')
                opener.assert_not_called()

    def test_all_earlier_frozen_implementations_are_still_bound(self):
        hashes = code_hashes()
        self.assertEqual(22, len(hashes))
        self.assertIn('validate_corridor_ais.py', hashes)
        self.assertIn('transit_cohort.py', hashes)

    def test_acquisition_rejects_truncation_wrong_format_and_oversized_length(self):
        for payload, length in ((b'HTML', '4'), (bytes.fromhex('28b52ffd'), '12'),
                                (b'', '400000001')):
            with tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                registration = root/'registration.json'
                record = dict(self.record, implementation=code_hashes(), dependencies=dependencies(),
                              createdAt='2000-01-01T00:00:00+00:00')
                registration.write_text(json.dumps(record))
                response = MagicMock()
                response.url = HOLDOUTS['july']['url']
                response.headers = {'Content-Length':length}
                response.read.return_value = payload
                response.__enter__.return_value = response
                with patch('acquire_transit_holdout.build_opener') as opener:
                    opener.return_value.open.return_value = response
                    with self.assertRaises(ValueError): acquire('july', registration, root/'download')
                self.assertFalse((root/'download').exists())

    def test_acquisition_binds_successful_receipt_and_refuses_overwrite(self):
        import zstandard
        payload = zstandard.ZstdCompressor().compress(b'SYNTHETIC_CSV\n')
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            registration = root/'registration.json'
            record = dict(self.record, implementation=code_hashes(), dependencies=dependencies(),
                          createdAt='2000-01-01T00:00:00+00:00')
            registration.write_text(json.dumps(record))
            response = MagicMock()
            response.url = HOLDOUTS['july']['url']
            response.headers = {'Content-Length':str(len(payload))}
            response.read.return_value = payload
            response.__enter__.return_value = response
            with patch('acquire_transit_holdout.build_opener') as opener:
                opener.return_value.open.return_value = response
                acquire('july', registration, root/'download')
                with self.assertRaises(ValueError): acquire('july', registration, root/'download')
                self.assertEqual(1, opener.call_count)
            path = root/'download'/HOLDOUTS['july']['filename']
            receipt = json.loads(path.with_name(path.name+'.receipt.json').read_text())
            self.assertEqual(sha(path), receipt['sha256'])
            self.assertEqual(sha(registration), receipt['registrationSha256'])
            self.assertEqual(record['implementation']['acquire_transit_holdout.py'], receipt['acquirerSha256'])


class TransitEvaluationTests(unittest.TestCase):
    def test_real_selection_search_metrics_and_failures_preserve_full_census(self):
        water = domain()
        source = synthetic_enc_source()
        source['layerRegistryVersion'] = 2
        for key, name in LAYERS_V2.items():
            source['layers'].setdefault(str(key), dict(metadata=dict(name='Harbor.'+name),
                objectIdField='OBJECTID', features=[]))
        nodes, edges, _, _, water = mesh(source, dict(id='SYNTHETIC'), water.config)
        segments, _, _ = training_segments({'GOOD':GOOD}, water, [-.01,-.01,.01,.01], 'TRAIN', CorridorConfig())
        returning = [(0,(-.009,-.008),10),(60,(.009,-.008),11),(120,(-.0089,-.008),12)]
        records = {'GOOD':GOOD, 'BAD':BAD, 'RETURN':returning}
        manifest = dict(graphVersion='SYNTHETIC', validationProfile=water.config.profile)
        model = dict(modelVersion='SYNTHETIC', trainingSource=dict(sha256='TRAINING'))
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            ais_path = root/'ais.zst'
            ais_path.write_bytes(bytes.fromhex('28b52ffd'))
            for name in ('graph', 'model', 'protocol', 'registration'):
                (root/name).write_text('{}')
            ais = dict(sha256='a'*64, registrationSha256=sha(root/'registration'))
            with ExitStack() as stack:
                mocks = {
                    'physical_context': (manifest, dict(sha256='CHART'), water, nodes, edges),
                    'read_model': (model, segments, CorridorConfig()),
                    'verify_receipt': (ais_path, ais), 'verify': None, 'bindings': {},
                    'chart_bounds': ([-.01,-.01,.01,.01], {}),
                }
                for name, value in mocks.items():
                    stack.enter_context(patch('validate_transit_ais.'+name, return_value=value))
                stack.enter_context(patch('validate_transit_ais.regional_observations',
                                         side_effect=lambda *args:(records, Counter())))
                reports = []
                for objective in OBJECTIVES:
                    output = root/(objective+'.json')
                    validate(root/'graph', root/'chart', root/'receipt', root/'model',
                             root/'protocol', output, objective, root/'registration')
                    reports.append(json.loads(output.read_text()))
                    with self.assertRaises(ValueError):
                        validate(root/'graph', root/'chart', root/'receipt', root/'model',
                                 root/'protocol', output, objective, root/'registration')
                with self.assertRaisesRegex(ValueError, 'registration is required'):
                    validate(root/'graph', root/'chart', root/'receipt', root/'model',
                             root/'protocol', root/'missing.json', OBJECTIVES[0])
                ais['registrationSha256'] = 'OTHER'
                with self.assertRaisesRegex(ValueError, 'another experiment'):
                    validate(root/'graph', root/'chart', root/'receipt', root/'model',
                             root/'protocol', root/'wrong.json', OBJECTIVES[0], root/'registration')
            self.assertEqual(reports[0]['candidateCensus'], reports[1]['candidateCensus'])
            self.assertEqual(1, paired(*reports)['commonMeasuredCases'])
            for mutation in ('census', 'cases', 'acceptance', 'cohort', 'objective'):
                changed = deepcopy(reports[1])
                if mutation == 'census': changed['candidateCensus'].pop()
                elif mutation == 'cases': changed['cases'].pop()
                elif mutation == 'acceptance': changed['regionalPhysicalAcceptance']['status'] = 'PASS'
                elif mutation == 'cohort': changed['cohortParameters']['minimum_displacement_fraction'] = .1
                else: changed['routingObjective'] = 'PICK_BEST'
                with self.assertRaises(ValueError): paired(reports[0], changed)
            for report in reports:
                self.assertEqual(3, len(report['candidateCensus']))
                self.assertEqual(2, len(report['cases']))
                self.assertEqual(1, report['counts']['OUTSIDE_TRANSIT_COHORT'])
                self.assertEqual(1, report['statuses']['OBSERVATION_CHART_WATER_CONFLICT'])
                self.assertEqual(1, report['statuses']['PHYSICAL_MODEL_MEASURED'])
                measured = next(c for c in report['cases'] if c['status']=='PHYSICAL_MODEL_MEASURED')
                self.assertTrue(measured['unevaluatedRestrictionIds'])
                self.assertEqual('PASSAGE_NOT_EVALUATED', measured['passageStatus'])
                self.assertEqual('NOT_PASSED', report['validationGate'])
                self.assertEqual(0, report['operationalRoutes'])
                self.assertEqual('FAIL', report['regionalPhysicalAcceptance']['status'])


if __name__ == '__main__': unittest.main()
