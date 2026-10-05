"""Audit append-only inheritance, source geometry, reciprocal links and reachability."""
import argparse
from array import array
import copy
import hashlib
import json
from pathlib import Path
import time
import zipfile

import numpy as np
from scipy.sparse import csr_matrix
from scipy.sparse.csgraph import connected_components
from build_graph import encode
from build_combined_research import verified_manifest
from validate_graph import reciprocal_pairs
from panama_geometry import SOURCE, ZONE, POLICY, sha, extract, coastal_land, source_path, connectors, clear, densify

def require(condition, message):
    if not condition:
        raise ValueError(message)

def prefix_digest(stream, length):
    digest = hashlib.sha256()
    while length:
        block = stream.read(min(length, 65536))
        require(bool(block), 'Truncated inherited table')
        length -= len(block)
        digest.update(block)
    return digest.hexdigest()

def validate(artifact, base_path, base_validation, source_receipt, land_receipt, output):
    require(not output.exists(), 'Immutable validation already exists')
    started = time.perf_counter()
    prior = json.loads(base_validation.read_text())
    base_hash = sha(base_path)
    require(prior.get('artifactSha256') == base_hash and prior.get('geometricCheck') == 'PASS'
            and prior.get('regionalControlCheck') == 'PASS' and prior.get('reciprocalGeometryVerified') is True,
            'Baseline geometry/regional evidence mismatch')
    data, receipt = extract(source_receipt)
    land, _ = coastal_land(land_receipt)
    path, blocked = source_path(data, land)
    with zipfile.ZipFile(base_path) as baseline, zipfile.ZipFile(artifact) as candidate:
        base = verified_manifest(baseline)
        manifest = verified_manifest(candidate)
        unsigned = dict(manifest)
        version = unsigned.pop('graphVersion')
        require(hashlib.sha256(encode(unsigned)).hexdigest() == version, 'Manifest version digest mismatch')
        require(manifest['createdAt'] == receipt['acquiredAt'], 'Canal acquisition date not represented')
        require(manifest['sources'] == base['sources'] + [receipt], 'Sources altered')
        require(manifest['regionalControls'] == base['regionalControls'], 'Regional controls altered')
        controls = manifest['panamaControls']
        require(controls['policy'] == POLICY and controls['baselineArtifactSha256'] == base_hash
                and controls['baselineValidationSha256'] == sha(base_validation)
                and controls['sourceSha256'] == receipt['sha256'], 'Canal evidence binding mismatch')
        additions = {}
        for table, digest in base['tables'].items():
            with candidate.open(table) as stream:
                require(prefix_digest(stream, baseline.getinfo(table).file_size) == digest, 'Inherited rows changed: ' + table)
                tail = stream.read()
                if table in ('nodes.jsonl', 'edges.jsonl'):
                    additions[table] = [json.loads(line) for line in tail.splitlines()]
                else:
                    require(not tail, 'Unexpected modification: ' + table)
        base_nodes = [json.loads(line) for line in baseline.open('nodes.jsonl')]
        offset = len(base_nodes)
        added_nodes, added_edges = additions['nodes.jsonl'], additions['edges.jsonl']
        require(len(added_nodes) == controls['addedNodes'], 'Added node count mismatch')
        require(added_nodes == [dict(id=f'PANAMA:OSM:{node}', kind='SOURCED_CANAL_REFERENCE', point=point, sourceIds=[SOURCE])
                               for node, point in zip(path['nodeIds'], path['points'])], 'Source nodes changed')
        links = connectors(path, base_nodes, land)
        expected = {}
        for index, way in enumerate(path['wayIds']):
            expected[(offset + index, offset + index + 1)] = (
                'CANAL_LOCK_RESEARCH' if path['lockSegments'][index] else 'CANAL_CENTERLINE_RESEARCH', [SOURCE], way)
        for link in links:
            expected[(offset + link['endpoint'], link['baseNode'])] = ('CANAL_OCEAN_CONNECTOR', ['gshhg', SOURCE], None)
        require(len(added_edges) == 2 * len(expected) == controls['addedDirectedEdges'], 'Unexpected added edges')
        nodes = base_nodes + added_nodes
        seen = set()
        for edge in added_edges:
            a, b = edge['fromNode'], edge['toNode']
            key = (a, b) if (a, b) in expected else (b, a)
            require(key in expected and (a, b) not in seen, 'Unsourced/duplicate connection')
            seen.add((a, b))
            kind, sources, way = expected[key]
            require(edge['kind'] == kind and edge['sourceIds'] == sources and edge.get('sourceWayId') == way, 'Source segment identity changed')
            require(edge['geometry'] == [nodes[a]['point'], nodes[b]['point']], 'Source coordinates changed')
            require(edge['specialZone'] == ZONE and edge['legalStatus'] == 'UNKNOWN' and edge['minimumDepthM'] is None
                    and edge['restrictions'] == [] and edge['regionalControlIds'] == [], 'Unsupported permission/depth/control claim')
            distance, points = densify(edge['geometry'], 10)
            require(abs(distance - edge['distanceM']) <= max(.01, distance * 1e-8), 'Distance mismatch')
            require(clear(points, land if kind == 'CANAL_OCEAN_CONNECTOR' else blocked), 'Unbacked land crossing')
        pairs = sum(1 for _ in reciprocal_pairs(added_edges))
        # Recompute directed reachability; all old restrictions remain effective.
        origins, destinations = array('i'), array('i')
        def record(edge):
            if not edge['restrictions'] and edge['legalStatus'] != 'PROHIBITED':
                origins.append(edge['fromNode']); destinations.append(edge['toNode'])
        for line in baseline.open('edges.jsonl'):
            record(json.loads(line))
        for edge in added_edges:
            record(edge)
        matrix = csr_matrix((np.ones(len(origins), dtype=np.int8), (origins, destinations)), shape=(len(nodes), len(nodes)))
        count, labels = connected_components(matrix, directed=True, connection='strong')
        ports = [json.loads(line) for line in baseline.open('ports.jsonl')]
        covered = [p for p in ports if p['status'] == 'CONNECTED_REFERENCE_POINT']
        component = labels[covered[0]['nodeId']]
        require(all(labels[p['nodeId']] == component for p in covered), 'Prior port connectivity lost')
        require(all(labels[i] == component for i in range(offset, len(nodes))), 'Canal not connected to global component')
        require(count == base['report']['strongComponents'], 'Unexpected baseline components joined')
        require(manifest['report']['nodes'] == len(nodes)
                and manifest['report']['directedEdges'] == base['report']['directedEdges'] + len(added_edges), 'Manifest count mismatch')
        require(manifest['tables']['ports.jsonl'] == base['tables']['ports.jsonl'], 'Port references changed')
        evidence = json.loads(candidate.read('panama-evidence.jsonl'))
        require(evidence['path'] == path and evidence['connectors'] == links, 'Source path audit mismatch')
        report = copy.deepcopy(prior)
        report.pop('unchangedGeometryTables', None)
        report.update(graphVersion=manifest['graphVersion'], artifactSha256=sha(artifact), artifactBytes=artifact.stat().st_size,
                      geometricCheck='PASS', regionalControlCheck='PASS', panamaControlCheck='PASS', panamaControls=controls,
                      panamaEvidenceSha256=manifest['tables']['panama-evidence.jsonl'],
                      checkedReciprocalPairs=prior['checkedReciprocalPairs'] + pairs, reciprocalGeometryVerified=True,
                      baselineArtifactSha256=base_hash, baselineValidationSha256=sha(base_validation),
                      inheritedTables=base['tables'], addedDirectedEdges=len(added_edges), addedNodes=len(added_nodes),
                      canalLengthM=path['lengthM'], canalSourceNodeCount=len(path['points']), oceanConnectors=len(links),
                      sourceGeometryConflicts=0, distanceMismatches=0, unchangedPorts=True,
                      strongComponents=int(count), allAddedNodesResearchPolicyReachable=True,
                      rejectedCanalWays=path['rejectedWays'], validatorSha256=sha(Path(__file__)),
                      geometryPolicySha256=sha(Path(__file__).with_name('panama_geometry.py')),
                      seconds=time.perf_counter() - started, validationGate='PANAMA_GEOMETRIC_RESEARCH_PASS_RUNTIME_PENDING',
                      limitations=prior['limitations'] + evidence['limitations'])
        report['counts'] = dict(prior.get('counts', {}), directedEdges=manifest['report']['directedEdges'], panamaAddedDirectedEdges=len(added_edges))
    output.write_bytes(encode(report) + b'\n')
    print(json.dumps({k: report[k] for k in ['graphVersion','geometricCheck','panamaControlCheck','addedNodes','addedDirectedEdges','canalLengthM','oceanConnectors','seconds']}), flush=True)

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('artifact', 'base', 'base-validation', 'source-receipt', 'land-receipt', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    a = parser.parse_args()
    validate(a.artifact, a.base, a.base_validation, a.source_receipt, a.land_receipt, a.output)
