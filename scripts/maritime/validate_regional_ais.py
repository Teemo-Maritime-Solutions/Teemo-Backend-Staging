"""Prospectively bounded NOAA AIS/ENC physical-model evaluation, never passage clearance."""
import argparse
from collections import Counter, defaultdict
import ctypes
from dataclasses import asdict
from datetime import datetime, timezone
import hashlib
from itertools import groupby
import json
import math
from pathlib import Path
import sys
import time
import zipfile

import numpy as np
import pyproj
import scipy
from scipy.sparse import csr_matrix
from scipy.spatial import cKDTree
import shapely
import zstandard

from build_enc_pilot import ChartDomain, PilotConfig, load_snapshot
from build_graph import encode, geodesic, point_ok, verify_receipt, xyz
from phase3_sources import chart_bounds
from validate_ais import AisConfig, densify, merchant_id, route_between_anchors, rows, split_tracks
from validate_graph import discrete_frechet_m, discrete_hausdorff_m
from regional_channel_objective import ChannelObjective, OBJECTIVES
from regional_experiment import bindings, verify_registration, IMPLEMENTATION_FILES

SOURCE_ID = "noaa-ais-regional-march-holdout"
FROZEN_CONFIG = AisConfig(64, 20, 600, 5000, 1000, 30000, 500, 500, 24, 100, 1000, 50000000, 2000000)
CRITERIA = dict(minSelectedCases=10, minMeasurableFraction=.8, maxDiscreteHausdorffM=1000,
                maxDiscreteFrechetM=1000, maxPredictedWaterConflicts=0)


def parsed_record(row):
    point = (float(row["lon"]), float(row["lat"]))
    if not point_ok(point):
        raise ValueError("Invalid observation coordinate")
    stamp = datetime.fromisoformat(row["timestamp"].replace("Z", "+00:00"))
    if stamp.tzinfo is None:
        stamp = stamp.replace(tzinfo=timezone.utc)
    return stamp.timestamp(), point


def within(point, bbox):
    return bbox[0] <= point[0] <= bbox[2] and bbox[1] <= point[1] <= bbox[3]


def regional_observations(path, config, bbox):
    vessels, counts = set(), Counter()
    for index, row in rows(path, config):
        counts["csvRows"] = index
        identity = merchant_id(row)
        if identity is None:
            continue
        try:
            _, point = parsed_record(row)
        except (ValueError, TypeError, AttributeError):
            counts["invalidMerchantRecords"] += 1
            continue
        if within(point, bbox):
            vessels.add(identity)
            counts["regionalMerchantRows"] += 1
    selected = set(sorted(vessels, key=lambda v: hashlib.sha256(v.encode()).digest())[:config.sample_vessels])
    counts.update(eligibleRegionalVessels=len(vessels), selectedVessels=len(selected))
    print(json.dumps(dict(stage="regional_cohort", **counts)), flush=True)
    records = defaultdict(list)
    for index, row in rows(path, config):
        identity = merchant_id(row)
        if identity not in selected:
            continue
        try:
            timestamp, point = parsed_record(row)
        except (ValueError, TypeError, AttributeError):
            counts["invalidSelectedRecords"] += 1
            continue
        # Keep outside points as segment boundaries; never join across an exit.
        records[identity].append((timestamp, point, index))
        counts["selectedRecords"] += 1
        if counts["selectedRecords"] > config.max_selected_records:
            raise ValueError("Regional record budget exceeded")
    return records, counts


def regional_tracks(records, config, bbox, counts):
    run = []
    for _, group in groupby(sorted(records), key=lambda r: r[0]):
        same_time = list(group)
        conflict = len({r[1] for r in same_time}) > 1
        if conflict or not within(same_time[0][1], bbox):
            if conflict:
                counts["conflictingSameTimePositions"] += 1
            if run:
                yield from split_tracks(run, config, counts)
                run = []
        else:
            run.extend(same_time)
    if run:
        yield from split_tracks(run, config, counts)


def anchors(point, points, tree, domain, config):
    _, nearest = tree.query(xyz([point])[0], k=min(config.connector_candidates, len(points)))
    accepted = []
    for index in np.atleast_1d(nearest):
        distance, geometry = geodesic(point, points[int(index)], domain.config.geodesic_step_m / 4)
        if distance <= config.connector_radius_m and domain.allows(geometry):
            accepted.append((int(index), distance))
    return accepted


