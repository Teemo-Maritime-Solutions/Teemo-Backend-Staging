"""Source-exact Panama research centerline; no guessed links across missing geometry."""
import hashlib
import heapq
import io
import json
from pathlib import Path
import sys
import zipfile

import shapefile
from shapely import Point, LineString, box, from_wkb
from shapely.geometry import mapping, shape
from shapely.ops import unary_union
from build_graph import GEOD, encode, geodesic, verify_receipt

BBOX = (-80.5, 8, -79, 10)
SOURCE = 'osm-panama'
ZONE = 'PANAMA_CANAL_RESEARCH'
POLICY = 'OSM_EXACT_CENTERLINE_AND_WATER_GSHHG_OUTSIDE_V1'

def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

def extract(receipt_path):
    path, receipt = verify_receipt(receipt_path)
    if receipt['id'] != SOURCE:
        raise ValueError('Unexpected Panama source')
    # Optional, isolated reader: do not change frozen research environments.
    sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'target/panama-python'))
    import osmium
    factory = osmium.geom.WKBFactory()
    clip = box(-80.05, 8.7, -79.4, 9.5)
    class Extract(osmium.SimpleHandler):
        def __init__(self):
            super().__init__()
            self.ways, self.areas = [], []
        def way(self, way):
            if way.tags.get('waterway') != 'canal':
                return
            points = [[node.lon, node.lat] for node in way.nodes]
            if not any(clip.covers(Point(p)) for p in points):
                return
            self.ways.append(dict(id=way.id, version=way.version, timestamp=str(way.timestamp),
                                 tags=dict(way.tags), nodes=[node.ref for node in way.nodes], geometry=points))
        def area(self, area):
            if area.tags.get('natural') != 'water' and area.tags.get('waterway') != 'riverbank':
                return
            geometry = from_wkb(factory.create_multipolygon(area))
            if geometry.intersects(clip):
                self.areas.append(dict(id=area.orig_id(), fromWay=area.from_way(), tags=dict(area.tags),
                                       geometry=mapping(geometry)))
    reader = Extract()
    reader.apply_file(str(path), locations=True)
    return dict(ways=reader.ways, areas=reader.areas), receipt

def coastal_land(receipt_path):
    path, receipt = verify_receipt(receipt_path)
    clip = box(*BBOX)
    polygons = []
    with zipfile.ZipFile(path) as archive:
        prefix = 'GSHHS_shp/f/GSHHS_f_L1'
        reader = shapefile.Reader(shp=io.BytesIO(archive.read(prefix + '.shp')),
                                 shx=io.BytesIO(archive.read(prefix + '.shx')),
                                 dbf=io.BytesIO(archive.read(prefix + '.dbf')))
        for item in reader.iterShapes(bbox=clip.bounds):
            geometry = shape(item.__geo_interface__)
            if not geometry.is_valid:
                geometry = geometry.envelope
            geometry = geometry.intersection(clip)
            if not geometry.is_empty:
                polygons.append(geometry)
    if not polygons:
        raise ValueError('Missing coastline coverage')
    return unary_union(polygons), receipt

def densify(points, step=25):
    geometry, length = [], 0.0
    for a, b in zip(points, points[1:]):
        distance, segment = geodesic(a, b, step)
        if distance <= 0:
            raise ValueError('Degenerate source segment')
        length += distance
        geometry.extend(segment if not geometry else segment[1:])
    return length, geometry

def clear(points, land):
    if not all(box(*BBOX).covers(Point(p)) for p in points):
        return False
    return not LineString(densify(points)[1]).intersects(land)

