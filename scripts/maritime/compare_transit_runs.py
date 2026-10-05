"""Post-evaluation paired reporting with complete transit census verification."""
import argparse
import json
from pathlib import Path

from build_graph import encode
from compare_regional_runs import compare
from regional_experiment import sha
from transit_experiment import OBJECTIVES, verify
from validate_regional_ais import acceptance


def audit_census(report):
    census, cases = report['candidateCensus'], report['cases']
    by_key = {c['trackKey']:c for c in census}
    if len(by_key) != len(census):
        raise ValueError('Duplicate census identity')
    if any(c['status'] not in ('TRANSIT_COHORT_ELIGIBLE', 'OUTSIDE_TRANSIT_COHORT') for c in census):
        raise ValueError('Unknown census status')
    expected = sorted(c['trackKey'] for c in census if c['status']=='TRANSIT_COHORT_ELIGIBLE')[:report['parameters']['max_cases']]
    actual = [c['trackKey'] for c in cases]
    if actual != expected or sorted(c['trackKey'] for c in census if c['selectedForEvaluation']) != expected:
        raise ValueError('Cases do not match prospective hash selection')
    for case in cases:
        for field in ('sourceRows', 'origin', 'destination', 'observationCount'):
            if case[field] != by_key[case['trackKey']][field]:
                raise ValueError('Changed original census evidence: '+field)
    if report['regionalPhysicalAcceptance'] != acceptance(cases):
        raise ValueError('Reported acceptance does not match all selected cases')
    if (report['counts']['originalEligibleSegments'] != len(census)
            or report['counts']['selectedTransitSegments'] != len(cases)):
        raise ValueError('Census denominator mismatch')


def paired(before, after):
    for report, objective in zip((before, after), OBJECTIVES):
        if report.get('routingObjective') != objective:
            raise ValueError('Expected fixed distance then directed-AIS comparison')
        audit_census(report)
    for field in ('candidateCensus', 'cohortParameters', 'modelSha256', 'modelVersion',
                  'trainingSources', 'implementation', 'dependencies', 'protocolSha256', 'evaluationRole'):
        if before.get(field) != after.get(field):
            raise ValueError('Changed paired transit evidence: '+field)
    result = compare(before, after)
    result.update(candidateCensus=before['candidateCensus'], cohortParameters=before['cohortParameters'],
                  counts=before['counts'], postEvaluationReporting=True)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for flag in ('before', 'after', 'registration', 'output'):
        parser.add_argument('--'+flag, type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise ValueError('Immutable paired output already exists')
    record = json.loads(args.registration.read_text())
    reports = [json.loads(p.read_text()) for p in (args.before, args.after)]
    for report in reports:
        if report['registrationSha256'] != sha(args.registration):
            raise ValueError('Wrong registration')
        bindings = {k:report[k] for k in ('artifactSha256', 'modelSha256', 'protocolSha256', 'implementation', 'dependencies')}
        bindings['chartSourceSha256'] = report['sources'][1]['sha256']
        verify(record, bindings, report['sources'][0], report['routingObjective'],
               dict(modelVersion=report['modelVersion'], trainingSource=report['trainingSources'][0]))
    result = paired(*reports)
    result.update(reportSha256s=[sha(p) for p in (args.before, args.after)],
                  implementationSha256=sha(Path(__file__)),
                  comparatorImplementationSha256=sha(Path(__file__).with_name('compare_regional_runs.py')))
    with args.output.open('xb') as stream:
        stream.write(encode(result)+b'\n')
    print(json.dumps(dict(summary=result['summary'], commonMeasuredCases=result['commonMeasuredCases'])), flush=True)


if __name__ == '__main__': main()
