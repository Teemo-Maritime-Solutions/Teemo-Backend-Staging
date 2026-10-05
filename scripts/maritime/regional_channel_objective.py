"""Offline chart-channel research objective; no passage or depth-clearance claim."""
import heapq
import math

from shapely import LineString, union_all
from shapely.ops import transform

from build_graph import geodesic

OBJECTIVES = ('DISTANCE_V1', 'CHART_CHANNEL_LEXICOGRAPHIC_V1', 'CHART_CHANNEL_BALANCED_V1')


def lexicographic_route(adjacency, origins, destinations, balanced=False):
    """Minimize (off-channel projected metres, physical geodesic metres).

    balanced=True uses distance + outside with the explicit fixed coefficient 1.
    No observed trajectory or artificial speed enters either objective. All clear
    connectors participate. Monotone tuple costs permit Dijkstra.
    """
    labels, parents, starts, queue = {}, {}, {}, []
    infinity = (math.inf, math.inf, math.inf)
    for node, distance, outside in origins + destinations:
        if (type(node) is not int or not 0 <= node < len(adjacency) or not math.isfinite(distance)
            or not math.isfinite(outside) or distance < 0 or outside < 0):
            raise ValueError('Invalid physical/channel connector cost')
    for node, distance, outside in origins:
        score = (outside + distance if balanced else outside, distance, outside)
        if score < labels.get(node, infinity):
            labels[node], parents[node], starts[node] = score, None, distance
            heapq.heappush(queue, (score, node))
    while queue:
        score, node = heapq.heappop(queue)
        if score != labels[node]:
            continue
        for end, distance, outside in adjacency[node]:
            if type(end) is not int or not 0 <= end < len(adjacency) or not math.isfinite(distance) or not math.isfinite(outside) or distance <= 0 or outside < 0:
                raise ValueError('Invalid physical/channel edge cost')
            candidate = (score[0] + outside + (distance if balanced else 0), score[1] + distance, score[2] + outside)
            if candidate < labels.get(end, infinity):
                labels[end], parents[end], starts[end] = candidate, node, starts[node]
                heapq.heappush(queue, (candidate, end))
    choices = [((labels[node][0] + outside + (distance if balanced else 0), labels[node][1] + distance, labels[node][2] + outside), node, distance)
               for node, distance, outside in destinations if node in labels]
    if not choices:
        return None
    score, end, end_distance = min(choices)
    nodes = [end]
    while parents[nodes[-1]] is not None:
        nodes.append(parents[nodes[-1]])
        if len(nodes) > len(adjacency):
            raise ValueError('Cyclic channel-path reconstruction')
    nodes.reverse()
    return (nodes, score[1], [starts[end], end_distance]), score[2]


class ChannelObjective:
    def __init__(self, domain, edges, node_count):
        if domain.config.water_model != 'S57_GROUP1_V2':
            raise ValueError('Channel experiment requires corrected V2 domain')
        self.domain = domain
        candidates = [(key, geom) for key, geom, _ in domain.features[208]
                      if geom.is_valid and geom.geom_type in ('Polygon', 'MultiPolygon')]
        projected = [(key, transform(domain.forward, geom)) for key, geom in candidates]
        projected.extend((key, geom) for key, geom, _ in domain.depths if key.startswith('noaa-enc:228:'))
        if not projected:
            raise ValueError('No source-published fairway/dredged channel geometry')
        self.source_ids = sorted(key for key, _ in projected)
        self.channel = union_all([geom for _, geom in projected]).intersection(domain.water)
        if self.channel.is_empty:
            raise ValueError('No chart channels in conservative water')
        self.adjacency = [[] for _ in range(node_count)]
        for edge in edges:
            self.adjacency[edge['fromNode']].append((edge['toNode'], edge['distanceM'], self.outside(edge['geometry'])))

    def outside(self, points):
        projected = transform(self.domain.forward, LineString(points))
        return float(projected.difference(self.channel).length)

    def route(self, origins, destinations, endpoints, points, balanced=False):
        connectors = []
        for anchors, endpoint in zip((origins, destinations), endpoints):
            connectors.append([(node, distance, self.outside(geodesic(endpoint, points[node], self.domain.config.geodesic_step_m / 4)[1]))
                               for node, distance in anchors])
        return lexicographic_route(self.adjacency, *connectors, balanced=balanced)
