"""Build a new immutable global research artifact with mandatory NOAA priority."""
import argparse
from array import array
from collections import Counter
from contextlib import ExitStack
from dataclasses import replace
import hashlib
import json
from pathlib import Path
import shutil
import tempfile
import zipfile

import numpy as np
from scipy.sparse import csr_matrix
from scipy.sparse.csgraph import connected_components

from build_enc_pilot import ChartDomain, PilotConfig, load_snapshot
from build_graph import Config, encode, geodesic, load_land, verify_receipt
from regional_priority import POLICY, REGION, RegionalPriority
from transition_mesh import PARAMETERS as TRANSITION_PARAMETERS, transition_nodes, transition_edges


def sha(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def verified_manifest(archive):
    if len(archive.namelist()) != len(set(archive.namelist())):
        raise ValueError("Duplicate artifact entries")
    manifest = json.loads(archive.read("manifest.json"))
    for name, expected in manifest["tables"].items():
        with archive.open(name) as stream:
            if hashlib.file_digest(stream, "sha256").hexdigest() != expected:
                raise ValueError("Source table checksum mismatch: " + name)
    return manifest


def rows(archive, name):
    return (json.loads(line) for line in archive.open(name))


def build(global_path, regional_path, receipt_path, land_receipt, base_report_path, output):
    if output.exists():
        raise ValueError("Output exists; choose a new immutable artifact")
    source, receipt = load_snapshot(receipt_path)
    base_report = json.loads(base_report_path.read_text())
    output.parent.mkdir(parents=True, exist_ok=True)
    with ExitStack() as stack:
        global_zip = stack.enter_context(zipfile.ZipFile(global_path))
        regional_zip = stack.enter_context(zipfile.ZipFile(regional_path))
        base = verified_manifest(global_zip)
        regional = verified_manifest(regional_zip)
        if (base_report.get("geometricCheck") != "PASS" or
                base_report.get("graphVersion") != base["graphVersion"] or
                base_report.get("checkedReciprocalPairs", -1) * 2 != base["report"]["directedEdges"] or
                base_report.get("reciprocalGeometryVerified") is not True or
                base_report.get("artifactBytes") != global_path.stat().st_size):
            raise ValueError("Global baseline must have complete matching geometry evidence")
        if regional.get("validationProfile") != "NOAA_ENC_REGIONAL_V2" or regional["sources"] != [receipt]:
            raise ValueError("Regional artifact/source mismatch")
        domain = ChartDomain(source, PilotConfig(**regional["parameters"]))
        priority = RegionalPriority(domain)
        land_path, land_source = verify_receipt(land_receipt)
        if base_report["sourceSha256"] != land_source["sha256"]:
            raise ValueError("Global coastline source mismatch")
        land = load_land(land_path, replace(Config(**base["parameters"]), shoreline_resolution="f", geodesic_step_m=500))
        nodes = list(rows(global_zip, "nodes.jsonl"))
        ports = list(rows(global_zip, "ports.jsonl"))
        original_count = len(nodes)
        regional_nodes = list(rows(regional_zip, "nodes.jsonl"))
        if {n["id"] for n in nodes} & {n["id"] for n in regional_nodes}:
            raise ValueError("Source node IDs collide")
        nodes.extend(regional_nodes)
        nodes.extend(transition_nodes(priority, land, land_source["id"]))
        for port in rows(regional_zip, "ports.jsonl"):
            if port["nodeId"] is not None or port["status"] == "CONNECTED_REFERENCE_POINT":
                raise ValueError("Regional connected ports need a separate country/access audit")
            ports.append(dict(port, country="US"))
        temp = Path(stack.enter_context(tempfile.TemporaryDirectory(prefix="combined-", dir=output.parent)))
        edge_stream = stack.enter_context((temp / "edges.jsonl").open("wb"))
        origins, destinations, routable_origins, routable_destinations = (array("i") for _ in range(4))
        counts = Counter()

        def write(edge):
            edge_stream.write(encode(edge) + b"\n")
            origins.append(edge["fromNode"])
            destinations.append(edge["toNode"])
            if not edge["restrictions"] and edge["legalStatus"] != "PROHIBITED":
                routable_origins.append(edge["fromNode"])
                routable_destinations.append(edge["toNode"])
            counts["directedEdges"] += 1
            counts["regionalControlledEdges"] += REGION in edge.get("regionalControlIds", [])

        for index, edge in enumerate(rows(global_zip, "edges.jsonl")):
            decision = priority.inspect(edge["geometry"])
            if not decision.allowed:
                counts["globalEdgesRemovedByRegionalGeometry"] += 1
                continue
            if decision.touched:
                edge["regionalControlIds"] = [REGION]
                edge["restrictions"] = sorted(set(edge["restrictions"]) | set(decision.restrictions))
                edge["sourceIds"] = sorted(set(edge["sourceIds"]) | {receipt["id"]})
                counts["globalEdgesWithRegionalControls"] += 1
            write(edge)
            if index % 250000 == 0:
                print(json.dumps(dict(globalEdgesInspected=index, counts=counts)), flush=True)
        for edge in rows(regional_zip, "edges.jsonl"):
            if not domain.allows(edge["geometry"]) or set(edge["restrictions"]) != set(domain.restrictions(edge["geometry"])):
                raise ValueError("Regional geometry/restrictions no longer match source")
            edge = dict(edge, fromNode=edge["fromNode"] + original_count, toNode=edge["toNode"] + original_count,
                        regionalControlIds=[REGION])
            write(edge)
            counts["regionalPilotEdges"] += 1

        for edge in transition_edges(nodes, original_count, len(regional_nodes), priority, land, land_source["id"], receipt["id"]):
            write(edge)
            counts[edge["kind"]] += 1
        edge_stream.close()
        matrix = csr_matrix((np.ones(len(origins), dtype=np.int8), (origins, destinations)), shape=(len(nodes), len(nodes)))
        physical_components, physical_labels = connected_components(matrix, directed=True, connection="strong")
        matrix = csr_matrix((np.ones(len(routable_origins), dtype=np.int8), (routable_origins, routable_destinations)), shape=(len(nodes), len(nodes)))
        components, labels = connected_components(matrix, directed=True, connection="strong")
        largest = int(np.bincount(labels).argmax())
        availability_changes = []
        for port in ports:
            node_id = port["nodeId"]
            port["component"] = None if node_id is None else int(labels[node_id])
            if port["status"] == "CONNECTED_REFERENCE_POINT" and (node_id is None or labels[node_id] != largest):
                availability_changes.append(dict(id=port["id"], previousStatus=port["status"], status="REGIONAL_CONTROL_DISCONNECTED"))
                port["priorStatus"] = port["status"]
                port["status"] = "REGIONAL_CONTROL_DISCONNECTED"
        for name, values in (("nodes.jsonl", nodes), ("ports.jsonl", ports)):
            with (temp / name).open("wb") as stream:
                for row in values:
                    stream.write(encode(row) + b"\n")
        with regional_zip.open("chart-evidence.jsonl") as source_stream, (temp / "regional-evidence.jsonl").open("wb") as destination:
            shutil.copyfileobj(source_stream, destination)
        manifest = dict(base)
        manifest.pop("graphVersion")
        manifest["schemaVersion"] = 2
        manifest["validationProfile"] = "GLOBAL_REGIONAL_RESEARCH_V1"
        manifest["sources"] = base["sources"] + [receipt]
        manifest["createdAt"] = max(s["acquiredAt"] for s in manifest["sources"])
        manifest["regionalControls"] = dict(policy=POLICY, regionId=REGION, chartSourceId=receipt["id"],
            chartSourceSha256=receipt["sha256"], regionalGraphVersion=regional["graphVersion"],
            regionalArtifactSha256=sha(regional_path), globalArtifactSha256=sha(global_path),
            globalGraphVersion=base["graphVersion"], globalValidationSha256=sha(base_report_path),
            evidenceTable="regional-evidence.jsonl", regionalParameters=regional["parameters"],
            transitionParameters=TRANSITION_PARAMETERS,
            seamPolicy="Positive source water eroded except a 2-tolerance chart-edge band using original positive water; buffered obstacles always retained",
            builderSha256=sha(Path(__file__)), policySha256=sha(Path(__file__).with_name("regional_priority.py")),
            transitionSha256=sha(Path(__file__).with_name("transition_mesh.py")))
        manifest["report"] = dict(base["report"], nodes=len(nodes), directedEdges=counts["directedEdges"],
            strongComponents=int(components), largestComponentNodes=int(np.bincount(labels).max()),
            physicalStrongComponents=int(physical_components),
            transitionNodes=len(nodes) - original_count - len(regional_nodes),
            regionalNodesInLargestPhysicalComponent=int(sum(physical_labels[original_count:original_count+len(regional_nodes)] == np.bincount(physical_labels).argmax())),
            portStatuses=dict(Counter(p["status"] for p in ports)), integrationCounts=dict(counts),
            availabilityChanges=availability_changes, validationGate="PENDING_COMBINED_VALIDATION")
        manifest["tables"] = {p.name: sha(p) for p in sorted(temp.iterdir())}
        manifest["limitations"] = base.get("limitations", []) + [
            "Global research geometry with NOAA source-coverage priority; no worldwide operational validation",
            "All pending New York permissions remain blocked, including regional connectors",
            "Global port references and unavailable regional berths are not approved terminal approaches"]
        manifest["graphVersion"] = hashlib.sha256(encode(manifest)).hexdigest()
        with zipfile.ZipFile(output, "x", compression=zipfile.ZIP_DEFLATED) as result:
            for name in sorted(manifest["tables"]):
                info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                with (temp / name).open("rb") as source_stream, result.open(info, "w", force_zip64=True) as destination:
                    shutil.copyfileobj(source_stream, destination)
            for name in sorted(n for n in global_zip.namelist() if n.startswith("notices/")):
                result.writestr(global_zip.getinfo(name), global_zip.read(name))
            info = zipfile.ZipInfo("manifest.json", date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            result.writestr(info, encode(manifest))
        print(json.dumps(dict(artifactSha256=sha(output), graphVersion=manifest["graphVersion"], report=manifest["report"])), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("global-artifact", "regional-artifact", "chart-receipt", "land-receipt", "global-validation", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    args = parser.parse_args()
    build(args.global_artifact, args.regional_artifact, args.chart_receipt, args.land_receipt, args.global_validation, args.output)
