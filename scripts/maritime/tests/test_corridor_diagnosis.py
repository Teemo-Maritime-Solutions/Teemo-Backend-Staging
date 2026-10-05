"""Synthetic geometry tests, not evidence of actual vessel intent."""
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from diagnose_corridor_failures import endpoint_diagnosis


class EndpointDiagnosisTests(unittest.TestCase):
    def test_near_returning_excursion_cannot_be_explained_by_cheap_endpoint_path(self):
        result = endpoint_diagnosis([(0,0),(.1,0),(.0001,0)],1000,30)
        self.assertTrue(result['lengthBoundExceedsCurrentObjective'])
        self.assertGreater(result['requiredLengthLowerBoundM'],19000)
        self.assertEqual(1,result['witnessObservationIndex'])

    def test_direct_transit_does_not_assert_incompatibility(self):
        result = endpoint_diagnosis([(0,0),(.05,0),(.1,0)],1000,12000)
        self.assertFalse(result['lengthBoundExceedsCurrentObjective'])

    def test_unmeasured_case_has_no_fabricated_objective(self):
        result = endpoint_diagnosis([(0,0),(.1,0),(0,0)],1000)
        self.assertIsNone(result['currentObjectiveUpperBoundM'])
        self.assertFalse(result['lengthBoundExceedsCurrentObjective'])


if __name__ == '__main__': unittest.main()
