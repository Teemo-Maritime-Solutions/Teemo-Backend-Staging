"""Offline regional ENC research mesh. Chart evidence is not navigation permission."""
import argparse
from collections import Counter
from dataclasses import asdict, dataclass
import hashlib
import json
import math
from pathlib import Path
import time
import zipfile

import h3
import numpy as np
import pyproj
from scipy.sparse import csr_matrix
from scipy.sparse.csgraph import connected_components
from scipy.spatial import cKDTree
import shapely
from shapely import LineString, Point, box, union_all
from shapely.geometry import shape
from shapely.ops import transform

from build_graph import encode, geodesic, verify_receipt
from enc_snapshot import layer_registry

EXCLUSIONS = {33, 34, 36, 84, 85, 87, 99, 121, 138, 141, 156, 158, 177,
              230, 233}
REGULATORY = {197, 214, 215}
EXCLUSIONS_V2 = EXCLUSIONS | {18, 28, 38, 53, 55, 58, 149, 176, 180}
MODE_PROFILES = {"DEPARE_ONLY_V1": "NOAA_ENC_REGIONAL_V1", "S57_GROUP1_V2": "NOAA_ENC_REGIONAL_V2"}
MISSING = ["current_permissions_and_closures", "tide_and_vertical_datum_clearance",
           "vessel_model", "weather", "held_out_ais_validation", "global_validation"]


def coverage_available(value):
    # ENC Direct exposes the decoded S-57 label as a string, not always numeric 1.
    return (type(value) is int and value == 1) or value == "coverage available"


@dataclass(frozen=True)
class PilotConfig:
    h3_resolution: int = 10
    geodesic_step_m: float = 25
    geometry_tolerance_m: float = 1
    coastal_buffer_m: float = 0
    max_connector_m: float = 500
    connector_candidates: int = 24
    connections_per_endpoint: int = 3
    max_nodes: int = 100000
    max_extent_degrees: float = 2
    water_model: str = "DEPARE_ONLY_V1"

    def validate(self):
        if not isinstance(self.water_model, str) or self.water_model not in MODE_PROFILES:
            raise ValueError("Unsupported versioned water model")
        for name, low, high in (("h3_resolution", 8, 12), ("connector_candidates", 1, 128),
                                ("connections_per_endpoint", 1, 16), ("max_nodes", 100, 500000)):
            value = getattr(self, name)
            if type(value) is not int or not low <= value <= high:
                raise ValueError("Invalid bounded integer: " + name)
        for name, low, high in (("geodesic_step_m", .1, 100), ("geometry_tolerance_m", .01, 100),
                                ("coastal_buffer_m", 0, 1000), ("max_connector_m", 1, 5000),
                                ("max_extent_degrees", .01, 2)):
            value = getattr(self, name)
            if type(value) not in (int, float) or not math.isfinite(value) or not low <= value <= high:
                raise ValueError("Invalid bounded engineering parameter: " + name)
        if self.connections_per_endpoint > self.connector_candidates:
            raise ValueError("Connections exceed candidate budget")

    @property
    def profile(self):
        self.validate()
        return MODE_PROFILES[self.water_model]


def overhead_bridge(layer, props):
    """Only classify source geometry; this never establishes vessel clearance.

    IHO S-57 UOC 4.4 section 4.8.2 separates bridge decks, water and PYLONS.
    Opening, floating, mixed, unknown and height-less bridges remain blocked.
    """
    height, category = props.get("VERCLR"), props.get("CATBRG")
    supported = (type(category) is int and category in (1, 12)) or (
        type(category) is str and category in ("fixed bridge", "suspension bridge"))
    return layer in (87, 141) and supported and type(height) in (int, float) and math.isfinite(height) and height > 0


def feature_key(layer, record, oid_field):
    props = record["properties"]
    return f"noaa-enc:{layer}:{props['DSNM']}:{props[oid_field]}"


def load_snapshot(receipt_path):
    archive_path, receipt = verify_receipt(receipt_path)
    with zipfile.ZipFile(archive_path) as archive:
        if len(set(archive.namelist())) != len(archive.namelist()) or any(i.file_size > 100000000 for i in archive.infolist()):
            raise ValueError("Duplicate or oversized source archive member")
        requests = json.loads(archive.read("requests.json"))
        for request in requests:
            if hashlib.sha256(archive.read(request["file"])).hexdigest() != request["sha256"]:
                raise ValueError("Source response digest mismatch")
        source = json.loads(archive.read("source.json"))
    if source.get("schemaVersion") != 1 or source.get("purpose") != "REGIONAL_RESEARCH_NOT_NAVIGATION":
        raise ValueError("Unsupported regional source")
    if set(source["layers"]) != {str(i) for i in layer_registry(source.get("layerRegistryVersion", 1))}:
        raise ValueError("Incomplete source layer registry")
    return source, receipt


