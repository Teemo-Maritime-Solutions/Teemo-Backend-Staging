"""Preserve exact frozen experiment code for later replay, without raw AIS redistribution."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

from build_graph import encode
from regional_experiment import IMPLEMENTATION_FILES, implementation, sha


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for flag in ('registration', 'protocol', 'output'): parser.add_argument('--' + flag, type=Path, required=True)
    args = parser.parse_args()
    record = json.loads(args.registration.read_text())
    if record['implementation'] != implementation() or record['protocolSha256'] != sha(args.protocol):
        raise ValueError('Current implementation/protocol differs from frozen registration')
    payloads = {'implementation/' + name: Path(__file__).with_name(name).read_bytes() for name in IMPLEMENTATION_FILES}
    payloads['registration.json'] = args.registration.read_bytes()
    payloads['protocol.md'] = args.protocol.read_bytes()
    payloads['bundle.json'] = encode(dict(purpose='FROZEN_RESEARCH_IMPLEMENTATION_REPLAY',
        sha256={key: hashlib.sha256(value).hexdigest() for key, value in payloads.items()},
        limitations=['No raw AIS/chart redistribution; use locally pinned receipts and files',
                     'Use the registered dependency versions; no operational navigation eligibility']))
    with zipfile.ZipFile(args.output, 'x') as archive:
        for name, data in sorted(payloads.items()):
            info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, data)
    with zipfile.ZipFile(args.output) as archive:
        check = json.loads(archive.read('bundle.json'))
        if any(hashlib.sha256(archive.read(name)).hexdigest() != digest for name, digest in check['sha256'].items()):
            raise ValueError('Code bundle read-back checksum mismatch')
    print(json.dumps(dict(sha256=sha(args.output), bytes=args.output.stat().st_size, registeredImplementationFiles=len(IMPLEMENTATION_FILES))), flush=True)
