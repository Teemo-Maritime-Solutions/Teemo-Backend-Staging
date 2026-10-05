"""Freeze both origin/destination transit holdout dates and preserve replayable implementation."""
import argparse
from dataclasses import asdict
from datetime import datetime, timezone
import json
from pathlib import Path
import zipfile

from build_graph import encode
from od_experiment import HOLDOUTS, OBJECTIVES, FILES, COHORT, PURPOSE, bindings
from od_corridor_model import CONFIG, physical_context, read_model
from regional_experiment import sha
from validate_regional_ais import FROZEN_CONFIG, CRITERIA


def freeze(artifact,chart_receipt,model_path,protocol,geometry_report,source_root,output,bundle):
    if output.exists() or bundle.exists():
        raise ValueError('Immutable registration/bundle exists')
    # Check all existing raw sources under the declared local data root, not just a new folder.
    if not source_root.is_dir():
        raise ValueError('Existing source root required for untouched-data audit')
    for source in HOLDOUTS.values():
        if next(source_root.rglob(source['filename']),None) is not None:
            raise ValueError('Designated untouched holdout already present')
    manifest,chart,domain,_,edges = physical_context(artifact,chart_receipt)
    model = read_model(model_path,artifact,chart)
    geometry = json.loads(geometry_report.read_text())
    if (geometry.get('geometricCheck') != 'PASS' or geometry.get('artifactSha256') != sha(artifact)
        or geometry.get('sourceSha256') != chart['sha256'] or geometry.get('reciprocalGeometryVerified') is not True
        or geometry.get('checkedReciprocalPairs',-1)*2 != len(edges)):
        raise ValueError('Complete matching physical geometry validation required')
    record = dict(schemaVersion=1,purpose=PURPOSE,
        createdAt=datetime.now(timezone.utc).isoformat(),holdouts=HOLDOUTS,objectives=OBJECTIVES,
        parameters=asdict(FROZEN_CONFIG),cohortParameters=asdict(COHORT),criteria=CRITERIA,modelVersion=model['modelVersion'],
        modelParameters=CONFIG, trainingSourceSha256s=[d['source']['sha256'] for d in model['datasets']], geometryReportSha256=sha(geometry_report),**bindings(artifact,chart,model_path,protocol),
        limitations=['Local preregistration, not a third-party timestamp attestation',
                     'Both dates reported independently; no pooled pass masking a failed date or model'])
    with output.open('xb') as stream: stream.write(encode(record)+b'\n')
    payloads = {'implementation/'+name:Path(__file__).with_name(name).read_bytes() for name in FILES}
    payloads.update({'registration.json':output.read_bytes(),'protocol.md':protocol.read_bytes()})
    with zipfile.ZipFile(bundle,'x') as archive:
        for name,raw in sorted(payloads.items()):
            info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
            archive.writestr(info,raw)
    with zipfile.ZipFile(bundle) as archive:
        if any(archive.read(name)!=raw for name,raw in payloads.items()):
            raise ValueError('Frozen code bundle read-back mismatch')
    print(json.dumps(dict(registrationSha256=sha(output),bundleSha256=sha(bundle),createdAt=record['createdAt'])),flush=True)


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ('artifact','chart-receipt','model','protocol','geometry-report','source-root','output','bundle'):
        parser.add_argument('--'+name,type=Path,required=True)
    args=parser.parse_args()
    freeze(args.artifact,args.chart_receipt,args.model,args.protocol,args.geometry_report,args.source_root,args.output,args.bundle)
