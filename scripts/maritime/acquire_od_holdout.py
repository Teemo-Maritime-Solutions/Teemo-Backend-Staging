"""Bounded fixed NOAA September/October download, with no TLS bypass or foreign redirects."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
from urllib.request import HTTPRedirectHandler, Request, build_opener

from acquire import SOURCES
from build_graph import encode
from od_experiment import HOLDOUTS, OBJECTIVES, COHORT, PURPOSE, code_hashes, dependencies
from dataclasses import asdict
from validate_regional_ais import FROZEN_CONFIG, CRITERIA
from regional_experiment import sha
from od_corridor_model import CONFIG

MAX_BYTES = 400000000


class FixedRedirects(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        if newurl != req.full_url:
            raise ValueError('Unexpected fixed NOAA source redirect')
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def acquire(month, registration, output):
    record = json.loads(registration.read_text())
    if (record.get('purpose') != PURPOSE or record.get('holdouts') != HOLDOUTS
        or record.get('modelParameters') != CONFIG
        or record.get('implementation') != code_hashes() or record.get('dependencies') != dependencies()
        or record.get('objectives') != OBJECTIVES or record.get('cohortParameters') != asdict(COHORT)
        or record.get('parameters') != asdict(FROZEN_CONFIG) or record.get('criteria') != CRITERIA):
        raise ValueError('Freeze the exact experiment before acquisition')
    source = HOLDOUTS[month]
    path = output / source['filename']
    receipt_path = output / (source['filename']+'.receipt.json')
    if path.exists() or receipt_path.exists():
        raise ValueError('Immutable source already exists')
    started = datetime.now(timezone.utc).isoformat()
    if datetime.fromisoformat(record['createdAt']) >= datetime.fromisoformat(started):
        raise ValueError('Registration must precede download')
    with build_opener(FixedRedirects()).open(Request(source['url'], headers={'User-Agent':'Teemo-Maritime-Research/1.0'}),timeout=60) as response:
        if response.url != source['url']:
            raise ValueError('Unexpected NOAA response URL')
        length = response.headers.get('Content-Length')
        if length is not None and (not length.isdigit() or int(length) > MAX_BYTES):
            raise ValueError('Oversized/invalid response length')
        data = response.read(MAX_BYTES+1)
        modified = response.headers.get('Last-Modified')
    if len(data) > MAX_BYTES or (length is not None and int(length) != len(data)) or not data.startswith(bytes.fromhex('28b52ffd')):
        raise ValueError('Truncated/oversized/non-Zstandard source; no snapshot published')
    metadata = {k:v for k,v in SOURCES['noaa-ais-validation'].items() if k not in ('maxBytes','filename')}
    metadata.update(id=source['id'],url=source['url'],file=source['filename'],
        version=source['filename']+'; independently registered transit physical-model holdout',
        status='SOURCE_DATA',acquisitionStartedAt=started,acquiredAt=datetime.now(timezone.utc).isoformat(),
        lastModified=modified,sha256=hashlib.sha256(data).hexdigest(),bytes=len(data),
        registrationSha256=sha(registration),acquirerSha256=sha(Path(__file__)))
    output.mkdir(parents=True,exist_ok=True)
    with path.open('xb') as stream: stream.write(data)
    with receipt_path.open('xb') as stream: stream.write(encode(metadata)+b'\n')
    print(json.dumps(dict(source=source['id'],sha256=metadata['sha256'],bytes=len(data))),flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('month',choices=HOLDOUTS)
    parser.add_argument('--registration',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args = parser.parse_args()
    acquire(args.month,args.registration,args.output)
