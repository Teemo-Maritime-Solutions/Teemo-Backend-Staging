"""Evaluate the revised research gate without modifying historical acceptance."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

from build_combined_research import sha
from build_graph import encode


def summarize(protocol_path, validation_path, runtime_path, python_log, output):
    if output.exists():
        raise ValueError("Summary exists; select a new evidence file")
    root = Path(__file__).resolve().parents[2]
    protocol = json.loads(protocol_path.read_text(encoding="utf-8"))
    validation = json.loads(validation_path.read_text(encoding="utf-8"))
    runtime = json.loads(runtime_path.read_text(encoding="utf-8"))
    checks = {}
    checks["registeredImplementationAndInputs"] = all(sha(root / p) == digest for group in ("implementation", "inputs")
                                                     for p, digest in protocol[group].items())
    controls = validation.get("regionalControls", {})
    checks["compositionMatchesRegisteredImplementation"] = (
        validation.get("validatorSha256") == protocol["implementation"]["scripts/maritime/validate_combined_research.py"]
        and controls.get("builderSha256") == protocol["implementation"]["scripts/maritime/build_combined_research.py"]
        and controls.get("policySha256") == validation.get("policySha256") == protocol["implementation"]["scripts/maritime/regional_priority.py"]
        and controls.get("transitionSha256") == protocol["implementation"]["scripts/maritime/transition_mesh.py"]
        and controls.get("globalArtifactSha256") == protocol["inputs"]["data/maritime-routing/global-coastal-r4-audited-v1.zip"]
        and controls.get("regionalArtifactSha256") == protocol["inputs"]["data/maritime-routing/noaa-new-york-pilot-v2-20260914.zip"])
    checks["combinedGeometryAndRegionalControls"] = (
        validation.get("geometricCheck") == validation.get("regionalControlCheck") == "PASS"
        and validation.get("reciprocalGeometryVerified") is True
        and validation.get("allCoveredPairsResearchPolicyReachable") is True
        and validation.get("regionalNodesInLargestPhysicalComponent", 0) > 0)
    checks["runtimeUsesRegisteredArtifact"] = (runtime.get("protocolSha256") == sha(protocol_path)
        and runtime.get("artifactSha256") == validation.get("artifactSha256")
        and runtime.get("graphVersion") == validation.get("graphVersion")
        and runtime.get("catalogStatus") == "RESEARCH_ONLY"
        and runtime.get("directedEdges") == validation.get("checkedReciprocalPairs", -1) * 2)
    actual_cases = [{k: row[k] for k in ("originId", "destinationId")} for row in runtime.get("cases", [])]
    checks["registeredWorldCases"] = actual_cases == protocol["cases"] and all(
        0 <= row["expandedNodes"] <= protocol["criteria"]["maxExpansions"]
        and row["distanceM"] > 0 and row["milliseconds"] >= 0 for row in runtime.get("cases", []))
    checks["worldGeometryCoverage"] = (any(row.get("crossesAntimeridian") for row in runtime.get("cases", []))
        and any(row.get("crossesEquator") for row in runtime.get("cases", []))
        and set(runtime.get("basinBoundingBoxChecks", [])) == {"PACIFIC", "ATLANTIC", "INDIAN"})
    alternatives = runtime.get("alternatives", {})
    checks["boundedAlternatives"] = (alternatives.get("budgetExhausted") is False
        and 2 <= alternatives.get("returnedK", 0) <= 3 and alternatives.get("minimumSeparationM") == 50000)
    checks["realHttpAndHardConstraints"] = (runtime.get("realArtifactHttpChecks", "").startswith("PASS:")
        and runtime.get("unknownDepthHttpCheck") == "PASS" and runtime.get("regionalHardRestrictionsCheck") == "PASS")
    java_tests = []
    for name, minimum in (("GraphArtifactLoaderTest", 5), ("MaritimeRoutingControllerTest", 5), ("RouteSearchTest", 14), ("GlobalArtifactIntegrationTest", 1)):
        path = next((root / "target/surefire-reports").glob("TEST-*." + name + ".xml"))
        suite = ET.parse(path).getroot()
        counts = {field: int(suite.get(field, 0)) for field in ("tests", "failures", "errors", "skipped")}
        java_tests.append(dict(name=name, **counts, reportSha256=sha(path)))
        checks[name] = counts["tests"] >= minimum and counts["failures"] == counts["errors"] == counts["skipped"] == 0
    raw = python_log.read_bytes()
    log = raw.decode("utf-16" if raw.startswith((b"\xff\xfe", b"\xfe\xff")) else "utf-8")
    matched = re.search(r"Ran (\d+) tests in", log)
    checks["pythonRegression"] = bool(matched and int(matched[1]) >= 117 and log.rstrip().endswith("OK"))
    historical = []
    for path in sorted((root / "docs/maritime-routing").glob("*holdout-registration*.json")):
        record = json.loads(path.read_text(encoding="utf-8"))
        unchanged = all(sha(root / "scripts/maritime" / name) == digest for name, digest in record["implementation"].items())
        protocol_file = path.with_name(path.name.replace("registration", "protocol").replace(".json", ".md"))
        unchanged = unchanged and sha(protocol_file) == record["protocolSha256"]
        historical.append(dict(registration=path.name, implementationFiles=len(record["implementation"]), unchanged=unchanged))
    checks["frozenAisImplementationsPreserved"] = len(historical) == 4 and all(row["unchanged"] for row in historical)
    result = dict(scope="PHASE3_GLOBAL_RESEARCH_V2", assessedAt=datetime.now(timezone.utc).isoformat(),
                  status="PASS" if all(checks.values()) else "NOT_PASSED", checks=checks,
                  graphVersion=runtime.get("graphVersion"), artifactSha256=runtime.get("artifactSha256"),
                  evidence=dict(protocolSha256=sha(protocol_path), validationSha256=sha(validation_path),
                                runtimeSha256=sha(runtime_path), pythonLogSha256=sha(python_log), summarizerSha256=sha(Path(__file__))),
                  javaTests=java_tests, pythonTests=int(matched[1]) if matched else None,
                  historicalRegistrations=historical,
                  originalGlobalGate="NOT_PASSED_UNCHANGED", operationalNavigation="NOT_VALIDATED",
                  limitations=["Pass applies only to the revised research scope, not worldwide operational navigation",
                               "All pending New York restrictions remain blocked; no new terminal authorization",
                               "Global coast checks inherited only for exact retained baseline geometries",
                               "Worldwide AIS accuracy, weather, vessel clearance and contractual costs remain unavailable"])
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(encode(result) + b"\n")
    print(json.dumps(result), flush=True)
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("protocol", "validation", "runtime", "python-log", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    args = parser.parse_args()
    result = summarize(args.protocol, args.validation, args.runtime, args.python_log, args.output)
    raise SystemExit(0 if result["status"] == "PASS" else 1)
