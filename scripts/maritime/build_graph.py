"""Offline H3 mesh builder. Derived geometry is not an observed sailing route."""
import argparse
import csv
import hashlib
import io
import json
import math
import re
import time
import zipfile
import sys
import pyproj
import scipy
from collections import Counter
from dataclasses import asdict, dataclass
from pathlib import Path

import h3
import numpy as np
import shapefile
import shapely
from pyproj import Geod
from scipy.sparse import csr_matrix
from scipy.sparse.csgraph import connected_components
from scipy.spatial import cKDTree
from shapely import LineString, Point, Polygon, STRtree, box
from shapely.affinity import translate

GEOD = Geod(ellps="WGS84")
BUILDER_SHA256 = hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
MISSING = ["surveyed_depth", "canal_permissions_and_geometry", "traffic_separation_rules",
           "closures", "weather", "ice", "vessel_model", "directed_ais_tracks", "held_out_ais_validation"]


@dataclass(frozen=True)
class Config:
    base_resolution: int = 2
    coastal_resolution: int = 4
    port_resolution: int = 6
    shoreline_resolution: str = "h"
    geodesic_step_m: float = 2000
    geometry_tolerance_deg: float = 0.00001
    coastal_buffer_m: float = 0
    max_port_connector_m: float = 20000
    port_candidates: int = 24
    port_connections: int = 3
    max_nodes: int = 500000
    invalid_geometry_policy: str = "REJECT"

    def validate(self):
        if not 0 <= self.base_resolution <= self.coastal_resolution <= self.port_resolution <= 8:
            raise ValueError("Require 0 <= base <= coastal <= port <= 8")
        if self.shoreline_resolution not in ("f", "h", "i"):
            raise ValueError("Use GSHHG full/high/intermediate explicitly")
        for value, upper in ((self.geodesic_step_m, 10000), (self.geometry_tolerance_deg, .01),
                             (self.max_port_connector_m, 100000)):
            if not math.isfinite(value) or not 0 < value <= upper:
                raise ValueError("Invalid bounded engineering parameter")
        if not math.isfinite(self.coastal_buffer_m) or not 0 <= self.coastal_buffer_m <= 10000:
            raise ValueError("Invalid coastal buffer")
        if not 1 <= self.port_connections <= self.port_candidates <= 128 or not 100 <= self.max_nodes <= 2000000:
            raise ValueError("Invalid connection/resource limit")
        if self.invalid_geometry_policy not in ("REJECT", "BLOCK_ENVELOPE"):
            raise ValueError("Unknown invalid geometry policy")


def encode(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False, allow_nan=False).encode("utf-8")


def verify_receipt(path):
    receipt = json.loads(path.read_text(encoding="utf-8"))
    for field in ("id", "url", "license", "licenseUrl", "version", "acquiredAt", "unit", "coverage", "limitations", "sha256", "file"):
        if not receipt.get(field):
            raise ValueError("Missing provenance: " + field)
    if receipt.get("status") != "SOURCE_DATA":
        raise ValueError("Only source-data receipts accepted by production builder")
    candidate = path.parent / receipt["file"]
    if candidate.resolve().parent != path.parent.resolve():
        raise ValueError("Source path must be a sibling of receipt")
    with candidate.open("rb") as file:
        digest = hashlib.file_digest(file, "sha256").hexdigest()
    if digest != receipt["sha256"]:
        raise ValueError("Source checksum mismatch: " + receipt["id"])
    return candidate, receipt


def point_ok(point):
    return (len(point) == 2 and all(math.isfinite(v) for v in point)
            and -180 <= point[0] <= 180 and -90 <= point[1] <= 90)


def split_dateline(points):
    """Split a densified geodesic at +/-180; never draw through Greenwich."""
    result, part = [], [points[0]]
    for a, b in zip(points, points[1:]):
        if abs(b[0] - a[0]) > 180:
            unwrapped = b[0] + (360 if b[0] < a[0] else -360)
            seam = 180 if a[0] >= 0 else -180
            lat = a[1] + (b[1] - a[1]) * (seam - a[0]) / (unwrapped - a[0])
            part.append((seam, lat))
            result.append(LineString(part))
            part = [(-seam, lat)]
        part.append(b)
    if len(part) > 1:
        result.append(LineString(part))
    return result


def geodesic(a, b, step):
    if not point_ok(a) or not point_ok(b):
        raise ValueError("Invalid geodesic endpoint")
    _, _, distance = GEOD.inv(*a, *b)
    count = max(0, math.ceil(distance / step) - 1)
    return distance, [a, *GEOD.npts(*a, *b, count), b] if count else [a, b]


