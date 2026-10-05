"""Attribute failed AIS/ENC observations to explicit chart evidence; no model tuning."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import zipfile

from shapely import Point, LineString, STRtree, union_all
from shapely.ops import transform
from build_enc_pilot import ChartDomain, PilotConfig, coverage_available, load_snapshot
from build_graph import encode, verify_receipt
from validate_ais import AisConfig, densify, rows
from validate_regional_ais import parsed_record


class CauseIndex:
    def __init__(self, domain):
        self.domain = domain
        self.coverage = transform(domain.forward, union_all([g for _, g, p in domain.features[219] if coverage_available(p.get("CATCOV"))]))
        self.depth = union_all([g for key, g, p in domain.depths if key.startswith("noaa-enc:227:") or domain.config.water_model == "S57_GROUP1_V2"])
        self.features = [(key, layer, transform(domain.forward, geom), props) for layer, records in domain.features.items()
                         for key, geom, props in records if (key in domain.solid_feature_ids or layer in (227, 228, 219)) and geom.is_valid]
        self.tree = STRtree([g for _, _, g, _ in self.features])

    def classify(self, points):
        geo = Point(points[0]) if len(points) == 1 else LineString(points)
        projected = transform(self.domain.forward, geo)
        reasons, hits = [], []
        if not self.coverage.covers(projected):
            reasons.append("OUTSIDE_DECLARED_CHART_COVERAGE")
        if not self.depth.covers(projected):
            reasons.append("OUTSIDE_POSITIVE_KNOWN_GROUP1_WATER" if self.domain.config.water_model == "S57_GROUP1_V2" else "OUTSIDE_POSITIVE_KNOWN_DEPARE")
        for index in self.tree.query(projected, predicate="intersects"):
            key, layer, _, props = self.features[index]
            if key in self.domain.solid_feature_ids:
                hits.append(dict(featureId=key, layer=layer))
                reasons.append("SOURCE_LAND_INTERSECTION" if layer == 233 else "SOURCE_EXCLUSION_INTERSECTION")
        if not reasons and not self.domain.water.covers(projected):
            reasons.append("BUFFER_OR_INVALID_OR_UNKNOWN_DEPTH_EXCLUSION")
        if not reasons:
            reasons.append("INSIDE_CONSERVATIVE_WATER")
        return dict(reasons=sorted(set(reasons)), sourceExclusionHits=hits)


def diagnose(report_path, chart_receipt, ais_receipt, artifact, output):
    if output.exists():
        raise ValueError("Immutable diagnostic report exists")
    report = json.loads(report_path.read_text())
    source, chart = load_snapshot(chart_receipt)
    ais_path, ais = verify_receipt(ais_receipt)
    if report["sources"] != [ais, chart] or report["artifactSha256"] != hashlib.sha256(artifact.read_bytes()).hexdigest():
        raise ValueError("Evaluation/source identity mismatch")
    with zipfile.ZipFile(artifact) as archive:
        manifest = json.loads(archive.read("manifest.json"))
    domain = ChartDomain(source, PilotConfig(**manifest["parameters"]))
    index = CauseIndex(domain)
    wanted = {row for case in report["cases"] for row in case["sourceRows"]}
    selected = {}
    for number, row in rows(ais_path, AisConfig(**report["parameters"])):
        if number in wanted:
            selected[number] = parsed_record(row)[1]
    if set(selected) != wanted:
        raise ValueError("Original AIS observations unavailable")
    cases = []
    for case in report["cases"]:
        points = [selected[row] for row in case["sourceRows"]]
        causes = [index.classify([p]) for p in points]
        counts = Counter(reason for c in causes for reason in c["reasons"])
        geometry = index.classify(densify(points, domain.config.geodesic_step_m / 4))
        cases.append(dict(trackKey=case["trackKey"], originalStatus=case["status"], observationCount=len(points),
                          observedPointReasonCounts=dict(counts), origin=causes[0], destination=causes[-1], entireObservedTrack=geometry))
    result = dict(schemaVersion=1, evaluationSha256=hashlib.sha256(report_path.read_bytes()).hexdigest(),
                  graphVersion=report["graphVersion"], cases=cases,
                  caseReasonCounts=dict(Counter(reason for c in cases for reason in c["entireObservedTrack"]["reasons"])),
                  pointReasonCounts=dict(sum((Counter(c["observedPointReasonCounts"]) for c in cases), Counter())),
                  implementationSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                  conclusion="Source evidence attribution only; overlapping reasons are not additive or confirmed causal truth",
                  validationGate="NOT_PASSED")
    with output.open("xb") as stream:
        stream.write(encode(result) + b"\n")
    print(json.dumps(dict(caseReasonCounts=result["caseReasonCounts"], pointReasonCounts=result["pointReasonCounts"],
                          reportSha256=hashlib.sha256(output.read_bytes()).hexdigest())), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for flag in ("report", "chart-receipt", "ais-receipt", "artifact", "output"):
        parser.add_argument("--" + flag, type=Path, required=True)
    args = parser.parse_args()
    diagnose(args.report, args.chart_receipt, args.ais_receipt, args.artifact, args.output)
