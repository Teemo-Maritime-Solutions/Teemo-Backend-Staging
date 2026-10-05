"""Held-out regional NOAA AIS diagnostic. Never trains the graph or passes the global gate."""
import argparse
import csv
import hashlib
import json
import math
import time
import zipfile
from collections import Counter, defaultdict
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from itertools import groupby

import numpy as np
import zstandard
import scipy
import shapely
import pyproj
import sys
from scipy.sparse import csr_matrix
from scipy.sparse.csgraph import dijkstra
from scipy.spatial import cKDTree

from build_graph import Config, GEOD, encode, geodesic, load_land, point_ok, verify_receipt, xyz
from validate_graph import discrete_frechet_m, discrete_hausdorff_m


@dataclass(frozen=True)
class AisConfig:
    # Sampling/filter limits are engineering choices, not observed vessel capabilities.
    sample_vessels: int
    max_cases: int
    max_gap_seconds: float
    max_jump_m: float
    min_track_distance_m: float
    max_track_distance_m: float
    max_track_points: int
    connector_radius_m: float
    connector_candidates: int
    metric_step_m: float
    max_metric_samples: int
    max_csv_rows: int
    max_selected_records: int

    def validate(self):
        for name, lower, upper in (("sample_vessels", 1, 1000), ("max_cases", 1, 200),
                                   ("max_track_points", 3, 3000), ("connector_candidates", 1, 128),
                                   ("max_metric_samples", 3, 5000),
                                   ("max_csv_rows", 1, 50000000), ("max_selected_records", 1, 2000000)):
            value = getattr(self, name)
            if type(value) is not int or not lower <= value <= upper:
                raise ValueError("Invalid resource bound: " + name)
        for name, upper in (("max_gap_seconds", 3600), ("max_jump_m", 100000),
                            ("connector_radius_m", 100000), ("metric_step_m", 5000),
                            ("min_track_distance_m", 500000), ("max_track_distance_m", 500000)):
            value = getattr(self, name)
            if not math.isfinite(value) or not 0 < value <= upper:
                raise ValueError("Invalid engineering parameter: " + name)
        if self.min_track_distance_m >= self.max_track_distance_m or self.metric_step_m < 100:
            raise ValueError("Invalid distance/metric bounds")


def fields(reader):
    old = dict(mmsi="MMSI", timestamp="BaseDateTime", lon="LON", lat="LAT", vessel_type="VesselType")
    new = dict(mmsi="mmsi", timestamp="base_date_time", lon="longitude", lat="latitude", vessel_type="vessel_type")
    for schema in (old, new):
        if set(schema.values()) <= set(reader.fieldnames or []):
            return schema
    raise ValueError("Unsupported NOAA AIS schema; do not guess field meanings")


def rows(path, config):
    with zstandard.open(path, "rt", encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream)
        schema = fields(reader)
        for index, row in enumerate(reader, 1):
            if index > config.max_csv_rows:
                raise ValueError("CSV row resource bound exceeded; not a partial validation")
            yield index, {key: row.get(field) for key, field in schema.items()}


def merchant_id(row):
    mmsi = row["mmsi"] or ""
    try:
        vessel_type = int(row["vessel_type"])
    except (TypeError, ValueError):
        return None
    # Standard AIS cargo/tanker codes, not a predicted cargo classification.
    return mmsi if len(mmsi) == 9 and mmsi.isdigit() and 70 <= vessel_type <= 89 else None


def observations(path, config):
    vessels, counts = set(), Counter()
    for index, row in rows(path, config):
        counts["csvRows"] = index
        identity = merchant_id(row)
        if identity:
            vessels.add(identity)
            counts["cargoTankerRows"] += 1
    # Preselect by identity hash before route/shoreline outcome; no success-only sampling.
    selected = set(sorted(vessels, key=lambda v: hashlib.sha256(v.encode()).digest())[:config.sample_vessels])
    counts.update(eligibleVessels=len(vessels), selectedVessels=len(selected))
    print(json.dumps(dict(stage="ais_selection", **counts)), flush=True)
    result = defaultdict(list)
    for index, row in rows(path, config):
        identity = merchant_id(row)
        if identity not in selected:
            continue
        try:
            point = (float(row["lon"]), float(row["lat"]))
            timestamp = datetime.fromisoformat(row["timestamp"].replace("Z", "+00:00"))
            # NOAA documents UTC even when the literal lacks an offset.
            if timestamp.tzinfo is None:
                timestamp = timestamp.replace(tzinfo=timezone.utc)
            if not point_ok(point):
                raise ValueError("Invalid source point")
        except (TypeError, ValueError, AttributeError):
            counts["invalidSelectedRecords"] += 1
            continue
        result[identity].append((timestamp.timestamp(), point, index))
        counts["selectedRecords"] += 1
        if counts["selectedRecords"] > config.max_selected_records:
            raise ValueError("Selected observation resource bound exceeded")
    return result, counts