class ChartDomain:
    """Conservative source-water intersection with explicit charted exclusions."""
    def __init__(self, source, config):
        config.validate()
        self.config = config
        registry = layer_registry(source.get("layerRegistryVersion", 1))
        corrected = config.water_model == "S57_GROUP1_V2"
        if corrected and (source.get("layerRegistryVersion") != 2 or set(source["layers"]) != {str(i) for i in registry}):
            raise ValueError("Corrected domain requires complete V2 support/hazard layers")
        self.features, self.invalid = {}, []
        cover = []
        for layer, value in source["layers"].items():
            layer = int(layer)
            if layer not in registry or value["metadata"]["name"] != "Harbor." + registry[layer]:
                raise ValueError("Source registry mismatch")
            seen = set()
            self.features[layer] = []
            for record in value["features"]:
                key = feature_key(layer, record, value["objectIdField"])
                if key in seen or record["properties"]["DSNM"] not in source["cells"]:
                    raise ValueError("Duplicate/out-of-scope source feature")
                seen.add(key)
                geom = shape(record["geometry"])
                bounds = geom.bounds
                if geom.is_empty or len(bounds) != 4 or not all(math.isfinite(v) for v in bounds) or not (-180 <= bounds[0] <= bounds[2] <= 180 and -90 <= bounds[1] <= bounds[3] <= 90):
                    raise ValueError("Missing/invalid coordinate extent: " + key)
                self.features[layer].append((key, geom, record["properties"]))
                if layer == 219 and coverage_available(record["properties"].get("CATCOV")):
                    if not geom.is_valid or geom.geom_type not in ("Polygon", "MultiPolygon"):
                        raise ValueError("Invalid source coverage: " + key)
                    cover.append(geom)
        if not cover:
            raise ValueError("No declared CATCOV=1 coverage")
        coverage = union_all(cover)
        lo, bottom, hi, top = coverage.bounds
        if hi - lo > config.max_extent_degrees or top - bottom > config.max_extent_degrees or max(abs(bottom), abs(top)) >= 80:
            raise ValueError("Regional projection extent exceeded; antimeridian/polar charts need a different profile")
        self.crs = pyproj.CRS.from_proj4(f"+proj=aeqd +lat_0={(bottom+top)/2} +lon_0={(lo+hi)/2} +datum=WGS84 +units=m")
        self.forward = pyproj.Transformer.from_crs(4326, self.crs, always_xy=True).transform
        self.inverse = pyproj.Transformer.from_crs(self.crs, 4326, always_xy=True).transform
        positive, blocked = [], []
        self.regulatory = []
        self.depths = []
        self.solid_feature_ids = set()
        self.overhead_feature_ids = []
        for layer, records in self.features.items():
            for key, geom, props in records:
                if not geom.is_valid:
                    # No repair or omission: an invalid feature's whole envelope is excluded.
                    self.invalid.append(key)
                    blocked.append(transform(self.forward, box(*geom.bounds)))
                    continue
                projected = transform(self.forward, geom)
                overhead = corrected and overhead_bridge(layer, props)
                if layer in REGULATORY or (corrected and layer == 154) or overhead:
                    self.regulatory.append((key, projected, props))
                if overhead:
                    self.overhead_feature_ids.append(key)
                if layer in (EXCLUSIONS_V2 if corrected else EXCLUSIONS) and not overhead:
                    blocked.append(projected)
                    self.solid_feature_ids.add(key)
                if layer == 219 and not coverage_available(props.get("CATCOV")):
                    blocked.append(projected)
                if layer in (227, 228):
                    lower, upper = props.get("DRVAL1"), props.get("DRVAL2")
                    known = type(lower) in (int, float) and math.isfinite(lower) and lower > 0
                    if upper is not None and (type(upper) not in (int, float) or not math.isfinite(upper) or not known or upper < lower):
                        known = False
                    if known and geom.geom_type in ("Polygon", "MultiPolygon"):
                        self.depths.append((key, projected, props))
                        # V1 is preserved for replay. IHO UOC 4.4 section 5.5:
                        # DRGARE itself is Group-1 water, not a DEPARE overlay.
                        if layer == 227 or corrected:
                            positive.append(projected)
                    else:
                        blocked.append(projected)
        if not positive:
            raise ValueError("No positive known charted water for selected model")
        padding = config.geometry_tolerance_m + config.coastal_buffer_m
        # Square-corner joins and an expanded radius conservatively enclose each buffer.
        self.excluded = union_all([g.buffer(padding * 1.01, quad_segs=16, join_style=2) for g in blocked])
        water = transform(self.forward, coverage).intersection(union_all(positive))
        self.water = water.buffer(-padding, join_style=2).difference(self.excluded)
        if self.water.is_empty or not self.water.is_valid:
            raise ValueError("Empty/invalid conservative water domain")
        shapely.prepare(self.water)
        self.depth_tree = shapely.STRtree([g for _, g, _ in self.depths])
        self.regulatory_tree = shapely.STRtree([g.buffer(padding * 1.01, quad_segs=16, join_style=2) for _, g, _ in self.regulatory])

    def allows(self, points):
        geom = Point(points[0]) if len(points) == 1 else LineString(points)
        return bool(self.water.covers(transform(self.forward, geom)))

    def depth_evidence(self, points):
        geom = transform(self.forward, LineString(points))
        return [self.depths[i][0] for i in sorted(self.depth_tree.query(geom, predicate="intersects"))]

    def restrictions(self, points):
        geom = transform(self.forward, Point(points[0]) if len(points) == 1 else LineString(points))
        return [self.regulatory[i][0] for i in sorted(self.regulatory_tree.query(geom, predicate="intersects"))]


