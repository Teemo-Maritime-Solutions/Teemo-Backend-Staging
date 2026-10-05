"""Standalone directed-AIS research evaluation; never bypass operational routing."""
import argparse
from collections import Counter
from dataclasses import asdict
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import time

from scipy.sparse import csr_matrix
from scipy.spatial import cKDTree

from build_graph import encode, geodesic, verify_receipt, xyz
from corridor_experiment import OBJECTIVES, bindings, code_hashes, dependencies, verify
from phase3_sources import chart_bounds
from regional_corridor_model import physical_context, read_model, DirectedCorridorObjective, TRAIN_ID, TRAIN_URL
from regional_experiment import sha
from validate_ais import densify, route_between_anchors
from validate_graph import discrete_frechet_m, discrete_hausdorff_m
from validate_regional_ais import FROZEN_CONFIG, acceptance, anchors, peak_working_set, regional_observations, regional_tracks


def evaluate_case(key, track, points, lookup, domain, tree, matrix, objective):
    config = FROZEN_CONFIG
    observed = [r[1] for r in track]
    case = dict(trackKey=key, sourceRows=[r[2] for r in track], observationCount=len(track), origin=observed[0], destination=observed[-1],
        startUtc=datetime.fromtimestamp(track[0][0], timezone.utc).isoformat(), endUtc=datetime.fromtimestamp(track[-1][0], timezone.utc).isoformat(),
        passageStatus='PASSAGE_NOT_EVALUATED')
    step = domain.config.geodesic_step_m / 4
    if not domain.allows(densify(observed, step)):
        return dict(case, status='OBSERVATION_CHART_WATER_CONFLICT')
    origin, destination = [anchors(p, points, tree, domain, config) for p in (observed[0], observed[-1])]
    case['clearAnchorCounts'] = [len(origin), len(destination)]
    if not origin or not destination:
        return dict(case, status='NO_CLEAR_ANCHOR')
    if objective is None:
        result = route_between_anchors(matrix, origin, destination)
    else:
        choice = objective.route(origin, destination, (observed[0], observed[-1]), points)
        result = None if choice is None else choice[0]
        if choice is not None: case['unsupportedDirectedEvidenceM'] = choice[1]
    if result is None:
        return dict(case, status='DISCONNECTED_ANCHORS')
    route_nodes, distance, connectors = result
    start_curve = geodesic(observed[0], points[route_nodes[0]], step)[1]
    end_curve = geodesic(points[route_nodes[-1]], observed[-1], step)[1]
    predicted = list(start_curve)
    restrictions, training_ids = set(domain.restrictions(start_curve)), set()
    for start, end in zip(route_nodes, route_nodes[1:]):
        edge = lookup[(start, end)]
        if edge['restrictions'] != domain.restrictions(edge['geometry']) or edge['legalStatus'] != 'UNKNOWN' or edge['minimumDepthM'] is not None:
            raise ValueError('Lost chart restriction or fabricated clearance')
        predicted.extend(edge['geometry'][1:])
        restrictions.update(edge['restrictions'])
        if objective is not None:
            training_ids.update(objective.edge_support[(start, end)])
    predicted.extend(end_curve[1:])
    restrictions.update(domain.restrictions(end_curve))
    if objective is not None:
        training_ids.update(objective.support(start_curve))
        training_ids.update(objective.support(end_curve))
    if not domain.allows(densify(predicted, step)):
        return dict(case, status='PREDICTED_CHART_WATER_CONFLICT')
    a = densify(observed, config.metric_step_m)
    b = densify([observed[0], *[points[n] for n in route_nodes], observed[-1]], config.metric_step_m)
    if max(len(a), len(b)) > config.max_metric_samples:
        return dict(case, status='METRIC_RESOURCE_BOUND', metricSampleCounts=[len(a), len(b)])
    observed_distance = sum(geodesic(p, q, config.metric_step_m)[0] for p, q in zip(observed, observed[1:]))
    return dict(case, status='PHYSICAL_MODEL_MEASURED', predictedDistanceM=distance, observedDistanceM=observed_distance,
        distanceRatio=distance/observed_distance, connectorDistancesM=connectors, discreteHausdorffM=discrete_hausdorff_m(a,b),
        discreteFrechetM=discrete_frechet_m(a,b), metricSampleCounts=[len(a),len(b)], routeNodeIndices=route_nodes,
        unevaluatedRestrictionIds=sorted(restrictions), trainingSegmentIndices=sorted(training_ids))


