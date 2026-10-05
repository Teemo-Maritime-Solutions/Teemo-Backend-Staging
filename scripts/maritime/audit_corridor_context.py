"""Pin the existing failure diagnosis before the next route-choice experiment."""
import argparse
import json
from pathlib import Path

from build_enc_pilot import load_snapshot, feature_key
from build_graph import encode
from regional_experiment import sha


def audit(before, after, causes_path, chart_receipt, output):
    left, right, causes = [json.loads(p.read_text()) for p in (before, after, causes_path)]
    source, chart = load_snapshot(chart_receipt)
    if (left['sources'] != right['sources'] or right['sources'][1] != chart
        or causes['evaluationSha256'] != sha(after)):
        raise ValueError('Diagnosis/evaluation/chart provenance mismatch')
    a = {c['trackKey']: c for c in left['cases']}
    if set(a) != {c['trackKey'] for c in right['cases']}:
        raise ValueError('Unpaired cases')
    choice_failures = [dict(trackKey=c['trackKey'], beforeFrechetM=a[c['trackKey']]['discreteFrechetM'],
                           afterFrechetM=c['discreteFrechetM'], status=c['status'])
                      for c in right['cases'] if c['status'] == 'PHYSICAL_MODEL_MEASURED' and c['discreteFrechetM'] > 1000]
    wanted = {hit['featureId'] for c in causes['cases'] if c['originalStatus'] == 'OBSERVATION_CHART_WATER_CONFLICT'
              for hit in c['entireObservedTrack']['sourceExclusionHits']}
    evidence = [dict(id=feature_key(layer, record, value['objectIdField']), attributes=record['properties'])
                for layer, value in source['layers'].items() for record in value['features']
                if feature_key(layer, record, value['objectIdField']) in wanted]
    if {e['id'] for e in evidence} != wanted:
        raise ValueError('Missing original chart exclusion evidence')
    result = dict(schemaVersion=1, inputs={p.name: sha(p) for p in (before, after, causes_path, chart_receipt)},
                  chartSourceSha256=chart['sha256'], routeChoiceFailures=choice_failures, chartExclusions=evidence,
                  conclusion=['Measured failures have clear physical paths; source-channel preference is not a universal route-choice model',
                              'The recorded exclusion features lack VALSOU; no depth clearance or error in AIS/chart inferred',
                              'Keep exclusions and frozen April outcomes; fit the next model only to separately declared training observations'],
                  validationGate='NOT_PASSED')
    with output.open('xb') as stream: stream.write(encode(result) + b'\n')
    print(json.dumps(dict(routeChoiceFailures=choice_failures, exclusionFeatureIds=sorted(wanted), sha256=sha(output))), flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('before', 'after', 'causes', 'chart-receipt', 'output'): parser.add_argument('--'+name, type=Path, required=True)
    args = parser.parse_args()
    audit(args.before, args.after, args.causes, args.chart_receipt, args.output)
