"""Synthetic policy tests; real chart checks are recorded separately."""
import unittest
from types import SimpleNamespace
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from shapely import box, STRtree

from regional_priority import RegionalPriority
from build_graph import GEOD


class RegionalPriorityTest(unittest.TestCase):
    def policy(self, blocked=False, restriction=True):
        area = box(-74.01, 40.70, -74.00, 40.71)
        barrier = box(-74.006, 40.703, -74.004, 40.707) if blocked else box(0, 0, 1, 1)
        rule = box(-74.007, 40.70, -74.003, 40.71)
        identity = lambda x, y: (x, y)
        domain = SimpleNamespace(
            features={219: [("coverage", area, {"CATCOV": 1})]},
            forward=identity, inverse=identity,
            config=SimpleNamespace(geometry_tolerance_m=0, coastal_buffer_m=0, geodesic_step_m=25),
            depths=[("depth", area.buffer(.01), {})], excluded=barrier,
            regulatory=[("pending-permission", rule, {})] if restriction else [],
            regulatory_tree=STRtree([rule] if restriction else []))
        return RegionalPriority(domain)

    def test_segment_with_both_endpoints_outside_keeps_pending_restriction(self):
        result = self.policy().inspect([[-74.02, 40.705], [-73.99, 40.705]])
        self.assertTrue(result.touched)
        self.assertTrue(result.allowed)
        self.assertEqual(result.restrictions, ("pending-permission",))

    def test_crossing_obstacle_rejected_even_when_endpoints_are_outside(self):
        self.assertFalse(self.policy(blocked=True).inspect([[-74.02, 40.705], [-73.99, 40.705]]).allowed)

    def test_water_seam_is_not_an_artificial_wall(self):
        result = self.policy(restriction=False).inspect([[-74.02, 40.701], [-74.005, 40.701]])
        self.assertTrue(result.allowed)
        self.assertTrue(result.touched)
        self.assertEqual(result.restrictions, ())

    def test_outside_and_dateline_segments_do_not_pick_up_new_york(self):
        for points in ([[-75, 41], [-74, 41]], [[179.99, 40.705], [-179.99, 40.705]]):
            self.assertFalse(self.policy().inspect(points).touched)

    def test_outside_coast_still_checked_at_regional_seam(self):
        class Land:
            def clear(self, points):
                return False
        self.assertFalse(self.policy().outside_clear([[-74.02, 40.705], [-73.99, 40.705]], Land()))

    def test_chart_clipped_positive_water_has_no_artificial_erosion_wall(self):
        first = self.policy(restriction=False)
        first.domain.depths = [("depth", first.geographic_coverage, {})]
        first.domain.config.geometry_tolerance_m = .00001
        policy = RegionalPriority(first.domain)
        self.assertTrue(policy.inspect([[-74.02, 40.705], [-74.005, 40.705]]).allowed)
        # A real water gap at the edge cannot be bridged by the seam rule.
        first.domain.depths = [("depth", box(-74.008, 40.70, -74.0, 40.71), {})]
        self.assertFalse(RegionalPriority(first.domain).inspect([[-74.02, 40.705], [-74.005, 40.705]]).allowed)

    def test_geodesic_can_enter_chart_when_endpoint_bounding_box_misses_it(self):
        a = GEOD.fwd(-74.005, 40.705, 90, 100000)[:2]
        b = GEOD.fwd(-74.005, 40.705, -90, 100000)[:2]
        self.assertLess(max(a[1], b[1]), 40.70)
        decision = self.policy().inspect([a, b])
        self.assertTrue(decision.touched)
        self.assertIn("pending-permission", decision.restrictions)


if __name__ == "__main__":
    unittest.main()
