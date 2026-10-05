"""Snapshot public OSM canal geometry for local research, preserving original IDs/tags."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import Request, urlopen

QUERIES = {
    'canals': '[out:json][timeout:60];way[waterway=canal](8.7,-80.05,9.5,-79.4);out meta geom;',
    'water': '[out:json][timeout:90];(way[natural=water](8.7,-80.05,9.5,-79.4);relation[natural=water](8.7,-80.05,9.5,-79.4);way[waterway=riverbank](8.7,-80.05,9.5,-79.4););out meta geom;',
}

def acquire(part, directory, endpoint):
    directory.mkdir(parents=True, exist_ok=True)
    target = directory / (part + '.json')
    if target.exists():
        raise ValueError('Immutable snapshot exists')
    query = QUERIES[part]
    url = endpoint + '?' + urlencode({'data': query})
    with urlopen(Request(url, headers={'User-Agent': 'Teemo-Maritime-Research/1.0'}), timeout=115) as response:
        raw = response.read(40_000_001)
    if len(raw) > 40_000_000:
        raise ValueError('Snapshot exceeds size bound')
    data = json.loads(raw)
    if data.get('remark') or not data.get('elements'):
        raise ValueError('Incomplete/empty source response')
    receipt = dict(id='osm-panama-' + part, url=url, query=query, file=target.name,
                   acquiredAt=datetime.now(timezone.utc).isoformat(), status='SOURCE_DATA',
                   version=data['osm3s']['timestamp_osm_base'], sha256=hashlib.sha256(raw).hexdigest(),
                   license='OpenStreetMap contributors, ODbL-1.0', licenseUrl='https://www.openstreetmap.org/copyright',
                   unit='WGS84 longitude/latitude degrees', coverage='Panama Canal research rectangle',
                   limitations=['Community mapping, not hydrographic authority or navigation data',
                                'No current passage permission, vessel clearance, depth or lock delay established',
                                'Keep original way/node identifiers and attribution; local research only'])
    target.write_bytes(raw)
    target.with_suffix('.receipt.json').write_text(json.dumps(receipt, indent=2), encoding='utf-8')
    print(json.dumps(dict(part=part, elements=len(data['elements']), bytes=len(raw), sha256=receipt['sha256'])), flush=True)

def extract_download(directory):
    directory.mkdir(parents=True, exist_ok=True)
    target = directory / 'panama-260922.osm.pbf'
    if target.exists():
        raise ValueError('Immutable extract exists')
    url = 'https://download.geofabrik.de/central-america/panama-260922.osm.pbf'
    with urlopen(Request(url, headers={'User-Agent': 'Teemo-Maritime-Research/1.0'}), timeout=45) as response:
        raw = response.read(60_000_001)
        modified = response.headers.get('Last-Modified')
    if len(raw) > 60_000_000:
        raise ValueError('Extract exceeds size bound')
    with urlopen(url + '.md5', timeout=30) as response:
        expected = response.read(1000).decode().split()[0]
    if hashlib.md5(raw).hexdigest() != expected:
        raise ValueError('Publisher checksum mismatch')
    target.write_bytes(raw)
    receipt = dict(id='osm-panama', file=target.name, url=url,
                   acquiredAt=datetime.now(timezone.utc).isoformat(), status='SOURCE_DATA',
                   version='Geofabrik Panama 2026-09-22', lastModified=modified,
                   sha256=hashlib.sha256(raw).hexdigest(), publisherMd5=expected,
                   license='OpenStreetMap contributors, ODbL-1.0', licenseUrl='https://www.openstreetmap.org/copyright',
                   unit='WGS84 longitude/latitude degrees', coverage='Panama extract; canal-only research import',
                   limitations=['Community mapping, not hydrographic authority or navigation data',
                                'No current passage permission, vessel clearance, depth or lock delay established',
                                'Retain OSM attribution and ODbL; no public distribution performed'])
    (directory / 'extract.receipt.json').write_text(json.dumps(receipt, indent=2), encoding='utf-8')
    print(json.dumps(dict(bytes=len(raw), sha256=receipt['sha256'])), flush=True)

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--part', choices=[*QUERIES, 'extract'], required=True)
    parser.add_argument('--output', type=Path, default=Path('data/maritime-routing/panama-osm-20260923'))
    parser.add_argument('--endpoint', default='https://overpass-api.de/api/interpreter')
    args = parser.parse_args()
    if args.part == 'extract':
        extract_download(args.output)
    else:
        acquire(args.part, args.output, args.endpoint)
