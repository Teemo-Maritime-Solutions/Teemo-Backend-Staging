"""Derived coastal refinement joining graph scales, never observed approaches."""
import h3
import numpy as np
from scipy.spatial import cKDTree
from shapely import Point
from shapely.ops import transform

from build_graph import geodesic
from regional_priority import REGION

PARAMETERS = dict(h3Resolution=8, extentM=20000, maxNodes=20000,
                  candidates=24, connectionsPerNode=3, globalConnectorM=20000,
                  regionalConnectorM=1500, geodesicStepM=25)


def transition_nodes(priority, land, source_id):
    area = priority.coverage.buffer(PARAMETERS["extentM"])
    geographic = transform(priority.domain.inverse, area)
    cells = sorted(h3.geo_to_cells(geographic.__geo_interface__, PARAMETERS["h3Resolution"]))
    if len(cells) > PARAMETERS["maxNodes"]:
        raise ValueError("Transition allocation exceeds registered budget")
    result = []
    for cell in cells:
        lat, lon = h3.cell_to_latlng(cell)
        point = [lon, lat]
        if priority.coverage.covers(Point(priority.domain.forward(lon, lat))) or not land.clear([point]):
            continue
        result.append(dict(id="H3_TRANSITION:" + cell, point=point, kind="DERIVED_COASTAL_TRANSITION",
                           sourceIds=[source_id]))
    return result


def transition_edges(nodes, global_count, regional_count, priority, land, land_id, chart_id):
    start = global_count + regional_count
    seen = set()

    def pair(a, b, kind, maximum):
        key = (min(a, b), max(a, b))
        if a == b or key in seen:
            return []
        distance, points = geodesic(nodes[a]["point"], nodes[b]["point"], PARAMETERS["geodesicStepM"])
        if not 0 < distance <= maximum:
            return []
        decision = priority.inspect(points)
        if not decision.allowed or not priority.outside_clear(points, land):
            return []
        seen.add(key)
        return [dict(fromNode=x, toNode=y, geometry=geometry, distanceM=distance,
                     geometryModel="WGS84_GEODESIC", minimumDepthM=None, specialZone=None,
                     legalStatus="UNKNOWN", restrictions=list(decision.restrictions), kind=kind,
                     regionalControlIds=[REGION] if decision.touched else [],
                     sourceIds=sorted({land_id, chart_id} if decision.touched else {land_id}))
                for x, y, geometry in ((a, b, points), (b, a, list(reversed(points))))]

    indices = {node["id"].split(":", 1)[1]: i for i, node in enumerate(nodes[start:], start)}
    for cell, a in indices.items():
        for neighbor in sorted(h3.grid_disk(cell, 1)):
            b = indices.get(neighbor)
            if b is not None and b > a:
                yield from pair(a, b, "COASTAL_TRANSITION_MESH", PARAMETERS["regionalConnectorM"])

    def connect_group(candidates, target_indices, maximum, kind):
        if not target_indices:
            return
        tree = cKDTree([priority.domain.forward(*nodes[i]["point"]) for i in target_indices])
        for a in candidates:
            _, near = tree.query(priority.domain.forward(*nodes[a]["point"]),
                                 k=min(PARAMETERS["candidates"], tree.n), distance_upper_bound=maximum)
            connected = 0
            for index in np.atleast_1d(near):
                if index >= tree.n:
                    continue
                edges = pair(a, target_indices[int(index)], kind, maximum)
                if edges:
                    yield from edges
                    connected += 1
                    if connected == PARAMETERS["connectionsPerNode"]:
                        break

    left, bottom, right, top = priority.geographic_coverage.bounds
    global_near = [i for i, node in enumerate(nodes[:global_count])
                   if left - .6 <= node["point"][0] <= right + .6 and bottom - .4 <= node["point"][1] <= top + .4]
    yield from connect_group(global_near, list(range(start, len(nodes))), PARAMETERS["globalConnectorM"], "GLOBAL_TRANSITION_CONNECTOR")
    yield from connect_group(range(start, len(nodes)), list(range(global_count, start)),
                             PARAMETERS["regionalConnectorM"], "REGIONAL_BOUNDARY_CONNECTOR")
