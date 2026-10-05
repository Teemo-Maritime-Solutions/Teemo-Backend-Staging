"""Paired AIS diagnostics. Never compare successful-only means from different cohorts."""
import argparse
import hashlib
import json
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

from build_graph import encode


def compare(before, after):
    for field in ("parameters", "validatorSha256", "implementation", "selection", "method"):
        if before.get(field) != after.get(field) or field not in before:
            raise ValueError("Paired comparison needs the same validator, sampling and parameters: " + field)
    def ais_sources(report):
        return sorted(s["sha256"] for s in report["sources"] if s["id"].startswith("noaa-ais-"))
    if not ais_sources(before) or ais_sources(before) != ais_sources(after):
        raise ValueError("Different AIS observations cannot be called paired evidence")
    if before["graphVersion"] == after["graphVersion"]:
        raise ValueError("Distinct graph versions required")
    def indexed(report):
        cases = {c["trackKey"]: c for c in report["cases"]}
        if not cases or len(cases) != len(report["cases"]) or len(cases) != report["selectedCases"]:
            raise ValueError("Missing/duplicate/incorrect sample counts")
        return cases
    old, new = indexed(before), indexed(after)
    if old.keys() != new.keys():
        raise ValueError("Case selection changed; paired comparison refused")
    transitions, cohorts, paired_metrics = Counter(), Counter(), []
    for key in sorted(old):
        a, b = old[key], new[key]
        if a["sourceRows"] != b["sourceRows"]:
            raise ValueError("Source observations changed for track " + key)
        transitions[(a["status"], b["status"])] += 1
        measured_a, measured_b = [c["status"] == "MEASURED_NOT_CERTIFIED" for c in (a, b)]
        cohort = "bothMeasured" if measured_a and measured_b else "newlyMeasured" if measured_b else "lostMeasured" if measured_a else "neitherMeasured"
        cohorts[cohort] += 1
        if measured_a and measured_b:
            paired_metrics.append(dict(trackKey=key,
                                       beforeHausdorffM=a["discreteHausdorffM"], afterHausdorffM=b["discreteHausdorffM"],
                                       hausdorffChangeM=b["discreteHausdorffM"] - a["discreteHausdorffM"],
                                       beforeFrechetM=a["discreteFrechetM"], afterFrechetM=b["discreteFrechetM"],
                                       frechetChangeM=b["discreteFrechetM"] - a["discreteFrechetM"]))
    return dict(beforeGraphVersion=before["graphVersion"], afterGraphVersion=after["graphVersion"],
                selectedCases=len(old), aisSourceSha256=ais_sources(before), parameters=before["parameters"],
                statusesBefore=dict(Counter(c["status"] for c in old.values())),
                statusesAfter=dict(Counter(c["status"] for c in new.values())), cohorts=dict(cohorts),
                transitions=[dict(before=a, after=b, count=n) for (a, b), n in sorted(transitions.items())],
                pairedMetrics=paired_metrics, validationGate="NOT_PASSED",
                limitations=["Same selected cases and validator, not a randomized or global operational trial",
                             "Newly measurable cases must not be confused with reduced error on a fixed cohort",
                             "Unknown errors are not zero; no pass threshold or risk probability inferred"])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for flag in ("before", "after", "output"):
        parser.add_argument("--" + flag, type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise ValueError("Preserve existing comparison evidence")
    inputs = [p.read_bytes() for p in (args.before, args.after)]
    reports = [json.loads(data) for data in inputs]
    result = compare(*reports)
    result.update(createdAt=datetime.now(timezone.utc).isoformat(), sources=reports[0]["sources"],
                  inputReportSha256=[hashlib.sha256(data).hexdigest() for data in inputs],
                  comparatorSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                  units="counts: cases; geometry/error/change: metres; timestamps: UTC")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("xb") as stream:
        stream.write(encode(result) + b"\n")
    print(json.dumps({k: result[k] for k in ("selectedCases", "statusesBefore", "statusesAfter", "cohorts", "validationGate")}), flush=True)


if __name__ == "__main__":
    main()
