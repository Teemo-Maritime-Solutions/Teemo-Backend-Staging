"""Recompute directed reachability from artifact tables; never certify navigability."""
import argparse
from array import array
from collections import Counter
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import sys
import time
import zipfile

import numpy as np
import scipy
from scipy.sparse import csr_matrix
from scipy.sparse.csgraph import connected_components

from build_graph import encode, point_ok


def check(nodes, edges, ports, max_edges):
    if not nodes or len({n["id"] for n in nodes}) != len(nodes):
        raise ValueError("Empty graph or duplicate node identifiers")
    if any(not point_ok(n["point"]) for n in nodes):
        raise ValueError("Invalid node coordinates")
    starts, ends, usable = array("i"), array("i"), array("b")
    for edge in edges:
        if len(starts) >= max_edges:
            raise ValueError("Edge resource limit exceeded; not a partial validation")
        a, b = edge["fromNode"], edge["toNode"]
        if any(type(i) is not int or not 0 <= i < len(nodes) for i in (a, b)) or a == b:
            raise ValueError("Invalid directed endpoint index")
        geometry = edge["geometry"]
        if len(geometry) < 2 or geometry[0] != nodes[a]["point"] or geometry[-1] != nodes[b]["point"]:
            raise ValueError("Edge geometry does not match node coordinates")
        if edge["legalStatus"] not in ("PERMITTED", "PROHIBITED", "UNKNOWN") or not isinstance(edge["restrictions"], list):
            raise ValueError("Unsupported restriction encoding")
        starts.append(a); ends.append(b)
        usable.append(edge["legalStatus"] != "PROHIBITED" and not edge["restrictions"])
    if len({p["id"] for p in ports}) != len(ports):
        raise ValueError("Duplicate port identifier")
    covered = []
    for port in ports:
        i = port["nodeId"]
        if i is not None and (type(i) is not int or not 0 <= i < len(nodes)
                              or port["id"] != nodes[i]["id"] or port["point"] != nodes[i]["point"]):
            raise ValueError("Port reference changed or does not identify its graph node")
        if port["status"] == "CONNECTED_REFERENCE_POINT":
            if i is None:
                raise ValueError("Covered port has no node")
            covered.append(i)
    matrix = csr_matrix((np.ones(len(starts), dtype=np.uint8), (starts, ends)), shape=(len(nodes), len(nodes)))
    # Normalize duplicate coordinates to adjacency, never let summed uint8 weights erase an edge.
    matrix.data[:] = 1
    count, labels = connected_components(matrix, directed=True, connection="strong")
    sizes = np.bincount(labels)
    selected = np.flatnonzero(np.asarray(usable, dtype=np.bool_))
    allowed = csr_matrix((np.ones(len(selected), dtype=np.uint8),
                          (np.asarray(starts)[selected], np.asarray(ends)[selected])), shape=matrix.shape)
    allowed.data[:] = 1
    _, allowed_labels = connected_components(allowed, directed=True, connection="strong")
    physical_components = set(int(labels[i]) for i in covered)
    usable_components = set(int(allowed_labels[i]) for i in covered)
    covered_count = len(covered)
    return dict(nodes=len(nodes), directedEdges=len(starts), strongComponents=int(count),
                largestComponentNodes=int(sizes.max()), coveredPortCount=covered_count,
                coveredOrderedDistinctPairs=covered_count * (covered_count - 1),
                coveredPortComponentCount=len(physical_components),
                coveredPortsInLargestComponent=bool(covered) and all(sizes[labels[i]] == sizes.max() for i in covered),
                allCoveredPairsTopologicallyReachable=covered_count >= 2 and len(physical_components) == 1,
                allCoveredPairsResearchPolicyReachable=covered_count >= 2 and len(usable_components) == 1,
                researchPolicyExcludedEdges=len(starts) - len(selected),
                portStatuses=dict(Counter(p["status"] for p in ports)))


def validate(artifact, output, max_nodes, max_edges, max_entry_bytes):
    if output.exists():
        raise ValueError("Preserve existing validation evidence")
    if not 1 <= max_nodes <= 1000000 or not 1 <= max_edges <= 10000000 or not 1024 <= max_entry_bytes <= 2000000000:
        raise ValueError("Invalid validation resource bounds")
    started = time.perf_counter()
    with zipfile.ZipFile(artifact) as archive:
        entries = archive.infolist()
        if len({e.filename for e in entries}) != len(entries) or any(e.file_size > max_entry_bytes for e in entries):
            raise ValueError("Duplicate or oversized artifact entry")
        if archive.getinfo("manifest.json").file_size > 2000000:
            raise ValueError("Oversized manifest")
        manifest = json.loads(archive.read("manifest.json"))
        for name in ("nodes.jsonl", "edges.jsonl", "ports.jsonl"):
            with archive.open(name) as stream:
                if hashlib.file_digest(stream, "sha256").hexdigest() != manifest["tables"][name]:
                    raise ValueError("Corrupt artifact table")
        nodes = []
        for row in archive.open("nodes.jsonl"):
            if len(nodes) >= max_nodes:
                raise ValueError("Node resource bound exceeded")
            nodes.append(json.loads(row))
        ports = [json.loads(row) for row in archive.open("ports.jsonl")]
        result = check(nodes, (json.loads(row) for row in archive.open("edges.jsonl")), ports, max_edges)
    mismatches = [key for key in ("nodes", "directedEdges", "strongComponents", "largestComponentNodes", "portStatuses")
                  if result[key] != manifest["report"][key]]
    passed = not mismatches and all(result[key] for key in ("coveredPortsInLargestComponent",
                  "allCoveredPairsTopologicallyReachable", "allCoveredPairsResearchPolicyReachable"))
    with artifact.open("rb") as stream:
        artifact_digest = hashlib.file_digest(stream, "sha256").hexdigest()
    result.update(graphVersion=manifest["graphVersion"], artifactSha256=artifact_digest,
                  sourceTableSha256=manifest["tables"], sources=manifest["sources"],
                  createdAt=datetime.now(timezone.utc).isoformat(), units="counts: nodes, directed edges, ports, ordered pairs; time: seconds",
                  validatorSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                  dependencies=dict(python=sys.version.split()[0], numpy=np.__version__, scipy=scipy.__version__),
                  parameters=dict(maxNodes=max_nodes, maxEdges=max_edges, maxEntryBytes=max_entry_bytes),
                  method="Strong components recomputed from directed tables; no sampled-pair inference",
                  manifestMismatches=mismatches, connectivityCheck="PASS" if passed else "FAIL",
                  validationGate="NOT_PASSED", seconds=time.perf_counter() - started,
                  limitations=["Topological existence only; no geometric, draft, time-dependent or navigational certification",
                               "Research policy excludes prohibited/unresolved restrictions but allows explicitly unknown legal status",
                               "Port references are not verified berths or approaches; unavailable ports excluded and counted"])
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("xb") as stream:
        stream.write(encode(result) + b"\n")
    print(json.dumps({key: result[key] for key in ("graphVersion", "connectivityCheck", "coveredPortCount",
                      "coveredOrderedDistinctPairs", "strongComponents", "seconds")}), flush=True)
    return passed


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--artifact", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--max-nodes", type=int, default=500000)
    parser.add_argument("--max-edges", type=int, default=10000000)
    parser.add_argument("--max-entry-bytes", type=int, default=1000000000)
    args = parser.parse_args()
    sys.exit(0 if validate(args.artifact, args.output, args.max_nodes, args.max_edges, args.max_entry_bytes) else 1)
