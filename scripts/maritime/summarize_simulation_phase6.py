"""Close the user-approved declared-scenario gate, not an empirical calibration gate."""
import argparse
from datetime import datetime, timezone
from decimal import Decimal
import hashlib
import json
import math
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET


def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def read(path, decimal=False):
    return json.loads(Path(path).read_bytes(), parse_float=Decimal if decimal else float)


def close(runtime, output):
    if output.exists():
        raise ValueError('Use a new evidence output directory')
    bindings = read(runtime / 'bindings.json')
    response = read(runtime / 'example-response.json', decimal=True)
    performance = read(runtime / 'runtime.json')
    previous = Path('data/maritime-routing/phase5-evidence-v1')
    p5 = read(previous / 'status.json')
    p4 = read('docs/maritime-routing/phase4-protocol-v3-20260922.json')
    frozen = read(previous / 'bindings.json')['implementation']
    tests, reports = [], []
    expected = dict(DiscreteRiskTest=6, ScenarioSimulationTest=8, SimulationControllerTest=5, SimulationEvidenceTest=1,
                    ContractEvaluatorTest=24, ContractControllerTest=3, ContractEvidenceTest=1,
                    WeatherRoutingTest=8, GraphArtifactLoaderTest=5, MaritimeRoutingControllerTest=5, RouteSearchTest=14)
    latest_source_time = max(Path(p).stat().st_mtime for p in bindings['implementation'])
    for name, expected_count in expected.items():
        paths = list(Path('target/surefire-reports').glob(f'TEST-*.{name}.xml'))
        if len(paths) != 1:
            raise ValueError(f'Missing or ambiguous report: {name}')
        path = paths[0]
        root = ET.parse(path).getroot()
        stats = {k: int(root.attrib[k]) for k in ('tests', 'failures', 'errors', 'skipped')}
        if stats['tests'] != expected_count or any(stats[k] for k in ('failures', 'errors', 'skipped')):
            raise ValueError(f'Test gate failed: {name}')
        if path.stat().st_mtime < latest_source_time:
            raise ValueError(f'Stale report relative to bound inputs: {name}')
        tests.append(dict(name=name, **stats, sha256=sha(path)))
        reports.append(path)
    alternatives = {a['id']: a for a in response['alternatives']}
    counts = response['sampledScenarioCounts']
    vectors = {'economy': [Decimal('1520'), Decimal('6350'), Decimal('51.36')],
               'protected': [Decimal('1780'), Decimal('3050'), Decimal('53.68')],
               'dominated': [Decimal('1720'), Decimal('6550'), Decimal('51.36')]}
    ref = alternatives['economy']['metrics']['BUYER_LOSS']['finiteDistributionReference']
    mc_mean = alternatives['economy']['metrics']['BUYER_LOSS']['monteCarlo']['mean']
    mc_oracle = Decimal(1250 * counts['delay'] + 11000 * counts['storm']) / Decimal(20000)
    epsilon = math.sqrt(math.log(2 * 15 / (1 - .95)) / (2 * 20000))
    intervals_contain = True
    for a in alternatives.values():
        for m in a['metrics'].values():
            for stat in ('mean', 'valueAtRisk', 'conditionalValueAtRisk'):
                interval = m['samplingBounds'][stat]
                intervals_contain &= interval['lower'] <= m['finiteDistributionReference'][stat] <= interval['upper']
    checks = dict(
        implementationBindings=all(sha(p) == digest for p, digest in bindings['implementation'].items()),
        frozenPriorBindings=bindings['preservedPhase5Bindings'] == len(frozen)
            and all(sha(p) == digest for p, digest in frozen.items()),
        priorEvidencePreserved=p5['status'] == 'PASS' and all(sha(previous / p) == digest for p, digest in p5['evidenceFiles'].items()),
        physicalGraphPreserved=sha(p4['graph']) == p4['graphSha256'],
        forecastManifestPreserved=sha(p4['manifest']) == p4['forecastSha256'],
        responseBinding=sha(runtime / 'example-response.json') == bindings['responseSha256'],
        runtimeBinding=sha(runtime / 'runtime.json') == bindings['runtimeSha256']
            and sha(runtime / 'capacity-request.json') == bindings['capacityRequestSha256'],
        all80TestsPassed=sum(t['tests'] for t in tests) == 80,
        explicitResearchScope=response['evidenceStatus'] == 'USER_DECLARED_UNCALIBRATED'
            and response['declaredInputs']['acknowledgeUncalibratedProbabilities'] is True,
        deterministicHttpReplay=performance['identicalHttpReplay'] is True,
        completeSampleAccounting=set(counts) == {'normal', 'delay', 'storm'} and sum(counts.values()) == 20000,
        handCalculatedMetrics=all(alternatives[k]['objectiveVector'] == v for k, v in vectors.items())
            and ref == dict(mean=320, valueAtRisk=1250, conditionalValueAtRisk=5150),
        commonRowMonteCarlo=mc_mean == mc_oracle,
        explicitPareto=response['paretoAlternativeIds'] == ['economy', 'protected']
            and alternatives['dominated']['dominatedBy'] == ['economy'],
        confidenceConvention=abs(float(response['simultaneousCdfErrorBound']) - epsilon) < 1e-14
            and response['tailProbability'] == Decimal('.05') and response['nominalSampleTailCount'] == 1000,
        registeredExampleIntervals=intervals_contain,
        maximumBudgetCase=(performance['capacitySamples'], performance['capacityAlternatives'], performance['capacityScenarios'],
                           performance['capacityFrontierSize']) == (200000, 16, 256, 16))
    status = dict(scope='PHASE6_DECLARED_JOINT_SCENARIO_RESEARCH', status='PASS' if all(checks.values()) else 'NOT_PASSED',
                  measuredAt=datetime.now(timezone.utc).isoformat(), modelVersion=bindings['modelVersion'],
                  calibrationStatus='NOT_ESTABLISHED_USER_SELECTED_DECLARED_SCENARIOS', checks=checks,
                  javaTests=tests, priorBoundFiles=len(frozen), inputSha256=response['inputSha256'],
                  sampledScenarioCounts=counts, runtime=performance,
                  limitations=['No empirical validation of probabilities, dependence, loss amounts or durations',
                               'Confidence bounds describe Monte Carlo error conditional on the supplied finite model',
                               'Pareto is over supplied alternatives and explicit objectives, without a unique recommendation',
                               'No physical route/forecast linkage, fuel calibration, insurance adjudication or deployment'])
    output.mkdir(parents=True)
    for path in [runtime / name for name in ('bindings.json', 'example-response.json', 'runtime.json', 'capacity-request.json')] + reports:
        shutil.copyfile(path, output / path.name)
    for name in ('phase6-plan.md', 'phase6-api.md', 'phase6-example.json'):
        shutil.copyfile(Path('docs/maritime-routing') / name, output / name)
    status['evidenceFiles'] = {p.name: sha(p) for p in sorted(output.iterdir())}
    status['closureImplementationSha256'] = sha(__file__)
    (output / 'status.json').write_text(json.dumps(status, sort_keys=True, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(dict(status=status['status'], checks=checks, javaTests=80, priorBoundFiles=len(frozen), sampledScenarioCounts=counts)))
    if status['status'] != 'PASS':
        raise SystemExit(1)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--runtime', type=Path, default=Path('target/maritime-routing/phase6'))
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    close(args.runtime, args.output)
