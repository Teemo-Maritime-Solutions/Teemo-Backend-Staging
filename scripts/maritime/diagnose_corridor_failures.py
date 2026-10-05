"""Post-evaluation geometry diagnosis. Never changes frozen cases or acceptance."""
import argparse
from datetime import datetime
import hashlib
import json
from pathlib import Path

from build_graph import GEOD, encode, verify_receipt
from regional_corridor_model import physical_context
from diagnose_regional_ais import CauseIndex
from validate_ais import AisConfig, densify, merchant_id, rows
from validate_regional_ais import parsed_record


def distance(a, b):
    return GEOD.inv(*a, *b)[2]


def endpoint_diagnosis(points, tolerance_m, objective_upper_bound_m=None):
    if len(points) < 2 or tolerance_m < 0:
        raise ValueError('Need a complete curve and nonnegative tolerance')
    a, b = points[0], points[-1]
    # Triangle inequality: any curve coming within epsilon of p must have length
    # >= d(a,p)+d(p,b)-2*epsilon. The current objective is >= physical length.
    bounds = [max(0, distance(a, p) + distance(p, b) - 2*tolerance_m) for p in points]
    index = max(range(len(points)), key=bounds.__getitem__)
    lower = bounds[index]
    return dict(endpointDistanceM=distance(a, b), observationLengthM=sum(distance(p, q) for p, q in zip(points, points[1:])),
                maxDistanceFromOriginM=max(distance(a, p) for p in points), toleranceM=tolerance_m,
                requiredLengthLowerBoundM=lower, witnessObservationIndex=index, witnessPoint=points[index],
                currentObjectiveUpperBoundM=objective_upper_bound_m,
                lengthBoundExceedsCurrentObjective=(objective_upper_bound_m is not None and lower > objective_upper_bound_m + .01))


def diagnose(report_path, ais_receipt, chart_receipt, artifact, output):
    if output.exists():
        raise ValueError('Immutable diagnostic exists')
    report = json.loads(report_path.read_text())
    path, ais = verify_receipt(ais_receipt)
    manifest, chart, domain, _, _ = physical_context(artifact, chart_receipt)
    if (report['sources'] != [ais, chart] or report['graphVersion'] != manifest['graphVersion']
            or report['artifactSha256'] != hashlib.sha256(artifact.read_bytes()).hexdigest()):
        raise ValueError('Source or graph evidence mismatch')
    index = CauseIndex(domain)
    wanted = {n for c in report['cases'] for n in c['sourceRows']}
    selected = {}
    for n, row in rows(path, AisConfig(**report['parameters'])):
        if n in wanted:
            selected[n] = (merchant_id(row), *parsed_record(row))
    if selected.keys() != wanted:
        raise ValueError('Missing original observations')
    cases = []
    for case in report['cases']:
        observed = [selected[n] for n in case['sourceRows']]
        vessel = observed[0][0]
        key = hashlib.sha256((ais['sha256']+':'+vessel+':'+str(observed[0][1])).encode()).hexdigest()
        points = [r[2] for r in observed]
        if (key != case['trackKey'] or any(r[0] != vessel for r in observed)
                or list(points[0]) != case['origin'] or list(points[-1]) != case['destination']
                or observed[0][1] != datetime.fromisoformat(case['startUtc']).timestamp()
                or observed[-1][1] != datetime.fromisoformat(case['endUtc']).timestamp()):
            raise ValueError('Case observations or identity changed')
        physical = case.get('predictedDistanceM')
        objective = None if physical is None else physical + case.get('unsupportedDirectedEvidenceM', 0)
        geometry = endpoint_diagnosis(points, report['regionalPhysicalAcceptance']['criteria']['maxDiscreteHausdorffM'], objective)
        cases.append(dict(trackKey=key, originalStatus=case['status'], sourceRows=case['sourceRows'],
                          endpointGeometry=geometry, originalFrechetM=case.get('discreteFrechetM'),
                          chartAttribution=index.classify(densify(points, domain.config.geodesic_step_m / 4))))
    result = dict(evaluationSha256=hashlib.sha256(report_path.read_bytes()).hexdigest(), cases=cases,
                  implementationSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                  originalAcceptance=report['regionalPhysicalAcceptance'], validationGate='NOT_PASSED',
                  limitations=['Post hoc diagnostic, not an acceptance exception or confirmed vessel intent',
                               'Length bound concerns original observation vertices, not resampled discrete metric vertices',
                               'No filtering, threshold change, source correction, new route or permission asserted'])
    with output.open('xb') as stream:
        stream.write(encode(result)+b'\n')
    print(json.dumps(dict(cases=len(cases), objectiveIncompatibilities=sum(c['endpointGeometry']['lengthBoundExceedsCurrentObjective'] for c in cases),
                          sha256=hashlib.sha256(output.read_bytes()).hexdigest())), flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for flag in ('report','ais-receipt','chart-receipt','artifact','output'):
        parser.add_argument('--'+flag, type=Path, required=True)
    args = parser.parse_args()
    diagnose(args.report,args.ais_receipt,args.chart_receipt,args.artifact,args.output)