def split_tracks(records, config, counts):
    track, length = [], 0.0
    for timestamp, group in groupby(sorted(records), key=lambda r: r[0]):
        simultaneous = list(group)
        if len(set(r[1] for r in simultaneous)) > 1:
            counts["conflictingSameTimePositions"] += 1
            if len(track) >= 3 and length >= config.min_track_distance_m:
                yield track
            track, length = [], 0.0
            # Quarantine ALL conflicting same-time positions, not whichever sorted last.
            continue
        counts["duplicateObservations"] += len(simultaneous) - 1
        timestamp, point, row = simultaneous[0]
        distance = GEOD.inv(*track[-1][1], *point)[2] if track else 0
        if track and (timestamp - track[-1][0] > config.max_gap_seconds or distance > config.max_jump_m
                      or length + distance > config.max_track_distance_m or len(track) >= config.max_track_points):
            counts["segmentBoundaries"] += 1
            if len(track) >= 3 and length >= config.min_track_distance_m:
                yield track
            track, length, distance = [], 0.0, 0.0
        track.append((timestamp, point, row))
        length += distance
    if len(track) >= 3 and length >= config.min_track_distance_m:
        yield track


def densify(points, step):
    result = [points[0]]
    for a, b in zip(points, points[1:]):
        result.extend(geodesic(a, b, step)[1][1:])
    return result


def attach(point, points, tree, land, config, geometry_step):
    _, nearest = tree.query(xyz([point])[0], k=min(config.connector_candidates, len(points)))
    accepted = []
    for node in np.atleast_1d(nearest):
        distance, geometry = geodesic(point, points[int(node)], geometry_step)
        if distance <= config.connector_radius_m and land.clear(geometry):
            accepted.append((int(node), distance))
    return accepted


def route_between_anchors(matrix, origins, destinations):
    """Evaluate ALL bounded clear connectors; the nearest may belong to a dead component."""
    if not origins or not destinations:
        return None
    distances, predecessors = dijkstra(matrix, directed=True, indices=[n for n, _ in origins], return_predecessors=True)
    choices = [(float(distances[i, end] + start_distance + end_distance), i, end, end_distance)
               for i, (_, start_distance) in enumerate(origins) for end, end_distance in destinations
               if math.isfinite(distances[i, end])]
    if not choices:
        return None
    distance, source_index, end, end_distance = min(choices)
    start, start_distance = origins[source_index]
    nodes = [end]
    while nodes[-1] != start:
        nodes.append(int(predecessors[source_index, nodes[-1]]))
        if nodes[-1] < 0 or len(nodes) > matrix.shape[0]:
            raise ValueError("Invalid shortest-path reconstruction")
    nodes.reverse()
    return nodes, distance, [start_distance, end_distance]


