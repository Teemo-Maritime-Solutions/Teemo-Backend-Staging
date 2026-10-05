"""Recheck every regional artifact edge against its pinned chart snapshot; not navigation certification."""
import argparse
from collections import Counter
from dataclasses import replace
import hashlib
import json
import math
from pathlib import Path
import time
import zipfile

import numpy as np
from scipy.sparse import csr_matrix
from scipy.sparse.csgraph import connected_components
from shapely import Point
from shapely.ops import transform

from build_enc_pilot import ChartDomain, PilotConfig, load_snapshot
from build_graph import encode, geodesic
from validate_graph import reciprocal_pairs


def audit_endpoint(domain, port):
    point = transform(domain.forward, Point(port["point"]))
    hits = []
    for layer, records in domain.features.items():
        for key, geom, props in records:
            if geom.is_valid and transform(domain.forward, geom).intersects(point):
                hits.append(dict(featureId=key, layer=layer, DRVAL1=props.get("DRVAL1"),
                                 DRVAL2=props.get("DRVAL2"), SORDAT=props.get("SORDAT")))
    return dict(id=port["id"], status=port["status"], point=port["point"],
                sourceName=port["sourceAttributes"].get("OBJNAM"),
                sourceSurveyDate=port["sourceAttributes"].get("SORDAT"),
                distanceToConservativeWaterProjectedM=point.distance(domain.water),
                sourceFeaturesAtPoint=hits, unresolvedRestrictions=domain.restrictions([port["point"]]),
                limitation="Distance is diagnostic only; the source endpoint was not relocated")


def check_edges(nodes, edges, domain, validation_step):
    checked = 0
    for edge in reciprocal_pairs(edges):
        start, end = edge["fromNode"], edge["toNode"]
        if type(start) is not int or type(end) is not int or not 0 <= start < len(nodes) or not 0 <= end < len(nodes):
            raise ValueError("Bad edge node reference")
        points = edge["geometry"]
        if points[0] != nodes[start]["point"] or points[-1] != nodes[end]["point"]:
            raise ValueError("Geometry/node endpoint mismatch")
        dense, distance = [], 0
        for a, b in zip(points, points[1:]):
            metres, segment = geodesic(a, b, validation_step)
            distance += metres
            dense.extend(segment if not dense else segment[1:])
        if not math.isfinite(edge["distanceM"]) or edge["distanceM"] <= 0 or abs(edge["distanceM"] - distance) > max(.01, distance * 1e-8):
            raise ValueError("Geodesic distance mismatch")
        if not domain.allows(dense):
            raise ValueError("Chart water/land/hazard conflict")
        # Check both reciprocal records' non-geometry evidence too.
        checked += 1
    for edge in edges:
        if edge["minimumDepthM"] is not None or edge["legalStatus"] != "UNKNOWN":
            raise ValueError("Unsupported current-depth or legal-permission assertion")
        if edge["restrictions"] != domain.restrictions(edge["geometry"]):
            raise ValueError("Missing/changed source regulatory restrictions")
        if edge["chartDepthFeatureIds"] != domain.depth_evidence(edge["geometry"]):
            raise ValueError("Missing/changed chart-depth evidence")
    return checked


