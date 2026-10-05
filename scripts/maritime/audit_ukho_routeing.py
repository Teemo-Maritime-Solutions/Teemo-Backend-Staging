"""Independent source-feature coverage audit of the research mesh; no legal clearance."""
import argparse
from collections import Counter, defaultdict
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import time
import zipfile

from shapely import LineString, Point, STRtree
from shapely.geometry import shape
from build_graph import encode, geodesic
from regional_experiment import sha


def audit(source_path,artifact,validation_path,output):
    if output.exists():
        raise ValueError('Immutable UKHO audit exists')
    started=time.perf_counter()
    receipt=json.loads(source_path.with_suffix('.receipt.json').read_text(encoding='utf-8'))
    if receipt.get('sha256')!=sha(source_path) or receipt.get('operationalImport') is not False:
        raise ValueError('UKHO snapshot receipt mismatch')
    with zipfile.ZipFile(source_path) as archive:
        for request in json.loads(archive.read('requests.json')):
            if hashlib.sha256(archive.read(request['file'])).hexdigest()!=request['sha256']:
                raise ValueError('Original UKHO response changed')
        source=json.loads(archive.read('source.json'))
    validation=json.loads(validation_path.read_text())
    if validation.get('geometricCheck')!='PASS' or validation.get('artifactBytes')!=artifact.stat().st_size:
        raise ValueError('Passing global geometry report and matching archive size required')
    features,geometries=[],[]
    duplicates=defaultdict(list)
    for layer_id,layer in source['layers'].items():
        if len(layer['features'])!=receipt['featureCounts'][layer_id]:
            raise ValueError('UKHO layer count mismatch')
        oid=layer['objectIdField']
        for feature in layer['features']:
            geom=shape(feature['geometry'])
            if geom.is_empty or not geom.is_valid or any(not -180<=v<=180 for v in (geom.bounds[0],geom.bounds[2])) or any(not -90<=v<=90 for v in (geom.bounds[1],geom.bounds[3])):
                raise ValueError('Invalid UKHO source geometry')
            properties=feature['properties']
            row=dict(sourceFeatureId='UKHO:'+layer_id+':'+str(properties[oid]),layerId=layer_id,
                sourceProperties=properties,geometryType=geom.geom_type,bounds=list(geom.bounds),
                coveredMeshNodes=0,intersectingReciprocalPairs=0,fullyContainedReciprocalPairs=0)
            if any(f['sourceFeatureId']==row['sourceFeatureId'] for f in features):
                raise ValueError('Duplicate source feature identity')
            features.append(row)
            geometries.append(geom)
            duplicates[geom.normalize().wkb_hex].append(row['sourceFeatureId'])
    tree=STRtree(geometries)
    boundary=[min(g.bounds[0] for g in geometries),min(g.bounds[1] for g in geometries),
              max(g.bounds[2] for g in geometries),max(g.bounds[3] for g in geometries)]
    edge_count=0
    with zipfile.ZipFile(artifact) as archive:
        manifest=json.loads(archive.read('manifest.json'))
        unsigned=dict(manifest)
        version=unsigned.pop('graphVersion')
        if hashlib.sha256(encode(unsigned)).hexdigest()!=version or validation.get('graphVersion')!=version:
            raise ValueError('Global geometry report does not match content-addressed graph')
        for name in ('nodes.jsonl','edges.jsonl'):
            digest=hashlib.sha256()
            with archive.open(name) as stream:
                for raw in stream:
                    digest.update(raw)
                    record=json.loads(raw)
                    if name=='nodes.jsonl':
                        geom=Point(record['point'])
                        for i in tree.query(geom,predicate='intersects'):
                            features[int(i)]['coveredMeshNodes']+=1
                        continue
                    edge_count+=1
                    if record['fromNode']>=record['toNode']:
                        continue
                    raw_curve=record['geometry']
                    # Research mesh edges are short geodesics. Keep dateline edges
                    # away from the UK domain instead of treating them as Greenwich.
                    if any(abs(b[0]-a[0])>180 for a,b in zip(raw_curve,raw_curve[1:])):
                        continue
                    bounds=LineString(raw_curve).bounds
                    if bounds[2]<boundary[0] or bounds[0]>boundary[2] or bounds[3]<boundary[1] or bounds[1]>boundary[3]:
                        continue
                    dense=[]
                    for a,b in zip(raw_curve,raw_curve[1:]):
                        segment=geodesic(a,b,500)[1]
                        dense.extend(segment if not dense else segment[1:])
                    geom=LineString(dense)
                    for i in tree.query(geom,predicate='intersects'):
                        row=features[int(i)]
                        row['intersectingReciprocalPairs']+=1
                        if geometries[int(i)].covers(geom):
                            row['fullyContainedReciprocalPairs']+=1
            if digest.hexdigest()!=manifest['tables'][name]:
                raise ValueError('Graph table changed: '+name)
    if validation.get('checkedReciprocalPairs',-1)*2!=edge_count or validation.get('reciprocalGeometryVerified') is not True:
        raise ValueError('Full reciprocal coverage is required for graph audit')
    lanes=[f for f in features if f['sourceProperties'].get('feature_ty')=='Traffic Separation Scheme Lanes']
    dover=[f for f in lanes if 'dover' in f['sourceProperties'].get('inform','').lower()]
    result=dict(schemaVersion=1,purpose='RESEARCH_MESH_SOURCE_COVERAGE_AUDIT_NOT_NAVIGATION',
        createdAt=datetime.now(timezone.utc).isoformat(),sourceSha256=sha(source_path),artifactSha256=sha(artifact),
        graphVersion=manifest['graphVersion'],geometryReportSha256=sha(validation_path),implementationSha256=sha(Path(__file__)),
        featureCounts=dict(Counter(f['layerId'] for f in features)),features=features,
        identicalGeometryGroups=[ids for ids in duplicates.values() if len(ids)>1],
        laneSummary=dict(total=len(lanes),withoutMeshNode=sum(f['coveredMeshNodes']==0 for f in lanes),
                         withoutContainedMeshPair=sum(f['fullyContainedReciprocalPairs']==0 for f in lanes)),
        doverLaneSummary=dict(total=len(dover),withoutMeshNode=sum(f['coveredMeshNodes']==0 for f in dover),
                              withoutContainedMeshPair=sum(f['fullyContainedReciprocalPairs']==0 for f in dover)),
        seconds=time.perf_counter()-started,validationGate='NOT_PASSED',operationalImport=False,
        attribution=source['attribution'],limitations=source['limitations']+[
            'Intersections and containment are geometry diagnostics, not expected-passage or directional compliance tests',
            'No route endpoints invented; no graph geometries or restrictions changed',
            'Missing source restriction strings remain unknown, never unrestricted',
            '500 m geodesic sampling and WGS84 planar intersection; not hydrographic validation'])
    with output.open('xb') as stream: stream.write(encode(result)+b'\n')
    print(json.dumps({k:result[k] for k in ('featureCounts','laneSummary','doverLaneSummary','seconds','validationGate')}),flush=True)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ('source','artifact','geometry-report','output'):
        parser.add_argument('--'+name,type=Path,required=True)
    args=parser.parse_args()
    audit(args.source,args.artifact,args.geometry_report,args.output)
