"""Synthetic reachability fixtures only; never global route evidence."""
from copy import deepcopy
import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from validate_connectivity import check
from synthetic_fixtures import WEST, EAST


class ConnectivityTests(unittest.TestCase):
    def setUp(self):
        self.nodes = [dict(id="SYNTHETIC_A", point=WEST), dict(id="SYNTHETIC_B", point=EAST)]
        self.ports = [dict(id=n["id"], point=n["point"], nodeId=i, status="CONNECTED_REFERENCE_POINT")
                      for i, n in enumerate(self.nodes)]
        self.edges = [dict(fromNode=a, toNode=b, geometry=[self.nodes[a]["point"], self.nodes[b]["point"]],
                           legalStatus="UNKNOWN", restrictions=[]) for a, b in ((0, 1), (1, 0))]

    def test_strong_reachability_does_not_invent_reverse_direction(self):
        result = check(self.nodes, self.edges, self.ports, 10)
        self.assertTrue(result["allCoveredPairsResearchPolicyReachable"])
        self.assertEqual(2, result["coveredOrderedDistinctPairs"])
        self.assertFalse(check(self.nodes, self.edges[:1], self.ports, 10)["allCoveredPairsTopologicallyReachable"])

    def test_prohibited_or_unresolved_edge_cannot_prove_usable_connectivity(self):
        for update in (dict(legalStatus="PROHIBITED"), dict(restrictions=["SYNTHETIC_UNRESOLVED"])):
            edges = deepcopy(self.edges); edges[0].update(update)
            result = check(self.nodes, edges, self.ports, 10)
            self.assertTrue(result["allCoveredPairsTopologicallyReachable"])
            self.assertFalse(result["allCoveredPairsResearchPolicyReachable"])
            self.assertEqual(1, result["researchPolicyExcludedEdges"])

    def test_bad_references_and_resource_bound_fail_explicitly(self):
        with self.assertRaises(ValueError): check(self.nodes, self.edges, self.ports, 1)
        edges = deepcopy(self.edges); edges[0]["geometry"].reverse()
        with self.assertRaises(ValueError): check(self.nodes, edges, self.ports, 10)
        ports = deepcopy(self.ports); ports[0]["nodeId"] = 1
        with self.assertRaises(ValueError): check(self.nodes, self.edges, ports, 10)
        ports = deepcopy(self.ports); ports[0]["nodeId"] = None
        with self.assertRaises(ValueError): check(self.nodes, self.edges, ports, 10)

    def test_no_ports_is_not_vacuously_a_global_pass(self):
        result = check(self.nodes, self.edges, [], 10)
        self.assertFalse(result["allCoveredPairsTopologicallyReachable"])
        self.assertFalse(result["coveredPortsInLargestComponent"])


if __name__ == "__main__":
    unittest.main()