def acceptance(cases):
    measured = [c for c in cases if c["status"] == "PHYSICAL_MODEL_MEASURED"]
    reasons = []
    if len(cases) < CRITERIA["minSelectedCases"]:
        reasons.append("INSUFFICIENT_SELECTED_CASES")
    if not cases or len(measured) / len(cases) < CRITERIA["minMeasurableFraction"]:
        reasons.append("INSUFFICIENT_MEASURABLE_COVERAGE")
    if any(not math.isfinite(c.get("discreteHausdorffM", math.inf)) or not math.isfinite(c.get("discreteFrechetM", math.inf))
           or not 0 <= c["discreteHausdorffM"] <= CRITERIA["maxDiscreteHausdorffM"]
           or not 0 <= c["discreteFrechetM"] <= CRITERIA["maxDiscreteFrechetM"] for c in measured):
        reasons.append("SPATIAL_ERROR_TARGET_EXCEEDED")
    if any(c["status"] == "PREDICTED_CHART_WATER_CONFLICT" for c in cases):
        reasons.append("PREDICTED_CHART_WATER_CONFLICT")
    return dict(status="PASS" if not reasons else "FAIL", reasons=reasons,
                selectedCases=len(cases), measuredCases=len(measured),
                measurableFraction=len(measured)/len(cases) if cases else 0, criteria=CRITERIA,
                scope="REGIONAL_PHYSICAL_MODEL_ONLY")


def peak_working_set():
    if sys.platform != "win32":
        return dict(peakWorkingSetBytes=None, reason="Windows process API unavailable")
    from ctypes import wintypes

    class Counters(ctypes.Structure):
        _fields_ = [("cb", wintypes.DWORD), ("PageFaultCount", wintypes.DWORD)] + [
            (name, ctypes.c_size_t) for name in ("PeakWorkingSetSize", "WorkingSetSize", "QuotaPeakPagedPoolUsage",
            "QuotaPagedPoolUsage", "QuotaPeakNonPagedPoolUsage", "QuotaNonPagedPoolUsage", "PagefileUsage", "PeakPagefileUsage")]
    get_process = ctypes.windll.kernel32.GetCurrentProcess
    get_process.restype = wintypes.HANDLE
    get_memory = ctypes.windll.psapi.GetProcessMemoryInfo
    get_memory.argtypes = [wintypes.HANDLE, ctypes.POINTER(Counters), wintypes.DWORD]
    counters = Counters()
    counters.cb = ctypes.sizeof(counters)
    if not get_memory(get_process(), ctypes.byref(counters), counters.cb):
        return dict(peakWorkingSetBytes=None, reason="GetProcessMemoryInfo failed")
    return dict(peakWorkingSetBytes=counters.PeakWorkingSetSize, currentWorkingSetBytes=counters.WorkingSetSize,
                scope="Whole validator process lifetime, including imports and data scan; not Java serving RSS")