def source_path(data, land):
    polygons = [shape(area['geometry']) for area in data['areas']]
    if not polygons or any(not p.is_valid or p.is_empty for p in polygons):
        raise ValueError('Invalid/empty source water; no silent repair')
    water = unary_union(polygons)
    blocked = land.difference(water)
    coordinates, adjacency, rejected = {}, {}, []
    source_segments = {}
    for way in data['ways']:
        tags = way['tags']
        if not (tags.get('name:en') == 'Panama Canal' or tags.get('lock') == 'yes' or way['id'] == 179053881):
            continue  # 179053881 is the unnamed source Atlantic approach, not a fabricated endpoint.
        reason = None
        if tags.get('oneway', 'no') not in ('no', '0', 'false'):
            reason = 'DIRECTIONAL_SOURCE_NOT_IMPORTED_AS_RECIPROCAL'
        elif any(tags.get(k) in ('no', 'private') for k in ('access', 'boat', 'ship')):
            reason = 'SOURCE_ACCESS_RESTRICTION'
        elif not clear(way['geometry'], blocked):
            reason = 'GEOMETRY_OUTSIDE_SOURCE_WATER'
        if reason:
            rejected.append(dict(wayId=way['id'], reason=reason))
            continue
        if len(way['nodes']) != len(way['geometry']):
            raise ValueError('Source node/geometry mismatch')
        for node, point in zip(way['nodes'], way['geometry']):
            if node in coordinates and coordinates[node] != point:
                raise ValueError('Inconsistent shared source node')
            coordinates[node] = point
        for a, b in zip(way['nodes'], way['nodes'][1:]):
            key = tuple(sorted((a, b)))
            if key in source_segments:
                raise ValueError('Duplicate source segment')
            source_segments[key] = way
            distance = GEOD.inv(*coordinates[a], *coordinates[b])[2]
            adjacency.setdefault(a, []).append((b, distance))
            adjacency.setdefault(b, []).append((a, distance))
    if not adjacency:
        raise ValueError('No source canal network')
    # Extremities must be source nodes, never snapped to a hand-written coordinate.
    start = min(coordinates, key=lambda n: coordinates[n][1])
    end = max(coordinates, key=lambda n: coordinates[n][1])
    distances, previous, queue = {start: 0.0}, {}, [(0.0, start)]
    while queue:
        distance, node = heapq.heappop(queue)
        if distance != distances[node]:
            continue
        if node == end:
            break
        for neighbor, cost in adjacency[node]:
            candidate = distance + cost
            if candidate < distances.get(neighbor, float('inf')):
                distances[neighbor] = candidate
                previous[neighbor] = node
                heapq.heappush(queue, (candidate, neighbor))
    if end not in previous:
        raise ValueError('No continuous source canal path')
    path = [end]
    while path[-1] != start:
        path.append(previous[path[-1]])
    path.reverse()
    ways = [source_segments[tuple(sorted((a, b)))] for a, b in zip(path, path[1:])]
    if not 60_000 <= distances[end] <= 100_000:
        raise ValueError('Canal source length outside sanity bounds')
    if not any(w['tags'].get('lock') == 'yes' for w in ways):
        raise ValueError('Canal path bypasses all source locks')
    return dict(nodeIds=path, points=[coordinates[n] for n in path],
                wayIds=[w['id'] for w in ways], lockSegments=[w['tags'].get('lock') == 'yes' for w in ways],
                lengthM=distances[end], rejectedWays=rejected), blocked

def connectors(path, nodes, land):
    result = []
    for endpoint in (0, len(path['points']) - 1):
        point = path['points'][endpoint]
        candidates = []
        for index, node in enumerate(nodes):
            if node['kind'] != 'DERIVED_MESH' or not box(*BBOX).covers(Point(node['point'])):
                continue
            distance = GEOD.inv(*point, *node['point'])[2]
            if 0 < distance <= 30_000 and clear([point, node['point']], land):
                candidates.append((distance, node['id'], index))
        if not candidates:
            raise ValueError('No coastline-clear ocean connector')
        result.extend(dict(endpoint=endpoint, baseNode=index, distanceM=distance)
                      for distance, _, index in sorted(candidates)[:3])
    return result