def mesh(source, receipt, config):
    domain = ChartDomain(source, config)
    polygon = transform(domain.inverse, domain.water)
    # A geographic envelope bounds allocation before H3 materializes the cell set.
    envelope = transform(domain.forward, box(*polygon.bounds))
    estimate = envelope.area / h3.average_hexagon_area(config.h3_resolution, "m^2")
    if estimate > config.max_nodes:
        raise ValueError("Mesh allocation estimate exceeds node budget")
    cells = sorted(h3.geo_to_cells(polygon.__geo_interface__, config.h3_resolution))
    nodes, indices = [], {}
    refs = [receipt["id"]]
    for cell in cells:
        lat, lon = h3.cell_to_latlng(cell)
        point = [lon, lat]
        if domain.allows([point]):
            indices[cell] = len(nodes)
            nodes.append(dict(id="h3:" + cell, point=point, kind="REGIONAL_RESEARCH_MESH", sourceIds=refs))
    if not nodes or len(nodes) > config.max_nodes:
        raise ValueError("Empty/oversized regional mesh")
    edges = []

    def connect(a, b, kind):
        distance, points = geodesic(nodes[a]["point"], nodes[b]["point"], config.geodesic_step_m)
        if distance <= 0 or not domain.allows(points):
            return False
        evidence = domain.depth_evidence(points)
        for start, end, geometry in ((a, b, points), (b, a, list(reversed(points)))):
            edges.append(dict(fromNode=start, toNode=end, geometry=geometry, distanceM=distance,
                              geometryModel="WGS84_GEODESIC", kind=kind, minimumDepthM=None,
                              chartDepthFeatureIds=evidence, legalStatus="UNKNOWN", restrictions=domain.restrictions(points),
                              specialZone=None, sourceIds=refs))
        return True

    for cell, a in indices.items():
        for neighbor in sorted(h3.grid_disk(cell, 1)):
            b = indices.get(neighbor)
            if b is not None and b > a:
                connect(a, b, "REGIONAL_MESH")
    tree = cKDTree([domain.forward(*n["point"]) for n in nodes])
    ports = []
    for key, geom, props in domain.features[49]:
        if geom.geom_type != "Point":
            raise ValueError("Berth point layer geometry changed")
        point = list(geom.coords[0])
        port = dict(id=key, name=props.get("OBJNAM") or "UNNAMED_SOURCE_BERTH", point=point,
                    nodeId=None, status="OUTSIDE_CONSERVATIVE_WATER", sourceIds=refs,
                    sourceFeatureId=key, sourceAttributes=props)
        ports.append(port)
        if not domain.allows([point]):
            continue
        if len(nodes) >= config.max_nodes:
            raise ValueError("Endpoint allocation exceeds node budget")
        a = len(nodes)
        nodes.append(dict(id=key, point=point, kind="SOURCE_BERTH_REFERENCE", sourceIds=refs))
        _, candidates = tree.query(domain.forward(*point), k=min(config.connector_candidates, tree.n), distance_upper_bound=config.max_connector_m)
        connected = 0
        for b in np.atleast_1d(candidates):
            if b < tree.n and geodesic(point, nodes[int(b)]["point"], config.geodesic_step_m)[0] <= config.max_connector_m and connect(a, int(b), "BERTH_CONNECTOR"):
                connected += 1
                if connected == config.connections_per_endpoint:
                    break
        if connected:
            port.update(nodeId=a, status="CONNECTED_REFERENCE_POINT")
        else:
            nodes.pop()
            port["status"] = "NO_CLEAR_CONNECTOR_WITHIN_BUDGET"
    graph = csr_matrix((np.ones(len(edges)), ([e["fromNode"] for e in edges], [e["toNode"] for e in edges])), shape=(len(nodes), len(nodes)))
    count, labels = connected_components(graph, directed=True, connection="strong")
    for port in ports:
        port["component"] = None if port["nodeId"] is None else int(labels[port["nodeId"]])
    report = dict(nodes=len(nodes), directedEdges=len(edges), strongComponents=count,
                  largestComponent=int(np.bincount(labels).max()), portStatuses=dict(Counter(p["status"] for p in ports)),
                  sourceFeatureCounts={str(k): len(v) for k, v in domain.features.items()},
                  invalidGeometryEnvelopeExclusions=domain.invalid,
                  unresolvedOverheadBridgeFeatureIds=domain.overhead_feature_ids,
                  waterAreaProjectedM2=domain.water.area)
    report["edgesBlockedByUnresolvedChartRestrictions"] = sum(bool(e["restrictions"]) for e in edges)
    report["routingEligibility"] = "NOT_OPERATIONALLY_VALIDATED"
    return nodes, edges, ports, report, domain


