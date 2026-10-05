"""Source-directed AIS evidence for offline route choice, never navigational clearance."""
from collections import Counter
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
import hashlib
import json
import math
from pathlib import Path
import zipfile

import numpy as np
from shapely import LineString, STRtree
from shapely.ops import transform

from build_enc_pilot import ChartDomain, PilotConfig, load_snapshot
from build_graph import encode, geodesic, verify_receipt
from phase3_sources import chart_bounds
from regional_channel_objective import lexicographic_route
from regional_experiment import sha
from validate_ais import densify
from validate_regional_ais import FROZEN_CONFIG, regional_observations, regional_tracks

TRAIN_ID = 'noaa-ais-regional-march-holdout'
TRAIN_URL = 'https://noaaocm.blob.core.windows.net/ais/csv2/csv2024/ais-2024-03-01.csv.zst'


@dataclass(frozen=True)
class CorridorConfig:
    radius_m: float = 150
    minimum_movement_m: float = 25
    minimum_direction_cosine: float = .5
    max_tracks: int = 10000
    max_segments: int = 100000

    def validate(self):
        for name, lo, hi in (('radius_m', 25, 500), ('minimum_movement_m', 1, 100), ('minimum_direction_cosine', 0, 1)):
            value = getattr(self, name)
            if type(value) not in (int, float) or not math.isfinite(value) or not lo <= value <= hi:
                raise ValueError('Invalid bounded corridor parameter: ' + name)
        for name, bound in (('max_tracks', 10000), ('max_segments', 100000)):
            value = getattr(self, name)
            if type(value) is not int or not 1 <= value <= bound:
                raise ValueError('Invalid corridor allocation bound: ' + name)


def physical_context(artifact, chart_receipt):
    source, chart = load_snapshot(chart_receipt)
    with zipfile.ZipFile(artifact) as archive:
        if len(archive.namelist()) != len(set(archive.namelist())) or any(i.file_size > 200000000 for i in archive.infolist()):
            raise ValueError('Duplicate/oversized graph member')
        manifest = json.loads(archive.read('manifest.json'))
        unsigned = dict(manifest)
        version = unsigned.pop('graphVersion')
        if (hashlib.sha256(encode(unsigned)).hexdigest() != version or manifest['sources'] != [chart]
            or manifest.get('validationProfile') != 'NOAA_ENC_REGIONAL_V2'
            or PilotConfig(**manifest['parameters']).profile != manifest['validationProfile']):
            raise ValueError('Invalid V2 physical graph/source identity')
        tables = {}
        for name, digest in manifest['tables'].items():
            data = archive.read(name)
            if hashlib.sha256(data).hexdigest() != digest:
                raise ValueError('Graph table checksum mismatch')
            if name in ('nodes.jsonl', 'edges.jsonl'):
                tables[name] = [json.loads(line) for line in data.splitlines()]
    domain = ChartDomain(source, PilotConfig(**manifest['parameters']))
    return manifest, chart, domain, tables['nodes.jsonl'], tables['edges.jsonl']


def training_segments(records, domain, bbox, source_sha, config):
    config.validate()
    counts, segments, tracks = Counter(), [], []
    for vessel in sorted(records):
        for observations in regional_tracks(records[vessel], FROZEN_CONFIG, bbox, counts):
            if len(tracks) >= config.max_tracks:
                raise ValueError('Training track resource bound exceeded')
            key = hashlib.sha256((source_sha + ':' + vessel + ':' + str(observations[0][0])).encode()).hexdigest()
            points = [o[1] for o in observations]
            accepted = domain.allows(densify(points, domain.config.geodesic_step_m / 4))
            tracks.append(dict(trackKey=key, sourceRows=[o[2] for o in observations],
                status='USED' if accepted else 'CHART_WATER_CONFLICT', observationCount=len(points)))
            counts['eligibleTracks'] += 1
            counts['usedTracks' if accepted else 'chartConflictTracks'] += 1
            if not accepted:
                continue
            for a, b in zip(observations, observations[1:]):
                distance, _ = geodesic(a[1], b[1], domain.config.geodesic_step_m)
                if distance < config.minimum_movement_m:
                    counts['stationaryOrShortPairs'] += 1
                    continue
                if len(segments) >= config.max_segments:
                    raise ValueError('Training segment resource bound exceeded')
                segments.append(dict(trackKey=key, sourceRows=[a[2], b[2]], points=[a[1], b[1]],
                                     timestamps=[a[0], b[0]], distanceM=distance))
    if not segments:
        raise ValueError('No valid directed training segments')
    return segments, tracks, dict(counts)