def validate(artifact, chart_receipt, ais_receipt, model_path, protocol, output, routing_objective, registration=None):
    started = time.perf_counter()
    if output.exists() or routing_objective not in OBJECTIVES:
        raise ValueError('Immutable output exists or unknown objective')
    manifest, chart, domain, nodes, edges = physical_context(artifact, chart_receipt)
    model, segments, config = read_model(model_path, artifact, chart, domain)
    path, ais = verify_receipt(ais_receipt)
    with path.open('rb') as stream:
        if stream.read(4) != bytes.fromhex('28b52ffd'):
            raise ValueError('Expected Zstandard observations')
    if registration is None:
        if ais['id'] != TRAIN_ID or ais['url'] != TRAIN_URL or ais['sha256'] != model['trainingSource']['sha256']:
            raise ValueError('Without prospective registration only March resubstitution is supported')
    else:
        verify(json.loads(registration.read_text()), bindings(artifact, chart, model_path, protocol), ais, routing_objective, model)
    points = [n['point'] for n in nodes]
    lookup = {(e['fromNode'],e['toNode']):e for e in edges}
    if len(lookup) != len(edges):
        raise ValueError('Duplicate physical edges')
    matrix = csr_matrix(([e['distanceM'] for e in edges], ([e['fromNode'] for e in edges],[e['toNode'] for e in edges])), shape=(len(nodes),len(nodes)))
    objective = DirectedCorridorObjective(domain,edges,len(nodes),segments,config) if routing_objective != 'DISTANCE_V1' else None
    bbox, _ = chart_bounds(chart_receipt)
    records, counts = regional_observations(path, FROZEN_CONFIG, bbox)
    tracks = []
    for vessel, observations in records.items():
        for track in regional_tracks(observations, FROZEN_CONFIG, bbox, counts):
            key = hashlib.sha256((ais['sha256']+':'+vessel+':'+str(track[0][0])).encode()).hexdigest()
            tracks.append((key,track))
    counts['eligibleRegionalSegments'] = len(tracks)
    selected = sorted(tracks)[:FROZEN_CONFIG.max_cases]
    tree, cases = cKDTree(xyz(points)), []
    for key, track in selected:
        case = evaluate_case(key,track,points,lookup,domain,tree,matrix,objective)
        cases.append(case)
        print(json.dumps(dict(stage='corridor_case',case=len(cases),status=case['status'],frechetM=case.get('discreteFrechetM'))),flush=True)
    result = acceptance(cases)
    report = dict(schemaVersion=1, graphVersion=manifest['graphVersion'], artifactSha256=sha(artifact), validationProfile=manifest['validationProfile'],
        sources=[ais,chart], trainingSources=[model['trainingSource']], modelSha256=sha(model_path), modelVersion=model['modelVersion'],
        modelParameters=asdict(config), parameters=asdict(FROZEN_CONFIG), routingObjective=routing_objective,
        evaluationRole='FROZEN_TEMPORAL_HOLDOUT' if registration else 'TRAINING_RESUBSTITUTION_NOT_VALIDATION',
        registrationSha256=sha(registration) if registration else None, protocolSha256=sha(protocol),
        implementation=code_hashes(), dependencies=dependencies(), createdAt=datetime.now(timezone.utc).isoformat(),
        counts=dict(counts), modelEdgeCoverage=None if objective is None else dict(objective.directional_counts), cases=cases,
        statuses=dict(Counter(c['status'] for c in cases)), regionalPhysicalAcceptance=result, validationGate='NOT_PASSED',
        operationalRoutes=0, seconds=time.perf_counter()-started, memory=peak_working_set(),
        limitations=['Research route preference, not legal direction, vessel clearance or current navigability',
                     'All chart restrictions retained and unevaluated in isolated offline diagnostic only',
                     'No graph/endpoint changes, source/holdout mixing or outcome-dependent route switching',
                     'Regional terrestrial AIS and 2026 chart mismatch; multiple segments may share a vessel',
                     'Training-direction compatibility is an engineering association, not a calibrated confidence'])
    with output.open('xb') as stream: stream.write(encode(report)+b'\n')
    print(json.dumps(dict(acceptance=result,seconds=report['seconds'],memory=report['memory'],sha256=sha(output))),flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('artifact','chart-receipt','ais-receipt','model','protocol','output'): parser.add_argument('--'+name,type=Path,required=True)
    parser.add_argument('--routing-objective',choices=OBJECTIVES,required=True)
    parser.add_argument('--registration',type=Path)
    args = parser.parse_args()
    validate(args.artifact,args.chart_receipt,args.ais_receipt,args.model,args.protocol,args.output,args.routing_objective,args.registration)