def validate(artifact, ais_receipt, chart_receipt, config_path, protocol, output, routing_objective='DISTANCE_V1', registration=None):
    if output.exists():
        raise ValueError("Immutable output already exists")
    started = time.perf_counter()
    if routing_objective not in OBJECTIVES:
        raise ValueError('Unknown physical research objective')
    config = AisConfig(**json.loads(config_path.read_text()))
    config.validate()
    if config != FROZEN_CONFIG:
        raise ValueError("Frozen evaluation configuration changed; register a new experiment")
    ais_path, ais = verify_receipt(ais_receipt)
    if registration is None and (ais["id"] != SOURCE_ID or "2024-03-01.csv.zst" not in ais["url"]):
        raise ValueError("Unregistered holdout source")
    with ais_path.open("rb") as stream:
        if stream.read(4) != bytes.fromhex("28b52ffd"):
            raise ValueError("Expected a Zstandard source")
    source, chart = load_snapshot(chart_receipt)
    if registration is not None:
        verify_registration(json.loads(registration.read_text()), bindings(artifact, chart['sha256'], config_path, protocol),
                            ais, routing_objective, config, CRITERIA)
    bbox, _ = chart_bounds(chart_receipt)
    with zipfile.ZipFile(artifact) as archive:
        if len(archive.namelist()) != len(set(archive.namelist())) or any(i.file_size > 200_000_000 for i in archive.infolist()):
            raise ValueError("Invalid regional archive")
        manifest = json.loads(archive.read("manifest.json"))
        check = dict(manifest)
        version = check.pop("graphVersion")
        if hashlib.sha256(encode(check)).hexdigest() != version or manifest.get("validationProfile") != PilotConfig(**manifest["parameters"]).profile or manifest["sources"] != [chart]:
            raise ValueError("Baseline source/profile mismatch")
        if any(s["sha256"] == ais["sha256"] or "ais" in s["id"].lower() for s in manifest["sources"]):
            raise ValueError("AIS leaked into baseline graph")
        tables = {}
        for name, expected in manifest["tables"].items():
            raw = archive.read(name)
            if hashlib.sha256(raw).hexdigest() != expected:
                raise ValueError("Baseline checksum mismatch")
            if name in ("nodes.jsonl", "edges.jsonl"):
                tables[name] = [json.loads(line) for line in raw.splitlines()]
    nodes, edges = tables["nodes.jsonl"], tables["edges.jsonl"]
    points = [n["point"] for n in nodes]
    domain = ChartDomain(source, PilotConfig(**manifest["parameters"]))
    if any(e["geometryModel"] != "WGS84_GEODESIC" for e in edges):
        raise ValueError("Unexpected physical edge model")
    # Intentional PHYSICAL diagnostic. The standalone output never enters either
    # semantic search or production routing, which still evaluate restrictions.
    matrix = csr_matrix(([e["distanceM"] for e in edges], ([e["fromNode"] for e in edges], [e["toNode"] for e in edges])), shape=(len(nodes), len(nodes)))
    lookup = {(e["fromNode"], e["toNode"]): e for e in edges}
    if len(lookup) != len(edges):
        raise ValueError("Duplicate physical edge")
    channel = ChannelObjective(domain, edges, len(nodes)) if routing_objective != 'DISTANCE_V1' else None
    records, counts = regional_observations(ais_path, config, bbox)
    tracks = []
    for vessel, observations in records.items():
        for track in regional_tracks(observations, config, bbox, counts):
            key = hashlib.sha256((ais["sha256"] + ":" + vessel + ":" + str(track[0][0])).encode()).hexdigest()
            tracks.append((key, track))
    counts["eligibleRegionalSegments"] = len(tracks)
    selected = sorted(tracks)[:config.max_cases]
    del records, tracks
    tree = cKDTree(xyz(points))
    cases = []
    for key, track in selected:
        observed = [r[1] for r in track]
        case = dict(trackKey=key, sourceRows=[r[2] for r in track], observationCount=len(track), origin=observed[0], destination=observed[-1],
                    startUtc=datetime.fromtimestamp(track[0][0], timezone.utc).isoformat(), endUtc=datetime.fromtimestamp(track[-1][0], timezone.utc).isoformat(),
                    passageStatus="PASSAGE_NOT_EVALUATED")
        cases.append(case)
        if not domain.allows(densify(observed, domain.config.geodesic_step_m / 4)):
            case["status"] = "OBSERVATION_CHART_WATER_CONFLICT"
            continue
        origin, destination = [anchors(p, points, tree, domain, config) for p in (observed[0], observed[-1])]
        case["clearAnchorCounts"] = [len(origin), len(destination)]
        if not origin or not destination:
            case["status"] = "NO_CLEAR_ANCHOR"
            continue
        if channel is None:
            result = route_between_anchors(matrix, origin, destination)
        else:
            choice = channel.route(origin, destination, (observed[0], observed[-1]), points, balanced=routing_objective == 'CHART_CHANNEL_BALANCED_V1')
            result = None if choice is None else choice[0]
            if choice is not None:
                case['offChartChannelProjectedM'] = choice[1]
        if result is None:
            case["status"] = "DISCONNECTED_ANCHORS"
            continue
        route_nodes, distance, connector_distances = result
        predicted = [observed[0]]
        predicted.extend(geodesic(observed[0], points[route_nodes[0]], domain.config.geodesic_step_m / 4)[1][1:])
        restrictions = set(domain.restrictions([observed[0], points[route_nodes[0]]]))
        for a, b in zip(route_nodes, route_nodes[1:]):
            edge = lookup[(a, b)]
            predicted.extend(edge["geometry"][1:])
            restrictions.update(edge["restrictions"])
        predicted.extend(geodesic(points[route_nodes[-1]], observed[-1], domain.config.geodesic_step_m / 4)[1][1:])
        restrictions.update(domain.restrictions([points[route_nodes[-1]], observed[-1]]))
        if not domain.allows(densify(predicted, domain.config.geodesic_step_m / 4)):
            case["status"] = "PREDICTED_CHART_WATER_CONFLICT"
            continue
        a, b = densify(observed, config.metric_step_m), densify([observed[0], *[points[n] for n in route_nodes], observed[-1]], config.metric_step_m)
        if max(len(a), len(b)) > config.max_metric_samples:
            case.update(status="METRIC_RESOURCE_BOUND", metricSampleCounts=[len(a), len(b)])
            continue
        observed_distance = sum(geodesic(p, q, config.metric_step_m)[0] for p, q in zip(observed, observed[1:]))
        case.update(status="PHYSICAL_MODEL_MEASURED", predictedDistanceM=distance, observedDistanceM=observed_distance,
                    distanceRatio=distance/observed_distance, connectorDistancesM=connector_distances,
                    discreteHausdorffM=discrete_hausdorff_m(a, b), discreteFrechetM=discrete_frechet_m(a, b),
                    metricSampleCounts=[len(a), len(b)], routeNodeIndices=route_nodes,
                    unevaluatedRestrictionIds=sorted(restrictions))
        print(json.dumps(dict(stage="regional_case", case=len(cases), status=case["status"], hausdorffM=case["discreteHausdorffM"], frechetM=case["discreteFrechetM"])), flush=True)
    result = acceptance(cases)
    dependencies = {"python": sys.version.split()[0], "numpy": np.__version__, "scipy": scipy.__version__,
                    "shapely": shapely.__version__, "pyproj": pyproj.__version__, "zstandard": zstandard.__version__}
    files = IMPLEMENTATION_FILES
    report = dict(schemaVersion=1, graphVersion=version, validationProfile=domain.config.profile,
                  evaluationRole="FROZEN_TEMPORAL_HOLDOUT" if registration is not None else ("DEVELOPMENT_REPLAY" if domain.config.water_model != "DEPARE_ONLY_V1" else "ORIGINAL_BASELINE_REPLAY"),
                  registrationSha256=None if registration is None else hashlib.sha256(registration.read_bytes()).hexdigest(),
                  routingObjective=routing_objective, channelSourceFeatureIds=[] if channel is None else channel.source_ids,
                  artifactSha256=hashlib.sha256(artifact.read_bytes()).hexdigest(), sources=[ais, chart],
                  parameters=asdict(config), protocolSha256=hashlib.sha256(protocol.read_bytes()).hexdigest(),
                  configSha256=hashlib.sha256(config_path.read_bytes()).hexdigest(),
                  implementation={name: hashlib.sha256(Path(__file__).with_name(name).read_bytes()).hexdigest() for name in files},
                  dependencies=dependencies, createdAt=datetime.now(timezone.utc).isoformat(), counts=dict(counts), cases=cases,
                  statuses=dict(Counter(c["status"] for c in cases)), regionalPhysicalAcceptance=result,
                  validationGate="NOT_PASSED", operationalRoutes=0, seconds=time.perf_counter()-started, memory=peak_working_set(),
                  limitations=["One region and date; physical-model diagnosis only; not global phase-3 acceptance",
                               "All chart restrictions retained but deliberately NOT evaluated in this offline physical diagnostic",
                               "Time-mismatched 2024 AIS and 2026 chart; no historical permission or hydrographic truth claimed",
                               "Failing cases remain in denominator; multiple segments can share a vessel and are not independent voyages",
                               "No terminal endpoint activation, draft clearance or operational safety conclusion"])
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("xb") as stream:
        stream.write(encode(report) + b"\n")
    print(json.dumps(dict(statuses=report["statuses"], regionalPhysicalAcceptance=result, seconds=report["seconds"], memory=report["memory"], reportSha256=hashlib.sha256(output.read_bytes()).hexdigest())), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for flag in ("artifact", "ais-receipt", "chart-receipt", "config", "protocol", "output"):
        parser.add_argument("--" + flag, type=Path, required=True)
    parser.add_argument('--routing-objective', choices=OBJECTIVES, default='DISTANCE_V1')
    parser.add_argument('--registration', type=Path)
    args = parser.parse_args()
    validate(args.artifact, args.ais_receipt, args.chart_receipt, args.config, args.protocol, args.output, args.routing_objective, args.registration)
