"""Pin the Geofabrik Egypt OSM extract used for Suez geometry research."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
from urllib.request import Request, urlopen


URL = 'https://download.geofabrik.de/africa/egypt-261002.osm.pbf'
MAX_BYTES = 200_000_000


def acquire(directory):
    directory.mkdir(parents=True, exist_ok=True)
    target = directory / 'egypt-261002.osm.pbf'
    receipt_path = directory / 'extract.receipt.json'
    if target.exists() or receipt_path.exists():
        raise ValueError('Immutable Suez source snapshot already exists')
    with urlopen(Request(URL + '.md5', headers={'User-Agent': 'Teemo-Maritime-Research/1.0'}), timeout=45) as response:
        expected = response.read(1000).decode().split()[0]
    if len(expected) != 32:
        raise ValueError('Publisher checksum unavailable')
    md5, sha256, count = hashlib.md5(), hashlib.sha256(), 0
    try:
        with urlopen(Request(URL, headers={'User-Agent': 'Teemo-Maritime-Research/1.0'}), timeout=90) as response, target.open('xb') as output:
            modified = response.headers.get('Last-Modified')
            while block := response.read(1024 * 1024):
                count += len(block)
                if count > MAX_BYTES:
                    raise ValueError('Extract exceeds size bound')
                md5.update(block); sha256.update(block); output.write(block)
        if md5.hexdigest() != expected:
            raise ValueError('Publisher checksum mismatch')
    except BaseException:
        target.unlink(missing_ok=True)
        raise
    receipt = dict(id='osm-egypt-suez', file=target.name, url=URL,
                   acquiredAt=datetime.now(timezone.utc).isoformat(), status='SOURCE_DATA',
                   version='Geofabrik Egypt 2026-10-02', lastModified=modified,
                   sha256=sha256.hexdigest(), publisherMd5=expected,
                   license='OpenStreetMap contributors, ODbL-1.0',
                   licenseUrl='https://www.openstreetmap.org/copyright',
                   unit='WGS84 longitude/latitude degrees', coverage='Egypt extract; Suez Canal research import',
                   limitations=['Community mapping, not hydrographic authority or navigation data',
                                'No current passage permission, vessel clearance or traffic control established',
                                'Retain OSM attribution and ODbL; no public distribution performed'])
    receipt_path.write_text(json.dumps(receipt, indent=2), encoding='utf-8')
    print(json.dumps(dict(bytes=count, sha256=receipt['sha256'], publisherMd5=expected)), flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=Path('data/maritime-routing/suez-osm-20261003'))
    acquire(parser.parse_args().output)
