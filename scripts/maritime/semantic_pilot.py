"""Isolated offline NOAA semantic pilot. Never loads into production GraphCatalog."""
import argparse
from collections import Counter
from datetime import datetime
import hashlib
import heapq
import json
import math
from pathlib import Path
import time
from urllib.parse import urlsplit
import zipfile

from build_graph import encode, geodesic, point_ok, verify_receipt
from build_enc_pilot import ChartDomain, PilotConfig, load_snapshot
from validate_enc_pilot import check_edges

CLASSES = {"ABSOLUTE_BLOCK", "CONDITIONAL_RESTRICTION", "NAVIGABLE_WITH_RULES", "INFORMATIONAL", "UNRESOLVED_BLOCK"}
CONDITIONAL = {"CONDITIONAL_RESTRICTION", "NAVIGABLE_WITH_RULES"}


def digest(value):
    return hashlib.sha256(encode(value)).hexdigest()


def instant(value):
    if not isinstance(value, str):
        raise ValueError("Explicit timestamp required")
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is None:
        raise ValueError("Timestamp needs timezone")
    return parsed


def text_field(row, key):
    value = row.get(key)
    if not isinstance(value, str) or not value.strip():
        raise ValueError("Missing text: " + key)
    return value


def interval(row):
    start, end = instant(row.get("validFrom")), instant(row.get("validUntil"))
    if start >= end:
        raise ValueError("Invalid effective interval")
    return start, end


def effective(row, at):
    start, end = interval(row)
    return start <= at < end


def reviewed(row, sources):
    text_field(row, "reviewer")
    text_field(row, "reason")
    reviewed_at = instant(row.get("reviewedAt"))
    start, end = interval(row)
    if reviewed_at >= end:
        raise ValueError("Review is outside validity horizon")
    refs = row.get("sourceIds")
    if not isinstance(refs, list) or not refs or len(set(refs)) != len(refs) or not set(refs) <= sources:
        raise ValueError("Review needs known unique evidence sources")


def evidence(row, field, sources):
    ref = row.get(field)
    if not isinstance(ref, dict) or ref.get("sourceId") not in sources or ref["sourceId"] not in row["sourceIds"]:
        raise ValueError("Missing source-backed " + field)
    text_field(ref, "locator")
    text_field(ref, "explanation")


