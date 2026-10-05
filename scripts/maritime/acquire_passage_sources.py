"""Bounded read-only snapshots of official passage evidence and UKHO public indexes."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, build_opener
import zipfile

from build_graph import encode
from probe_global_sources import SameHostRedirects

SOURCES = {
    'ukho-routing-index': ('https://datahub.admiralty.co.uk/portal/sharing/rest/search?f=pjson&q=routeing&num=50', 'PUBLIC_METADATA_NOT_GEOMETRY'),
    'ukho-services-index': ('https://datahub.admiralty.co.uk/server/rest/services?f=pjson', 'PUBLIC_METADATA_NOT_GEOMETRY'),
    'mca-dover-guidance': ('https://www.gov.uk/government/publications/dover-strait-crossings-channel-navigation-information-service/dover-strait-crossings-channel-navigation-information-service-cnis', 'OFFICIAL_GUIDANCE_NOT_GEOMETRY'),
    'mpa-singapore-publications': ('https://www.mpa.gov.sg/who-we-are/newsroom-resources/publications/singapore-port-information', 'OFFICIAL_PUBLICATION_INDEX_NOT_GEOMETRY'),
    'sca-rules-index': ('https://www.suezcanal.gov.eg/English/Navigation/Pages/RulesOfNavigation.aspx', 'OFFICIAL_RULES_INDEX_NOT_GEOMETRY'),
}
MAX_BYTES=5_000_000


def acquire(output):
    if output.exists():
        raise ValueError('Immutable official-source snapshot exists')
    records,payloads=[],{}
    for name,(url,scope) in SOURCES.items():
        record=dict(sourceId=name,url=url,scope=scope,checkedAt=datetime.now(timezone.utc).isoformat())
        try:
            with build_opener(SameHostRedirects()).open(Request(url,headers={'User-Agent':'Teemo-Maritime-Research/1.0'}),timeout=25) as response:
                data=response.read(MAX_BYTES+1)
                if len(data)>MAX_BYTES:
                    raise ValueError('Official document exceeds resource bound')
                record.update(status='READ',httpStatus=response.status,finalUrl=response.url,bytes=len(data),
                    sha256=hashlib.sha256(data).hexdigest(),contentType=response.headers.get('Content-Type'),
                    lastModified=response.headers.get('Last-Modified'))
                payloads[name+'.raw']=data
                if name.startswith('ukho-'):
                    parsed=json.loads(data)
                    record['responseSummary']=parsed
                    if 'error' in parsed:
                        record['status']='SERVICE_ERROR'
        except HTTPError as error:
            record.update(status='HTTP_ERROR',httpStatus=error.code)
        except (URLError,TimeoutError,OSError,ValueError) as error:
            if '10013' in str(error):
                raise PermissionError('Sandbox network denial requires approved retry') from error
            record.update(status='ACCESS_ERROR',errorType=type(error).__name__,detail=str(error))
        records.append(record)
        print(json.dumps({k:v for k,v in record.items() if k!='responseSummary'}),flush=True)
    audit=dict(schemaVersion=1,sources=records,implementationSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        limitations=['Original public responses only; no data import, clearance, account, licence acceptance or publication',
                     'A source index and official guidance do not supply navigable geometries or complete live restrictions'])
    payloads['audit.json']=encode(audit)
    with zipfile.ZipFile(output,'x') as archive:
        for name,data in sorted(payloads.items()):
            info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
            archive.writestr(info,data)
    print(json.dumps(dict(snapshotSha256=hashlib.sha256(output.read_bytes()).hexdigest())),flush=True)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,required=True)
    acquire(parser.parse_args().output)
