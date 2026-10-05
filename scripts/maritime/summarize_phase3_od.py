"""Evidence-bound regional acceptance and explicit remaining global phase-3 gaps."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path

from compare_od_runs import audit
from od_corridor_model import write_new
from od_experiment import HOLDOUTS, code_hashes
from regional_experiment import sha


def summarize(model_path,registration_path,reports,ukho_path,output):
    if output.exists():
        raise ValueError('Immutable phase summary exists')
    model=json.loads(model_path.read_text(encoding='utf-8'))
    registration=json.loads(registration_path.read_text(encoding='utf-8'))
    if registration['implementation']!=code_hashes() or registration['modelSha256']!=sha(model_path):
        raise ValueError('Frozen implementation or model changed')
    expected={s['id'] for s in HOLDOUTS.values()}
    seen=set()
    dates=[]
    for path in reports:
        report=json.loads(path.read_text(encoding='utf-8'))
        if report['evaluationRole']!='FROZEN_OD_TRANSIT_TEMPORAL_HOLDOUT' or report['registrationSha256']!=sha(registration_path) or report['modelSha256']!=sha(model_path):
            raise ValueError('Not a matching frozen temporal report')
        paired=audit(report,model,registration)
        identifier=report['reports'][0]['source']['id']
        if identifier not in expected or identifier in seen:
            raise ValueError('Missing, duplicated or substituted registered date')
        seen.add(identifier)
        row=paired['paired'][0]
        dates.append(dict(date=row['date'],sourceId=identifier,reportSha256=sha(path),
                          baseline=row['summary'][0],conditioned=row['summary'][1],counts=row['counts']))
    if seen!=expected:
        raise ValueError('Both registered dates required; no partial success')
    ukho=json.loads(ukho_path.read_text(encoding='utf-8'))
    if ukho.get('purpose')!='RESEARCH_MESH_SOURCE_COVERAGE_AUDIT_NOT_NAVIGATION' or ukho.get('operationalImport') is not False:
        raise ValueError('Expected separate non-operational UKHO geometry audit')
    result=dict(schemaVersion=1,createdAt=datetime.now(timezone.utc).isoformat(),
        regionalTemporalTransitAcceptance='PASS' if all(d['conditioned']['acceptance']['status']=='PASS' for d in dates) else 'FAIL',
        regionalScope='Two registered NOAA New York physical transit dates only',dates=sorted(dates,key=lambda d:d['date']),
        globalPhase3='NOT_PASSED',laterPhasesAuthorizedByThisReport=False,
        remainingRequirements=[
            dict(id='GLOBAL_CHANNEL_GEOMETRY_AND_EXPECTED_PASSAGES',status='NOT_PASSED',
                 evidenceSha256=sha(ukho_path),laneSummary=ukho['laneSummary'],doverLaneSummary=ukho['doverLaneSummary'],
                 detail='Independent UKHO research audit exposes incomplete mesh representation; no compliant passage route test'),
            dict(id='GLOBAL_WATER_SIDE_ACCESS_AND_CONSTRAINTS',status='UNRESOLVED',
                 detail='No new terminal associations, clearances, applicable permissions or closure coverage were established'),
            dict(id='WIDER_INDEPENDENT_TRAJECTORY_VALIDATION',status='NOT_PASSED',
                 detail='New York terrestrial segments do not establish worldwide prediction quality; earlier failures remain visible')],
        modelSha256=sha(model_path),registrationSha256=sha(registration_path),implementationSha256=sha(Path(__file__)),
        limitations=['Regional pass is not universal accuracy, navigation clearance or global phase-3 completion',
                     'Previously failed grouped development months and historical temporal experiments are not overwritten'])
    write_new(output,result)
    print(json.dumps(dict(regionalTemporalTransitAcceptance=result['regionalTemporalTransitAcceptance'],
                          globalPhase3=result['globalPhase3'],sha256=sha(output))),flush=True)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ('model','registration','ukho-audit','output'):
        parser.add_argument('--'+name,type=Path,required=True)
    parser.add_argument('--report',type=Path,action='append',required=True)
    args=parser.parse_args()
    summarize(args.model,args.registration,args.report,args.ukho_audit,args.output)