class Resolution:
    def __init__(self, curation, source_sha, graph_version, features, source_ids):
        if curation.get("schemaVersion") != 1 or curation.get("sourceSha256") != source_sha or curation.get("baseGraphVersion") != graph_version:
            raise ValueError("Curation snapshot/graph mismatch; review before migration")
        self.rules = {}
        self.source_ids = set(source_ids)
        self.source_metadata = source_ids if isinstance(source_ids, dict) else {}
        rows = curation.get("rules")
        if not isinstance(rows, list) or len(rows) > 10000:
            raise ValueError("Bounded rule list required")
        for row in rows:
            key = row.get("featureId")
            if key not in features or key in self.rules or row.get("featureSha256") != digest(features[key]):
                raise ValueError("Unknown/duplicate/changed restriction feature")
            kind = row.get("classification")
            if kind not in CLASSES:
                raise ValueError("Unknown restriction classification")
            if kind != "UNRESOLVED_BLOCK":
                reviewed(row, self.source_ids)
                evidence(row, "regulatoryEvidence", self.source_ids)
                text_field(row, "regulatoryCitation")
            conditions = row.get("conditions", [])
            if not isinstance(conditions, list) or len(conditions) > 32:
                raise ValueError("Bounded declarative condition list required")
            if kind in CONDITIONAL and not conditions:
                raise ValueError("Conditional rules cannot be an unconditional allow")
            if kind not in CONDITIONAL and conditions:
                raise ValueError("Conditions require a conditional classification")
            seen = set()
            for condition in conditions:
                key_name = text_field(condition, "key")
                if key_name in seen or not {"key", "equals"} <= set(condition) or not set(condition) <= {"key", "equals", "requiredSource", "requiredScope"}:
                    raise ValueError("Duplicate condition or unsupported predicate")
                if "requiredSource" in condition and condition["requiredSource"] not in {"SOURCE_DATA", "USER_SUPPLIED"}:
                    raise ValueError("Unknown condition provenance requirement")
                if "requiredScope" in condition and condition["requiredScope"] != "PASSAGE":
                    raise ValueError("Unknown condition scope requirement")
                seen.add(key_name)
                value = condition["equals"]
                if type(value) not in (bool, str, int, float) or isinstance(value, float) and not math.isfinite(value):
                    raise ValueError("Condition must compare a finite scalar")
            self.rules[key] = row

    def evaluate(self, feature_ids, context):
        at = instant(context.get("at"))
        facts = context.get("facts", {})
        if not isinstance(facts, dict):
            raise ValueError("Context facts must be an object")
        decisions = []
        for key in sorted(set(feature_ids)):
            row = self.rules.get(key)
            kind = row["classification"] if row else "UNRESOLVED_BLOCK"
            reason, allowed, missing = "UNRESOLVED_BLOCK", False, []
            if kind != "UNRESOLVED_BLOCK":
                if not effective(row, at) or instant(row["reviewedAt"]) > at:
                    reason = "REVIEW_NOT_EFFECTIVE"
                elif kind == "ABSOLUTE_BLOCK":
                    reason = "ABSOLUTE_BLOCK"
                elif kind == "INFORMATIONAL":
                    reason, allowed = "INFORMATIONAL_ONLY", True
                else:
                    mismatched = []
                    for condition in row["conditions"]:
                        name = condition["key"]
                        fact = facts.get(name)
                        if not isinstance(fact, dict) or fact.get("source") not in {"USER_SUPPLIED", "SOURCE_DATA"} or not effective(fact, at):
                            missing.append(name)
                            continue
                        if fact["source"] == "SOURCE_DATA" and fact.get("sourceId") not in self.source_ids:
                            missing.append(name)
                            continue
                        if fact["source"] == "SOURCE_DATA":
                            source = self.source_metadata.get(fact["sourceId"], {})
                            if not source.get("sha256") or fact.get("evidenceSha256") != source["sha256"] or not isinstance(fact.get("locator"), str) or not fact["locator"].strip():
                                missing.append(name)
                                continue
                        if condition.get("requiredSource", fact["source"]) != fact["source"]:
                            missing.append(name)
                            continue
                        if condition.get("requiredScope") == "PASSAGE":
                            scope = dict(featureId=key, vesselId=context.get("vesselId"), voyageId=context.get("voyageId"))
                            if any(not isinstance(v, str) or not v.strip() for v in scope.values()) or fact.get("scope") != scope:
                                missing.append(name)
                                continue
                        if not isinstance(fact.get("providedBy"), str) or not fact["providedBy"].strip() or instant(fact.get("providedAt")) > at:
                            missing.append(name)
                            continue
                        actual, expected = fact.get("value"), condition["equals"]
                        if type(actual) is not type(expected) or actual != expected:
                            mismatched.append(name)
                    allowed = not missing and not mismatched
                    reason = "CONDITIONS_MET_IN_DECLARED_SCENARIO" if allowed else "MISSING_CONDITION_EVIDENCE" if missing else "CONDITIONS_NOT_MET"
            decisions.append(dict(featureId=key, classification=kind, allowed=allowed, reason=reason, missingConditions=missing))
        return dict(allowed=all(d["allowed"] for d in decisions), decisions=decisions)


