"""Source-vertex candidates for human review ONLY. Does not associate or activate vessel approaches."""
import argparse
import hashlib
import json
from pathlib import Path
import time
import zipfile

from shapely.geometry import Polygon
from build_enc_pilot import ChartDomain, PilotConfig, load_snapshot
from build_graph import GEOD, encode, geodesic
from semantic_pilot import digest


def suggest(artifact, receipt_path, output, per_terminal=3):
    started = time.perf_counter()
    if output.exists() or type(per_terminal) is not int or not 1 <= per_terminal <= 10:
        raise ValueError("New immutable output and candidate budget 1-10 required")
    source, receipt = load_snapshot(receipt_path)
    with zipfile.ZipFile(artifact) as archive:
        manifest = json.loads(archive.read("manifest.json"))
        if manifest["sources"] != [receipt] or manifest.get("validationProfile") != "NOAA_ENC_REGIONAL_V1":
            raise ValueError("Pilot/source mismatch")
        raw = archive.read("nodes.jsonl")
        if hashlib.sha256(raw).hexdigest() != manifest["tables"]["nodes.jsonl"]:
            raise ValueError("Baseline node checksum mismatch")
        nodes = [json.loads(row) for row in raw.splitlines()]
    config = PilotConfig(**manifest["parameters"])
    domain = ChartDomain(source, config)
    # Only exact vertices from positive charted depth features; no snapped or offset coordinates.
    accepted_depths = {key for key, _, _ in domain.depths}
    vertices = {}
    for layer in (227, 228):
        for key, geom, props in domain.features[layer]:
            if key not in accepted_depths or not geom.is_valid:
                continue
            polygons = [geom] if isinstance(geom, Polygon) else list(geom.geoms)
            for part, polygon in enumerate(polygons):
                for ring, boundary in enumerate([polygon.exterior, *polygon.interiors]):
                    for index, point in enumerate(list(boundary.coords)[:-1]):
                        if point in vertices or not domain.allows([point]):
                            continue
                        vertices[point] = dict(sourceFeatureId=key, sourceFeatureSha256=digest(dict(geometry=geom.__geo_interface__, properties=props)),
                                               polygonIndex=part, ringIndex=ring, vertexIndex=index,
                                               chartAttributes=props, point=list(point))
    mesh_nodes = [n for n in nodes if n["kind"] == "REGIONAL_RESEARCH_MESH"]
    reviews = []
    for key, geom, props in domain.features[49]:
        terminal_point = list(geom.coords[0])
        near = sorted((GEOD.inv(*terminal_point, *point)[2], point) for point in vertices)
        candidates = []
        for distance, point in near:
            if distance > config.max_connector_m:
                break
            neighbors = sorted((GEOD.inv(*point, *n["point"])[2], n["id"], n["point"]) for n in mesh_nodes)
            clear = []
            for length, node_id, node_point in neighbors[:config.connector_candidates]:
                if length <= 0 or length > config.max_connector_m:
                    continue
                _, geometry = geodesic(point, node_point, config.geodesic_step_m / 2)
                if domain.allows(geometry):
                    clear.append(dict(nodeId=node_id, distanceM=length))
            if not clear:
                continue
            candidates.append(dict(vertices[point], sourceId=receipt["id"], sourceSha256=receipt["sha256"],
                                   terminalDistanceM=distance, waterGeometryCheck="PASS", clearMeshConnectors=clear,
                                   restrictions=domain.restrictions([point]), terminalAssociation="UNVERIFIED_CANDIDATE_ONLY",
                                   rationale="Nearby original chart vertex with clear water-to-mesh geometry; proximity does NOT establish terminal access"))
            if len(candidates) == per_terminal:
                break
        reviews.append(dict(terminalId=key, originalPoint=terminal_point, sourceAttributes=props, candidates=candidates,
                            status="MANUAL_ASSOCIATION_EVIDENCE_REQUIRED" if candidates else "NO_SOURCE_VERTEX_CANDIDATE_WITHIN_BUDGET"))
    report = dict(purpose="REVIEW_CANDIDATES_NOT_ROUTE_ENDPOINTS", schemaVersion=1, baseGraphVersion=manifest["graphVersion"],
                  source=receipt, parameters=dict(perTerminal=per_terminal, searchRadiusM=config.max_connector_m,
                                                  geodesicStepM=config.geodesic_step_m / 2),
                  terminals=len(reviews), terminalsWithCandidates=sum(bool(r["candidates"]) for r in reviews),
                  candidateCount=sum(len(r["candidates"]) for r in reviews), activatedEndpoints=0, reviews=reviews,
                  implementationSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                  limitations=["Depth contour vertices are not observed approaches or approved berth positions",
                               "No terminal association is inferred; manual evidence review is required",
                               "Straight terminal distance is diagnostic, not a navigable connection through land"])
    report["reviewHash"] = digest(report)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(encode(report) + b"\n")
    print(json.dumps({**{k: report[k] for k in ("terminals", "terminalsWithCandidates", "candidateCount", "activatedEndpoints", "reviewHash")},
                      "seconds": time.perf_counter()-started}), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("artifact", "receipt", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--per-terminal", type=int, default=3)
    args = parser.parse_args()
    suggest(args.artifact, args.receipt, args.output, args.per_terminal)
