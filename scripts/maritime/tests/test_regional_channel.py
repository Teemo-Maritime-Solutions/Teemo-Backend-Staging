"""Synthetic tuple-search tests only."""
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from regional_channel_objective import lexicographic_route, ChannelObjective
from build_enc_pilot import ChartDomain, PilotConfig, mesh
from synthetic_fixtures import synthetic_enc_source
from dataclasses import replace
from enc_snapshot import LAYERS_V2
from shapely import box


class ChannelTests(unittest.TestCase):
    def test_balanced_penalty_avoids_excessive_detour_and_reports_physical_length(self):
        graph = [[(2, 10, 10), (1, 20, 0)], [(2, 20, 0)], []]
        result, outside = lexicographic_route(graph, [(0, 2, 1)], [(2, 3, 1)], balanced=True)
        self.assertEqual(([0, 2], 15, [2, 3]), result)
        self.assertEqual(12, outside)
        graph[0][1] = (1, 7, 0)
        graph[1][0] = (2, 7, 0)
        result, outside = lexicographic_route(graph, [(0, 0, 0)], [(2, 0, 0)], balanced=True)
        self.assertEqual(([0, 1, 2], 14, [0, 0]), result)
        self.assertEqual(0, outside)

    def test_channel_objective_is_not_weighted_fake_distance(self):
        # Short off-channel edge versus a longer two-edge published-channel path.
        graph = [[(2, 10, 10), (1, 20, 0)], [(2, 20, 0)], []]
        result, outside = lexicographic_route(graph, [(0, 2, 1)], [(2, 3, 1)])
        self.assertEqual(([0, 1, 2], 45, [2, 3]), result)
        self.assertEqual(2, outside)

    def test_connectors_participate_and_all_candidates_are_considered(self):
        graph = [[(2, 5, 0)], [(2, 10, 0)], [], []]
        result, outside = lexicographic_route(graph, [(3, 0, 0), (0, 1, 1), (1, 2, 0)], [(2, 3, 0)])
        self.assertEqual(([1, 2], 15, [2, 3]), result)
        self.assertEqual(0, outside)
        self.assertIsNone(lexicographic_route(graph, [(3, 0, 0)], [(2, 3, 0)]))

    def test_equal_channel_cost_uses_distance_and_no_cycles(self):
        graph = [[(1, 5, 0), (2, 20, 0)], [(0, 5, 0), (2, 5, 0)], []]
        result, _ = lexicographic_route(graph, [(0, 0, 0)], [(2, 0, 0)])
        self.assertEqual([0, 1, 2], result[0])

    def test_nonfinite_or_negative_cost_rejected(self):
        for distance, outside in ((0, 0), (-1, 0), (1, -1), (float('nan'), 0), (1, float('inf'))):
            with self.assertRaises(ValueError): lexicographic_route([[(1, distance, outside)], []], [(0, 0, 0)], [(1, 0, 0)])

    def test_source_channel_geometry_and_restrictions_are_preserved(self):
        source = synthetic_enc_source()
        with self.assertRaises(ValueError): ChannelObjective(ChartDomain(source, PilotConfig()), [], 0)
        source['layerRegistryVersion'] = 2
        for key, name in LAYERS_V2.items():
            source['layers'].setdefault(str(key), dict(metadata=dict(name='Harbor.' + name), objectIdField='OBJECTID', features=[]))
        config = replace(PilotConfig(), water_model='S57_GROUP1_V2')
        with self.assertRaises(ValueError): ChannelObjective(ChartDomain(source, config), [], 0)
        source['layers']['208']['features'] = [dict(type='Feature', properties=dict(OBJECTID=901, DSNM='SYNTHETIC'), geometry=box(-.01, -.01, .01, .01).__geo_interface__)]
        nodes, edges, ports, _, domain = mesh(source, dict(id='SYNTHETIC'), config)
        channel = ChannelObjective(domain, edges, len(nodes))
        origin, destination = ports[:2]
        choice = channel.route([(origin['nodeId'], 0)], [(destination['nodeId'], 0)],
                               [origin['point'], destination['point']], [n['point'] for n in nodes])
        self.assertIsNotNone(choice)
        route, outside = choice
        self.assertEqual(0, outside)
        self.assertTrue(domain.allows([nodes[i]['point'] for i in route[0]]))
        self.assertTrue(all(e['restrictions'] and e['legalStatus'] == 'UNKNOWN' for e in edges))
        self.assertEqual(['noaa-enc:208:SYNTHETIC:901'], channel.source_ids)


if __name__ == '__main__': unittest.main()
