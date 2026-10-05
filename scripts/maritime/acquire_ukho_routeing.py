"""Snapshot official UKHO routing features for research audit, never navigation."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import Request, build_opener
import zipfile

from build_graph import encode
from phase3_sources import download_features
from probe_global_sources import SameHostRedirects

ROOT='https://datahub.admiralty.co.uk'
ITEM='fbf168cd00374802be438255513134a4'
SERVICE=ROOT+'/server/rest/services/Hosted/Ships_Routeing_Measures/FeatureServer'


class Client:
    def __init__(self):
        self.responses=[]
        self.total=0

    def request(self,url,parameters):
        if not url.startswith(ROOT+'/'):
            raise ValueError('Unexpected UKHO host')
        url += '?'+urlencode(parameters)
        start=datetime.now(timezone.utc).isoformat()
        with build_opener(SameHostRedirects()).open(Request(url,headers={'User-Agent':'Teemo-Maritime-Research/1.0'}),timeout=45) as response:
            raw=response.read(25_000_001)
        self.total+=len(raw)
        if len(raw)>25_000_000 or self.total>150_000_000:
            raise ValueError('UKHO byte budget exceeded')
        data=json.loads(raw)
        if not isinstance(data,dict) or 'error' in data:
            raise ValueError('UKHO API response error')
        self.responses.append(dict(url=url,startedAt=start,completedAt=datetime.now(timezone.utc).isoformat(),
                                   sha256=hashlib.sha256(raw).hexdigest(),raw=raw))
        return data


def acquire(output):
    if output.exists():
        raise ValueError('Immutable UKHO snapshot exists')
    client=Client()
    item=client.request(ROOT+'/portal/sharing/rest/content/items/'+ITEM,dict(f='json'))
    if item.get('id')!=ITEM or item.get('owner')!='UKHydrographicOffice' or item.get('url')!=SERVICE or item.get('access')!='public':
        raise ValueError('UKHO public publisher/service identity changed')
    service=client.request(SERVICE,dict(f='json'))
    if service.get('serviceItemId')!=ITEM or len(service.get('layers',[]))>30:
        raise ValueError('Unexpected UKHO service identity/layer count')
    layers={}
    for layer in service['layers']:
        layer_id=layer['id']
        if type(layer_id) is not int or layer_id<0:
            raise ValueError('Invalid published layer id')
        url=SERVICE+'/'+str(layer_id)
        metadata=client.request(url,dict(f='json'))
        oid=[f['name'] for f in metadata.get('fields',[]) if f.get('type')=='esriFieldTypeOID']
        if len(oid)!=1 or metadata.get('type')!='Feature Layer':
            raise ValueError('Missing UKHO feature identity')
        layers[str(layer_id)]=download_features(client,url,metadata,oid[0],[-180,-90,180,90])
        print(json.dumps(dict(layer=layer_id,name=layer['name'],features=len(layers[str(layer_id)]['features']))),flush=True)
    payload=dict(schemaVersion=1,purpose='UKHO_RESEARCH_PASSAGE_REFERENCE_NOT_NAVIGATION',item=item,service=service,layers=layers,
        attribution='Contains public sector information, licensed under the Open Government Licence v3.0, from the UK Hydrographic Office',
        limitations=['Publisher states unsuitable for marine navigation or creation of navigational products',
                     'Research geometry audit only; no graph import, passage clearance, live closure or completeness claim'])
    requests=[]
    with zipfile.ZipFile(output,'x',compression=zipfile.ZIP_DEFLATED) as archive:
        archive.writestr('source.json',encode(payload))
        for i,response in enumerate(client.responses):
            name='responses/%04d.raw'%i
            archive.writestr(name,response['raw'])
            requests.append(dict(file=name,**{k:v for k,v in response.items() if k!='raw'}))
        archive.writestr('requests.json',encode(requests))
    receipt=dict(id='ukho-routeing-research-20260921',file=output.name,url=SERVICE,
        acquiredAt=datetime.now(timezone.utc).isoformat(),sha256=hashlib.sha256(output.read_bytes()).hexdigest(),
        bytes=output.stat().st_size,license=item.get('licenseInfo'),licenseUrl='https://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/',
        implementationSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        featureCounts={k:len(v['features']) for k,v in layers.items()},operationalImport=False)
    with output.with_suffix('.receipt.json').open('xb') as stream:
        stream.write(encode(receipt)+b'\n')
    print(json.dumps({k:v for k,v in receipt.items() if k!='license'}),flush=True)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,required=True)
    acquire(parser.parse_args().output)
