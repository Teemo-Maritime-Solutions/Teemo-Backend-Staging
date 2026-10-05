"""Prospective transit-only registration; all earlier experiments remain untouched."""
from dataclasses import asdict
from datetime import datetime
from pathlib import Path

from regional_experiment import dependencies, sha
from corridor_experiment import FILES as PREVIOUS_FILES
from transit_cohort import TransitConfig
from validate_regional_ais import FROZEN_CONFIG, CRITERIA

HOLDOUTS = {month: dict(id='noaa-ais-transit-'+month+'-holdout',
    url='https://noaaocm.blob.core.windows.net/ais/csv2/csv2024/ais-2024-'+number+'-01.csv.zst',
    filename='ais-2024-'+number+'-01.csv.zst') for month, number in (('july','07'),('august','08'))}
OBJECTIVES = ['DISTANCE_V1', 'DIRECTED_AIS_BALANCED_V1']
FILES = PREVIOUS_FILES + ('transit_cohort.py', 'transit_experiment.py',
    'validate_transit_ais.py', 'acquire_transit_holdout.py', 'freeze_transit_experiment.py')
COHORT = TransitConfig()
PURPOSE = 'FROZEN_TRANSIT_PHYSICAL_EXPERIMENT'


def code_hashes():
    return {name: sha(Path(__file__).with_name(name)) for name in FILES}


def bindings(artifact, chart, model, protocol):
    return dict(artifactSha256=sha(artifact), chartSourceSha256=chart['sha256'], modelSha256=sha(model),
                protocolSha256=sha(protocol), implementation=code_hashes(), dependencies=dependencies())


def verify(record, actual, source, objective, model_manifest):
    if (type(record.get('schemaVersion')) is not int or record['schemaVersion'] != 1
        or record.get('purpose') != PURPOSE
        or record.get('holdouts') != HOLDOUTS or record.get('objectives') != OBJECTIVES
        or objective not in OBJECTIVES or record.get('parameters') != asdict(FROZEN_CONFIG)
        or record.get('cohortParameters') != asdict(COHORT)
        or record.get('criteria') != CRITERIA or record.get('modelVersion') != model_manifest['modelVersion']
        or any(record.get(k) != v for k,v in actual.items())):
        raise ValueError('Frozen transit experiment changed')
    if not any(source['id'] == s['id'] and source['url'] == s['url'] for s in HOLDOUTS.values()):
        raise ValueError('Unregistered holdout source')
    if source.get('acquirerSha256') != actual['implementation']['acquire_transit_holdout.py']:
        raise ValueError('Unexpected acquisition implementation')
    if source['sha256'] == model_manifest['trainingSource']['sha256']:
        raise ValueError('Training/holdout leakage')
    try:
        created, acquired = [datetime.fromisoformat(s) for s in (record['createdAt'], source['acquisitionStartedAt'])]
        valid = created.tzinfo is not None and acquired.tzinfo is not None and created < acquired
    except (KeyError, TypeError, ValueError):
        valid = False
    if not valid:
        raise ValueError('Experiment must precede holdout acquisition')
