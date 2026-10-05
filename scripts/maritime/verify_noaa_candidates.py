"""Verify exact source coordinates and clear connectors in a review-only candidate report."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

from shapely.geometry import Polygon
from build_enc_pilot import ChartDomain, PilotConfig, load_snapshot
from build_graph import GEOD, encode, geodesic
from semantic_pilot import digest


def verify(report_path, artifact, receipt_path, output):
    if output.exists():
        raise ValueError("Validation output already exists")
    source, receipt = load_snapshot(receipt_path)
    report = json.loads(report_path.read_text())
    expected_hash = report.pop("reviewHash")
    if digest(report) != expected_hash or report["source"] != receipt or report["purpose"] != "REVIEW_CANDIDATES_NOT_ROUTE_ENDPOINTS" or report["activatedEndpoints"] != 0:
        raise ValueError("Invalid candidate report hash/source/purpose")
    with zipfile.ZipFile(artifact) as archive:
        manifest = json.loads(archive.read("manifest.json"))
        raw = archive.read("nodes.jsonl")
        if report["baseGraphVersion"] != manifest["graphVersion"] or hashlib.sha256(raw).hexdigest() != manifest["tables"]["nodes.jsonl"]:
            raise ValueError("Baseline graph changed")
        nodes = {row["id"]: row for row in (json.loads(line) for line in raw.splitlines())}
    domain = ChartDomain(source, PilotConfig(**manifest["parameters"]))
    features = {key: (geom, props) for records in domain.features.values() for key, geom, props in records}
    terminals = {key for key, _, _ in domain.features[49]}
    if len(report["reviews"]) != len(terminals) or {r["terminalId"] for r in report["reviews"]} != terminals:
        raise ValueError("Missing/duplicate terminal reviews")
    count = connectors = 0
    for review in report["reviews"]:
        terminal_geom, terminal_props = features[review["terminalId"]]
        if review["originalPoint"] != list(terminal_geom.coords[0]) or review["sourceAttributes"] != terminal_props:
            raise ValueError("Changed source terminal")
        for candidate in review["candidates"]:
            if candidate["terminalAssociation"] != "UNVERIFIED_CANDIDATE_ONLY" or candidate["sourceSha256"] != receipt["sha256"]:
                raise ValueError("Candidate was promoted to an unverified association")
            geom, props = features[candidate["sourceFeatureId"]]
            if candidate["sourceFeatureSha256"] != digest(dict(geometry=geom.__geo_interface__, properties=props)):
                raise ValueError("Candidate source feature changed")
            polygon = ([geom] if isinstance(geom, Polygon) else list(geom.geoms))[candidate["polygonIndex"]]
            ring = [polygon.exterior, *polygon.interiors][candidate["ringIndex"]]
            point = list(ring.coords[candidate["vertexIndex"]])
            if candidate["point"] != point or not domain.allows([point]):
                raise ValueError("Moved or out-of-water candidate")
            distance = GEOD.inv(*review["originalPoint"], *point)[2]
            if abs(distance - candidate["terminalDistanceM"]) > .01 or distance > domain.config.max_connector_m:
                raise ValueError("Candidate distance mismatch")
            if candidate["restrictions"] != domain.restrictions([point]) or not candidate["clearMeshConnectors"]:
                raise ValueError("Missing connector/restriction evidence")
            for connector in candidate["clearMeshConnectors"]:
                node = nodes[connector["nodeId"]]
                distance, points = geodesic(point, node["point"], domain.config.geodesic_step_m / 4)
                if node["kind"] != "REGIONAL_RESEARCH_MESH" or abs(distance - connector["distanceM"]) > .01 or distance > domain.config.max_connector_m or not domain.allows(points):
                    raise ValueError("Invalid water connector")
                connectors += 1
            count += 1
    if count != report["candidateCount"] or report["terminals"] != len(terminals) or report["terminalsWithCandidates"] != sum(bool(r["candidates"]) for r in report["reviews"]):
        raise ValueError("Candidate count mismatch")
    result = dict(reviewHash=expected_hash, sourceSha256=receipt["sha256"], candidatesVerified=count,
                  connectorsVerified=connectors, exactSourceCoordinates="PASS", geometryValidation="PASS",
                  terminalAssociation="NOT_VERIFIED", activatedEndpoints=0,
                  validatorSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest())
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(encode(result) + b"\n")
    print(json.dumps(result), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("report", "artifact", "receipt", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    args = parser.parse_args()
    verify(args.report, args.artifact, args.receipt, args.output)