class Land:
    def __init__(self, polygons, config):
        if not polygons or any(p.is_empty or not p.is_valid for p in polygons):
            raise ValueError("Empty/invalid land geometries; no silent repair")
        self.config = config
        self.polygons = np.array(polygons, dtype=object)
        shapely.prepare(self.polygons)
        self.tree = STRtree(self.polygons)
        self.boundaries = STRtree([p.boundary for p in polygons])

    def intersects(self, geom):
        ids = self.tree.query(geom)
        return bool(len(ids) and shapely.intersects(self.polygons[ids], geom).any())

    def on_land(self, point):
        if not point_ok(point):
            raise ValueError("Invalid point")
        return self.intersects(Point(point))

    def coastal_cell(self, cell):
        lat, lon = h3.cell_to_latlng(cell)
        # A conservative envelope is only a refinement trigger, never a navigability test.
        radius = h3.average_hexagon_edge_length(h3.get_resolution(cell), "m") * 3 / 110000
        dx = min(180, radius / max(.01, math.cos(math.radians(lat))))
        for shift in (-360, 0, 360):
            envelope = box(lon - dx + shift, max(-90, lat - radius), lon + dx + shift, min(90, lat + radius))
            if len(self.boundaries.query(envelope, predicate="intersects")):
                return True
        return False

    def clear(self, points):
        if not points or any(not point_ok(p) for p in points):
            raise ValueError("Missing/invalid edge geometry")
        if len(points) == 1:
            return not self.on_land(points[0])
        for line in split_dateline(points):
            max_lat = max(abs(line.bounds[1]), abs(line.bounds[3]))
            # Conservative angular expansion in both axes, not a claimed exact metric buffer.
            padding = self.config.geometry_tolerance_deg
            if self.config.coastal_buffer_m:
                padding += self.config.coastal_buffer_m / (110000 * max(.00001, math.cos(math.radians(max_lat))))
            corridor = line.buffer(padding)
            for shift in (-360, 0, 360):
                if self.intersects(translate(corridor, xoff=shift)):
                    return False
        return True


def load_land(archive, config):
    polygons = []
    quarantined = []
    with zipfile.ZipFile(archive) as z:
        for level in (1, 5):
            prefix = f"GSHHS_shp/{config.shoreline_resolution}/GSHHS_{config.shoreline_resolution}_L{level}"
            reader = shapefile.Reader(shp=io.BytesIO(z.read(prefix + ".shp")),
                                      shx=io.BytesIO(z.read(prefix + ".shx")),
                                      dbf=io.BytesIO(z.read(prefix + ".dbf")))
            for source_index, record in enumerate(reader.iterShapes()):
                geometry = shapely.geometry.shape(record.__geo_interface__)
                if not geometry.is_valid or geometry.is_empty:
                    detail = dict(layer=prefix, record=source_index, reason=shapely.is_valid_reason(geometry))
                    if config.invalid_geometry_policy == "REJECT" or geometry.is_empty:
                        raise ValueError("Invalid source geometry: " + json.dumps(detail))
                    # Never turn uncertain land into water. Block its entire source-derived extent.
                    detail["blockedBounds"] = list(geometry.bounds)
                    quarantined.append(detail)
                    geometry = geometry.envelope
                if geometry.geom_type == "MultiPolygon":
                    polygons.extend(geometry.geoms)
                else:
                    polygons.append(geometry)
    land = Land(polygons, config)
    land.quarantined = quarantined
    return land


def load_ports(archive, receipt):
    ports, seen = [], {}
    if receipt["id"] == "unlocode":
        with zipfile.ZipFile(archive) as z:
            for filename in sorted(z.namelist()):
                if not re.search(r"csv/UNLOCODE CodeListPart\d.csv$", filename):
                    continue
                # Source release uses single-byte western characters; ASCII names are column 4.
                for row in csv.reader(io.StringIO(z.read(filename).decode("latin-1"))):
                    if len(row) < 12 or not row[6].startswith("1") or row[0] == "X":
                        continue
                    port_id = "UNLOCODE:" + row[1] + row[2]
                    match = re.fullmatch(r"(\d{2})(\d{2})([NS]) (\d{3})(\d{2})([EW])", row[10])
                    point = None
                    if match and int(match[2]) < 60 and int(match[5]) < 60:
                        lat = (int(match[1]) + int(match[2]) / 60) * (1 if match[3] == "N" else -1)
                        lon = (int(match[4]) + int(match[5]) / 60) * (1 if match[6] == "E" else -1)
                        if point_ok((lon, lat)):
                            point = [lon, lat]
                    if port_id in seen:
                        previous = seen[port_id]
                        previous.setdefault("duplicateSourceRows", 0)
                        previous["duplicateSourceRows"] += 1
                        if previous["point"] != point:
                            previous["status"] = "CONFLICTING_SOURCE_COORDINATES"
                            previous["point"] = None
                        continue
                    port = dict(id=port_id, name=row[4] or row[3], country=row[1], point=point,
                                      originalCoordinate=row[10], sourceIds=[receipt["id"]],
                                      nodeId=None, status="UNVALIDATED" if point else "MISSING_COORDINATES")
                    ports.append(port)
                    seen[port_id] = port
    else:
        raise ValueError("Port importer unavailable for this source; no schema guessing")
    if not ports:
        raise ValueError("No source ports found")
    return sorted(ports, key=lambda p: p["id"])


