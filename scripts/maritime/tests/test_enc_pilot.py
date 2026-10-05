"""SYNTHETIC fixtures only; no real-world observations invented by these tests."""
from dataclasses import replace
import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from build_enc_pilot import ChartDomain, PilotConfig, coverage_available, mesh
from synthetic_fixtures import synthetic_enc_source
from validate_enc_pilot import check_edges


class EncPilotTests(unittest.TestCase):
    def test_configuration_rejects_unbounded_or_nonfinite_values(self):
        for changes in (dict(h3_resolution=13), dict(max_nodes=True), dict(geometry_tolerance_m=float("nan")),
                        dict(max_extent_degrees=180), dict(connections_per_endpoint=10, connector_candidates=2)):
            with self.assertRaises(ValueError): replace(PilotConfig(), **changes).validate()

    def test_coverage_decoding_does_not_accept_unknown_or_boolean(self):
        self.assertTrue(coverage_available("coverage available"))
        self.assertTrue(coverage_available(1))
        for value in (True, None, "unknown", "1", 2): self.assertFalse(coverage_available(value))

    def test_land_crossing_and_outside_coverage_are_rejected(self):
        domain = ChartDomain(synthetic_enc_source(), PilotConfig())
        self.assertTrue(domain.allows([[-.005, 0]]))
        self.assertFalse(domain.allows([[0, 0]]))
        self.assertFalse(domain.allows([[-.005, 0], [.005, 0]]))
        self.assertFalse(domain.allows([[.02, 0]]))
        self.assertTrue(domain.restrictions([[-.005, 0]]))

    def test_unknown_depth_does_not_become_positive_water(self):
        source = synthetic_enc_source()
        source["layers"]["227"]["features"][0]["properties"]["DRVAL1"] = None
        with self.assertRaises(ValueError): ChartDomain(source, PilotConfig())

    def test_mesh_preserves_berth_coordinates_and_hard_restrictions(self):
        source = synthetic_enc_source()
        nodes, edges, ports, report, domain = mesh(source, dict(id="SYNTHETIC"), PilotConfig())
        self.assertGreater(len(edges), 0)
        self.assertEqual(2, sum(p["status"] == "CONNECTED_REFERENCE_POINT" for p in ports))
        self.assertEqual("OUTSIDE_CONSERVATIVE_WATER", ports[2]["status"])
        self.assertEqual([-.005, 0], ports[0]["point"])
        self.assertEqual(len(edges), report["edgesBlockedByUnresolvedChartRestrictions"])
        self.assertTrue(all(e["minimumDepthM"] is None and e["restrictions"] for e in edges))
        self.assertTrue(all(domain.allows(e["geometry"]) for e in edges))
        self.assertEqual(len(edges), len({(e["fromNode"], e["toNode"]) for e in edges}))
        self.assertEqual(nodes[ports[0]["nodeId"]]["point"], ports[0]["point"])
        self.assertEqual(len(edges) // 2, check_edges(nodes, edges, domain, 12.5))
        from copy import deepcopy
        for field, replacement in (("restrictions", []), ("minimumDepthM", 10), ("chartDepthFeatureIds", [])):
            broken = deepcopy(edges)
            broken[1][field] = replacement
            with self.assertRaises(ValueError): check_edges(nodes, broken, domain, 12.5)
        with self.assertRaises(ValueError): check_edges(nodes, edges[:-1], domain, 12.5)

    def test_duplicate_source_features_are_rejected(self):
        source = synthetic_enc_source()
        source["layers"]["49"]["features"] *= 2
        with self.assertRaises(ValueError): ChartDomain(source, PilotConfig())


if __name__ == "__main__": unittest.main()
