"""Close the integrated research gate from actual route/recalculation evidence."""
import argparse
from datetime import datetime, timezone
from decimal import Decimal, ROUND_HALF_UP
import hashlib
import json
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
    first, second = [read(runtime / name, decimal=True) for name in ('plan-v1.json', 'plan-v2.json')]
    performance = read(runtime / 'runtime.json')
    previous = Path('data/maritime-routing/phase6-evidence-v1')
    previous_status = read(previous / 'status.json')
    frozen = read(previous / 'bindings.json')['implementation']
    protocol = read('docs/maritime-routing/phase4-protocol-v3-20260922.json')
    expected = dict(PlanIntegrationTest=8, RealPlanIntegrationTest=1, DiscreteRiskTest=6, ScenarioSimulationTest=8,
                    SimulationControllerTest=5, SimulationEvidenceTest=1, ContractEvaluatorTest=24,
                    ContractControllerTest=3, ContractEvidenceTest=1, WeatherRoutingTest=8,
                    GraphArtifactLoaderTest=5, MaritimeRoutingControllerTest=5, RouteSearchTest=14)
    tests, reports = [], []
    newest_input = max(Path(p).stat().st_mtime for p in bindings['implementation'])
    for name, count in expected.items():
        paths = list(Path('target/surefire-reports').glob(f'TEST-*.{name}.xml'))
        if len(paths) != 1:
            raise ValueError(f'Missing report {name}')
        path = paths[0]
        stats = {k: int(ET.parse(path).getroot().attrib[k]) for k in ('tests', 'failures', 'errors', 'skipped')}
        if stats['tests'] != count or any(stats[k] for k in ('failures', 'errors', 'skipped')) or path.stat().st_mtime < newest_input:
            raise ValueError(f'Failed, skipped or stale tests: {name}')
        tests.append(dict(name=name, **stats, sha256=sha(path)))
        reports.append(path)
    versions_ok, time_ok, duration_ok, geometry_ok, no_fake_fuel = True, True, True, True, True
    rows = []
    for label, plan in [('original', first), ('recalculated', second)]:
        content = plan['content']
        requests = {r['id']: r['request'] for r in content['declaredInputs']['routes']}
        impacts = {r['id']: r['outcomes'] for r in content['declaredInputs']['simulation']['distribution']['scenarios']}
        effective = {r['id']: r['outcomes'] for r in content['comparison']['declaredInputs']['distribution']['scenarios']}
        for rid, route in content['routes'].items():
            physical = route['route']
            depart = datetime.fromisoformat(requests[rid]['departure'].replace('Z', '+00:00'))
            arrive = datetime.fromisoformat(physical['arrival'].replace('Z', '+00:00'))
            elapsed = Decimal(str((arrive - depart).total_seconds()))
            versions_ok &= route['graphVersion'] == requests[rid]['expectedGraphVersion'] == content['provenance']['graphVersion']
            versions_ok &= route['forecastVersion'] == requests[rid]['expectedForecastVersion'] == protocol['forecastSha256']
            time_ok &= abs(elapsed - physical['sailingSeconds'] - physical['modelledWaitingSeconds']) < Decimal('.00001')
            geometry_ok &= route['geometry']['type'] == 'MultiLineString' and bool(route['geometry']['coordinates'])
            no_fake_fuel &= route['fuelTonnes'] is None and route['emissionsTonnes'] is None
            for binding in content['bindings']:
                if binding['routeId'] != rid:
                    continue
                duration_ok &= binding['physicalElapsedHours'] == (elapsed / 3600).quantize(Decimal('.000001'), rounding=ROUND_HALF_UP)
                for sid in effective:
                    aid = binding['alternativeId']
                    duration_ok &= effective[sid][aid]['durationHours'] == binding['physicalElapsedHours'] + binding['nonSailingHours'] + impacts[sid][aid]['additionalHours']
            rows.append(dict(revision=label, routeId=rid, distanceM=float(physical['distanceM']), arrival=physical['arrival'],
                             sailingHours=float(physical['sailingSeconds']/3600), waitingSeconds=float(physical['modelledWaitingSeconds']),
                             expandedStates=physical['expandedStates']))
    closed = performance['closedEdgeId']
    original_edges = {l['edgeId'] for l in first['content']['routes']['base']['route']['legs'] if l['edgeId'] is not None}
    revised_edges = {l['edgeId'] for r in second['content']['routes'].values() for l in r['route']['legs'] if l['edgeId'] is not None}
    checks = dict(
        implementationBindings=all(sha(p) == digest for p, digest in bindings['implementation'].items()),
        priorBindingsPreserved=bindings['priorBoundFiles'] == len(frozen) and all(sha(p) == digest for p, digest in frozen.items()),
        priorEvidencePreserved=previous_status['status'] == 'PASS' and all(sha(previous / p) == digest for p, digest in previous_status['evidenceFiles'].items()),
        realArtifactPins=sha(protocol['graph']) == protocol['graphSha256'] and sha(protocol['manifest']) == protocol['forecastSha256'],
        outputBindings=all(sha(runtime / name) == digest for name, digest in bindings['outputs'].items()),
        all89TestsPassed=sum(t['tests'] for t in tests) == 89,
        realRoutesAndVersions=versions_ok and all(p['content']['status'] == 'INTEGRATED_RESEARCH_COMPARISON' and len(p['content']['routes']) == 2 for p in (first, second)),
        physicalTimeAccounting=time_ok,
        linkedScenarioDurations=duration_ok,
        geometryAndMissingFuel=geometry_ok and no_fake_fuel,
        explicitCommercialScope=all(p['content']['comparison']['evidenceStatus'] == 'USER_DECLARED_UNCALIBRATED' and len(p['content']['bindings']) == 3 for p in (first, second)),
        immutableParentLink=first['planId'] != second['planId'] and second['content']['parent']['planId'] == first['planId']
            and second['content']['parent']['contentSha256'] == first['contentSha256'],
        actualClosureRerouting=closed in original_edges and closed not in revised_edges,
        changedInputExplanation=second['content']['changes']['changedInputSections'] == ['routes'],
        regionalRestrictionsPreserved=performance['blockedRegionalEdges'] == 16330,
        failuresDoNotSavePartialComparisons=performance['negativeHttpStatuses'] == [409, 422] and performance['storedRevisions'] == 2)
    status = dict(scope='PHASE7_INTEGRATED_RESEARCH_PLANS', status='PASS' if all(checks.values()) else 'NOT_PASSED',
                  measuredAt=datetime.now(timezone.utc).isoformat(), checks=checks, javaTests=tests,
                  priorBoundFiles=len(frozen), runtime=performance, routes=rows,
                  limitations=['Actual historical ocean-node routes, not certified port or terminal access',
                               'Commercial mapping, probabilities and additional durations are declared synthetic scenarios',
                               'Local research storage without owner authorization; no public deployment or production certification',
                               'No calibrated ETA, fuel, emissions or insurance recovery'])
    output.mkdir(parents=True)
    for path in [runtime / name for name in ('bindings.json', 'plan-v1.json', 'plan-v2.json', 'runtime.json', 'recalculation-request.json')] + reports:
        shutil.copyfile(path, output / path.name)
    for name in ('phase7-plan.md', 'phase7-api.md', 'phase7-example.json'):
        shutil.copyfile(Path('docs/maritime-routing') / name, output / name)
    status['evidenceFiles'] = {p.name: sha(p) for p in sorted(output.iterdir())}
    status['closureImplementationSha256'] = sha(__file__)
    (output / 'status.json').write_text(json.dumps(status, sort_keys=True, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(dict(status=status['status'], checks=checks, javaTests=89, priorBoundFiles=len(frozen), routes=rows)))
    if status['status'] != 'PASS':
        raise SystemExit(1)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--runtime', type=Path, default=Path('target/maritime-routing/phase7'))
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    close(args.runtime, args.output)