def connect_endpoints(nodes, edges, terminals, rows, domain, sources, at):
    if not isinstance(rows, list) or len(rows) > 1000:
        raise ValueError("Bounded endpoint list required")
    terminal_ids = {p["id"] for p in terminals}
    mesh_indices = [i for i, n in enumerate(nodes) if n["kind"] == "REGIONAL_RESEARCH_MESH"]
    endpoints, seen = [], set(n["id"] for n in nodes)
    for row in rows:
        key = text_field(row, "id")
        if key in seen or row.get("terminalId") not in terminal_ids:
            raise ValueError("Duplicate endpoint or unknown terminal association")
        seen.add(key)
        reviewed(row, sources)
        evidence(row, "coordinateEvidence", sources)
        evidence(row, "associationEvidence", sources)
        point = row.get("point")
        if not isinstance(point, list) or len(point) != 2 or any(type(v) not in (int, float) for v in point) or not point_ok(point):
            raise ValueError("Invalid curated WGS84 coordinate")
        endpoint = dict(row, nodeId=None, status="REVIEW_NOT_EFFECTIVE", connectorCount=0)
        endpoints.append(endpoint)
        if not effective(row, at) or instant(row["reviewedAt"]) > at:
            continue
        endpoint["status"] = "OUTSIDE_CONSERVATIVE_WATER"
        if not domain.allows([point]):
            continue
        if len(nodes) >= domain.config.max_nodes:
            raise ValueError("Endpoint node budget exceeded")
        candidates = sorted((geodesic(point, nodes[i]["point"], domain.config.geodesic_step_m)[0], i) for i in mesh_indices)
        start = len(nodes)
        nodes.append(dict(id=key, point=point, kind="CURATED_WATER_APPROACH", sourceIds=row["sourceIds"]))
        for distance, end in candidates[:domain.config.connector_candidates]:
            if distance > domain.config.max_connector_m:
                break
            _, geometry = geodesic(point, nodes[end]["point"], domain.config.geodesic_step_m / 2)
            if distance <= 0 or not domain.allows(geometry):
                continue
            for a, b, points in ((start, end, geometry), (end, start, list(reversed(geometry)))):
                edges.append(dict(fromNode=a, toNode=b, geometry=points, distanceM=distance,
                                  geometryModel="WGS84_GEODESIC", minimumDepthM=None, kind="WATER_APPROACH_CONNECTOR",
                                  chartDepthFeatureIds=domain.depth_evidence(points), restrictions=domain.restrictions(points),
                                  sourceIds=sorted(sources), specialZone=None, legalStatus="UNKNOWN"))
            endpoint["connectorCount"] += 1
            if endpoint["connectorCount"] == domain.config.connections_per_endpoint:
                break
        if endpoint["connectorCount"]:
            endpoint.update(nodeId=start, status="CONNECTED_WATER_APPROACH")
        else:
            nodes.pop()
            endpoint["status"] = "NO_CLEAR_CONNECTOR_WITHIN_BUDGET"
    return endpoints