def xyz(points):
    radians = np.radians(np.array(points))
    lon, lat = radians[:, 0], radians[:, 1]
    return np.column_stack((np.cos(lat) * np.cos(lon), np.cos(lat) * np.sin(lon), np.sin(lat)))


def build(land, ports, config):
    cells = set()
    candidates = sorted(child for root in h3.get_res0_cells() for child in h3.cell_to_children(root, config.base_resolution))
    while candidates:
        next_level = []
        for cell in candidates:
            lat, lon = h3.cell_to_latlng(cell)
            if not land.on_land((lon, lat)):
                cells.add(cell)
            if h3.get_resolution(cell) < config.coastal_resolution and land.coastal_cell(cell):
                next_level.extend(h3.cell_to_children(cell))
        if len(cells) > config.max_nodes:
            raise ValueError("Node budget exceeded; choose engineering resolution explicitly")
        print(json.dumps({"stage": "mesh", "nodes": len(cells), "nextCells": len(next_level)}), flush=True)
        candidates = sorted(set(next_level))
    for port in ports:
        if port["point"] is None:
            continue
        if land.on_land(port["point"]):
            port["status"] = "ON_LAND_REQUIRES_SOURCED_APPROACH"
            continue
        lon, lat = port["point"]
        for resolution in range(config.base_resolution, config.port_resolution + 1):
            for cell in h3.grid_disk(h3.latlng_to_cell(lat, lon, resolution), 2):
                cell_lat, cell_lon = h3.cell_to_latlng(cell)
                if not land.on_land((cell_lon, cell_lat)):
                    cells.add(cell)
        if len(cells) > config.max_nodes:
            raise ValueError("Port refinement exceeded node budget")
    nodes = [dict(id="H3:" + cell, point=list(reversed(h3.cell_to_latlng(cell))), kind="DERIVED_MESH", sourceIds=["gshhg"])
             for cell in sorted(cells)]
    indices = {n["id"][3:]: i for i, n in enumerate(nodes)}
    edges, pairs = [], set()

    def connect(a, b, kind, source_ids):
        pair = (min(a, b), max(a, b))
        if a == b or pair in pairs:
            return False
        pairs.add(pair)
        distance, geometry = geodesic(nodes[a]["point"], nodes[b]["point"], config.geodesic_step_m)
        if distance <= 0 or not land.clear(geometry):
            return False
        # Geometry stored as geodesic endpoints, not degree-linear display segments.
        # API densifies using the same ellipsoidal definition; endpoints are lossless for this type.
        physical = dict(distanceM=distance, geometry=[nodes[a]["point"], nodes[b]["point"]],
                        minimumDepthM=None, kind=kind, specialZone=None, restrictions=[],
                        sourceIds=source_ids, aisObservations=None, observedSpeedKnots=None,
                        legalStatus="UNKNOWN", geometryModel="WGS84_GEODESIC")
        edges.append(dict(fromNode=a, toNode=b, **physical))
        edges.append(dict(fromNode=b, toNode=a, **{**physical, "geometry": list(reversed(physical["geometry"]))}))
        return True

    for count, cell in enumerate(sorted(cells)):
        a = indices[cell]
        neighbours = set(h3.grid_disk(cell, 1))
        for resolution in range(config.base_resolution, h3.get_resolution(cell)):
            neighbours.update(h3.grid_disk(h3.cell_to_parent(cell, resolution), 1))
        for neighbour in sorted(neighbours & cells):
            connect(a, indices[neighbour], "OCEAN_MESH", ["gshhg"])
        if count % 5000 == 0:
            print(json.dumps({"stage": "edges", "processedNodes": count, "directedEdges": len(edges)}), flush=True)

    tree = cKDTree(xyz([n["point"] for n in nodes]))
    for port in ports:
        if port["status"] != "UNVALIDATED":
            continue
        a = len(nodes)
        nodes.append(dict(id=port["id"], point=port["point"], kind="PORT_REFERENCE", sourceIds=port["sourceIds"]))
        _, nearest = tree.query(xyz([port["point"]])[0], k=min(config.port_candidates, tree.n))
        accepted = 0
        for b in np.atleast_1d(nearest):
            distance = GEOD.inv(*port["point"], *nodes[int(b)]["point"])[2]
            if distance <= config.max_port_connector_m and connect(a, int(b), "PORT_CONNECTOR", ["gshhg", *port["sourceIds"]]):
                accepted += 1
            if accepted == config.port_connections:
                break
        port["nodeId"] = a if accepted else None
        port["status"] = "CONNECTED_REFERENCE_POINT" if accepted else "NO_VALID_WATER_CONNECTOR"

    matrix = csr_matrix((np.ones(len(edges)), ([e["fromNode"] for e in edges], [e["toNode"] for e in edges])), shape=(len(nodes), len(nodes)))
    component_count, labels = connected_components(matrix, directed=True, connection="strong")
    largest = Counter(labels).most_common(1)[0][0]
    for port in ports:
        if port["nodeId"] is not None:
            port["component"] = int(labels[port["nodeId"]])
            if labels[port["nodeId"]] != largest:
                port["status"] = "DISCONNECTED_FROM_GLOBAL_COMPONENT"
    report = dict(nodes=len(nodes), directedEdges=len(edges), strongComponents=component_count,
                  largestComponentNodes=int(np.count_nonzero(labels == largest)),
                  portStatuses=dict(Counter(p["status"] for p in ports)),
                  landCrossingCheck="ALL_ACCEPTED_EDGES_CHECKED_AGAINST_BUILD_SOURCE",
                  validationGate="NOT_PASSED", missingData=MISSING)
    report["quarantinedLandGeometries"] = getattr(land, "quarantined", [])
    return nodes, sorted(edges, key=lambda e: (e["fromNode"], e["toNode"])), ports, report


