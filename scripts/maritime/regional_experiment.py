"""Immutable prospective registration bindings for the April regional experiment."""
from dataclasses import asdict
from datetime import datetime
import hashlib
from importlib.metadata import version
from pathlib import Path
import platform

APRIL_ID = 'noaa-ais-regional-april-holdout'
APRIL_URL = 'https://noaaocm.blob.core.windows.net/ais/csv2/csv2024/ais-2024-04-01.csv.zst'
REGISTERED_OBJECTIVES = ['DISTANCE_V1', 'CHART_CHANNEL_BALANCED_V1']
IMPLEMENTATION_FILES = ('regional_experiment.py', 'validate_regional_ais.py', 'regional_channel_objective.py',
    'validate_ais.py', 'validate_graph.py', 'build_enc_pilot.py', 'build_graph.py', 'enc_snapshot.py',
    'phase3_sources.py', 'acquire_regional_ais.py', 'acquire.py')


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def implementation():
    return {name: sha(Path(__file__).with_name(name)) for name in IMPLEMENTATION_FILES}


def dependencies():
    return dict(python=platform.python_version(), **{name: version(name) for name in ('numpy', 'scipy', 'shapely', 'pyproj', 'h3', 'zstandard')})


def bindings(artifact, chart_sha, config, protocol):
    return dict(artifactSha256=sha(artifact), chartSourceSha256=chart_sha,
                configSha256=sha(config), protocolSha256=sha(protocol),
                implementation=implementation(), dependencies=dependencies())


def verify_registration(record, actual_bindings, ais, objective, config, criteria):
    if type(record.get('schemaVersion')) is not int or record['schemaVersion'] != 1 or record.get('purpose') != 'FROZEN_REGIONAL_PHYSICAL_EXPERIMENT':
        raise ValueError('Unsupported prospective registration')
    if record.get('aisSource') != dict(id=APRIL_ID, url=APRIL_URL) or ais.get('id') != APRIL_ID or ais.get('url') != APRIL_URL:
        raise ValueError('Unregistered prospective AIS source')
    if record.get('objectives') != REGISTERED_OBJECTIVES or objective not in REGISTERED_OBJECTIVES:
        raise ValueError('Unregistered prospective objective')
    if record.get('parameters') != asdict(config) or record.get('criteria') != criteria:
        raise ValueError('Frozen cohort or acceptance criteria changed')
    if any(record.get(key) != value for key, value in actual_bindings.items()):
        raise ValueError('Frozen artifact, chart, protocol, config, code or environment changed')
    try:
        frozen = datetime.fromisoformat(record['createdAt'])
        acquired = datetime.fromisoformat(ais['acquisitionStartedAt'])
        valid = frozen.tzinfo is not None and acquired.tzinfo is not None and frozen < acquired
    except (KeyError, TypeError, ValueError):
        valid = False
    if not valid:
        raise ValueError('Registration must precede holdout acquisition')
