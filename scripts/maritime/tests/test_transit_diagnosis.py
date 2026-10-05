"""Cost reconstruction checks; attribution never changes frozen predictions."""
from copy import deepcopy
from pathlib import Path
import sys
from types import SimpleNamespace
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from build_graph import geodesic
from diagnose_transit_preferences import score_path


class TransitDiagnosisTests(unittest.TestCase):
    def setUp(self):
        self.points = [[0,0], [.01,0]]
        self.distance = geodesic(*self.points, 25)[0]
        self.lookup = {(0,1):dict(distanceM=self.distance)}
        self.objective = SimpleNamespace(domain=SimpleNamespace(config=SimpleNamespace(geodesic_step_m=100)),
                                         edge_support={(0,1):[1]}, support=lambda curve: [])
        self.case = dict(routeNodeIndices=[0,1], origin=self.points[0], destination=self.points[1],
                         predictedDistanceM=self.distance, unsupportedDirectedEvidenceM=0)

    def test_supported_edge_and_zero_length_connectors(self):
        result = score_path(self.case, self.points, self.lookup, self.objective)
        self.assertAlmostEqual(self.distance, result['objectiveCostM'])
        self.assertEqual(1, result['supportedDistanceFraction'])

    def test_unsupported_connectors_are_included_in_both_cost_terms(self):
        self.case['origin'] = [-.001,0]
        connector = geodesic(self.case['origin'], self.points[0], 25)[0]
        self.case.update(predictedDistanceM=self.distance+connector, unsupportedDirectedEvidenceM=connector)
        result = score_path(self.case, self.points, self.lookup, self.objective)
        self.assertAlmostEqual(self.distance+2*connector, result['objectiveCostM'])

    def test_changed_distance_support_missing_edge_and_invalid_indices_rejected(self):
        for change in (dict(predictedDistanceM=1), dict(unsupportedDirectedEvidenceM=1),
                       dict(predictedDistanceM=float('nan')), dict(unsupportedDirectedEvidenceM=True),
                       dict(routeNodeIndices=[]), dict(routeNodeIndices=[True,1]),
                       dict(routeNodeIndices=[0,2])):
            with self.assertRaises(ValueError):
                score_path(dict(self.case, **change), self.points, self.lookup, self.objective)
        with self.assertRaises(ValueError): score_path(self.case, self.points, {}, self.objective)

    def test_attribution_leaves_original_case_unchanged(self):
        before = deepcopy(self.case)
        score_path(self.case, self.points, self.lookup, self.objective)
        self.assertEqual(before, self.case)


if __name__ == '__main__': unittest.main()
