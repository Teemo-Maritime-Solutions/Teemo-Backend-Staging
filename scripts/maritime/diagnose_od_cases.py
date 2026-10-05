"""Adapt immutable O/D results to the original source-row geometry diagnostic."""
import argparse
import json
from pathlib import Path

from build_graph import verify_receipt
from diagnose_corridor_failures import diagnose
from od_corridor_model import write_new
from regional_experiment import sha


def run(report_path,ais_receipt,chart_receipt,artifact,view_path,output):
    if output.exists() or view_path.exists():
        raise ValueError('Immutable diagnosis/view exists')
    report=json.loads(report_path.read_text(encoding='utf-8'))
    _,chart=verify_receipt(chart_receipt)
    rows=[r for r in report['reports'] if r['routingObjective']=='OD_COHERENT_V1']
    if len(rows)!=1 or report['evaluationRole']!='FROZEN_OD_TRANSIT_TEMPORAL_HOLDOUT':
        raise ValueError('Expected one completed immutable holdout date')
    row=rows[0]
    view=dict(row,sources=[row['source'],chart],graphVersion=report['graphVersion'],
              artifactSha256=report['artifactSha256'],parameters=report['parameters'],
              originalOdReportSha256=sha(report_path),postEvaluationView=True)
    write_new(view_path,view)
    diagnose(view_path,ais_receipt,chart_receipt,artifact,output)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ('report','ais-receipt','chart-receipt','artifact','view','output'):
        parser.add_argument('--'+name,type=Path,required=True)
    args=parser.parse_args()
    run(args.report,args.ais_receipt,args.chart_receipt,args.artifact,args.view,args.output)
