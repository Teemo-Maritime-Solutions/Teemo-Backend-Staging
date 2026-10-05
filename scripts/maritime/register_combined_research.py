"""Register engineering regression cases before evaluating the combined artifact."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import zipfile

from build_combined_research import sha
from build_graph import encode


def register(output):
    if output.exists():
        raise ValueError("Registration exists")
    root = Path(__file__).resolve().parents[2]
    baseline = root / "data/maritime-routing/global-coastal-r4-audited-v1.zip"
    with zipfile.ZipFile(baseline) as archive:
        ports = sorted([json.loads(line) for line in archive.open("ports.jsonl")], key=lambda p: p["id"])
    countries = ["JP", "NZ", "CL", "ZA", "GB", "US", "AU", "BR", "IN"]
    selected = {country: next(p["id"] for p in ports if p["id"].startswith("UNLOCODE:" + country)
                             and p["status"] == "CONNECTED_REFERENCE_POINT") for country in countries}
    cases = [dict(originId=selected[countries[i]], destinationId=selected[countries[(i+1) % 8]]) for i in range(8)]
    cases.append(dict(originId=selected["IN"], destinationId=selected["ZA"]))
    python_names = ["build_combined_research.py", "validate_combined_research.py", "regional_priority.py",
                    "transition_mesh.py", "register_combined_research.py", "build_graph.py", "build_enc_pilot.py", "validate_graph.py"]
    java_root = "src/main/java/org/teemo/solutions/upcpre202501cc1asi07324441teemosolutionsbackend/mapping/routing/v2/"
    test_root = java_root.replace("src/main/java", "src/test/java")
    files = ["scripts/maritime/" + n for n in python_names] + [java_root+n+".java" for n in
             ["GraphArtifactLoader", "GraphCatalog", "PhysicalGraph", "RouteCostPolicy", "RouteSearch", "MaritimeRoutingController"]]
    files += [test_root + n + ".java" for n in ["GlobalArtifactIntegrationTest", "GraphArtifactLoaderTest", "MaritimeRoutingControllerTest"]]
    files.append("scripts/maritime/tests/test_regional_priority.py")
    sources = ["data/maritime-routing/global-coastal-r4-audited-v1.zip",
               "data/maritime-routing/global-coastal-r4-audited-v1.validation.json",
               "data/maritime-routing/noaa-new-york-pilot-v2-20260914.zip",
               "data/maritime-routing/noaa-new-york-v2-20260914/enc-source.zip.receipt.json"]
    result = dict(scope="PHASE3_GLOBAL_RESEARCH_V2", schemaVersion=1, registeredAt=datetime.now(timezone.utc).isoformat(),
                  purpose="ENGINEERING_REGRESSION_NOT_NEW_AIS_HOLDOUT", cases=cases,
                  implementation={p: sha(root / p) for p in files}, inputs={p: sha(root / p) for p in sources},
                  criteria=dict(completeGeometry=True, preserveRegionalRestrictions=True, physicalRegionalAttachment=True,
                                allConnectedReferencesMutuallyReachable=True, allNinePairsReachable=True,
                                actualAntimeridianAndEquatorCrossings=True, basinBoundingBoxes=["PACIFIC", "ATLANTIC", "INDIAN"],
                                maxExpansions=2000000, requestedAlternatives=3, maxCandidates=20, separationM=50000,
                                unknownLegalAndDepthRejectedWhenRequired=True, missingCorruptRegionalEvidenceRejected=True,
                                perRouteCoverageMetadata=True),
                  limitations=["Known global regression cases, not new independent observed voyages",
                               "One additional source-derived India/South Africa case; basin boxes are engineering coverage checks",
                               "Time and memory reported without a production SLA; no global AIS accuracy or legal clearance"])
    output.write_bytes(encode(result) + b"\n")
    print(json.dumps(dict(protocol=str(output), sha256=sha(output), cases=cases)))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    register(parser.parse_args().output)
