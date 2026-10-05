"""Exhaustive composition audit; inherits only byte-equivalent baseline geometry."""
import argparse
from array import array
from collections import Counter
from dataclasses import replace
import json
from pathlib import Path
import time
import zipfile
import h3

import numpy as np
from scipy.sparse import csr_matrix
from scipy.sparse.csgraph import connected_components

from build_combined_research import rows, sha, verified_manifest
from build_enc_pilot import ChartDomain, PilotConfig, load_snapshot
from build_graph import Config, encode, geodesic, load_land, verify_receipt
from regional_priority import POLICY, REGION, RegionalPriority
from validate_graph import reciprocal_pairs
from transition_mesh import PARAMETERS as TRANSITION_PARAMETERS, transition_nodes


def require(condition, message):
    if not condition:
        raise ValueError(message)


def validate(artifact, global_path, regional_path, receipt_path, land_receipt, base_report_path, output):
    require(not output.exists(), "Report exists; use a new output")
    started = time.perf_counter()
    source, receipt = load_snapshot(receipt_path)
    baseline = json.loads(base_report_path.read_text())
    with zipfile.ZipFile(artifact) as combined, zipfile.ZipFile(global_path) as original, zipfile.ZipFile(regional_path) as regional:
        manifest, base, local = [verified_manifest(z) for z in (combined, original, regional)]
        binding = manifest["regionalControls"]
        require(manifest["schemaVersion"] == 2 and binding["policy"] == POLICY and binding["regionId"] == REGION, "Unsupported combined policy")
        for key, expected in dict(globalArtifactSha256=sha(global_path), regionalArtifactSha256=sha(regional_path),
                                  chartSourceSha256=receipt["sha256"], globalValidationSha256=sha(base_report_path),
                                  globalGraphVersion=base["graphVersion"], regionalGraphVersion=local["graphVersion"]).items():
            require(binding.get(key) == expected, "Binding mismatch: " + key)
        require(baseline["graphVersion"] == base["graphVersion"] and baseline["geometricCheck"] == "PASS"
                and baseline["reciprocalGeometryVerified"] is True and baseline["checkedReciprocalPairs"] * 2 == base["report"]["directedEdges"]
                and baseline["artifactBytes"] == global_path.stat().st_size, "Invalid inherited baseline")
        require(manifest["tables"]["regional-evidence.jsonl"] == local["tables"]["chart-evidence.jsonl"], "Regional evidence altered")
        require(manifest["sources"] == base["sources"] + [receipt] and local["sources"] == [receipt], "Source registry mismatch")
        domain = ChartDomain(source, PilotConfig(**local["parameters"]))
        priority = RegionalPriority(domain)
        land_path, land_source = verify_receipt(land_receipt)
        require(baseline["sourceSha256"] == land_source["sha256"], "Coastline changed")
        land = load_land(land_path, replace(Config(**base["parameters"]), shoreline_resolution="f", geodesic_step_m=500))
        nodes = list(rows(combined, "nodes.jsonl"))
        base_nodes = list(rows(original, "nodes.jsonl"))
        local_nodes = list(rows(regional, "nodes.jsonl"))
        require(binding["transitionParameters"] == TRANSITION_PARAMETERS, "Transition configuration changed")
        require(nodes == base_nodes + local_nodes + transition_nodes(priority, land, land_source["id"]), "Node geometry/identity changed")
        offset = len(base_nodes)
        transition_start = offset + len(local_nodes)
        counts = Counter()
        origins, destinations = array("i"), array("i")
        physical_origins, physical_destinations = array("i"), array("i")
        actual = iter(rows(combined, "edges.jsonl"))

        def record(edge, fresh=False):
            a, b = edge["fromNode"], edge["toNode"]
            require(edge["geometry"][0] == nodes[a]["point"] and edge["geometry"][-1] == nodes[b]["point"], "Endpoint geometry mismatch")
            if fresh:
                distance = sum(geodesic(a, b, 25)[0] for a, b in zip(edge["geometry"], edge["geometry"][1:]))
                require(abs(distance - edge["distanceM"]) <= max(.01, distance * 1e-8), "Physical distance mismatch")
            counts["directedEdges"] += 1
            counts["regionalControlledEdges"] += bool(edge.get("regionalControlIds"))
            physical_origins.append(a)
            physical_destinations.append(b)
            if not edge["restrictions"] and edge["legalStatus"] != "PROHIBITED":
                origins.append(a)
                destinations.append(b)
            return edge

        def checked_edges():
            for edge in rows(original, "edges.jsonl"):
                decision = priority.inspect(edge["geometry"])
                if not decision.allowed:
                    counts["globalEdgesRemovedByRegionalGeometry"] += 1
                    continue
                if decision.touched:
                    edge = dict(edge, regionalControlIds=[REGION],
                                restrictions=sorted(set(edge["restrictions"]) | set(decision.restrictions)),
                                sourceIds=sorted(set(edge["sourceIds"]) | {receipt["id"]}))
                    counts["globalEdgesWithRegionalControls"] += 1
                require(next(actual, None) == edge, "Retained global edge altered/omitted or regional control bypass")
                counts["inheritedGlobalDirectedGeometryChecks"] += 1
                yield record(edge)
            for edge in rows(regional, "edges.jsonl"):
                expected = dict(edge, fromNode=edge["fromNode"] + offset, toNode=edge["toNode"] + offset, regionalControlIds=[REGION])
                require(next(actual, None) == expected, "Frozen regional edge altered/omitted")
                require(domain.allows(edge["geometry"]) and set(edge["restrictions"]) == set(domain.restrictions(edge["geometry"])), "Regional source-water/restriction mismatch")
                counts["regionalPilotEdges"] += 1
                yield record(expected, True)
            connector_counts = Counter()
            for edge in actual:
                a, b = edge["fromNode"], edge["toNode"]
                kind = edge["kind"]
                low, high = sorted((a, b))
                require(0 <= low < high < len(nodes), "Invalid transition indices")
                if kind == "COASTAL_TRANSITION_MESH":
                    require(low >= transition_start and nodes[high]["id"].split(":")[1] in h3.grid_disk(nodes[low]["id"].split(":")[1], 1), "Invalid mesh neighbor")
                    maximum = TRANSITION_PARAMETERS["regionalConnectorM"]
                elif kind == "GLOBAL_TRANSITION_CONNECTOR":
                    require(low < offset and high >= transition_start, "Invalid global connector")
                    maximum = TRANSITION_PARAMETERS["globalConnectorM"]
                elif kind == "REGIONAL_BOUNDARY_CONNECTOR":
                    require(offset <= low < transition_start <= high, "Invalid regional connector")
                    maximum = TRANSITION_PARAMETERS["regionalConnectorM"]
                else:
                    raise ValueError("Unregistered transition kind")
                distance, _ = geodesic(nodes[a]["point"], nodes[b]["point"], 25)
                require(0 < distance <= maximum, "Connector budget exceeded")
                decision = priority.inspect(edge["geometry"])
                require(decision.allowed and priority.outside_clear(edge["geometry"], land), "Transition crosses source obstacle/land")
                require(edge["regionalControlIds"] == ([REGION] if decision.touched else []) and set(edge["restrictions"]) == set(decision.restrictions)
                        and edge["sourceIds"] == sorted({receipt["id"], land_source["id"]} if decision.touched else {land_source["id"]})
                        and edge["minimumDepthM"] is None and edge["legalStatus"] == "UNKNOWN", "Transition bypasses source controls")
                if a < b and kind != "COASTAL_TRANSITION_MESH":
                    key = (kind, low if kind == "GLOBAL_TRANSITION_CONNECTOR" else high)
                    connector_counts[key] += 1
                    require(connector_counts[key] <= TRANSITION_PARAMETERS["connectionsPerNode"], "Connector count budget exceeded")
                counts[kind] += 1
                yield record(edge, True)

        pairs = sum(1 for _ in reciprocal_pairs(checked_edges()))
        require(counts["directedEdges"] == manifest["report"]["directedEdges"] == pairs * 2, "Incomplete directed validation")
        for key, value in manifest["report"]["integrationCounts"].items():
            require(counts[key] == value, "Integration census mismatch: " + key)
        physical_matrix = csr_matrix((np.ones(len(physical_origins), dtype=np.int8), (physical_origins, physical_destinations)), shape=(len(nodes), len(nodes)))
        _, physical_labels = connected_components(physical_matrix, directed=True, connection="strong")
        attached = int(sum(physical_labels[offset:transition_start] == np.bincount(physical_labels).argmax()))
        require(attached > 0 and attached == manifest["report"]["regionalNodesInLargestPhysicalComponent"], "Regional physical graph not integrated")
        matrix = csr_matrix((np.ones(len(origins), dtype=np.int8), (origins, destinations)), shape=(len(nodes), len(nodes)))
        component_count, labels = connected_components(matrix, directed=True, connection="strong")
        ports = list(rows(combined, "ports.jsonl"))
        old_ports = list(rows(original, "ports.jsonl")) + list(rows(regional, "ports.jsonl"))
        require(len(ports) == len(old_ports) and len({p["id"] for p in ports}) == len(ports), "Port census altered")
        for port, old in zip(ports, old_ports):
            require(all(port.get(k) == old.get(k) for k in ("id", "point", "nodeId", "sourceIds")), "Source port relocated or reassigned")
            if old["status"] != "CONNECTED_REFERENCE_POINT":
                require(port["status"] == old["status"], "Unavailable port silently promoted")
        covered = [p for p in ports if p["status"] == "CONNECTED_REFERENCE_POINT"]
        require(len({int(labels[p["nodeId"]]) for p in covered}) == 1, "Covered ports not mutually reachable under research controls")
        require(manifest["report"]["portStatuses"] == dict(Counter(p["status"] for p in ports)), "Port status count mismatch")
        report = dict(graphVersion=manifest["graphVersion"], artifactSha256=sha(artifact), artifactBytes=artifact.stat().st_size,
                      geometricCheck="PASS", regionalControlCheck="PASS", regionalControls=binding,
                      regionalEvidenceSha256=manifest["tables"]["regional-evidence.jsonl"],
                      reciprocalGeometryVerified=True, checkedReciprocalPairs=pairs,
                      regionalControlledEdges=counts["regionalControlledEdges"], counts=dict(counts),
                      regionalNodesInLargestPhysicalComponent=attached,
                      coveredPortCount=len(covered), portCount=len(ports), strongComponents=int(component_count),
                      allCoveredPairsResearchPolicyReachable=True, availabilityChanges=manifest["report"]["availabilityChanges"],
                      validatorSha256=sha(Path(__file__)), policySha256=sha(Path(__file__).with_name("regional_priority.py")),
                      seconds=time.perf_counter() - started, validationGate="PENDING_RUNTIME_AND_PROTOCOL_CHECKS",
                      limitations=["Exact retained global geometry inherits pinned full-GSHHG checks; not a second coastline survey",
                                   "Regional and boundary geometry checked against NOAA priority and GSHHG outside coverage",
                                   "Separate execution shares the regional geometry policy implementation",
                                   "No present passage authorization, vessel clearance or global AIS accuracy established"])
    output.write_bytes(encode(report) + b"\n")
    print(json.dumps(report), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("artifact", "global-artifact", "regional-artifact", "chart-receipt", "land-receipt", "global-validation", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    args = parser.parse_args()
    validate(args.artifact, args.global_artifact, args.regional_artifact, args.chart_receipt,
             args.land_receipt, args.global_validation, args.output)
