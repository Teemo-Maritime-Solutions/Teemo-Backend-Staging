"""Compare pinned source snapshots, distinguishing service IDs from substantive evidence."""
import argparse
from collections import Counter
import hashlib
from pathlib import Path

from build_enc_pilot import load_snapshot
from build_graph import encode


def fingerprints(layer, geometry_only=False):
    oid = layer['objectIdField']
    return Counter(hashlib.sha256(encode(dict(geometry=f['geometry'], attributes=(
        {} if geometry_only else {k: v for k, v in f['properties'].items() if k != oid})))).hexdigest()
        for f in layer['features'])


def compare(left, right):
    rows = []
    for key in sorted(set(left['layers']) | set(right['layers']), key=int):
        a, b = left['layers'].get(key), right['layers'].get(key)
        row = dict(layer=int(key), before=None if a is None else len(a['features']), after=None if b is None else len(b['features']))
        if a is None or b is None:
            row['status'] = 'ADDED_LAYER' if a is None else 'REMOVED_LAYER'
        else:
            row.update(geometryUnchanged=fingerprints(a, True) == fingerprints(b, True),
                       attributesAndGeometryUnchangedIgnoringObjectId=fingerprints(a) == fingerprints(b),
                       exactFeaturesUnchanged=encode(a['features']) == encode(b['features']))
        rows.append(row)
    return dict(sameCells=left['cells'] == right['cells'], layers=rows,
                limitation='Multiset comparison excludes only declared service object ID; retrieval is not survey time')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for flag in ('before', 'after', 'output'): parser.add_argument('--' + flag, type=Path, required=True)
    args = parser.parse_args()
    left, a = load_snapshot(args.before)
    right, b = load_snapshot(args.after)
    report = dict(compare(left, right), sources=[a, b], implementationSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest())
    with args.output.open('xb') as stream: stream.write(encode(report) + b'\n')
    print(encode(report).decode(), flush=True)
