"""Bind synthetic contracts to a preserved real ocean-node experiment; no terminal claim."""
import argparse
import copy
import json
from pathlib import Path


def example():
    scenario = json.loads(Path('docs/maritime-routing/phase6-example.json').read_bytes())
    real = json.loads(Path('data/maritime-routing/phase4-evidence-v1-20260922/runtime-report.json').read_bytes())
    route = copy.deepcopy(next(c['request'] for c in real['cases'] if c['basin'] == 'ATLANTIC'))
    route['search']['maximumRuntimeSeconds'] = 30
    conservative = copy.deepcopy(route)
    conservative['vessel']['calmSpeedKnots'] = 14
    alternatives = [dict(id=a['id'], routeId='conservative' if a['id'] == 'protected' else 'base',
                         carriageLegId='ocean', mappingEvidence='Synthetic contractual ocean leg mapped to actual ocean graph nodes for engineering only; no terminal association',
                         nonSailingHours=2, contract=a['contract']) for a in scenario['alternatives']]
    distribution = copy.deepcopy(scenario['distribution'])
    distribution['version'] = 'SYNTHETIC-PHASE7-JOINT-001'
    distribution['applicability'] = 'Real research ocean traversal with synthetic commercial assumptions and extra durations; no port voyage or calibrated probabilities'
    for row in distribution['scenarios']:
        for aid, outcome in row['outcomes'].items():
            duration = outcome.pop('durationHours')
            outcome['additionalHours'] = duration - (52 if aid == 'protected' else 48)
    simulation = {k: scenario[k] for k in ['expectedModelVersion', 'acknowledgeUncalibratedProbabilities',
                                          'seed', 'samples', 'tailAlpha', 'confidenceLevel', 'objectives']}
    simulation['distribution'] = distribution
    return dict(expectedPlanVersion='INTEGRATED_RESEARCH_PLAN_1', acknowledgeDeclaredEventMapping=True,
                routes=[dict(id='base', request=route), dict(id='conservative', request=conservative)],
                alternatives=alternatives, simulation=simulation)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    with args.output.open('x', encoding='utf-8') as stream:
        json.dump(example(), stream, indent=2, allow_nan=False)
        stream.write('\n')