def validate(artifact, receipt_path, output):
    started = time.perf_counter()
    if output.exists():
        raise ValueError("Report exists; select a new immutable output")
    source, receipt = load_snapshot(receipt_path)
    with zipfile.ZipFile(artifact) as archive:
        if len(set(archive.namelist())) != len(archive.namelist()) or any(e.file_size > 200000000 for e in archive.infolist()):
            raise ValueError("Duplicate/oversized artifact entry")
        manifest = json.loads(archive.read("manifest.json"))
        version = manifest.pop("graphVersion")
        if hashlib.sha256(encode(manifest)).hexdigest() != version:
            raise ValueError("Manifest version mismatch")
        if manifest.get("validationProfile") != PilotConfig(**manifest["parameters"]).profile or manifest["sources"] != [receipt]:
            raise ValueError("Profile/source provenance mismatch")
        tables = {}
        for name in ("nodes.jsonl", "edges.jsonl", "ports.jsonl", "chart-evidence.jsonl"):
            data = archive.read(name)
            if hashlib.sha256(data).hexdigest() != manifest["tables"][name]:
                raise ValueError("Artifact table digest mismatch")
            tables[name] = [json.loads(line) for line in data.splitlines()]
    config = PilotConfig(**manifest["parameters"])
    domain = ChartDomain(source, config)
    nodes, edges, ports = [tables[n] for n in ("nodes.jsonl", "edges.jsonl", "ports.jsonl")]
    evidence = [dict(id=key, geometry=geom.__geo_interface__, attributes=props, sourceIds=[receipt["id"]])
                for records in domain.features.values() for key, geom, props in records]
    if encode(evidence) != encode(tables["chart-evidence.jsonl"]):
        raise ValueError("Source chart evidence changed")
    if not nodes or len({n["id"] for n in nodes}) != len(nodes) or any(not domain.allows([n["point"]]) for n in nodes):
        raise ValueError("Invalid/duplicate/out-of-water nodes")
    if any(row["sourceIds"] != [receipt["id"]] for name in ("nodes.jsonl", "edges.jsonl", "ports.jsonl") for row in tables[name]):
        raise ValueError("Unknown row source provenance")
    validation_step = config.geodesic_step_m / 2
    pairs = check_edges(nodes, edges, domain, validation_step)
    source_berths = {key: (geom, props) for key, geom, props in domain.features[49]}
    if len(ports) != len(source_berths) or {p["id"] for p in ports} != set(source_berths):
        raise ValueError("Missing/duplicated/unrequested source berths")
    for port in ports:
        geom, props = source_berths[port["id"]]
        if port["point"] != list(geom.coords[0]) or port["sourceAttributes"] != props or port["sourceFeatureId"] != port["id"]:
            raise ValueError("Relocated or changed source berth")
        if port["nodeId"] is not None:
            node = nodes[port["nodeId"]]
            if node["id"] != port["id"] or node["point"] != port["point"] or port["status"] != "CONNECTED_REFERENCE_POINT":
                raise ValueError("Invalid source berth connector")
        elif port["status"] == "CONNECTED_REFERENCE_POINT":
            raise ValueError("Connected berth lacks node")
    matrix = csr_matrix((np.ones(len(edges)), ([e["fromNode"] for e in edges], [e["toNode"] for e in edges])), shape=(len(nodes), len(nodes)))
    components, labels = connected_components(matrix, directed=True, connection="strong")
    actual = dict(nodes=len(nodes), directedEdges=len(edges), strongComponents=components,
                  largestComponent=int(np.bincount(labels).max()), portStatuses=dict(Counter(p["status"] for p in ports)),
                  edgesBlockedByUnresolvedChartRestrictions=sum(bool(e["restrictions"]) for e in edges))
    if any(manifest["report"].get(k) != v for k, v in actual.items()):
        raise ValueError("Artifact count/connectivity report mismatch")
    for port in ports:
        if port["component"] != (None if port["nodeId"] is None else int(labels[port["nodeId"]])):
            raise ValueError("Source berth component mismatch")
    report = dict(validationProfile=config.profile, graphVersion=version,
                  artifactSha256=hashlib.sha256(artifact.read_bytes()).hexdigest(), sourceSha256=receipt["sha256"],
                  geometricCheck="PASS", validationGate="NOT_PASSED", reciprocalGeometryVerified=True,
                  checkedReciprocalPairs=pairs, validationGeodesicStepM=validation_step,
                  coveredPortCount=sum(p["status"] == "CONNECTED_REFERENCE_POINT" for p in ports),
                  metrics=actual, endpoints=[audit_endpoint(domain, p) for p in ports],
                  validatorSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                  domainImplementationSha256=hashlib.sha256(Path(__file__).with_name("build_enc_pilot.py").read_bytes()).hexdigest(),
                  limitations=["Recomputed from pinned NOAA GIS; shared mask implementation, not independent hydrographic truth",
                               "No successful berth routing implied by water-mesh geometry PASS",
                               "Unresolved regulatory features remain hard route exclusions, including research mode",
                               "No surveyed-accuracy threshold, tide, vessel clearance or untouched AIS validation"],
                  seconds=time.perf_counter()-started)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(encode(report) + b"\n")
    print(json.dumps({k: v for k, v in report.items() if k != "endpoints"}), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--artifact", type=Path, required=True)
    parser.add_argument("--receipt", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    validate(args.artifact, args.receipt, args.output)