def fit(artifact, chart_receipt, training_receipt, output, config=CorridorConfig()):
    if output.exists():
        raise ValueError('Immutable training model already exists')
    manifest, chart, domain, _, _ = physical_context(artifact, chart_receipt)
    path, ais = verify_receipt(training_receipt)
    if ais['id'] != TRAIN_ID or ais['url'] != TRAIN_URL:
        raise ValueError('Only registered March training allowed; no April/future holdout fitting')
    bbox, _ = chart_bounds(chart_receipt)
    records, counts = regional_observations(path, FROZEN_CONFIG, bbox)
    segments, tracks, quality = training_segments(records, domain, bbox, ais['sha256'], config)
    payloads = {'segments.jsonl': b''.join(encode(s)+b'\n' for s in segments),
                'tracks.jsonl': b''.join(encode(t)+b'\n' for t in tracks)}
    model = dict(schemaVersion=1, purpose='DIRECTED_AIS_RESEARCH_PREFERENCE_NOT_PERMISSION',
        physicalArtifactSha256=sha(artifact), graphVersion=manifest['graphVersion'], chartSourceSha256=chart['sha256'],
        trainingSource=ais, parameters=asdict(config), cohortParameters=asdict(FROZEN_CONFIG),
        counts=dict(counts), quality=quality, directedSegments=len(segments),
        tables={name: hashlib.sha256(raw).hexdigest() for name, raw in payloads.items()},
        implementationSha256=sha(Path(__file__)),
        limitations=['Directed buffers are engineering association tolerances, not measured positional accuracy or legal lanes',
                     'AIS excluded tracks retained in quality accounting; no observed or graph point relocated',
                     'Historical training preference only; current chart restrictions stay unresolved'])
    model['modelVersion'] = hashlib.sha256(encode(model)).hexdigest()
    payloads['manifest.json'] = encode(model)
    with zipfile.ZipFile(output, 'x') as archive:
        for name, raw in sorted(payloads.items()):
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, raw)
    print(json.dumps(dict(modelVersion=model['modelVersion'], modelSha256=sha(output), segments=len(segments), quality=quality)), flush=True)


def read_model(path, artifact, chart, domain):
    with zipfile.ZipFile(path) as archive:
        if len(archive.namelist()) != len(set(archive.namelist())) or any(i.file_size > 100000000 for i in archive.infolist()):
            raise ValueError('Invalid/oversized training archive')
        manifest = json.loads(archive.read('manifest.json'))
        unsigned = dict(manifest)
        version = unsigned.pop('modelVersion')
        if (hashlib.sha256(encode(unsigned)).hexdigest() != version
            or manifest.get('purpose') != 'DIRECTED_AIS_RESEARCH_PREFERENCE_NOT_PERMISSION'
            or manifest['physicalArtifactSha256'] != sha(artifact) or manifest['chartSourceSha256'] != chart['sha256']
            or manifest['trainingSource']['id'] != TRAIN_ID or manifest['trainingSource']['url'] != TRAIN_URL):
            raise ValueError('Training model identity/source mismatch')
        tables = {}
        for name in ('segments.jsonl', 'tracks.jsonl'):
            raw = archive.read(name)
            if hashlib.sha256(raw).hexdigest() != manifest['tables'][name]:
                raise ValueError('Training model table changed')
            tables[name] = [json.loads(line) for line in raw.splitlines()]
    config = CorridorConfig(**manifest['parameters'])
    config.validate()
    segments, tracks = tables['segments.jsonl'], tables['tracks.jsonl']
    track_rows = {t['trackKey']: t for t in tracks}
    if len(track_rows) != len(tracks) or len(tracks) > config.max_tracks or len(segments) != manifest['directedSegments'] or not 0 < len(segments) <= config.max_segments:
        raise ValueError('Training allocation/count mismatch')
    for s in segments:
        t = track_rows.get(s['trackKey'])
        if t is None or t['status'] != 'USED' or len(s['sourceRows']) != 2 or not all(r in t['sourceRows'] for r in s['sourceRows']):
            raise ValueError('Missing training track provenance')
        if len(s['timestamps']) != 2 or not s['timestamps'][0] < s['timestamps'][1] or len(s['points']) != 2:
            raise ValueError('Invalid directed observation pair')
        distance, curve = geodesic(*s['points'], domain.config.geodesic_step_m / 4)
        if abs(distance - s['distanceM']) > .01 or distance < config.minimum_movement_m or not domain.allows(curve):
            raise ValueError('Training distance/chart conflict')
    return manifest, segments, config


class DirectedCorridorObjective:
    def __init__(self, domain, edges, node_count, segments, config):
        config.validate()
        self.domain, self.config, self.segments = domain, config, segments
        curves, directions = [], []
        for s in segments:
            curve = transform(domain.forward, LineString(geodesic(*s['points'], domain.config.geodesic_step_m / 4)[1]))
            vector = np.array(curve.coords[-1]) - np.array(curve.coords[0])
            if np.linalg.norm(vector) <= 0:
                raise ValueError('Stationary training segment')
            curves.append(curve.buffer(config.radius_m))
            directions.append(vector / np.linalg.norm(vector))
        self.buffers, self.directions = curves, directions
        self.tree = STRtree(curves)
        self.adjacency = [[] for _ in range(node_count)]
        self.edge_support, self.directional_counts = {}, Counter()
        for e in edges:
            ids = self.support(e['geometry'])
            self.edge_support[(e['fromNode'], e['toNode'])] = ids
            self.directional_counts['supportedEdges' if ids else 'unsupportedEdges'] += 1
            self.adjacency[e['fromNode']].append((e['toNode'], e['distanceM'], 0 if ids else e['distanceM']))

    def support(self, points):
        curve = transform(self.domain.forward, LineString(points))
        vector = np.array(curve.coords[-1]) - np.array(curve.coords[0])
        norm = np.linalg.norm(vector)
        if norm <= 0:
            return []
        direction = vector / norm
        return [int(i) for i in sorted(self.tree.query(curve, predicate='intersects'))
                if np.dot(direction, self.directions[i]) >= self.config.minimum_direction_cosine and self.buffers[i].covers(curve)]

    def route(self, origins, destinations, endpoints, points):
        start = [(n, d, 0 if self.support(geodesic(endpoints[0], points[n], self.domain.config.geodesic_step_m / 4)[1]) else d) for n, d in origins]
        end = [(n, d, 0 if self.support(geodesic(points[n], endpoints[1], self.domain.config.geodesic_step_m / 4)[1]) else d) for n, d in destinations]
        return lexicographic_route(self.adjacency, start, end, balanced=True)
