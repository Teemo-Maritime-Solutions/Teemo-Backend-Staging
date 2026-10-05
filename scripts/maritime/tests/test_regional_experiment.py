"""Synthetic preregistration binding tests, not real timestamp attestation."""
from copy import deepcopy
from dataclasses import asdict
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from regional_experiment import verify_registration, APRIL_ID, APRIL_URL, REGISTERED_OBJECTIVES
from validate_regional_ais import FROZEN_CONFIG, CRITERIA


class RegistrationTests(unittest.TestCase):
    def setUp(self):
        self.bindings = {key: 'SYNTHETIC' for key in ('artifactSha256', 'chartSourceSha256', 'configSha256', 'protocolSha256', 'implementation', 'dependencies')}
        self.ais = dict(id=APRIL_ID, url=APRIL_URL, acquisitionStartedAt='2026-09-15T02:00:00+00:00')
        self.record = dict(schemaVersion=1, purpose='FROZEN_REGIONAL_PHYSICAL_EXPERIMENT',
            createdAt='2026-09-15T01:00:00+00:00', aisSource=dict(id=APRIL_ID, url=APRIL_URL),
            objectives=REGISTERED_OBJECTIVES, parameters=asdict(FROZEN_CONFIG), criteria=CRITERIA, **self.bindings)

    def check(self, record=None, ais=None, objective='CHART_CHANNEL_BALANCED_V1'):
        verify_registration(self.record if record is None else record, self.bindings, self.ais if ais is None else ais, objective, FROZEN_CONFIG, CRITERIA)

    def test_exact_bindings_and_both_registered_comparators_accepted(self):
        self.check()
        self.check(objective='DISTANCE_V1')

    def test_code_data_environment_and_targets_cannot_change_after_freeze(self):
        for key in self.bindings:
            with self.assertRaises(ValueError): self.check(record=dict(self.record, **{key: 'CHANGED'}))
        changed = deepcopy(self.record)
        changed['criteria']['maxDiscreteFrechetM'] = 2000
        with self.assertRaises(ValueError): self.check(record=changed)
        changed = deepcopy(self.record)
        changed['parameters']['max_cases'] = 10
        with self.assertRaises(ValueError): self.check(record=changed)

    def test_source_and_objective_cannot_be_selected_after_outcome(self):
        for changes in (dict(id='noaa-ais-regional-march-holdout'), dict(url='https://example.com/2024-04-01.csv.zst')):
            with self.assertRaises(ValueError): self.check(ais=dict(self.ais, **changes))
        with self.assertRaises(ValueError): self.check(objective='CHART_CHANNEL_LEXICOGRAPHIC_V1')
        with self.assertRaises(ValueError): self.check(record=dict(self.record, objectives=['DISTANCE_V1']))

    def test_registration_must_precede_acquisition_with_aware_timestamps(self):
        for stamp in ('2026-09-15T02:00:00+00:00', '2026-09-15T03:00:00+00:00', '2026-09-15T01:00:00', None, 'invalid'):
            with self.assertRaises(ValueError): self.check(record=dict(self.record, createdAt=stamp))
        missing = dict(self.ais)
        del missing['acquisitionStartedAt']
        with self.assertRaises(ValueError): self.check(ais=missing)


if __name__ == '__main__': unittest.main()
