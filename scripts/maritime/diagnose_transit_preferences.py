"""Post hoc cost attribution, never a replacement prediction or acceptance rule."""
import argparse
import json
import math
from pathlib import Path

from build_graph import encode, geodesic
from compare_transit_runs import paired
from regional_corridor_model import physical_context, read_model, DirectedCorridorObjective
from regional_experiment import sha


def score_path(case, points, lookup, objective):
    for field in ('predictedDistanceM', 'unsupportedDirectedEvidenceM'):
        if field in case:
            value = case[field]
            if isinstance(value, bool) or not isinstance(value, (float, int)) or not math.isfinite(value) or value < 0:
                raise ValueError('Invalid recorded cost: '+field)
    indices = case['routeNodeIndices']
    if not indices or any(type(n) is not int or not 0 <= n < len(points) for n in indices):
        raise ValueError('Invalid recorded path indices')
    step = objective.domain.config.geodesic_step_m / 4
    length, unsupported = 0.0, 0.0
    for start, end in ((case['origin'], points[indices[0]]), (points[indices[-1]], case['destination'])):
        distance, curve = geodesic(start, end, step)
        length += distance
        if not objective.support(curve): unsupported += distance
    for start, end in zip(indices, indices[1:]):
        if (start, end) not in lookup:
            raise ValueError('Recorded path uses missing physical edge')
        edge = lookup[(start, end)]
        length += edge['distanceM']
        if not objective.edge_support[(start, end)]: unsupported += edge['distanceM']
    if abs(length-case['predictedDistanceM']) > .01:
        raise ValueError('Reconstructed physical distance mismatch')
    if 'unsupportedDirectedEvidenceM' in case and abs(unsupported-case['unsupportedDirectedEvidenceM']) > .01:
        raise ValueError('Reconstructed directed support mismatch')
    return dict(physicalDistanceM=length, unsupportedDirectedEvidenceM=unsupported,
                objectiveCostM=length+unsupported,
                supportedDistanceFraction=1-unsupported/length if length else None)


def diagnose(before_path, after_path, artifact, chart_receipt, model_path, output):
    if output.exists():
        raise ValueError('Immutable attribution exists')
    before, after = [json.loads(p.read_text()) for p in (before_path, after_path)]
    paired(before, after)
    manifest, chart, domain, nodes, edges = physical_context(artifact, chart_receipt)
    model, segments, config = read_model(model_path, artifact, chart, domain)
    if (after['artifactSha256'] != sha(artifact) or after['graphVersion'] != manifest['graphVersion']
            or after['sources'][1] != chart or after['modelSha256'] != sha(model_path)
            or after['trainingSources'] != [model['trainingSource']]):
        raise ValueError('Actual graph/chart/model differs from evaluated evidence')
    objective = DirectedCorridorObjective(domain, edges, len(nodes), segments, config)
    points = [n['point'] for n in nodes]
    lookup = {(e['fromNode'], e['toNode']):e for e in edges}
    cases = []
    for baseline, directed in zip(before['cases'], after['cases']):
        row = dict(trackKey=directed['trackKey'], baselineStatus=baseline['status'], directedStatus=directed['status'])
        if baseline['status'] == directed['status'] == 'PHYSICAL_MODEL_MEASURED':
            left, right = [score_path(case, points, lookup, objective) for case in (baseline, directed)]
            row.update(baseline=left, directed=right,
                frechetChangeM=directed['discreteFrechetM']-baseline['discreteFrechetM'],
                objectiveCostChangeM=right['objectiveCostM']-left['objectiveCostM'],
                exceedsSpatialTarget=(directed['discreteFrechetM'] > 1000 or directed['discreteHausdorffM'] > 1000))
            row['classification'] = ('REQUIRES_SEARCH_REVIEW' if row['objectiveCostChangeM'] > .01 else
                'PREFERENCE_IMPROVES_COST_BUT_WORSENS_SHAPE' if row['frechetChangeM'] > .01 else
                'NO_SHAPE_REGRESSION')
        cases.append(row)
    report = dict(schemaVersion=1, reportSha256s=[sha(p) for p in (before_path, after_path)],
        implementationSha256=sha(Path(__file__)), cases=cases,
        originalAcceptance=after['regionalPhysicalAcceptance'], validationGate='NOT_PASSED',
        limitations=['Post hoc attribution using recorded paths, not new routes or independent validation',
                     'Training support is association, not confidence, legal permission or vessel intent',
                     'Lower objective cost does not guarantee lower held-out shape error',
                     'No per-case model switching, acceptance changes or dropped observations'])
    with output.open('xb') as stream: stream.write(encode(report)+b'\n')
    print(json.dumps(dict(cases=len(cases), regressions=[c for c in cases if c.get('frechetChangeM',0)>.01],
                          sha256=sha(output))), flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('before', 'after', 'artifact', 'chart-receipt', 'model', 'output'):
        parser.add_argument('--'+name, type=Path, required=True)
    args = parser.parse_args()
    diagnose(args.before, args.after, args.artifact, args.chart_receipt, args.model, args.output)
