"""Append a source-checked canal path to an immutable baseline; preserve every base row."""
import argparse
from contextlib import ExitStack
import copy
import hashlib
import json
from pathlib import Path
import shutil
import tempfile
import zipfile

from build_graph import encode
from build_combined_research import verified_manifest
from panama_geometry import SOURCE, ZONE, POLICY, sha, extract, coastal_land, source_path, connectors, densify

def build(base_path, base_validation, source_receipt, land_receipt, output):
    if output.exists():
        raise ValueError('Choose a new immutable artifact')
    validation = json.loads(base_validation.read_text())
    base_hash = sha(base_path)
    if validation.get('artifactSha256') != base_hash or validation.get('geometricCheck') != 'PASS' or validation.get('regionalControlCheck') != 'PASS':
        raise ValueError('Pinned complete baseline validation required')
    data, receipt = extract(source_receipt)
    land, land_source = coastal_land(land_receipt)
    path, _ = source_path(data, land)
    with ExitStack() as stack:
        archive = stack.enter_context(zipfile.ZipFile(base_path))
        base = verified_manifest(archive)
        if base['graphVersion'] != validation['graphVersion'] or validation['checkedReciprocalPairs'] * 2 != base['report']['directedEdges']:
            raise ValueError('Baseline version/count mismatch')
        if not any(s['id'] == 'gshhg' and s['sha256'] == land_source['sha256'] for s in base['sources']):
            raise ValueError('Coastline source mismatch')
        nodes = [json.loads(line) for line in archive.open('nodes.jsonl')]
        links = connectors(path, nodes, land)
        offset = len(nodes)
        new_nodes = [dict(id=f'PANAMA:OSM:{node}', kind='SOURCED_CANAL_REFERENCE', point=point, sourceIds=[SOURCE])
                     for node, point in zip(path['nodeIds'], path['points'])]
        if {n['id'] for n in nodes} & {n['id'] for n in new_nodes}:
            raise ValueError('Canal already imported')
        edges = []
        def pair(a, b, points, kind, sources, way=None):
            distance, _ = densify(points)
            edge = dict(fromNode=a, toNode=b, geometry=points, distanceM=distance,
                        geometryModel='WGS84_GEODESIC', kind=kind, specialZone=ZONE,
                        minimumDepthM=None, observedSpeedKnots=None, aisObservations=None,
                        restrictions=[], legalStatus='UNKNOWN', sourceIds=sources, regionalControlIds=[])
            if way is not None:
                edge['sourceWayId'] = way
            edges.append(edge)
            edges.append(dict(edge, fromNode=b, toNode=a, geometry=list(reversed(points))))
        for index, (a, b) in enumerate(zip(path['points'], path['points'][1:])):
            pair(offset + index, offset + index + 1, [a, b],
                 'CANAL_LOCK_RESEARCH' if path['lockSegments'][index] else 'CANAL_CENTERLINE_RESEARCH', [SOURCE], path['wayIds'][index])
        for link in links:
            a = link['endpoint']
            pair(offset + a, link['baseNode'], [path['points'][a], nodes[link['baseNode']]['point']],
                 'CANAL_OCEAN_CONNECTOR', ['gshhg', SOURCE])
        temp = Path(stack.enter_context(tempfile.TemporaryDirectory(prefix='panama-', dir=output.parent)))
        for name in base['tables']:
            with archive.open(name) as original, (temp / name).open('wb') as destination:
                shutil.copyfileobj(original, destination)
        for name, rows in [('nodes.jsonl', new_nodes), ('edges.jsonl', edges)]:
            with (temp / name).open('ab') as destination:
                for row in rows:
                    destination.write(encode(row) + b'\n')
        evidence = dict(policy=POLICY, sourceSha256=receipt['sha256'], sourceIds=[SOURCE],
                        path=path, connectors=links,
                        limitations=['OSM centerline/water consistency, not independent hydrographic validation',
                                     'UNKNOWN permission/depth; no ship-size check, lock delay, tide or booking model',
                                     'Only classic source-supported transit; expanded-lock branches excluded'])
        (temp / 'panama-evidence.jsonl').write_bytes(encode(evidence) + b'\n')
        manifest = copy.deepcopy(base)
        manifest.pop('graphVersion')
        manifest['buildVersion'] = 'PANAMA_SOURCE_RESEARCH_V1'
        manifest['createdAt'] = receipt['acquiredAt']
        manifest['builderSha256'] = sha(Path(__file__))
        manifest['sources'].append(receipt)
        manifest['panamaControls'] = dict(policy=POLICY, zoneId=ZONE, sourceId=SOURCE,
                                          sourceSha256=receipt['sha256'], baselineArtifactSha256=base_hash,
                                          baselineGraphVersion=base['graphVersion'], baselineValidationSha256=sha(base_validation),
                                          evidenceTable='panama-evidence.jsonl', geometryImplementationSha256=sha(Path(__file__).with_name('panama_geometry.py')),
                                          addedNodes=len(new_nodes), addedDirectedEdges=len(edges))
        manifest['report']['nodes'] += len(new_nodes)
        manifest['report']['directedEdges'] += len(edges)
        manifest['report']['largestComponentNodes'] += len(new_nodes)
        manifest['report']['integrationCounts']['directedEdges'] += len(edges)
        manifest['report']['integrationCounts']['panamaDirectedEdges'] = len(edges)
        manifest['report']['validationGate'] = 'PENDING_PANAMA_VALIDATION'
        manifest['report']['missingData'] += ['panama_current_permission_and_vessel_clearance', 'panama_lock_delays_and_booking']
        manifest['limitations'] += evidence['limitations']
        manifest['tables'] = {p.name: sha(p) for p in sorted(temp.iterdir())}
        manifest['graphVersion'] = hashlib.sha256(encode(manifest)).hexdigest()
        with zipfile.ZipFile(output, 'x', compression=zipfile.ZIP_DEFLATED) as result:
            for name in sorted(manifest['tables']):
                info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0)); info.compress_type = zipfile.ZIP_DEFLATED
                with (temp / name).open('rb') as source, result.open(info, 'w', force_zip64=True) as destination:
                    shutil.copyfileobj(source, destination)
            for name in archive.namelist():
                if name.startswith('notices/'):
                    result.writestr(archive.getinfo(name), archive.read(name))
            result.writestr('notices/OSM-Panama.txt', 'Canal geometry: (c) OpenStreetMap contributors, ODbL-1.0. https://www.openstreetmap.org/copyright\nResearch only. Retain attribution and licence obligations for any future distribution.\n')
            result.writestr('manifest.json', encode(manifest))
    print(json.dumps(dict(graphVersion=manifest['graphVersion'], artifactSha256=sha(output), canalLengthM=path['lengthM'], addedNodes=len(new_nodes), addedEdges=len(edges))), flush=True)

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('base', 'base-validation', 'source-receipt', 'land-receipt', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args()
    build(args.base, args.base_validation, args.source_receipt, args.land_receipt, args.output)