def shortest(nodes, edges, endpoints, request, evaluations, max_expansions=100000):
    if not isinstance(request, dict) or set(request) not in ({"originEndpointId", "destinationEndpointId"}, {"originTerminalId", "destinationTerminalId"}):
        raise ValueError("A request must identify two endpoints or two terminals")
    indexed = {e["id"]: e for e in endpoints}
    if "originTerminalId" in request:
        origins = [e for e in endpoints if e["terminalId"] == request["originTerminalId"] and e["nodeId"] is not None]
        destinations = [e for e in endpoints if e["terminalId"] == request["destinationTerminalId"] and e["nodeId"] is not None]
        if len(origins) != 1 or len(destinations) != 1:
            return dict(status="REJECTED", reason="TERMINAL_APPROACH_UNAVAILABLE" if not origins or not destinations else "EXPLICIT_ENDPOINT_SELECTION_REQUIRED")
        a, b = origins[0], destinations[0]
    else:
        a, b = indexed.get(request.get("originEndpointId")), indexed.get(request.get("destinationEndpointId"))
    if not a or not b or a["nodeId"] is None or b["nodeId"] is None:
        return dict(status="REJECTED", reason="ENDPOINT_UNAVAILABLE")
    if a["id"] == b["id"]:
        return dict(status="REJECTED", reason="IDENTICAL_ENDPOINTS")
    adjacency = [[] for _ in nodes]
    for i, edge in enumerate(edges):
        adjacency[edge["fromNode"]].append((i, edge))
    queue, distances, previous, expanded = [(0, a["nodeId"])], {a["nodeId"]: 0}, {}, 0
    rejections = Counter()
    while queue:
        cost, node = heapq.heappop(queue)
        if cost != distances[node]:
            continue
        if node == b["nodeId"]:
            path = []
            while node != a["nodeId"]:
                edge_id = previous[node]
                path.append(edge_id)
                node = edges[edge_id]["fromNode"]
            return dict(status="SCENARIO_ROUTE_FOUND", distanceM=cost, edgeIds=list(reversed(path)), expandedNodes=expanded,
                        legalPermission="NOT_ESTABLISHED", vesselClearance="NOT_ESTABLISHED")
        if expanded >= max_expansions:
            return dict(status="REJECTED", reason="SEARCH_BUDGET_EXCEEDED")
        expanded += 1
        for edge_id, edge in adjacency[node]:
            decision = evaluations[edge_id]
            if not decision["allowed"]:
                rejections.update(d["reason"] for d in decision["decisions"] if not d["allowed"])
                continue
            next_cost = cost + edge["distanceM"]
            if next_cost < distances.get(edge["toNode"], math.inf):
                distances[edge["toNode"]] = next_cost
                previous[edge["toNode"]] = edge_id
                heapq.heappush(queue, (next_cost, edge["toNode"]))
    return dict(status="REJECTED", reason="NO_FEASIBLE_SCENARIO_PATH", rejectedEvaluations=dict(rejections), expandedNodes=expanded)


