"""Source-exact Suez centerline and audited connections to the ocean mesh."""
import hashlib
import heapq
import io
import json
from pathlib import Path
import sys
import zipfile

import shapefile
from shapely import Point, LineString, box
from shapely.geometry import shape
from shapely.ops import unary_union
from build_graph import GEOD, geodesic, verify_receipt


BBOX = (31.0, 28.5, 34.5, 32.2)
SOURCE = 'osm-egypt-suez'
ZONE = 'SUEZ_CANAL_RESEARCH'
POLICY = 'OSM_EXACT_SUEZ_CENTERLINE_GSHHG_OUTSIDE_V1'


def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def extract(receipt_path):
    path, receipt = verify_receipt(receipt_path)
    if receipt['id'] != SOURCE:
        raise ValueError('Unexpected Suez source')
    sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'target/panama-python'))
    import osmium
    class Extract(osmium.SimpleHandler):
        def __init__(self):
            super().__init__(); self.ways = []
        def way(self, way):
            if way.tags.get('waterway') != 'canal' or way.tags.get('name:en') != 'Suez Canal':
                return
            points = [[node.lon, node.lat] for node in way.nodes]
            if not any(box(*BBOX).covers(Point(p)) for p in points):
                return
            self.ways.append(dict(id=way.id, version=way.version, timestamp=str(way.timestamp),
                                  tags=dict(way.tags), nodes=[node.ref for node in way.nodes], geometry=points))
    reader = Extract()
    reader.apply_file(str(path), locations=True)
    return dict(ways=reader.ways), receipt


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


def source_path(data):
    coordinates, adjacency, source_segments, rejected = {}, {}, {}, []
    for way in data['ways']:
        tags = way['tags']
        if tags.get('oneway', 'no') not in ('no', '0', 'false') or any(tags.get(k) in ('no', 'private') for k in ('access', 'boat', 'ship')):
            rejected.append(dict(wayId=way['id'], reason='DIRECTIONAL_OR_RESTRICTED_SOURCE'))
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
            source_segments[key] = way['id']
            distance = GEOD.inv(*coordinates[a], *coordinates[b])[2]
            adjacency.setdefault(a, []).append((b, distance))
            adjacency.setdefault(b, []).append((a, distance))
    if not adjacency:
        raise ValueError('No source canal network')
    start = max(coordinates, key=lambda n: coordinates[n][1])
    end = min(coordinates, key=lambda n: coordinates[n][1])
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
        raise ValueError('No continuous source Suez canal path')
    ids = [end]
    while ids[-1] != start:
        ids.append(previous[ids[-1]])
    ids.reverse()
    if not 100_000 <= distances[end] <= 230_000:
        raise ValueError('Source length outside Suez sanity bounds')
    return dict(nodeIds=ids, points=[coordinates[n] for n in ids],
                wayIds=[source_segments[tuple(sorted((a, b)))] for a, b in zip(ids, ids[1:])],
                lengthM=distances[end], rejectedWays=rejected)


def connectors(path, nodes, land):
    result = []
    for endpoint in (0, len(path['points']) - 1):
        point = path['points'][endpoint]
        candidates = []
        for index, node in enumerate(nodes):
            if node['kind'] != 'DERIVED_MESH' or not box(*BBOX).covers(Point(node['point'])):
                continue
            distance = GEOD.inv(*point, *node['point'])[2]
            if 0 < distance <= 80_000 and clear([point, node['point']], land):
                candidates.append((distance, node['id'], index))
        if not candidates:
            raise ValueError('No coastline-clear ocean connector for endpoint ' + str(endpoint))
        result.extend(dict(endpoint=endpoint, baseNode=index, distanceM=distance)
                      for distance, _, index in sorted(candidates)[:3])
    return result
