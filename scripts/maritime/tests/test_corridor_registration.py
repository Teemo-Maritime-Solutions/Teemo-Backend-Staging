"""Synthetic source, chronology and registration failure checks."""
from copy import deepcopy
from dataclasses import asdict
from pathlib import Path
import sys
import unittest
from urllib.request import Request

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from acquire_corridor_holdout import FixedRedirects
from corridor_experiment import HOLDOUTS, OBJECTIVES, verify
from validate_regional_ais import FROZEN_CONFIG, CRITERIA


class CorridorRegistrationTests(unittest.TestCase):
    def setUp(self):
        self.bindings = {key:'SYNTHETIC' for key in ('artifactSha256','chartSourceSha256','modelSha256','protocolSha256','implementation','dependencies')}
        self.model = dict(modelVersion='SYNTHETIC_MODEL',trainingSource=dict(sha256='TRAINING'))
        self.record = dict(schemaVersion=1,purpose='FROZEN_DIRECTED_AIS_PHYSICAL_EXPERIMENT',
            holdouts=HOLDOUTS,objectives=OBJECTIVES,parameters=asdict(FROZEN_CONFIG),criteria=CRITERIA,
            modelVersion='SYNTHETIC_MODEL',createdAt='2026-09-15T01:00:00+00:00',**self.bindings)

    def source(self,month):
        return dict(HOLDOUTS[month],sha256='HELDOUT',acquisitionStartedAt='2026-09-15T02:00:00+00:00')

    def test_both_dates_and_comparators_accepted_without_pooling(self):
        for month in HOLDOUTS:
            for objective in OBJECTIVES:
                verify(self.record,self.bindings,self.source(month),objective,self.model)

    def test_data_code_model_and_criteria_cannot_be_replaced(self):
        for key in self.bindings:
            with self.assertRaises(ValueError):
                verify(dict(self.record,**{key:'CHANGED'}),self.bindings,self.source('may'),OBJECTIVES[1],self.model)
        changed=deepcopy(self.record);changed['criteria']['maxDiscreteFrechetM']=1500
        with self.assertRaises(ValueError): verify(changed,self.bindings,self.source('may'),OBJECTIVES[1],self.model)

    def test_training_leakage_source_swap_and_chronology_rejected(self):
        for changes in (dict(sha256='TRAINING'),dict(url='https://example.com/fake'),
                        dict(id='noaa-ais-regional-march-holdout'),
                        dict(acquisitionStartedAt='2026-09-15T00:00:00+00:00'),dict(acquisitionStartedAt='2026-09-15T02:00:00')):
            with self.assertRaises(ValueError): verify(self.record,self.bindings,dict(self.source('may'),**changes),OBJECTIVES[1],self.model)
        with self.assertRaises(ValueError): verify(self.record,self.bindings,self.source('may'),'PICK_BEST_PER_CASE',self.model)

    def test_redirect_cannot_change_the_prespecified_source(self):
        request=Request(HOLDOUTS['may']['url'])
        for target in (HOLDOUTS['june']['url'],'https://example.com/data','http://noaaocm.blob.core.windows.net/data'):
            with self.assertRaises(ValueError): FixedRedirects().redirect_request(request,None,302,'Found',{},target)


if __name__ == '__main__': unittest.main()
