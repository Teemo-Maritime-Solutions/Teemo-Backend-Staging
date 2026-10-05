"""Audit USACE references and NAVCEN notices against an immutable NOAA chart.

No point relocation, inferred terminal match, rule reclassification or activation.
"""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import time
import zipfile

from shapely import Point, box
from shapely.geometry import shape
from build_enc_pilot import ChartDomain, PilotConfig, load_snapshot
from build_graph import GEOD, encode, geodesic, point_ok, verify_receipt


def read_evidence(receipt_path, expected_id, chart_sha):
    path, receipt = verify_receipt(receipt_path)
    if receipt["id"] != expected_id or receipt.get("chartSourceSha256") != chart_sha:
        raise ValueError("External source identity or chart selection mismatch")
    with zipfile.ZipFile(path) as archive:
        if len(archive.namelist()) != len(set(archive.namelist())) or any(i.file_size > 25_000_000 for i in archive.infolist()):
            raise ValueError("Invalid external source archive")
        for request in json.loads(archive.read("requests.json")):
            if hashlib.sha256(archive.read(request["file"])).hexdigest() != request["sha256"]:
                raise ValueError("External response changed")
        source = json.loads(archive.read("source.json"))
    if source.get("chartSourceSha256") != chart_sha:
        raise ValueError("Source payload chart selection mismatch")
    return source, receipt


def classify_dock(feature, domain, nodes):
    props, geometry = feature["properties"], feature["geometry"]
    point = geometry.get("coordinates")
    if geometry.get("type") != "Point" or not isinstance(point, list) or len(point) != 2 or not point_ok(point):
        raise ValueError("Unsupported original USACE coordinate")
    row = dict(objectId=props["OBJECTID"], navUnitId=props.get("NAV_UNIT_ID"), name=props.get("NAV_UNIT_NAME"),
               point=point, sourceAttributes=props, featureSha256=hashlib.sha256(encode(feature)).hexdigest(),
               status="OUTSIDE_CONSERVATIVE_CHART_WATER", connectors=[],
               terminalAccess="NOT_VERIFIED", noaaAssociation="NOT_VERIFIED")
    if not domain.allows([point]):
        return row
    row["status"] = "NO_CLEAR_MESH_CONNECTOR"
    mesh = [(GEOD.inv(*point, *n["point"])[2], i) for i, n in enumerate(nodes) if n["kind"] == "REGIONAL_RESEARCH_MESH"]
    for distance, index in sorted(mesh)[:domain.config.connector_candidates]:
        if distance > domain.config.max_connector_m:
            break
        _, line = geodesic(point, nodes[index]["point"], domain.config.geodesic_step_m / 4)
        if distance > 0 and domain.allows(line):
            row["connectors"].append(dict(nodeId=index, distanceM=distance, geometry=line,
                                           restrictions=domain.restrictions(line), depthFeatures=domain.depth_evidence(line)))
        if len(row["connectors"]) >= domain.config.connections_per_endpoint:
            break
    if row["connectors"]:
        row["status"] = "SOURCE_REFERENCE_HAS_CLEAR_CONNECTOR"
    return row


