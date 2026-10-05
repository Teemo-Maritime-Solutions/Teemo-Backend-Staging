"""Grouped development and registered temporal validation of coherent O/D preferences."""
import argparse
from collections import Counter
from dataclasses import asdict
from datetime import datetime, timezone
import json
from pathlib import Path
import time

from scipy.sparse import csr_matrix
from scipy.spatial import cKDTree

from build_graph import verify_receipt, xyz
from od_corridor_model import CONFIG, OdObjective, physical_context, read_model, write_new
from regional_experiment import dependencies, sha
from transit_cohort import TransitConfig, select
from phase3_sources import chart_bounds
from validate_corridor_ais import evaluate_case
from validate_regional_ais import FROZEN_CONFIG, acceptance, peak_working_set, regional_observations

OBJECTIVES = ('DISTANCE_V1', 'OD_COHERENT_V1')


def run(artifact, chart_receipt, model_path, output, ais_receipt=None, protocol=None, registration=None):
    started = time.perf_counter()
    if output.exists():
        raise ValueError('Immutable evaluation output already exists')
    manifest, chart, domain, nodes, edges = physical_context(artifact, chart_receipt)
    model = read_model(model_path, artifact, chart)
    points = [n['point'] for n in nodes]
    lookup = {(e['fromNode'], e['toNode']):e for e in edges}
    if len(lookup) != len(edges):
        raise ValueError('Duplicate physical edges')
    matrix = csr_matrix(([e['distanceM'] for e in edges],
        ([e['fromNode'] for e in edges], [e['toNode'] for e in edges])), shape=(len(nodes),len(nodes)))
    tree = cKDTree(xyz(points))
    tracks = [t for d in model['datasets'] for t in d['tracks']]
    datasets = model['datasets']
    role = 'GROUPED_VESSEL_DEVELOPMENT_NOT_INDEPENDENT_HOLDOUT'
    registration_sha = None
    if ais_receipt is not None:
        from od_experiment import bindings, verify
        if registration is None or protocol is None:
            raise ValueError('Prospective registration and protocol required')
        path, source = verify_receipt(ais_receipt)
        registration_sha = sha(registration)
        if source.get('registrationSha256') != registration_sha:
            raise ValueError('Acquisition belongs to another experiment')
        verify(json.loads(registration.read_text()), bindings(artifact,chart,model_path,protocol),source,model)
        with path.open('rb') as stream:
            if stream.read(4) != bytes.fromhex('28b52ffd'):
                raise ValueError('Expected Zstandard AIS source')
        bbox, _ = chart_bounds(chart_receipt)
        records, counts = regional_observations(path,FROZEN_CONFIG,bbox)
        selected, census, cohort_counts = select(records,FROZEN_CONFIG,bbox,source['sha256'])
        counts.update(cohort_counts)
        datasets = [dict(date=path.name[4:14], source=source, counts=dict(counts), candidateCensus=census,
                        selectedKeys=[k for k,_ in selected], tracks=[dict(trackKey=k, observations=t) for k,t in selected])]
        role = 'FROZEN_OD_TRANSIT_TEMPORAL_HOLDOUT'
    objective = OdObjective(domain,edges,len(nodes),tracks)
    reports = []
    for dataset in datasets:
        by_key = {t['trackKey']:t for t in dataset['tracks']}
        for objective_name in OBJECTIVES:
            cases, begin = [], time.perf_counter()
            for key in dataset['selectedKeys']:
                target = by_key[key]
                predictor = objective if objective_name == 'OD_COHERENT_V1' else None
                if predictor is not None:
                    predictor.excluded_fold = target['fold'] if ais_receipt is None else None
                    predictor.last_selection = None
                case = evaluate_case(key,target['observations'],points,lookup,domain,tree,matrix,predictor)
                if predictor is not None:
                    case['templateSelection'] = predictor.last_selection
                    if ais_receipt is None:
                        case['evaluationFold'] = target['fold']
                        case['evaluationVesselGroup'] = target['vesselGroup']
                        if predictor.last_selection is not None and predictor.last_selection.get('trainingFold') == target['fold']:
                            raise ValueError('Held-out vessel fold leaked into prediction')
                cases.append(case)
            measured = [c['discreteFrechetM'] for c in cases if c['status']=='PHYSICAL_MODEL_MEASURED']
            report = dict(date=dataset['date'], source=dataset['source'], counts=dataset['counts'],
                candidateCensus=dataset['candidateCensus'], routingObjective=objective_name,
                cases=cases, regionalPhysicalAcceptance=acceptance(cases), seconds=time.perf_counter()-begin,
                maxFrechetM=max(measured) if measured else None,
                over1km=sum(c.get('discreteFrechetM',0)>1000 or c.get('discreteHausdorffM',0)>1000 for c in cases),
                statuses=dict(Counter(c['status'] for c in cases)))
            reports.append(report)
            print(json.dumps({k:report[k] for k in ('date','routingObjective','regionalPhysicalAcceptance','maxFrechetM','over1km','seconds')}),flush=True)
    from od_experiment import code_hashes
    result = dict(schemaVersion=1, evaluationRole=role, createdAt=datetime.now(timezone.utc).isoformat(),
        artifactSha256=sha(artifact),graphVersion=manifest['graphVersion'],chartSourceSha256=chart['sha256'],
        modelSha256=sha(model_path),modelVersion=model['modelVersion'],modelParameters=CONFIG,
        trainingSources=[d['source'] for d in model['datasets']],parameters=asdict(FROZEN_CONFIG),
        cohortParameters=asdict(TransitConfig()),implementation=code_hashes(),dependencies=dependencies(),
        registrationSha256=registration_sha,protocolSha256=sha(protocol) if protocol else None,
        reports=reports,seconds=time.perf_counter()-started,memory=peak_working_set(),validationGate='NOT_PASSED',operationalRoutes=0,
        limitations=['Regional physical geometry only; restrictions remain unevaluated',
                     'Grouped development uses previously inspected sources; no independent validation claim',
                     'Temporal holdouts can contain previously seen vessels and are not unseen-vessel tests',
                     '2024 terrestrial AIS versus 2026 charts; no verified port calls or worldwide representation'])
    write_new(output,result)
    print(json.dumps(dict(reportSha256=sha(output),seconds=result['seconds'],memory=result['memory'])),flush=True)
    return result


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ('artifact','chart-receipt','model','output'):
        parser.add_argument('--'+name,type=Path,required=True)
    for name in ('ais-receipt','protocol','registration'):
        parser.add_argument('--'+name,type=Path)
    args=parser.parse_args()
    run(args.artifact,args.chart_receipt,args.model,args.output,args.ais_receipt,args.protocol,args.registration)
