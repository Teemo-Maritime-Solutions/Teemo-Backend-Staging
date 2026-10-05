import copy
import unittest
from shapely.geometry import box, mapping
from panama_geometry import source_path, clear, connectors

class PanamaGeometryTest(unittest.TestCase):
    def fixture(self):
        return dict(ways=[dict(id=1, nodes=[1, 2, 3], geometry=[[-79.7,8.8],[-79.7,9.15],[-79.7,9.5]], tags={'lock':'yes'})],
                    areas=[dict(geometry=mapping(box(-79.71,8.79,-79.69,9.51)))])
    def test_exact_source_nodes_and_continuity(self):
        data = self.fixture(); path, _ = source_path(data, box(-80,8,-79,10))
        self.assertEqual(path['points'], data['ways'][0]['geometry'])
        self.assertEqual(path['nodeIds'], [1,2,3]); self.assertEqual(path['wayIds'], [1,1])
        self.assertGreater(path['lengthM'], 77000)
    def test_no_water_or_island_conflict_cannot_be_bypassed(self):
        data = self.fixture()
        data['areas'][0]['geometry'] = mapping(box(-79.71,8.79,-79.69,9.51).difference(box(-79.705,9.1,-79.695,9.2)))
        with self.assertRaises(ValueError): source_path(data, box(-80,8,-79,10))
    def test_directional_and_access_restricted_ways_are_not_made_bidirectional(self):
        for tags in ({'lock':'yes','oneway':'yes'}, {'lock':'yes','access':'private'}, {'lock':'yes','ship':'no'}):
            data=self.fixture();data['ways'][0]['tags']=tags
            with self.assertRaises(ValueError): source_path(data, box(-80,8,-79,10))
    def test_source_gap_cannot_be_snapped_closed(self):
        data=self.fixture(); data['ways']=[dict(id=1,nodes=[1,2],geometry=[[-79.7,8.8],[-79.7,9.1]],tags={'lock':'yes'}),
                                          dict(id=2,nodes=[3,4],geometry=[[-79.7,9.10001],[-79.7,9.5]],tags={'lock':'yes'})]
        with self.assertRaisesRegex(ValueError,'continuous'):source_path(data,box(-80,8,-79,10))
    def test_ocean_connector_cannot_use_inland_water_override(self):
        path,_=source_path(self.fixture(),box(-80,8,-79,10))
        nodes=[dict(id='x',kind='DERIVED_MESH',point=[-79.6,8.8])]
        with self.assertRaisesRegex(ValueError,'connector'):connectors(path,nodes,box(-80,8,-79,10))
        self.assertFalse(clear([[-80.6,8.8],[-79.7,8.8]],box(-79.2,8,-79,10)))

if __name__ == '__main__': unittest.main()