def build(receipt_path, output, config):
    started = time.perf_counter()
    if output.exists():
        raise ValueError("Artifact exists; choose an immutable new output")
    source, receipt = load_snapshot(receipt_path)
    nodes, edges, ports, report, domain = mesh(source, receipt, config)
    payloads = {name: b"".join(encode(row) + b"\n" for row in rows) for name, rows in
                (("nodes.jsonl", nodes), ("edges.jsonl", edges), ("ports.jsonl", ports))}
    payloads["chart-evidence.jsonl"] = b"".join(encode(dict(id=key, geometry=geom.__geo_interface__, attributes=props, sourceIds=[receipt["id"]])) + b"\n"
                                                   for records in domain.features.values() for key, geom, props in records)
    manifest = dict(schemaVersion=1, purpose="RESEARCH_NOT_NAVIGATION", validationProfile=config.profile,
                    parameters=asdict(config), sources=[receipt], report=report, missingData=MISSING,
                    projection=domain.crs.to_string(), tables={name: hashlib.sha256(data).hexdigest() for name, data in payloads.items()},
                    implementation=dict(builderSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                                        geodesicHelperSha256=hashlib.sha256(Path(__file__).with_name("build_graph.py").read_bytes()).hexdigest(),
                                        h3=h3.__version__, shapely=shapely.__version__, pyproj=pyproj.__version__),
                    limitations=["Regional GIS geometry evidence only; not certified for navigation or global phase-3 validation",
                                 "Bidirectional research topology does not assert permission or observed AIS direction",
                                 "Chart depth attributes retained verbatim, not current water depth or vessel clearance",
                                 "Overhead bridge geometry, when enabled, remains an unresolved source restriction; no height/datum/maintenance inference",
                                 "Invalid source geometries excluded by full envelope, never repaired",
                                 "No automatic connection to global mesh, activation or country-QA exemption"])
    manifest["graphVersion"] = hashlib.sha256(encode(manifest)).hexdigest()
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "x", compression=zipfile.ZIP_DEFLATED) as archive:
        for name, data in sorted({**payloads, "manifest.json": encode(manifest)}.items()):
            info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, data)
    print(json.dumps(dict(graphVersion=manifest["graphVersion"], report=report, seconds=time.perf_counter()-started,
                          artifactBytes=output.stat().st_size, artifactSha256=hashlib.sha256(output.read_bytes()).hexdigest())), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--receipt", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--config", type=Path, required=True)
    args = parser.parse_args()
    build(args.receipt, args.output, PilotConfig(**json.loads(args.config.read_text())))