def run(artifact, receipt_path, curation_path, context_path, output, evidence_receipts=()):
    started = time.perf_counter()
    if output.exists():
        raise ValueError("Immutable output already exists")
    source, receipt = load_snapshot(receipt_path)
    with zipfile.ZipFile(artifact) as archive:
        if len(archive.namelist()) != len(set(archive.namelist())) or any(i.file_size > 200000000 for i in archive.infolist()):
            raise ValueError("Duplicate/oversized baseline entry")
        manifest = json.loads(archive.read("manifest.json"))
        base_version = manifest.pop("graphVersion")
        if digest(manifest) != base_version or manifest.get("validationProfile") != "NOAA_ENC_REGIONAL_V1" or manifest["sources"] != [receipt]:
            raise ValueError("Baseline manifest/source mismatch")
        tables = {}
        for name, expected in manifest["tables"].items():
            raw = archive.read(name)
            if hashlib.sha256(raw).hexdigest() != expected:
                raise ValueError("Baseline table checksum mismatch")
            tables[name] = [json.loads(line) for line in raw.splitlines()]
    domain = ChartDomain(source, PilotConfig(**manifest["parameters"]))
    nodes, edges, terminals = [tables[name] for name in ("nodes.jsonl", "edges.jsonl", "ports.jsonl")]
    features = {key: dict(geometry=geom.__geo_interface__, properties=props) for records in domain.features.values() for key, geom, props in records}
    berth_features = {key: (geom, props) for key, geom, props in domain.features[49]}
    if len(terminals) != len(berth_features) or {t["id"] for t in terminals} != set(berth_features):
        raise ValueError("Missing/duplicated baseline terminals")
    for terminal in terminals:
        geom, props = berth_features[terminal["id"]]
        if terminal["point"] != list(geom.coords[0]) or terminal["sourceAttributes"] != props:
            raise ValueError("Baseline terminal was relocated or changed")
    regulatory = {key: features[key] for key, _, _ in domain.regulatory}
    sources = {receipt["id"]: receipt}
    for path in evidence_receipts:
        _, meta = verify_receipt(path)
        if meta["id"] in sources:
            raise ValueError("Duplicate evidence source")
        sources[meta["id"]] = meta
    curation = json.loads(curation_path.read_text(encoding="utf-8"))
    context = json.loads(context_path.read_text(encoding="utf-8"))
    at = instant(context.get("at"))
    resolution = Resolution(curation, receipt["sha256"], base_version, regulatory, sources)
    original_nodes, original_edges = len(nodes), len(edges)
    check_edges(nodes, edges, domain, domain.config.geodesic_step_m / 2)
    endpoints = connect_endpoints(nodes, edges, terminals, curation.get("endpoints"), domain, set(sources), at)
    pairs = check_edges(nodes, edges, domain, domain.config.geodesic_step_m / 2)
    evaluations = [resolution.evaluate(e["restrictions"], context) for e in edges]
    blocked_types = Counter()
    for evaluation in evaluations:
        blocked_types.update({d["classification"] for d in evaluation["decisions"] if not d["allowed"]})
    requests = context.get("requests", [])
    if not isinstance(requests, list) or len(requests) > 100:
        raise ValueError("At most 100 explicit route requests")
    results = [dict(request=r, result=shortest(nodes, edges, endpoints, r, evaluations)) for r in requests]
    queue = [dict(featureId=key, featureSha256=digest(value), classification=resolution.rules.get(key, {}).get("classification", "UNRESOLVED_BLOCK"),
                  sourceAttributes=value["properties"], evaluation=resolution.evaluate([key], context)) for key, value in sorted(regulatory.items())]
    report = dict(baseGraphVersion=base_version, baseNodes=original_nodes, baseEdges=original_edges,
                  nodes=len(nodes), directedEdges=len(edges), originalTerminals=len(terminals),
                  connectedEndpoints=sum(e["nodeId"] is not None for e in endpoints),
                  endpointStatuses=dict(Counter(e["status"] for e in endpoints)), blockedEdgesByRestrictionType=dict(blocked_types),
                  restrictionClassifications=dict(Counter(r["classification"] for r in queue)),
                  blockedEdges=sum(not e["allowed"] for e in evaluations),
                  unresolvedRestrictions=[r["featureId"] for r in queue if any(d["reason"] in
                    {"UNRESOLVED_BLOCK", "REVIEW_NOT_EFFECTIVE"} for d in r["evaluation"]["decisions"])],
                  restrictionsMissingContext=[r["featureId"] for r in queue if any(d["reason"] == "MISSING_CONDITION_EVIDENCE" for d in r["evaluation"]["decisions"])],
                  routesFound=sum(r["result"]["status"] == "SCENARIO_ROUTE_FOUND" for r in results),
                  routesRejected=sum(r["result"]["status"] == "REJECTED" for r in results), routeRequests=len(results),
                  terminalsWithoutApproach=[t["id"] for t in terminals if not any(e["terminalId"] == t["id"] and e["nodeId"] is not None for e in endpoints)],
                  geometryValidation="PASS", checkedReciprocalPairs=pairs, globalValidation="NOT_PASSED")
    payloads = {"nodes.json": nodes, "edges.json": edges, "terminals.json": terminals, "endpoints.json": endpoints,
                "restriction-review.json": queue, "curation.json": curation, "context.json": context, "routes.json": results, "report.json": report}
    meta = dict(schemaVersion=1, purpose="ISOLATED_SEMANTIC_RESEARCH_NOT_NAVIGATION", baseGraphVersion=base_version,
                baseArtifactSha256=hashlib.sha256(artifact.read_bytes()).hexdigest(), sources=list(sources.values()),
                tables={k: digest(v) for k, v in payloads.items()}, parameters=manifest["parameters"],
                implementation={p: hashlib.sha256(Path(__file__).with_name(p).read_bytes()).hexdigest() for p in
                                ("semantic_pilot.py", "build_enc_pilot.py", "build_graph.py", "validate_enc_pilot.py", "validate_graph.py")},
                limitations=["Scenario conditions are declarations, not authenticated legal permissions",
                             "Manual evidence association requires external review; schema checks do not prove truth",
                             "Physical geometry does not establish vessel clearance or global validation"])
    meta["semanticVersion"] = digest(meta)
    payloads["manifest.json"] = meta
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "x", compression=zipfile.ZIP_DEFLATED) as archive:
        for name, value in sorted(payloads.items()):
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, encode(value))
    print(json.dumps(dict(report=report, semanticVersion=meta["semanticVersion"], artifactSha256=hashlib.sha256(output.read_bytes()).hexdigest(),
                          seconds=time.perf_counter()-started, artifactBytes=output.stat().st_size)), flush=True)