def write_artifact(output, nodes, edges, ports, sources, config, report, notices):
    if output.exists():
        raise ValueError("Artifact exists; select a new output path")
    output.parent.mkdir(parents=True, exist_ok=True)
    table_hashes = {}
    with zipfile.ZipFile(output, "x", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        for name, rows in sorted({"nodes.jsonl": nodes, "edges.jsonl": edges, "ports.jsonl": ports}.items()):
            entry = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            digest = hashlib.sha256()
            with z.open(entry, "w", force_zip64=True) as target:
                for row in rows:
                    data = encode(row) + b"\n"
                    digest.update(data)
                    target.write(data)
            table_hashes[name] = digest.hexdigest()
        manifest = dict(schemaVersion=1, createdAt=max(s["acquiredAt"] for s in sources), sources=sources,
                        parameters=asdict(config), report=report, tables=table_hashes,
                        purpose="RESEARCH_NOT_NAVIGATION", buildVersion="teemo-maritime-v1",
                        builderSha256=BUILDER_SHA256,
                        dependencies={"python": sys.version.split()[0], "h3": h3.__version__, "shapely": shapely.__version__,
                                      "pyproj": pyproj.__version__, "proj": pyproj.proj_version_str,
                                      "numpy": np.__version__, "scipy": scipy.__version__})
        manifest["graphVersion"] = hashlib.sha256(encode(manifest)).hexdigest()
        for name, data in sorted({"manifest.json": encode(manifest), **notices}.items()):
            entry = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(entry, data)
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--land-receipt", type=Path, required=True)
    parser.add_argument("--ports-receipt", type=Path, required=True)
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    config = Config(**json.loads(args.config.read_text()))
    config.validate()
    land_path, land_source = verify_receipt(args.land_receipt)
    port_path, port_source = verify_receipt(args.ports_receipt)
    if land_source["id"] != "gshhg":
        raise ValueError("Only verified GSHHG shorelines supported")
    started = time.perf_counter()
    land = load_land(land_path, config)
    ports = load_ports(port_path, port_source)
    print(json.dumps({"stage": "sources", "polygons": len(land.polygons), "ports": len(ports)}), flush=True)
    nodes, edges, ports, report = build(land, ports, config)
    with zipfile.ZipFile(land_path) as z:
        notices = {"notices/" + n: z.read(n) for n in ("README.TXT", "LICENSE.TXT", "COPYING.LESSERv3")}
    manifest = write_artifact(args.output, nodes, edges, ports, [land_source, port_source], config, report, notices)
    print(json.dumps({"graphVersion": manifest["graphVersion"], "seconds": time.perf_counter() - started,
                      "artifactBytes": args.output.stat().st_size, **report}), flush=True)


if __name__ == "__main__":
    main()
