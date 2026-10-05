"""Synthetic directed evidence tests; no real-world passage assertions."""
from collections import Counter
from copy import deepcopy
from dataclasses import replace
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from build_enc_pilot import ChartDomain, PilotConfig, mesh
from enc_snapshot import LAYERS_V2
from regional_corridor_model import CorridorConfig, DirectedCorridorObjective, training_segments
from synthetic_fixtures import synthetic_enc_source
from validate_corridor_ais import evaluate_case
from build_graph import xyz
from scipy.sparse import csr_matrix
from scipy.spatial import cKDTree


def domain():
    source = synthetic_enc_source()
    source['layerRegistryVersion'] = 2
    for key, name in LAYERS_V2.items():
        source['layers'].setdefault(str(key), dict(metadata=dict(name='Harbor.'+name), objectIdField='OBJECTID', features=[]))
    return ChartDomain(source, replace(PilotConfig(), water_model='S57_GROUP1_V2'))


GOOD = [(0, (-.009, -.008), 1), (60, (-.001, -.008), 2), (120, (.009, -.008), 3)]
BAD = [(0, (-.009, 0), 4), (60, (0, 0), 5), (120, (.009, 0), 6)]


class DirectedEvidenceTests(unittest.TestCase):
    def test_complete_route_evaluation_keeps_restrictions_and_failed_observations(self):
        water = domain()
        source = synthetic_enc_source()
        source['layerRegistryVersion'] = 2
        for key, name in LAYERS_V2.items():
            source['layers'].setdefault(str(key),dict(metadata=dict(name='Harbor.'+name),objectIdField='OBJECTID',features=[]))
        nodes,edges,_,_,water = mesh(source,dict(id='SYNTHETIC'),water.config)
        segments,_,_ = training_segments({'GOOD':GOOD},water,[-.01,-.01,.01,.01],'SYNTHETIC',CorridorConfig())
        objective = DirectedCorridorObjective(water,edges,len(nodes),segments,CorridorConfig())
        points = [n['point'] for n in nodes]
        lookup = {(e['fromNode'],e['toNode']):e for e in edges}
        tree = cKDTree(xyz(points))
        matrix = csr_matrix(([e['distanceM'] for e in edges],([e['fromNode'] for e in edges],[e['toNode'] for e in edges])),shape=(len(nodes),len(nodes)))
        case = evaluate_case('SYNTHETIC',GOOD,points,lookup,water,tree,matrix,objective)
        self.assertEqual('PHYSICAL_MODEL_MEASURED',case['status'])
        self.assertEqual('PASSAGE_NOT_EVALUATED',case['passageStatus'])
        self.assertTrue(case['unevaluatedRestrictionIds'])
        self.assertTrue(case['trainingSegmentIndices'])
        self.assertLess(case['discreteFrechetM'],1000)
        bad = evaluate_case('SYNTHETIC_BAD',BAD,points,lookup,water,tree,matrix,objective)
        self.assertEqual('OBSERVATION_CHART_WATER_CONFLICT',bad['status'])
        self.assertEqual([4,5,6],bad['sourceRows'])

    def test_bounds_reject_unknown_nonfinite_or_boolean_values(self):
        for kwargs in (dict(radius_m=0), dict(radius_m=float('nan')), dict(minimum_direction_cosine=2),
                       dict(max_segments=True), dict(max_tracks=10001), dict(minimum_movement_m='25')):
            with self.assertRaises(ValueError): replace(CorridorConfig(), **kwargs).validate()

    def test_training_keeps_failures_and_only_original_time_ordered_pairs(self):
        segments, tracks, counts = training_segments({'GOOD': GOOD, 'BAD': BAD}, domain(), [-.01,-.01,.01,.01], 'SYNTHETIC', CorridorConfig())
        self.assertEqual(2, counts['eligibleTracks'])
        self.assertEqual(1, counts['chartConflictTracks'])
        self.assertEqual(2, len(segments))
        self.assertEqual([1, 2], segments[0]['sourceRows'])
        self.assertEqual([2, 3], segments[1]['sourceRows'])
        self.assertEqual([GOOD[0][1], GOOD[1][1]], segments[0]['points'])
        self.assertEqual(2, len(tracks))
        with self.assertRaises(ValueError): training_segments({'BAD': BAD}, domain(), [-.01,-.01,.01,.01], 'SYNTHETIC', CorridorConfig())

    def test_reciprocal_mesh_does_not_create_reverse_ais_evidence(self):
        water = domain()
        segments, _, _ = training_segments({'GOOD': GOOD}, water, [-.01,-.01,.01,.01], 'SYNTHETIC', CorridorConfig())
        geometry = [[-.007,-.008],[-.006,-.008]]
        edges = [dict(fromNode=0,toNode=1,geometry=geometry,distanceM=85,restrictions=['SYNTHETIC_UNKNOWN'],legalStatus='UNKNOWN'),
                 dict(fromNode=1,toNode=0,geometry=list(reversed(geometry)),distanceM=85,restrictions=['SYNTHETIC_UNKNOWN'],legalStatus='UNKNOWN')]
        original = deepcopy(edges)
        objective = DirectedCorridorObjective(water, edges, 2, segments, CorridorConfig())
        self.assertTrue(objective.edge_support[(0,1)])
        self.assertFalse(objective.edge_support[(1,0)])
        self.assertEqual(0, objective.adjacency[0][0][2])
        self.assertEqual(85, objective.adjacency[1][0][2])
        self.assertEqual(original, edges)

    def test_full_edge_and_direction_required_not_presence_near_endpoint(self):
        water = domain()
        segments, _, _ = training_segments({'GOOD': GOOD}, water, [-.01,-.01,.01,.01], 'SYNTHETIC', CorridorConfig())
        objective = DirectedCorridorObjective(water, [], 0, segments, CorridorConfig())
        self.assertFalse(objective.support([[-.007,-.008],[-.006,-.003]]))
        self.assertFalse(objective.support([[-.006,-.008],[-.007,-.008]]))
        self.assertFalse(objective.support([[-.007,-.008],[-.007,-.008]]))

    def test_resource_exhaustion_is_not_partial_training_success(self):
        with self.assertRaises(ValueError):
            training_segments({'GOOD': GOOD}, domain(), [-.01,-.01,.01,.01], 'SYNTHETIC', replace(CorridorConfig(), max_segments=1))


if __name__ == '__main__': unittest.main()