def audit(artifact, chart_receipt, dock_receipt, waterway_receipt, notice_receipt, output):
    if output.exists():
        raise ValueError("Preserve immutable reports; choose a new path")
    started = time.perf_counter()
    source, chart = load_snapshot(chart_receipt)
    with zipfile.ZipFile(artifact) as archive:
        manifest = json.loads(archive.read("manifest.json"))
        check = dict(manifest)
        version = check.pop("graphVersion")
        if hashlib.sha256(encode(check)).hexdigest() != version or manifest["sources"] != [chart]:
            raise ValueError("NOAA baseline identity mismatch")
        tables = {}
        for name in ("nodes.jsonl", "ports.jsonl"):
            raw = archive.read(name)
            if hashlib.sha256(raw).hexdigest() != manifest["tables"][name]:
                raise ValueError("NOAA baseline table checksum mismatch")
            tables[name] = [json.loads(line) for line in raw.splitlines()]
    domain = ChartDomain(source, PilotConfig(**manifest["parameters"]))
    docks, docks_meta = read_evidence(dock_receipt, "usace-docks", chart["sha256"])
    waterways, waterways_meta = read_evidence(waterway_receipt, "usace-waterways", chart["sha256"])
    notices, notices_meta = read_evidence(notice_receipt, "navcen-safety-zones", chart["sha256"])
    facilities = [classify_dock(f, domain, tables["nodes.jsonl"]) for f in docks["features"]]
    comparisons = []
    for port in tables["ports.jsonl"]:
        nearest = sorted(facilities, key=lambda r: GEOD.inv(*port["point"], *r["point"])[2])[:3]
        comparisons.append(dict(terminalId=port["id"], name=port["name"], sourcePoint=port["point"],
                                candidates=[dict(navUnitId=r["navUnitId"], name=r["name"], distanceM=GEOD.inv(*port["point"], *r["point"])[2],
                                                 status=r["status"]) for r in nearest],
                                association="NOT_VERIFIED", method="Proximity review queue only; never an identity join"))
    network = []
    for feature in waterways["features"]:
        geometry = shape(feature["geometry"])
        if geometry.geom_type not in ("LineString", "MultiLineString") or geometry.is_empty or not geometry.is_valid:
            raise ValueError("Unsupported USACE waterway geometry")
        parts = [geometry] if geometry.geom_type == "LineString" else list(geometry.geoms)
        clear = []
        for part in parts:
            points = list(part.coords)
            dense = [points[0]]
            for a, b in zip(points, points[1:]):
                dense.extend(geodesic(a, b, domain.config.geodesic_step_m / 4)[1][1:])
            clear.append(domain.allows(dense))
        network.append(dict(sourceAttributes=feature["properties"], geometry=feature["geometry"],
                            allPartsInsideChartWater=all(clear), status="REFERENCE_ONLY_NOT_IMPORTED",
                            reason="National analytical geometry; full original path checked including portions outside chart coverage"))
    relevant, unavailable, invalid_notices = [], [], []
    envelope = box(*notices["chartBounds"])
    for item in notices["files"]:
        if item["status"] != "DOWNLOADED":
            unavailable.append({k: v for k, v in item.items() if k != "content"})
            continue
        for feature in item["content"]["features"]:
            try:
                geom = shape(feature["geometry"])
                if geom.is_empty or not geom.is_valid:
                    raise ValueError("Invalid geometry")
            except (ValueError, KeyError, TypeError, AttributeError):
                invalid_notices.append(dict(file=item["url"], featureId=feature.get("id")))
                continue
            if geom.intersects(envelope):
                props = feature["properties"]
                relevant.append(dict(file=item["url"], featureId=feature.get("id"), properties=props,
                                     geometry=feature["geometry"], interpretation="REQUIRES_ENFORCEMENT_AND_SCOPE_REVIEW"))
    report = dict(schemaVersion=1, purpose="EXTERNAL_EVIDENCE_AVAILABILITY_AUDIT", graphVersion=version,
                  sources=[chart, docks_meta, waterways_meta, notices_meta],
                  facilityStatuses=dict(Counter(r["status"] for r in facilities)), facilities=facilities,
                  terminalComparisons=comparisons, waterwayReferences=network,
                  notices=dict(publishedFileCompleteness=notices["completeness"], unavailableFiles=unavailable,
                               invalidGeometries=invalid_notices, intersectingNotices=relevant),
                  verifiedTerminalApproaches=0, activatedEndpoints=0, validationGate="NOT_PASSED",
                  seconds=time.perf_counter()-started,
                  implementationSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                  limitations=["A national-scale dock point in chart water is not a surveyed access point",
                               "Nearby references and matching generic berth numbers do not establish identity",
                               "Whole-link geometry failure can mean outside coverage, not a land collision",
                               "Incomplete/absent notices cannot prove unrestricted passage"])
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("xb") as stream:
        stream.write(encode(report) + b"\n")
    print(json.dumps(dict(facilityStatuses=report["facilityStatuses"], waterwayReferences=len(network),
                          clearWholeWaterwayReferences=sum(r["allPartsInsideChartWater"] for r in network),
                          intersectingNotices=len(relevant), unavailableNoticeFiles=len(unavailable),
                          seconds=report["seconds"], reportSha256=hashlib.sha256(output.read_bytes()).hexdigest())), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for flag in ("artifact", "chart-receipt", "dock-receipt", "waterway-receipt", "notice-receipt", "output"):
        parser.add_argument("--" + flag, type=Path, required=True)
    args = parser.parse_args()
    audit(args.artifact, args.chart_receipt, args.dock_receipt, args.waterway_receipt, args.notice_receipt, args.output)
