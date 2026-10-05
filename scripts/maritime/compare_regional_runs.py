"""Paired reporting on an identical cohort; never select the better route per case."""
import argparse
import hashlib
import json
from pathlib import Path
from statistics import median

from build_graph import encode


def compare(before, after):
    for key in ('artifactSha256', 'sources', 'parameters', 'registrationSha256'):
        if before.get(key) != after.get(key):
            raise ValueError('Not a paired graph/source/cohort/registration comparison: ' + key)
    a, b = [{case['trackKey']: case for case in r['cases']} for r in (before, after)]
    if set(a) != set(b) or len(a) != len(before['cases']) or len(b) != len(after['cases']):
        raise ValueError('Case set changed or contains duplicates')
    cases = []
    for key, left in a.items():
        right = b[key]
        for name in ('sourceRows', 'origin', 'destination', 'startUtc', 'endUtc', 'observationCount'):
            if left[name] != right[name]:
                raise ValueError('Changed selected observation evidence: ' + name)
        row = dict(trackKey=key, beforeStatus=left['status'], afterStatus=right['status'])
        if left['status'] == right['status'] == 'PHYSICAL_MODEL_MEASURED':
            row.update(beforeHausdorffM=left['discreteHausdorffM'], afterHausdorffM=right['discreteHausdorffM'],
                beforeFrechetM=left['discreteFrechetM'], afterFrechetM=right['discreteFrechetM'],
                hausdorffChangeM=right['discreteHausdorffM']-left['discreteHausdorffM'],
                frechetChangeM=right['discreteFrechetM']-left['discreteFrechetM'],
                beforeDistanceM=left['predictedDistanceM'], afterDistanceM=right['predictedDistanceM'],
                beforeDistanceRatio=left['distanceRatio'], afterDistanceRatio=right['distanceRatio'])
        cases.append(row)
    summary = []
    for report in (before, after):
        measured = [c for c in report['cases'] if c['status'] == 'PHYSICAL_MODEL_MEASURED']
        summary.append(dict(objective=report.get('routingObjective', 'DISTANCE_V1'),
            legacyDistanceObjectiveInferred='routingObjective' not in report, acceptance=report['regionalPhysicalAcceptance'],
            maxHausdorffM=max((c['discreteHausdorffM'] for c in measured), default=None),
            maxFrechetM=max((c['discreteFrechetM'] for c in measured), default=None),
            medianFrechetM=median(c['discreteFrechetM'] for c in measured) if measured else None,
            seconds=report['seconds'], memory=report['memory'], operationalRoutes=report['operationalRoutes']))
    return dict(summary=summary, cases=cases, commonMeasuredCases=sum('frechetChangeM' in c for c in cases),
        validationGate='NOT_PASSED',
        limitations=['No outcome-dependent model selection or blended acceptance',
                     'Descriptive paired segments, not independent-voyage significance testing',
                     'Optimization score not interpreted as physical distance or operational permission'])


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for flag in ('before', 'after', 'output'): parser.add_argument('--' + flag, type=Path, required=True)
    args = parser.parse_args()
    result = compare(json.loads(args.before.read_text()), json.loads(args.after.read_text()))
    result.update(reportSha256s=[hashlib.sha256(p.read_bytes()).hexdigest() for p in (args.before, args.after)],
                  implementationSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest())
    with args.output.open('xb') as stream: stream.write(encode(result) + b'\n')
    print(json.dumps(dict(summary=result['summary'], commonMeasuredCases=result['commonMeasuredCases'])), flush=True)
