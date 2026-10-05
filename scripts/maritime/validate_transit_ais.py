"""Prospectively selected origin/destination transit research evaluation; never bypass operational routing."""
import argparse
from collections import Counter
from dataclasses import asdict
from datetime import datetime, timezone
import json
from pathlib import Path
import time

from scipy.sparse import csr_matrix
from scipy.spatial import cKDTree

from build_graph import encode, verify_receipt, xyz
from transit_experiment import OBJECTIVES, COHORT, bindings, code_hashes, dependencies, verify
from transit_cohort import select
from validate_corridor_ais import evaluate_case
from phase3_sources import chart_bounds
from regional_corridor_model import physical_context, read_model, DirectedCorridorObjective
from regional_experiment import sha
from validate_regional_ais import FROZEN_CONFIG, acceptance, peak_working_set, regional_observations


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
        raise ValueError('Prospective registration is required for transit evaluation')
    if ais.get('registrationSha256') != sha(registration):
        raise ValueError('Acquisition receipt belongs to another experiment')
    verify(json.loads(registration.read_text()), bindings(artifact, chart, model_path, protocol), ais, routing_objective, model)
    points = [n['point'] for n in nodes]
    lookup = {(e['fromNode'],e['toNode']):e for e in edges}
    if len(lookup) != len(edges):
        raise ValueError('Duplicate physical edges')
    matrix = csr_matrix(([e['distanceM'] for e in edges], ([e['fromNode'] for e in edges],[e['toNode'] for e in edges])), shape=(len(nodes),len(nodes)))
    objective = DirectedCorridorObjective(domain,edges,len(nodes),segments,config) if routing_objective != 'DISTANCE_V1' else None
    bbox, _ = chart_bounds(chart_receipt)
    records, counts = regional_observations(path, FROZEN_CONFIG, bbox)
    selected, census, cohort_counts = select(records, FROZEN_CONFIG, bbox, ais['sha256'], COHORT)
    counts.update(cohort_counts)
    del records
    tree, cases = cKDTree(xyz(points)), []
    for key, track in selected:
        case = evaluate_case(key,track,points,lookup,domain,tree,matrix,objective)
        cases.append(case)
        print(json.dumps(dict(stage='transit_case',case=len(cases),status=case['status'],frechetM=case.get('discreteFrechetM'))),flush=True)
    result = acceptance(cases)
    report = dict(schemaVersion=1, graphVersion=manifest['graphVersion'], artifactSha256=sha(artifact), validationProfile=manifest['validationProfile'],
        sources=[ais,chart], trainingSources=[model['trainingSource']], modelSha256=sha(model_path), modelVersion=model['modelVersion'],
        modelParameters=asdict(config), parameters=asdict(FROZEN_CONFIG), cohortParameters=asdict(COHORT), routingObjective=routing_objective,
        evaluationRole='FROZEN_TRANSIT_TEMPORAL_HOLDOUT',
        registrationSha256=sha(registration), protocolSha256=sha(protocol),
        implementation=code_hashes(), dependencies=dependencies(), createdAt=datetime.now(timezone.utc).isoformat(),
        counts=dict(counts), candidateCensus=census, modelEdgeCoverage=None if objective is None else dict(objective.directional_counts), cases=cases,
        statuses=dict(Counter(c['status'] for c in cases)), regionalPhysicalAcceptance=result, validationGate='NOT_PASSED',
        operationalRoutes=0, seconds=time.perf_counter()-started, memory=peak_working_set(),
        limitations=['Research route preference, not legal direction, vessel clearance or current navigability',
                     'All chart restrictions retained and unevaluated in isolated offline diagnostic only',
                     'No graph/endpoint changes, source/holdout mixing or outcome-dependent route switching',
                     'Displacement-dominated regional segments only, not verified port calls or all voyages',
                     'Full candidate census retained; cohort selection precedes graph and routing outcomes',
                     'Regional terrestrial AIS and 2026 chart mismatch; multiple segments may share a vessel',
                     'Training-direction compatibility is an engineering association, not a calibrated confidence'])
    with output.open('xb') as stream: stream.write(encode(report)+b'\n')
    print(json.dumps(dict(acceptance=result,seconds=report['seconds'],memory=report['memory'],sha256=sha(output))),flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('artifact','chart-receipt','ais-receipt','model','protocol','output'): parser.add_argument('--'+name,type=Path,required=True)
    parser.add_argument('--routing-objective',choices=OBJECTIVES,required=True)
    parser.add_argument('--registration',type=Path,required=True)
    args = parser.parse_args()
    validate(args.artifact,args.chart_receipt,args.ais_receipt,args.model,args.protocol,args.output,args.routing_objective,args.registration)
