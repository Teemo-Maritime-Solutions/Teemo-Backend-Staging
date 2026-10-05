"""Recompute O/D experiment census, paired results and group-leakage accounting."""
import argparse
from collections import Counter
from dataclasses import asdict
import json
from pathlib import Path

from compare_regional_runs import compare
from compare_transit_runs import audit_census
from od_corridor_model import CONFIG, write_new
from od_experiment import OBJECTIVES, verify
from regional_experiment import sha
from transit_cohort import TransitConfig
from validate_regional_ais import FROZEN_CONFIG


def audit(report,model,registration=None):
    if (report.get('parameters')!=asdict(FROZEN_CONFIG) or report.get('cohortParameters')!=asdict(TransitConfig())
            or report.get('modelParameters')!=CONFIG or report.get('modelVersion')!=model['modelVersion']
            or report.get('trainingSources')!=[d['source'] for d in model['datasets']]
            or report.get('validationGate')!='NOT_PASSED' or report.get('operationalRoutes')!=0):
        raise ValueError('Changed experiment configuration or model identity')
    development=report['evaluationRole']=='GROUPED_VESSEL_DEVELOPMENT_NOT_INDEPENDENT_HOLDOUT'
    if not development and report['evaluationRole']!='FROZEN_OD_TRANSIT_TEMPORAL_HOLDOUT':
        raise ValueError('Unknown evaluation role')
    expected_dates=[d['date'] for d in model['datasets']] if development else [report['reports'][0]['date']]
    if len(report['reports'])!=2*len(expected_dates):
        raise ValueError('Missing or added experiment result')
    training={t['trackKey']:t for d in model['datasets'] for t in d['tracks']}
    by_date={d['date']:d for d in model['datasets']}
    paired=[]
    for index,date in enumerate(expected_dates):
        rows=report['reports'][2*index:2*index+2]
        if rows[0]['candidateCensus']!=rows[1]['candidateCensus'] or rows[0]['source']!=rows[1]['source']:
            raise ValueError('Paired census or source changed')
        expanded=[]
        for row,objective in zip(rows,OBJECTIVES):
            if row['date']!=date or row['routingObjective']!=objective:
                raise ValueError('Wrong date/objective order')
            combined=dict(row,parameters=report['parameters'],cohortParameters=report['cohortParameters'],
                artifactSha256=report['artifactSha256'],sources=[row['source']],
                registrationSha256=report['registrationSha256'],memory=report['memory'],operationalRoutes=0)
            audit_census(combined)
            if development:
                source_dataset=by_date[date]
                if row['source']!=source_dataset['source'] or row['candidateCensus']!=source_dataset['candidateCensus']:
                    raise ValueError('Changed cached development source/census')
            else:
                if registration is None:
                    raise ValueError('Missing holdout registration')
                actual={k:report[k] for k in ('artifactSha256','chartSourceSha256','modelSha256','protocolSha256','implementation','dependencies')}
                verify(registration,actual,row['source'],model)
                if row['source'].get('registrationSha256')!=report['registrationSha256']:
                    raise ValueError('Acquisition registration changed')
            if objective=='OD_COHERENT_V1':
                for case in row['cases']:
                    selection=case.get('templateSelection')
                    if development:
                        target=training[case['trackKey']]
                        if case.get('evaluationFold')!=target['fold'] or case.get('evaluationVesselGroup')!=target['vesselGroup']:
                            raise ValueError('Changed held-out vessel identity')
                    if case['status']=='PHYSICAL_MODEL_MEASURED' and selection is None:
                        raise ValueError('Missing measured template provenance')
                    if selection is None:
                        continue
                    if selection['status']=='COHERENT_TRACK':
                        template=training.get(selection['trackKey'])
                        if template is None or template['trainingStatus']!='USED' or selection.get('trainingFold')!=template['fold'] or selection.get('vesselGroup')!=template['vesselGroup']:
                            raise ValueError('Invalid selected training provenance')
                        if development and (template['fold']==target['fold'] or template['vesselGroup']==target['vesselGroup']):
                            raise ValueError('Vessel-group training leakage')
                    elif selection['status']!='NO_MATCH_DISTANCE_OBJECTIVE':
                        raise ValueError('Unknown template status')
                    expected_fold=target['fold'] if development else None
                    if selection.get('excludedFold')!=expected_fold:
                        raise ValueError('Changed exclusion group')
            expanded.append(combined)
        comparison=compare(*expanded)
        comparison['date']=date
        comparison['counts']=rows[0]['counts']
        comparison['templateSelections']=dict(Counter((c.get('templateSelection') or {}).get('status','NOT_ROUTED') for c in rows[1]['cases']))
        paired.append(comparison)
    return dict(schemaVersion=1,evaluationRole=report['evaluationRole'],paired=paired,validationGate='NOT_PASSED',
                limitations=['Post-evaluation reporting only; no new routes or case/model selection',
                             'No pooling across dates to hide a failed acceptance result'])


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ('report','model','output'):
        parser.add_argument('--'+name,type=Path,required=True)
    parser.add_argument('--registration',type=Path)
    args=parser.parse_args()
    if args.output.exists():
        raise ValueError('Immutable paired audit exists')
    report=json.loads(args.report.read_text(encoding='utf-8'))
    model=json.loads(args.model.read_text(encoding='utf-8'))
    if report['modelSha256']!=sha(args.model):
        raise ValueError('Changed model bytes')
    registration=json.loads(args.registration.read_text(encoding='utf-8')) if args.registration else None
    if registration is not None and report['registrationSha256']!=sha(args.registration):
        raise ValueError('Changed registration bytes')
    result=audit(report,model,registration)
    result.update(reportSha256=sha(args.report),modelSha256=sha(args.model),implementationSha256=sha(Path(__file__)))
    write_new(args.output,result)
    print(json.dumps(dict(dates=[r['date'] for r in result['paired']],reportSha256=sha(args.output))),flush=True)
