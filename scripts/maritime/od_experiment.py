"""Immutable registration bindings for the separately versioned O/D experiment."""
from dataclasses import asdict
from datetime import datetime
from pathlib import Path

from od_corridor_model import CONFIG
from regional_experiment import dependencies, sha
from transit_experiment import FILES as PREVIOUS_FILES
from transit_cohort import TransitConfig
from validate_regional_ais import FROZEN_CONFIG, CRITERIA

HOLDOUTS = {month:dict(id='noaa-ais-od-'+month+'-holdout',
    url='https://noaaocm.blob.core.windows.net/ais/csv2/csv2024/ais-2024-'+number+'-01.csv.zst',
    filename='ais-2024-'+number+'-01.csv.zst') for month,number in (('september','09'),('october','10'))}
OBJECTIVES = ['DISTANCE_V1','OD_COHERENT_V1']
COHORT = TransitConfig()
PURPOSE = 'FROZEN_OD_COHERENT_TRANSIT_EXPERIMENT'
FILES = PREVIOUS_FILES + ('od_corridor_model.py','evaluate_od_corridors.py','od_experiment.py',
                          'acquire_od_holdout.py','freeze_od_experiment.py')


def code_hashes():
    return {name:sha(Path(__file__).with_name(name)) for name in FILES}


def bindings(artifact,chart,model,protocol):
    return dict(artifactSha256=sha(artifact),chartSourceSha256=chart['sha256'],modelSha256=sha(model),
                protocolSha256=sha(protocol),implementation=code_hashes(),dependencies=dependencies())


def verify(record,actual,source,model):
    if (type(record.get('schemaVersion')) is not int or record['schemaVersion'] != 1
            or record.get('purpose') != PURPOSE or record.get('holdouts') != HOLDOUTS
            or record.get('objectives') != OBJECTIVES or record.get('parameters') != asdict(FROZEN_CONFIG)
            or record.get('cohortParameters') != asdict(COHORT) or record.get('criteria') != CRITERIA
            or record.get('modelParameters') != CONFIG or record.get('modelVersion') != model['modelVersion']
            or record.get('trainingSourceSha256s') != [d['source']['sha256'] for d in model['datasets']]
            or any(record.get(k) != v for k,v in actual.items())):
        raise ValueError('Frozen OD experiment changed')
    if not any(source['id']==s['id'] and source['url']==s['url'] for s in HOLDOUTS.values()):
        raise ValueError('Unregistered OD holdout source')
    if source.get('acquirerSha256') != actual['implementation']['acquire_od_holdout.py']:
        raise ValueError('Unexpected OD acquirer')
    if source['sha256'] in record['trainingSourceSha256s']:
        raise ValueError('Training/holdout leakage')
    try:
        created,acquired=[datetime.fromisoformat(s) for s in (record['createdAt'],source['acquisitionStartedAt'])]
        valid=created.tzinfo is not None and acquired.tzinfo is not None and created<acquired
    except (KeyError,TypeError,ValueError):
        valid=False
    if not valid:
        raise ValueError('Registration must precede acquisition')
