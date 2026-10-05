"""Coherent O/D-conditioned AIS preferences, isolated from operational routing."""
from collections import Counter
from dataclasses import asdict
import hashlib
import json
from pathlib import Path

from shapely import LineString, Point
from shapely.ops import transform

from build_graph import encode, geodesic, verify_receipt
from phase3_sources import chart_bounds
from regional_corridor_model import CorridorConfig, DirectedCorridorObjective, physical_context
from regional_channel_objective import lexicographic_route
from regional_experiment import sha
from transit_cohort import TransitConfig, classify, select
from validate_ais import densify
from validate_regional_ais import FROZEN_CONFIG, regional_observations, regional_tracks

PURPOSE = 'OD_COHERENT_AIS_RESEARCH_NOT_PERMISSION'
CONFIG = dict(endpointRadiusM=1000, minimumProjectedTransitM=1000, vesselFolds=5,
              corridor=asdict(CorridorConfig()))
DEVELOPMENT_DATES = tuple('2024-%02d-01' % m for m in range(3, 9))


def group_for(vessel):
    return int(hashlib.sha256(vessel.encode()).hexdigest(), 16) % CONFIG['vesselFolds']


def write_new(path, record):
    with Path(path).open('xb') as stream:
        stream.write(encode(record) + b'\n')


def extract(artifact, chart_receipt, receipts, output):
    """One bounded raw-data pass per date; cache preserves rows and full census."""
    if output.exists():
        raise ValueError('Immutable extraction already exists')
    manifest, chart, domain, _, _ = physical_context(artifact, chart_receipt)
    bbox, _ = chart_bounds(chart_receipt)
    dates, datasets = set(), []
    for receipt in receipts:
        path, source = verify_receipt(receipt)
        date = path.name.removeprefix('ais-').removesuffix('.csv.zst')
        if date not in DEVELOPMENT_DATES or date in dates:
            raise ValueError('Only distinct declared development dates may be fitted')
        expected = 'https://noaaocm.blob.core.windows.net/ais/csv2/csv2024/ais-'+date+'.csv.zst'
        if source['url'] != expected:
            raise ValueError('Unexpected development source URL')
        dates.add(date)
        records, counts = regional_observations(path, FROZEN_CONFIG, bbox)
        selected, census, cohort_counts = select(records, FROZEN_CONFIG, bbox, source['sha256'])
        counts.update(cohort_counts)
        tracks = []
        for vessel in sorted(records):
            for track in regional_tracks(records[vessel], FROZEN_CONFIG, bbox, Counter()):
                key = hashlib.sha256((source['sha256']+':'+vessel+':'+str(track[0][0])).encode()).hexdigest()
                eligible = classify(track)['status'] == 'TRANSIT_COHORT_ELIGIBLE'
                clear = domain.allows(densify([r[1] for r in track], domain.config.geodesic_step_m/4))
                tracks.append(dict(trackKey=key, vesselGroup=hashlib.sha256(vessel.encode()).hexdigest(),
                    fold=group_for(vessel), observations=track,
                    trainingStatus='USED' if eligible and clear else 'OUTSIDE_COHORT' if not eligible else 'CHART_WATER_CONFLICT'))
        if {t['trackKey'] for t in tracks} != {c['trackKey'] for c in census}:
            raise ValueError('Extraction lost original candidate tracks')
        datasets.append(dict(date=date, source=source, counts=dict(counts), candidateCensus=census,
                             selectedKeys=[k for k, _ in selected], tracks=tracks))
        print(json.dumps(dict(stage='od_extract', date=date, tracks=len(tracks),
                              statuses=dict(Counter(t['trainingStatus'] for t in tracks)))), flush=True)
    if dates != set(DEVELOPMENT_DATES):
        raise ValueError('All six declared development dates required')
    record = dict(schemaVersion=1, purpose=PURPOSE, parameters=CONFIG,
        physicalArtifactSha256=sha(artifact), chartSourceSha256=chart['sha256'], graphVersion=manifest['graphVersion'],
        cohortParameters=asdict(TransitConfig()), evaluationParameters=asdict(FROZEN_CONFIG),
        implementationSha256=sha(Path(__file__)), datasets=sorted(datasets, key=lambda d:d['date']))
    record['modelVersion'] = hashlib.sha256(encode(record)).hexdigest()
    write_new(output, record)
    print(json.dumps(dict(modelSha256=sha(output), modelVersion=record['modelVersion'])), flush=True)


