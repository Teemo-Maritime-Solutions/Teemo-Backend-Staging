"""Prospective research cohort for displacement-dominated origin/destination transits.

This is not a port-call detector, voyage-intent classifier or acceptance exception.
Do not apply it retrospectively to replace existing frozen evaluation results.
"""
from collections import Counter
from dataclasses import dataclass
import hashlib
import math

from build_graph import GEOD, point_ok
from validate_regional_ais import regional_tracks


@dataclass(frozen=True)
class TransitConfig:
    minimum_endpoint_displacement_m: float = 1000
    minimum_displacement_fraction: float = .5

    def validate(self):
        for name, limit in (('minimum_endpoint_displacement_m', 30000), ('minimum_displacement_fraction', 1)):
            value = getattr(self, name)
            if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value) or not 0 < value <= limit:
                raise ValueError('Invalid transit cohort parameter: '+name)


def classify(track, config=TransitConfig()):
    config.validate()
    if (len(track) < 3 or any(not point_ok(r[1]) for r in track)
            or any(b[0] <= a[0] for a, b in zip(track, track[1:]))):
        raise ValueError('Expected an original time-ordered candidate track')
    points = [r[1] for r in track]
    length = sum(GEOD.inv(*a, *b)[2] for a, b in zip(points, points[1:]))
    displacement = GEOD.inv(*points[0], *points[-1])[2]
    fraction = displacement / length if length else 0
    reasons = []
    if displacement < config.minimum_endpoint_displacement_m:
        reasons.append('BELOW_MINIMUM_ENDPOINT_DISPLACEMENT')
    if fraction < config.minimum_displacement_fraction:
        reasons.append('NOT_DISPLACEMENT_DOMINANT')
    return dict(status='TRANSIT_COHORT_ELIGIBLE' if not reasons else 'OUTSIDE_TRANSIT_COHORT',
                reasons=reasons, endpointDisplacementM=displacement, observedLengthM=length,
                displacementFraction=fraction, sourceRows=[r[2] for r in track],
                observationCount=len(track), origin=points[0], destination=points[-1])


def select(records, ais_config, bbox, source_sha, transit_config=TransitConfig()):
    """Classify before any graph/water/routing/metric outcome is available.

Every original eligible segment appears in census, including all out-of-scope
curves and transit candidates not selected by the existing deterministic hash.
"""
    transit_config.validate()
    ais_config.validate()
    if len(source_sha) != 64 or any(c not in '0123456789abcdef' for c in source_sha):
        raise ValueError('Expected a source SHA-256, not an unbound sample')
    counts, census, candidates = Counter(), [], []
    for vessel in sorted(records):
        for track in regional_tracks(records[vessel], ais_config, bbox, counts):
            key = hashlib.sha256((source_sha+':'+vessel+':'+str(track[0][0])).encode()).hexdigest()
            classification = classify(track, transit_config)
            census.append(dict(trackKey=key, **classification))
            counts[classification['status']] += 1
            if classification['status'] == 'TRANSIT_COHORT_ELIGIBLE':
                candidates.append((key, track))
    if len({c['trackKey'] for c in census}) != len(census):
        raise ValueError('Duplicate source track identity')
    selected = sorted(candidates)[:ais_config.max_cases]
    selected_keys = {key for key, _ in selected}
    for case in census:
        case['selectedForEvaluation'] = case['trackKey'] in selected_keys
    counts['originalEligibleSegments'] = len(census)
    counts['selectedTransitSegments'] = len(selected)
    return selected, sorted(census, key=lambda c: c['trackKey']), dict(counts)