def validate(artifact, ais_receipt, land_receipt, config_path, output):
    if output.exists():
        raise ValueError("Report exists; preserve previous evidence")
    started = time.perf_counter()
    config = AisConfig(**json.loads(config_path.read_text()))
    config.validate()
    ais_path, source = verify_receipt(ais_receipt)
    land_path, land_source = verify_receipt(land_receipt)
    if source["id"] not in ("noaa-ais-validation", "noaa-ais-holdout") or land_source["id"] != "gshhg":
        raise ValueError("Unsupported validation source")
    with zipfile.ZipFile(artifact) as archive:
        manifest = json.loads(archive.read("manifest.json"))
        if any(s["sha256"] == source["sha256"] or "ais" in s["id"].lower() for s in manifest["sources"]):
            raise ValueError("Validation source may have leaked into graph construction")
        if not any(s["sha256"] == land_source["sha256"] for s in manifest["sources"]):
            raise ValueError("Land snapshot must match the graph")
        for name, digest in manifest["tables"].items():
            with archive.open(name) as stream:
                if hashlib.file_digest(stream, "sha256").hexdigest() != digest:
                    raise ValueError("Corrupt artifact")
        points = [json.loads(line)["point"] for line in archive.open("nodes.jsonl")]
        starts, ends, costs = [], [], []
        for line in archive.open("edges.jsonl"):
            edge = json.loads(line)
            if edge["geometryModel"] != "WGS84_GEODESIC" or len(edge["geometry"]) != 2:
                raise ValueError("Validator requires endpoint-encoded mesh geometry")
            if edge["legalStatus"] == "PROHIBITED" or edge["restrictions"]:
                continue
            starts.append(edge["fromNode"]); ends.append(edge["toNode"]); costs.append(edge["distanceM"])
    matrix = csr_matrix((costs, (starts, ends)), shape=(len(points), len(points)))
    del starts, ends, costs
    observations_by_vessel, counts = observations(ais_path, config)
    tracks = []
    for vessel, records in observations_by_vessel.items():
        for track in split_tracks(records, config, counts):
            key = hashlib.sha256((source["sha256"] + ":" + vessel + ":" + str(track[0][0])).encode()).hexdigest()
            tracks.append((key, track))
    counts["eligibleTrackSegments"] = len(tracks)
    selected = sorted(tracks)[:config.max_cases]
    del observations_by_vessel, tracks
    graph_config = Config(**manifest["parameters"])
    graph_config.validate()
    if graph_config.shoreline_resolution != "f" or graph_config.geodesic_step_m > 500:
        raise ValueError("Full-coast, <=500 m artifact required")
    land = load_land(land_path, graph_config)
    tree = cKDTree(xyz(points))
    cases = []
    for key, track in selected:
        observed = [row[1] for row in track]
        observed_length = sum(GEOD.inv(*a, *b)[2] for a, b in zip(observed, observed[1:]))
        case = dict(trackKey=key, sourceRows=[row[2] for row in track], observationCount=len(track),
                    startUtc=datetime.fromtimestamp(track[0][0], timezone.utc).isoformat(),
                    endUtc=datetime.fromtimestamp(track[-1][0], timezone.utc).isoformat(),
                    origin=observed[0], destination=observed[-1], observedDistanceM=observed_length)
        cases.append(case)
        if not land.clear(densify(observed, graph_config.geodesic_step_m)):
            case["status"] = "OBSERVATION_COASTLINE_CONFLICT"
            continue
        origin = attach(observed[0], points, tree, land, config, graph_config.geodesic_step_m)
        destination = attach(observed[-1], points, tree, land, config, graph_config.geodesic_step_m)
        case["clearAnchorCounts"] = [len(origin), len(destination)]
        if not origin or not destination:
            case["status"] = "NO_GEOMETRICALLY_VALID_ANCHOR"
            continue
        routed = route_between_anchors(matrix, origin, destination)
        if routed is None:
            case["status"] = "DISCONNECTED_ANCHORS"
            continue
        route_nodes, predicted_distance, connector_distances = routed
        predicted = [observed[0], *[points[n] for n in route_nodes], observed[-1]]
        # Every query-specific connector AND returned full path checked, never a direct-line fallback.
        if not land.clear(densify(predicted, graph_config.geodesic_step_m)):
            raise ValueError("Predicted path conflicts with source coastline")
        a, b = densify(observed, config.metric_step_m), densify(predicted, config.metric_step_m)
        if max(len(a), len(b)) > config.max_metric_samples:
            case.update(status="METRIC_RESOURCE_BOUND", metricSampleCounts=[len(a), len(b)])
            continue
        case.update(status="MEASURED_NOT_CERTIFIED", routeNodeIndices=route_nodes,
                    predictedDistanceM=predicted_distance,
                    discreteHausdorffM=discrete_hausdorff_m(a, b), discreteFrechetM=discrete_frechet_m(a, b),
                    connectorDistancesM=connector_distances, metricSampleCounts=[len(a), len(b)])
        print(json.dumps(dict(stage="ais_case", evaluated=len(cases), status=case["status"],
                              hausdorffM=case["discreteHausdorffM"])), flush=True)
    statuses = dict(Counter(c["status"] for c in cases))
    report = dict(graphVersion=manifest["graphVersion"], sources=[source, land_source], parameters=asdict(config),
                  validatorSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                  implementation={"codeSha256": {name: hashlib.sha256(Path(__file__).with_name(name).read_bytes()).hexdigest()
                                                   for name in ("validate_ais.py", "build_graph.py", "validate_graph.py")},
                                  "python": sys.version.split()[0], "scipy": scipy.__version__, "numpy": np.__version__,
                                  "shapely": shapely.__version__, "pyproj": pyproj.__version__, "proj": pyproj.proj_version_str,
                                  "zstandard": zstandard.__version__},
                  createdAt=datetime.now(timezone.utc).isoformat(), seconds=time.perf_counter() - started,
                  units="WGS84 degrees; distance/error metres; UTC timestamps; engineering limits recorded",
                  method="Directed Dijkstra on unchanged distance graph; minimum total distance over all bounded clear endpoint anchors",
                  selection="Smallest SHA256 vessel IDs, then smallest source/vessel/start hashes before route outcomes",
                  counts=dict(counts), selectedCases=len(cases), statuses=statuses, cases=cases,
                  validationGate="NOT_PASSED", acceptanceThreshold="NOT_DEFINED_DIAGNOSTIC_ONLY",
                  limitations=["One historical day, coastal US receivers and cargo/tanker subset, not global voyage validation",
                               "Track segments are not proven complete voyages or port-to-port observations",
                               "Discrete geodesic metrics depend on sampling; not exact continuous Frechet/Hausdorff",
                               "AIS/coastline conflicts and unavailable anchors remain in the denominator",
                               "Not used for topology tuning; a future tuned model needs a new untouched validation split",
                               "No authoritative channel permissions, safe depth, port approach or operational certification"])
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("xb") as stream:
        stream.write(encode(report) + b"\n")
    print(json.dumps(dict(graphVersion=report["graphVersion"], seconds=report["seconds"], statuses=statuses,
                         selectedCases=len(cases), validationGate="NOT_PASSED")), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for flag in ("artifact", "ais-receipt", "land-receipt", "config", "output"):
        parser.add_argument("--" + flag, type=Path, required=True)
    args = parser.parse_args()
    validate(args.artifact, args.ais_receipt, args.land_receipt, args.config, args.output)