def read_model(path, artifact, chart):
    if path.stat().st_size > 100_000_000:
        raise ValueError('Model exceeds allocation bound')
    model = json.loads(path.read_text())
    unsigned = dict(model)
    version = unsigned.pop('modelVersion')
    if (hashlib.sha256(encode(unsigned)).hexdigest() != version or model.get('purpose') != PURPOSE
            or model.get('parameters') != CONFIG or model.get('physicalArtifactSha256') != sha(artifact)
            or model.get('chartSourceSha256') != chart['sha256']
            or model.get('cohortParameters') != asdict(TransitConfig())
            or model.get('evaluationParameters') != asdict(FROZEN_CONFIG)
            or [d['date'] for d in model['datasets']] != list(DEVELOPMENT_DATES)):
        raise ValueError('OD model identity/configuration mismatch')
    keys = set()
    for dataset in model['datasets']:
        if dataset['source']['url'] != 'https://noaaocm.blob.core.windows.net/ais/csv2/csv2024/ais-'+dataset['date']+'.csv.zst':
            raise ValueError('Training source outside declared dates')
        if len(dataset['tracks']) > 10000:
            raise ValueError('Training track allocation exceeded')
        census = {c['trackKey']:c for c in dataset['candidateCensus']}
        if len(census) != len(dataset['tracks']):
            raise ValueError('Model census mismatch')
        for track in dataset['tracks']:
            key, obs = track['trackKey'], track['observations']
            if key in keys or key not in census or not 3 <= len(obs) <= FROZEN_CONFIG.max_track_points:
                raise ValueError('Invalid/duplicate model track')
            keys.add(key)
            if track['fold'] != int(track['vesselGroup'], 16) % CONFIG['vesselFolds']:
                raise ValueError('Inconsistent vessel grouping')
            classification = classify(obs)
            if any(classification[k] != census[key][k] for k in classification):
                raise ValueError('Model observations changed from census')
            if track['trainingStatus'] not in ('USED', 'OUTSIDE_COHORT', 'CHART_WATER_CONFLICT'):
                raise ValueError('Unknown training status')
            if track['trainingStatus'] == 'USED' and classification['status'] != 'TRANSIT_COHORT_ELIGIBLE':
                raise ValueError('Out-of-scope training track')
    return model


class OdObjective:
    """Prediction sees endpoints only. Group exclusion precedes any template ranking."""
    def __init__(self, domain, edges, node_count, tracks):
        self.domain, self.edges, self.node_count = domain, edges, node_count
        self.templates, segments = [], []
        config = CorridorConfig()
        for track in sorted(tracks, key=lambda t:t['trackKey']):
            if track['trainingStatus'] != 'USED':
                continue
            obs = track['observations']
            if not domain.allows(densify([r[1] for r in obs], domain.config.geodesic_step_m/4)):
                raise ValueError('Training track no longer clears physical domain')
            curve = transform(domain.forward, LineString([r[1] for r in obs]))
            position, members = 0., []
            for a, b in zip(obs, obs[1:]):
                length = Point(domain.forward(*a[1])).distance(Point(domain.forward(*b[1])))
                distance, _ = geodesic(a[1], b[1], domain.config.geodesic_step_m)
                if distance >= config.minimum_movement_m:
                    members.append((len(segments), position, position+length))
                    segments.append(dict(trackKey=track['trackKey'], points=[a[1], b[1]],
                                         sourceRows=[a[2], b[2]], timestamps=[a[0], b[0]], distanceM=distance))
                position += length
            self.templates.append((track['trackKey'], curve, members, track['fold'], track['vesselGroup']))
        if len(segments) > config.max_segments:
            raise ValueError('OD segment budget exceeded')
        self.base = DirectedCorridorObjective(domain, edges, node_count, segments, config)
        self.active_ids, self.edge_support, self.last_selection = set(), {}, None
        self.excluded_fold = None

    def choose(self, endpoints):
        a, b = [Point(self.domain.forward(*p)) for p in endpoints]
        candidates = []
        for key, curve, members, fold, vessel_group in self.templates:
            if self.excluded_fold is not None and fold == self.excluded_fold:
                continue
            start, end = curve.project(a), curve.project(b)
            da, db = a.distance(curve.interpolate(start)), b.distance(curve.interpolate(end))
            if max(da, db) <= CONFIG['endpointRadiusM'] and end-start >= CONFIG['minimumProjectedTransitM']:
                candidates.append((da+db, key, start, end, members, fold, vessel_group))
        if not candidates:
            self.last_selection = dict(status='NO_MATCH_DISTANCE_OBJECTIVE', excludedFold=self.excluded_fold)
            return set()
        score, key, start, end, members, fold, vessel_group = min(candidates, key=lambda c:c[:2])
        self.last_selection = dict(status='COHERENT_TRACK', trackKey=key, endpointMismatchM=score,
            startAlongM=start, endAlongM=end, trainingFold=fold, vesselGroup=vessel_group, excludedFold=self.excluded_fold)
        return {i for i, lo, hi in members if hi > start and lo < end}

    def support(self, points):
        return [i for i in self.base.support(points) if i in self.active_ids]

    def route(self, origins, destinations, endpoints, points):
        self.active_ids = self.choose(endpoints)
        adjacency = [[] for _ in range(self.node_count)]
        self.edge_support = {pair:[i for i in ids if i in self.active_ids] for pair, ids in self.base.edge_support.items()}
        for edge in self.edges:
            pair = edge['fromNode'], edge['toNode']
            distance = edge['distanceM']
            adjacency[pair[0]].append((pair[1], distance, 0 if self.edge_support[pair] else distance))
        step = self.domain.config.geodesic_step_m/4
        start = [(n,d,0 if self.support(geodesic(endpoints[0],points[n],step)[1]) else d) for n,d in origins]
        end = [(n,d,0 if self.support(geodesic(points[n],endpoints[1],step)[1]) else d) for n,d in destinations]
        return lexicographic_route(adjacency,start,end,balanced=True)


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('artifact', 'chart-receipt', 'output'):
        parser.add_argument('--'+name, type=Path, required=True)
    parser.add_argument('--training-receipt', type=Path, action='append', required=True)
    args = parser.parse_args()
    extract(args.artifact,args.chart_receipt,args.training_receipt,args.output)
