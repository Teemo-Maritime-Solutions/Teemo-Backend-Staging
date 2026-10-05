"""Archive phase-5 engineering evidence; never overwrite an existing closure."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET


def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def read(path):
    return json.loads(Path(path).read_bytes())


def close(runtime, output):
    if output.exists():
        raise ValueError('Output exists; choose another evidence version')
    bindings = read(runtime / 'bindings.json')
    response = read(runtime / 'example-response.json')
    p4_path = Path('docs/maritime-routing/phase4-protocol-v3-20260922.json')
    p4 = read(p4_path)
    p3_status = read('data/maritime-routing/phase3-research-status-v2-20260922.json')
    p4_status_path = Path('data/maritime-routing/phase4-evidence-v1-20260922/status.json')
    p4_status = read(p4_status_path)
    tests, reports = [], []
    expected = dict(ContractEvaluatorTest=24, ContractControllerTest=3, ContractEvidenceTest=1,
                    WeatherRoutingTest=8, GraphArtifactLoaderTest=5,
                    MaritimeRoutingControllerTest=5, RouteSearchTest=14)
    for name, count in expected.items():
        matches = list(Path('target/surefire-reports').glob(f'TEST-*.{name}.xml'))
        if len(matches) != 1:
            raise ValueError(f'Missing or ambiguous test report: {name}')
        path = matches[0]
        root = ET.parse(path).getroot()
        stats = {k: int(root.attrib[k]) for k in ('tests', 'failures', 'errors', 'skipped')}
        if stats['tests'] != count or any(stats[k] for k in ('failures', 'errors', 'skipped')):
            raise ValueError(f'Test gate failed: {name}')
        tests.append(dict(name=name, **stats, sha256=sha(path)))
        reports.append(path)
    # The evidence test creates bindings after checking all frozen files itself.
    checks = {
        'implementationBindings': all(sha(p) == digest for p, digest in bindings['implementation'].items()),
        'frozenImplementationCount': bindings['frozenImplementationCount'] == 31,
        'graphPreserved': sha(p4['graph']) == p4['graphSha256'],
        'forecastManifestPreserved': sha(p4['manifest']) == p4['forecastSha256'],
        'priorClosuresPassed': p3_status['status'] == p4_status['status'] == 'PASS',
        'phase4ProtocolBinding': p4_status['protocolSha256'] == sha(p4_path),
        'responseBinding': sha(runtime / 'example-response.json') == bindings['exampleResponseSha256'],
        'all60TestsPassed': sum(t['tests'] for t in tests) == 60,
        'exampleActorCosts': response['sellerCosts']['total'] == 4750 and response['buyerCosts']['total'] == 1200,
        'exampleIndependentControl': response['legs'][1]['transportPayer'] == 'SELLER'
            and response['legs'][1]['cargoRiskBearer'] == 'BUYER' and response['legs'][1]['routingControl'] == 'CARRIER',
        'conditionalNotInsuredLoss': response['conditionalLosses'][0]['buyerGrossLoss'] == 10250
            and response['conditionalLosses'][0]['insuranceRecovery'] is None,
        'declaredInputsRetained': response['declaredInputs'] == read('docs/maritime-routing/phase5-example-cif.json')
            or _matches_with_nulls(response['declaredInputs'], read('docs/maritime-routing/phase5-example-cif.json')),
    }
    status = dict(scope='PHASE5_CONTRACTUAL_RESEARCH', status='PASS' if all(checks.values()) else 'NOT_PASSED',
                  measuredAt=datetime.now(timezone.utc).isoformat(), ruleVersion=bindings['ruleVersion'], checks=checks,
                  javaTests=tests, frozenImplementationCount=31,
                  limitations=['Synthetic commercial amounts and losses, not actual tariffs/contracts/policies',
                               'Ordinary performance only; no legal or insurance adjudication',
                               'No probabilities, expected losses, optimisation or operational route validation',
                               'Legacy fixed-rate Incoterm endpoint remains outside this evidence'])
    output.mkdir(parents=True)
    for path in [runtime / 'bindings.json', runtime / 'example-response.json', *reports,
                 Path('docs/maritime-routing/phase5-example-cif.json')]:
        shutil.copyfile(path, output / path.name)
    for name in ['phase5-api.md', 'phase5-plan.md', 'phase5-sources.md']:
        shutil.copyfile(Path('docs/maritime-routing') / name, output / name)
    files = {p.name: sha(p) for p in sorted(output.iterdir())}
    status['evidenceFiles'] = files
    status['closureImplementationSha256'] = sha(__file__)
    (output / 'status.json').write_text(json.dumps(status, sort_keys=True, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(dict(status=status['status'], checks=checks, javaTests=60)))
    if status['status'] != 'PASS':
        raise SystemExit(1)


def _matches_with_nulls(actual, expected):
    """Jackson emits absent optional fields as null; no non-null addition is allowed."""
    if isinstance(actual, dict) and isinstance(expected, dict):
        return all(k in actual and _matches_with_nulls(actual[k], v) for k, v in expected.items()) and all(
            k in expected or v is None for k, v in actual.items())
    if isinstance(actual, list) and isinstance(expected, list):
        return len(actual) == len(expected) and all(_matches_with_nulls(a, b) for a, b in zip(actual, expected))
    return actual == expected


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--runtime', type=Path, default=Path('target/maritime-routing/phase5'))
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    close(args.runtime, args.output)
