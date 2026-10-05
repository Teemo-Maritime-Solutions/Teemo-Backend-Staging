"""Synthetic regression tests, never evidence of real navigability."""
from copy import deepcopy
from dataclasses import replace
from pathlib import Path
import sys
import unittest

from shapely import box, Point

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from build_enc_pilot import ChartDomain, PilotConfig, overhead_bridge, mesh
from enc_snapshot import LAYERS_V2, layer_registry
from synthetic_fixtures import synthetic_enc_source
from validate_enc_pilot import check_edges
from compare_enc_snapshots import compare


def source_v2():
    source = synthetic_enc_source()
    source['layerRegistryVersion'] = 2
    for key, name in LAYERS_V2.items():
        source['layers'].setdefault(str(key), dict(metadata=dict(name='Harbor.' + name), objectIdField='OBJECTID', features=[]))
    return source


def add(source, layer, geom, **props):
    source['layers'][str(layer)]['features'].append(dict(type='Feature', geometry=geom.__geo_interface__,
        properties=dict(OBJECTID=900 + len(source['layers'][str(layer)]['features']), DSNM='SYNTHETIC', **props)))


V2 = replace(PilotConfig(), water_model='S57_GROUP1_V2')


class Group1Tests(unittest.TestCase):
    def test_snapshot_comparison_ignores_only_declared_object_id(self):
        original = synthetic_enc_source()
        changed = deepcopy(original)
        changed['layers']['227']['features'][0]['properties']['OBJECTID'] = 123
        row = next(r for r in compare(original, changed)['layers'] if r['layer'] == 227)
        self.assertFalse(row['exactFeaturesUnchanged'])
        self.assertTrue(row['attributesAndGeometryUnchangedIgnoringObjectId'])
        changed['layers']['227']['features'][0]['properties']['DRVAL1'] = 1
        row = next(r for r in compare(original, changed)['layers'] if r['layer'] == 227)
        self.assertTrue(row['geometryUnchanged'])
        self.assertFalse(row['attributesAndGeometryUnchangedIgnoringObjectId'])

    def test_models_and_registries_fail_closed(self):
        for value in (None, True, [], 'S57_GROUP1_V3'):
            with self.assertRaises(ValueError): replace(V2, water_model=value).validate()
        for value in (True, None, 3, '2'):
            with self.assertRaises(ValueError): layer_registry(value)
        with self.assertRaises(ValueError): ChartDomain(synthetic_enc_source(), V2)
        source = source_v2()
        del source['layers']['149']
        with self.assertRaises(ValueError): ChartDomain(source, V2)
        self.assertEqual('NOAA_ENC_REGIONAL_V1', PilotConfig().profile)
        self.assertEqual('NOAA_ENC_REGIONAL_V2', V2.profile)

    def test_dredged_water_is_group1_without_depare(self):
        source = source_v2()
        source['layers']['228']['features'] = source['layers']['227']['features']
        source['layers']['227']['features'] = []
        with self.assertRaises(ValueError): ChartDomain(source, PilotConfig())
        domain = ChartDomain(source, V2)
        self.assertTrue(domain.allows([[-.005, 0]]))
        self.assertFalse(domain.allows([[0, 0]]))
        self.assertFalse(domain.allows([[.02, 0]]))
        self.assertTrue(all(key.startswith('noaa-enc:228:') for key in domain.depth_evidence([[-.005, 0], [-.004, 0]])))

    def test_unknown_dredged_depth_still_blocks_overlapping_positive_water(self):
        for depth in (None, 0, -1, True, float('nan'), '10'):
            source = source_v2()
            add(source, 228, box(-.006, -.001, -.004, .001), DRVAL1=depth)
            self.assertFalse(ChartDomain(source, V2).allows([[-.005, 0]]))

    def test_deck_is_unresolved_constraint_but_support_remains_solid(self):
        source = source_v2()
        add(source, 141, box(-.006, -.003, -.004, .003), CATBRG='suspension bridge', VERCLR=40, INFORM='SYNTHETIC maintenance reduction unknown')
        add(source, 149, box(-.0052, -.0002, -.0048, .0002))
        add(source, 28, Point(-.005, .002))
        old, domain = ChartDomain(source, PilotConfig()), ChartDomain(source, V2)
        clear = [[-.007, .001], [-.003, .001]]
        self.assertFalse(old.allows(clear))
        self.assertTrue(domain.allows(clear))
        self.assertTrue(any(key.startswith('noaa-enc:141:') for key in domain.restrictions(clear)))
        self.assertFalse(domain.allows([[-.005, 0]]))
        self.assertFalse(domain.allows([[-.005, .002]]))
        nodes, edges, _, report, domain = mesh(source, dict(id='SYNTHETIC'), V2)
        self.assertEqual(len(edges) // 2, check_edges(nodes, edges, domain, 12.5))
        self.assertTrue(any(any(k.startswith('noaa-enc:141:') for k in e['restrictions']) for e in edges))
        self.assertTrue(all(e['minimumDepthM'] is None and e['legalStatus'] == 'UNKNOWN' for e in edges))
        self.assertEqual(1, len(report['unresolvedOverheadBridgeFeatureIds']))
        damaged = deepcopy(edges)
        edge = next(e for e in damaged if any(k.startswith('noaa-enc:141:') for k in e['restrictions']))
        edge['restrictions'] = [k for k in edge['restrictions'] if not k.startswith('noaa-enc:141:')]
        with self.assertRaises(ValueError): check_edges(nodes, damaged, domain, 12.5)

    def test_unknown_opening_mixed_and_heightless_bridges_stay_blocked(self):
        for category in ('opening bridge', 'pontoon bridge', 'fixed bridge,swing bridge', None, True, [1, 2]):
            self.assertFalse(overhead_bridge(141, dict(CATBRG=category, VERCLR=50)))
        for height in (None, 0, -1, True, '50', float('inf')):
            self.assertFalse(overhead_bridge(141, dict(CATBRG='fixed bridge', VERCLR=height)))
        for layer in (87, 141):
            source = source_v2()
            add(source, layer, box(-.006, -.001, -.004, .001), CATBRG='fixed bridge')
            self.assertFalse(ChartDomain(source, V2).allows([[-.005, 0]]))

    def test_caution_is_retained_and_floating_dock_is_solid(self):
        source = source_v2()
        add(source, 154, box(-.008, -.002, -.003, .002), INFORM='SYNTHETIC silting')
        add(source, 176, box(-.006, -.001, -.004, .001))
        domain = ChartDomain(source, V2)
        self.assertFalse(domain.allows([[-.005, 0]]))
        self.assertTrue(any(k.startswith('noaa-enc:154:') for k in domain.restrictions([[-.007, 0]])))


if __name__ == '__main__': unittest.main()
