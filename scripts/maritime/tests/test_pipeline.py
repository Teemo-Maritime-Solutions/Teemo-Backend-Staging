"""Synthetic unit/property fixtures. These tests cannot pass the global quality gate."""
import hashlib
import io
import json
import random
import sys
import tempfile
import unittest
import zipfile
from dataclasses import replace
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from build_graph import Config, Land, geodesic, load_ports, split_dateline, verify_receipt, write_artifact
from validate_graph import discrete_frechet_m, discrete_hausdorff_m, reciprocal_pairs
from audit_ports import consistent
from synthetic_fixtures import ISLAND, WEST, EAST, NORTH, DATELINE_PATH


class PipelineTests(unittest.TestCase):
    def test_reciprocal_validation_checks_geometry_and_completeness(self):
        a = dict(fromNode=0, toNode=1, geometry=[WEST, EAST], distanceM=geodesic(WEST, EAST, 1000)[0])
        b = dict(a, fromNode=1, toNode=0, geometry=[EAST, WEST])
        self.assertEqual([a], list(reciprocal_pairs([a, b])))
        for invalid in ([a], [a, a], [a, dict(b, geometry=[EAST, NORTH, WEST])]):
            with self.assertRaises(ValueError): list(reciprocal_pairs(invalid))

    def test_country_sanity_never_moves_or_qualifies_a_berth(self):
        point = [0, 0]
        self.assertEqual(("COUNTRY_SANITY_PASS_NOT_BERTH_VALIDATION", 0), consistent(point, [ISLAND], 1000))
        self.assertEqual([0, 0], point)
        self.assertEqual("COUNTRY_COORDINATE_MISMATCH", consistent(WEST, [ISLAND], 1000)[0])
        self.assertEqual(("COUNTRY_GEOMETRY_UNAVAILABLE", None), consistent(WEST, [], 1000))

    def test_intersections_not_only_sample_points(self):
        land = Land([ISLAND], Config())
        self.assertFalse(land.clear([WEST, EAST]))
        self.assertTrue(land.clear([WEST, NORTH, EAST]))
        self.assertFalse(land.clear([(0, 0), EAST]))

    def test_dateline_does_not_cross_greenwich(self):
        land = Land([ISLAND], Config())
        self.assertTrue(land.clear(DATELINE_PATH))
        self.assertEqual(2, len(split_dateline(DATELINE_PATH)))
        self.assertLess(geodesic(*DATELINE_PATH, 1000)[0], 25000)

    def test_buffer_rejects_close_paths(self):
        land = Land([ISLAND], replace(Config(), coastal_buffer_m=1000))
        self.assertFalse(land.clear([(-.1, .011), (.1, .011)]))

    def test_invalid_config_and_geometry(self):
        with self.assertRaises(ValueError): replace(Config(), geodesic_step_m=float("nan")).validate()
        with self.assertRaises(ValueError): replace(Config(), base_resolution=5, coastal_resolution=2).validate()
        with self.assertRaises(ValueError): Land([], Config())
        with self.assertRaises(ValueError): geodesic((181, 0), EAST, 1000)

    def test_seeded_geodesic_properties(self):
        rng = random.Random(7102)
        for _ in range(50):
            a, b = [(rng.uniform(-180, 180), rng.uniform(-90, 90)) for _ in range(2)]
            forward, path = geodesic(a, b, 50000)
            backward, _ = geodesic(b, a, 50000)
            self.assertAlmostEqual(forward, backward, places=6)
            self.assertEqual(a, path[0]); self.assertEqual(b, path[-1])
            for p, q in zip(path, path[1:]):
                self.assertLessEqual(geodesic(p, q, 50000)[0], 50000.000001)

    def test_geometry_metrics(self):
        self.assertEqual(0, discrete_frechet_m([WEST, EAST], [WEST, EAST]))
        self.assertEqual(0, discrete_hausdorff_m([WEST, EAST], [EAST, WEST]))
        self.assertGreater(discrete_frechet_m([WEST, EAST], [EAST, WEST]), 0)
        with self.assertRaises(ValueError): discrete_hausdorff_m([], [WEST])

    def test_artifact_deterministic_bytes(self):
        with tempfile.TemporaryDirectory() as directory:
            a, b = Path(directory) / "a.zip", Path(directory) / "b.zip"
            for path in (a, b):
                write_artifact(path, [], [], [], [{"acquiredAt": "2026-01-01T00:00:00Z"}], Config(), {}, {})
            self.assertEqual(a.read_bytes(), b.read_bytes())
            with self.assertRaises(ValueError): write_artifact(a, [], [], [], [], Config(), {}, {})

    def test_receipt_checksum_and_path_rejection(self):
        with tempfile.TemporaryDirectory() as directory:
            p = Path(directory); source = p / "synthetic.bin"; source.write_bytes(b"SYNTHETIC")
            metadata = dict.fromkeys(["id", "url", "license", "licenseUrl", "version", "acquiredAt", "unit", "coverage"], "synthetic")
            metadata.update(status="SOURCE_DATA", limitations=["SYNTHETIC TEST ONLY"], file=source.name,
                            sha256=hashlib.sha256(source.read_bytes()).hexdigest())
            receipt = p / "receipt.json"; receipt.write_text(json.dumps(metadata))
            self.assertEqual(source, verify_receipt(receipt)[0])
            source.write_bytes(b"tampered")
            with self.assertRaises(ValueError): verify_receipt(receipt)
            metadata["file"] = "../outside.bin"; receipt.write_text(json.dumps(metadata))
            with self.assertRaises(ValueError): verify_receipt(receipt)


if __name__ == "__main__":
    unittest.main()
