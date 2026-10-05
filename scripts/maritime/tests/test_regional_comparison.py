"""Synthetic pairing checks, including rejection of cherry-picked case sets."""
from copy import deepcopy
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from compare_regional_runs import compare


class ComparisonTests(unittest.TestCase):
    def setUp(self):
        self.report = dict(artifactSha256='SYNTHETIC', sources=['SYNTHETIC'], parameters={},
            registrationSha256='SYNTHETIC', routingObjective='DISTANCE_V1',
            regionalPhysicalAcceptance=dict(status='FAIL'), seconds=1, memory={}, operationalRoutes=0,
            cases=[dict(trackKey='SYNTHETIC', sourceRows=[1, 2, 3], origin=[0, 0], destination=[1, 0],
                startUtc='SYNTHETIC', endUtc='SYNTHETIC', observationCount=3,
                status='PHYSICAL_MODEL_MEASURED', discreteHausdorffM=1100, discreteFrechetM=1200,
                predictedDistanceM=1000, distanceRatio=1)])

    def test_pair_summary_never_turns_individual_model_failures_into_pass(self):
        other = deepcopy(self.report)
        other['cases'][0]['discreteFrechetM'] = 100
        result = compare(self.report, other)
        self.assertEqual(-1100, result['cases'][0]['frechetChangeM'])
        self.assertEqual('NOT_PASSED', result['validationGate'])
        self.assertTrue(all(s['acceptance']['status'] == 'FAIL' for s in result['summary']))

    def test_missing_duplicate_or_changed_observations_are_rejected(self):
        for replacement in ([], self.report['cases'] * 2):
            other = dict(self.report, cases=replacement)
            with self.assertRaises(ValueError): compare(self.report, other)
        for key, value in (('sourceRows', [4, 5, 6]), ('origin', [2, 0]), ('startUtc', 'CHANGED')):
            other = deepcopy(self.report)
            other['cases'][0][key] = value
            with self.assertRaises(ValueError): compare(self.report, other)

    def test_data_artifact_registration_or_cohort_cannot_change(self):
        for key in ('artifactSha256', 'sources', 'parameters', 'registrationSha256'):
            with self.assertRaises(ValueError): compare(self.report, dict(self.report, **{key: 'CHANGED'}))

    def test_unmeasurable_cases_remain_in_paired_report(self):
        other = deepcopy(self.report)
        other['cases'][0]['status'] = 'OBSERVATION_CHART_WATER_CONFLICT'
        result = compare(self.report, other)
        self.assertEqual(1, len(result['cases']))
        self.assertEqual(0, result['commonMeasuredCases'])
        self.assertIsNone(result['summary'][1]['maxFrechetM'])


if __name__ == '__main__': unittest.main()
