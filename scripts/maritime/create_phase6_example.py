"""Generate an explicitly synthetic phase-6 example; no market estimates or observations."""
import argparse
import copy
import json
from pathlib import Path


def example():
    source = json.loads(Path('docs/maritime-routing/phase5-example-cif.json').read_bytes())
    alternatives = []
    for name, extra, damage in [('economy', 0, 0.10), ('protected', 500, 0.02), ('dominated', 200, 0.10)]:
        contract = copy.deepcopy(source)
        contract['saleContractReference'] = 'SYNTHETIC-SALE-SAME-CARGO-' + name
        contract['carriage']['contractReference'] = 'SYNTHETIC-CARRIAGE-' + name
        contract['lossScenarios'] = []
        for sid, fraction, expense in [('normal', 0, 0), ('delay', 0 if name == 'protected' else 0.01, 250), ('storm', damage, 1000)]:
            contract['lossScenarios'].append(dict(id=sid, legId='ocean', damageFraction=fraction,
                buyerAdditionalLoss=expense, sellerAdditionalLoss=0,
                evidence=dict(kind='USER_DECLARED_SCENARIO', reference='Synthetic conditional cargo/expense outcome: ' + name + '/' + sid)))
        if extra:
            contract['fees'].append(dict(id='declared-buyer-service', kind='OTHER_AGREED', amount=extra, agreedPayer='BUYER',
                evidence=dict(kind='USER_DECLARED_SCENARIO', reference='Synthetic buyer service charge; not an insurance policy')))
        alternatives.append(dict(id=name, contract=contract))
    scenarios = []
    for sid, probability, duration, protected_duration in [('normal', 0.90, 48, 52), ('delay', 0.08, 72, 64), ('storm', 0.02, 120, 88)]:
        scenarios.append(dict(id=sid, probability=probability, outcomes={a['id']: dict(lossScenarioId=sid,
            durationHours=protected_duration if a['id'] == 'protected' else duration) for a in alternatives}))
    return dict(expectedModelVersion='JOINT_DISCRETE_RESEARCH_1', acknowledgeUncalibratedProbabilities=True,
                seed=20260922, samples=20000, tailAlpha=0.95, confidenceLevel=0.95, alternatives=alternatives,
                distribution=dict(version='SYNTHETIC-JOINT-001',
                    evidenceReference='User-authorized research example; all probabilities and losses are illustrative',
                    dependenceDescription='The same hypothetical weather state jointly changes duration and damage in every alternative; rows are never independently combined',
                    applicability='One hypothetical shipment, same cargo and currency; no claim of route, port, forecast or policy validation',
                    scenarios=scenarios),
                objectives=[dict(dimension='BUYER_TOTAL_COST', statistic='MEAN'),
                            dict(dimension='BUYER_TOTAL_COST', statistic='CVAR'),
                            dict(dimension='DURATION_HOURS', statistic='MEAN')])


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    with args.output.open('x', encoding='utf-8') as stream:
        json.dump(example(), stream, indent=2, allow_nan=False)
        stream.write('\n')
