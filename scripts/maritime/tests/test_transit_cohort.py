"""Synthetic prospective selection tests; no retrospective holdout reruns."""
from dataclasses import replace
from pathlib import Path
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from transit_cohort import TransitConfig, classify, select
from validate_regional_ais import FROZEN_CONFIG


TRANSIT = [(0, (0,0), 1), (60, (.02,0), 2), (120, (.04,0), 3)]
RETURN = [(0, (0,0), 4), (60, (.02,0), 5), (120, (.0001,0), 6)]


class TransitCohortTests(unittest.TestCase):
    def test_original_transit_rows_and_endpoints_unchanged(self):
        result = classify(TRANSIT)
        self.assertEqual('TRANSIT_COHORT_ELIGIBLE',result['status'])
        self.assertEqual([1,2,3],result['sourceRows'])
        self.assertEqual(TRANSIT[0][1],result['origin'])
        self.assertEqual(TRANSIT[-1][1],result['destination'])

    def test_return_is_out_of_scope_not_labelled_invalid_or_successful(self):
        result = classify(RETURN)
        self.assertEqual('OUTSIDE_TRANSIT_COHORT',result['status'])
        self.assertIn('NOT_DISPLACEMENT_DOMINANT',result['reasons'])
        self.assertEqual([4,5,6],result['sourceRows'])

    def test_census_retains_out_of_scope_and_resource_unselected_transits(self):
        records = {'A':TRANSIT,'B':RETURN,'C':[(r[0],r[1],r[2]+10) for r in TRANSIT]}
        selected,census,counts = select(records,replace(FROZEN_CONFIG,max_cases=1),[-1,-1,1,1],'a'*64)
        self.assertEqual(1,len(selected))
        self.assertEqual(3,len(census))
        self.assertEqual(2,counts['TRANSIT_COHORT_ELIGIBLE'])
        self.assertEqual(1,counts['OUTSIDE_TRANSIT_COHORT'])
        self.assertEqual(1,sum(c['selectedForEvaluation'] for c in census))
        self.assertEqual(selected,select(dict(reversed(list(records.items()))),replace(FROZEN_CONFIG,max_cases=1),[-1,-1,1,1],'a'*64)[0])

    def test_sinuous_transit_allowed_when_displacement_dominates(self):
        track = [(0,(0,0),1),(60,(.02,.015),2),(120,(.04,0),3)]
        self.assertEqual('TRANSIT_COHORT_ELIGIBLE',classify(track)['status'])

    def test_reject_invalid_parameters_or_time_order(self):
        for kwargs in (dict(minimum_endpoint_displacement_m=True),dict(minimum_displacement_fraction=float('nan')),
                       dict(minimum_displacement_fraction=1.1),dict(minimum_endpoint_displacement_m=0)):
            with self.assertRaises(ValueError): classify(TRANSIT,TransitConfig(**kwargs))
        with self.assertRaises(ValueError): classify(list(reversed(TRANSIT)))


if __name__ == '__main__': unittest.main()
