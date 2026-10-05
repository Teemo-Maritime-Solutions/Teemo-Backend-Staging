"""Only synthetic algorithm tests; real AIS evidence is a separate offline command."""
import csv
import io
import json
import sys
import unittest
from collections import Counter
from dataclasses import replace
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from validate_ais import AisConfig, fields, merchant_id, split_tracks, route_between_anchors
from scipy.sparse import csr_matrix
from synthetic_fixtures import AIS_RECORDS, WEST, EAST
from synthetic_fixtures import synthetic_ais_report
from compare_ais import compare


class AisTests(unittest.TestCase):
    def setUp(self):
        self.config = AisConfig(**json.loads((Path(__file__).resolve().parents[1] / "ais-validation-config.json").read_text()))

    def test_configuration_fails_instead_of_truncating_bad_limits(self):
        self.config.validate()
        with self.assertRaises(ValueError): replace(self.config, sample_vessels=1.5).validate()
        with self.assertRaises(ValueError): replace(self.config, max_gap_seconds=float("nan")).validate()

    def test_schema_and_merchant_codes_are_explicit(self):
        reader = csv.DictReader(io.StringIO("MMSI,BaseDateTime,LON,LAT,VesselType\n"))
        self.assertEqual("LON", fields(reader)["lon"])
        with self.assertRaises(ValueError): fields(csv.DictReader(io.StringIO("x,y,time\n")))
        self.assertEqual("200000001", merchant_id(dict(mmsi="200000001", vessel_type="70")))
        self.assertIsNone(merchant_id(dict(mmsi="200000001", vessel_type="30")))
        self.assertIsNone(merchant_id(dict(mmsi="bad", vessel_type="70")))

    def test_deduplication_gaps_and_conflicts_preserve_evidence(self):
        counts = Counter()
        self.assertEqual([AIS_RECORDS], list(split_tracks(AIS_RECORDS + [AIS_RECORDS[1]], self.config, counts)))
        self.assertEqual(1, counts["duplicateObservations"])
        conflicting = [*AIS_RECORDS, (60, WEST, 4)]
        self.assertEqual([], list(split_tracks(conflicting, self.config, Counter())))
        gap = [AIS_RECORDS[0], AIS_RECORDS[1], (10000, EAST, 3)]
        self.assertEqual([], list(split_tracks(gap, self.config, Counter())))

    def test_all_clear_anchors_considered_without_inventing_reverse_edges(self):
        # SYNTHETIC graph: nearest node 0 is isolated; another valid anchor 1 reaches 2.
        matrix = csr_matrix(([10.0], ([1], [2])), shape=(3, 3))
        self.assertEqual(([1, 2], 15.0, [2.0, 3.0]), route_between_anchors(matrix, [(0, 0.0), (1, 2.0)], [(2, 3.0)]))
        self.assertIsNone(route_between_anchors(matrix, [(2, 0.0)], [(1, 0.0)]))
        self.assertIsNone(route_between_anchors(matrix, [], [(2, 0.0)]))

    def test_paired_comparison_preserves_failures_and_rejects_changed_validator(self):
        before = synthetic_ais_report("SYNTHETIC-before", "NO_GEOMETRICALLY_VALID_ANCHOR")
        after = synthetic_ais_report("SYNTHETIC-after", "MEASURED_NOT_CERTIFIED")
        result = compare(before, after)
        self.assertEqual({"newlyMeasured": 1}, result["cohorts"])
        self.assertEqual([], result["pairedMetrics"])
        after["validatorSha256"] = "SYNTHETIC-different-validator"
        with self.assertRaises(ValueError): compare(before, after)


if __name__ == "__main__":
    unittest.main()