def prepare(artifact, receipt_path, output, at):
    """Generate a fail-closed review queue; never invent a water endpoint or reviewed rule."""
    instant(at)
    if output.exists():
        raise ValueError("Review directory already exists")
    source, receipt = load_snapshot(receipt_path)
    with zipfile.ZipFile(artifact) as archive:
        manifest = json.loads(archive.read("manifest.json"))
    domain = ChartDomain(source, PilotConfig(**manifest["parameters"]))
    if manifest.get("sources") != [receipt]:
        raise ValueError("Review source does not match artifact")
    rules = []
    for key, _, _ in sorted(domain.regulatory):
        original = next((g, props) for records in domain.features.values() for k, g, props in records if k == key)
        value = dict(geometry=original[0].__geo_interface__, properties=original[1])
        rules.append(dict(featureId=key, featureSha256=digest(value), classification="UNRESOLVED_BLOCK", conditions=[]))
    terminal_ids = sorted(key for key, _, _ in domain.features[49])
    # All source terminal pairs, bounded before materialization; failures stay in denominator.
    if len(terminal_ids) * (len(terminal_ids)-1) > 100:
        selected = terminal_ids[:10]
    else:
        selected = terminal_ids
    requests = [dict(originTerminalId=a, destinationTerminalId=b) for a in selected for b in selected if a != b]
    payloads = {"curation.json": dict(schemaVersion=1, sourceSha256=receipt["sha256"], baseGraphVersion=manifest["graphVersion"], rules=rules, endpoints=[]),
                "context.json": dict(at=at, facts={}, requests=requests, purpose="DATA_AVAILABILITY_AUDIT_NOT_A_VOYAGE",
                                     requestSelection="Lexicographic source terminal IDs; first 10 if all ordered pairs exceed 100"),
                "endpoint-review.json": [dict(terminalId=key, sourcePoint=list(geom.coords[0]), sourceAttributes=props,
                                               waterApproach=None, status="COORDINATE_AND_ASSOCIATION_EVIDENCE_REQUIRED")
                                         for key, geom, props in domain.features[49]]}
    output.mkdir(parents=True, exist_ok=False)
    for name, value in payloads.items():
        (output / name).write_bytes(encode(value) + b"\n")
    print(json.dumps(dict(restrictions=len(rules), terminals=len(terminal_ids), requests=len(requests), curatedEndpoints=0)), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("artifact", "receipt", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--curation", type=Path)
    parser.add_argument("--context", type=Path)
    parser.add_argument("--prepare-review", action="store_true")
    parser.add_argument("--at")
    parser.add_argument("--evidence-receipt", type=Path, action="append", default=[])
    args = parser.parse_args()
    if args.prepare_review:
        if not args.at:
            parser.error("--at required for reproducible review context")
        prepare(args.artifact, args.receipt, args.output, args.at)
    else:
        if args.curation is None or args.context is None:
            parser.error("--curation and --context required")
        run(args.artifact, args.receipt, args.curation, args.context, args.output, args.evidence_receipt)
