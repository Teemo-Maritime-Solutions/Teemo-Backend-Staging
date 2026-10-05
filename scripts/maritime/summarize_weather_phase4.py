"""Close the research-weather gate from pinned inputs, runtime evidence and test reports."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET
import zipfile


def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def encode(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), allow_nan=False).encode()+b'\n'


def close(args):
    protocol = json.loads(args.protocol.read_bytes())
    report = json.loads((args.runtime/'runtime-report.json').read_bytes())
    manifest_path = Path(protocol['manifest'])
    manifest = json.loads(manifest_path.read_bytes())
    checks = {}
    checks['implementationBindings'] = all(sha(name) == digest for name, digest in protocol['implementation'].items())
    checks['inputBindings'] = sha(protocol['graph']) == protocol['graphSha256'] and sha(manifest_path) == protocol['forecastSha256']
    checks['reportBindings'] = report['protocolSha256'] == sha(args.protocol) and report['forecastVersion'] == protocol['forecastSha256']
    phase3_path = Path('docs/maritime-routing/phase3-research-protocol-v3-20260921.json')
    phase3 = json.loads(phase3_path.read_bytes())
    checks['frozenPhase3'] = sha(phase3_path) == protocol['phase3ProtocolSha256'] and all(
        sha(name) == digest for name, digest in phase3['implementation'].items())
    source = manifest_path.parent/'source-receipt.json'
    receipt = json.loads(source.read_bytes())
    checks['sourceReceipt'] = sha(source) == manifest['sourceReceiptSha256'] and not receipt['probeOnly']
    original = args.source/'source-receipt.json'
    checks['originalPublisherFiles'] = sha(original) == sha(source) and all(
        sha(args.source/record['file']) == record['sha256'] for record in receipt['responses'])
    checks['derivedFrames'] = len(manifest['frames']) == 29 and all(
        sha(manifest_path.parent/frame['file']) == frame['sha256'] for frame in manifest['frames'])
    cases = report['cases']
    checks['threeBasins'] = len(cases) == 3 and {c['basin'] for c in cases} == {'ATLANTIC', 'PACIFIC', 'INDIAN'}
    checks['boundedRoutes'] = all(c['httpStatus'] == 200 and c['response']['route']['status'] == 'FOUND_RESEARCH_SCENARIO'
        and c['response']['route']['expandedStates'] <= protocol['search']['maximumExpandedStates']
        and c['response']['route']['labels'] <= protocol['search']['maximumLabels']
        and c['response']['route']['distanceM'] > 0 for c in cases)
    checks['explicitScenarioNoInventedFuel'] = all(c['response']['vesselModelEvidence'] == 'USER_DECLARED_RESEARCH_SCENARIO_NOT_CALIBRATED'
        and c['response']['fuelTonnes'] is None and c['response']['emissionsTonnes'] is None
        and c['response']['vesselModel'] == protocol['vessel'] for c in cases)
    checks['timeAccounting'] = all(abs((datetime.fromisoformat(c['response']['route']['arrival'].replace('Z', '+00:00'))
        -datetime.fromisoformat(protocol['departure'].replace('Z', '+00:00'))).total_seconds()
        -c['response']['route']['sailingSeconds']-c['response']['route']['modelledWaitingSeconds']) < 1e-5 for c in cases)
    checks['datelineGeometry'] = all(abs(a[0]-b[0]) <= 180 for c in cases
        for part in c['response']['geometry']['coordinates'] for a, b in zip(part, part[1:])) and any(
        c['basin'] == 'PACIFIC' and len(c['response']['geometry']['coordinates']) > 1 for c in cases)
    census_path = args.runtime/'port-weather-coverage.json'
    census = json.loads(census_path.read_bytes())
    checks['completePortPointCensus'] = len(census) == report['connectedPortReferences'] == 1114 and len({c['portId'] for c in census}) == 1114
    checks['honestCoverage'] = sum(c['availableAtDeparture'] for c in census) == report['weatherAvailableAtDeparture'] and sum(
        c['validFrames'] == len(manifest['frames']) for c in census) == report['weatherAvailableAllFrames']
    checks['regionalRestrictionsPreserved'] = report['blockedRegionalEdges'] == 16330
    tests, test_files = [], []
    for name in ['WeatherRoutingTest', 'RealWeatherIntegrationTest', 'GraphArtifactLoaderTest', 'MaritimeRoutingControllerTest', 'RouteSearchTest']:
        matches = list(Path('target/surefire-reports').glob(f'TEST-*.{name}.xml'))
        if len(matches) != 1:
            raise ValueError(f'Missing test report {name}')
        path = matches[0]; root = ET.parse(path).getroot(); test_files.append(path)
        tests.append(dict(name=name, **{k: int(root.attrib[k]) for k in ('tests', 'failures', 'errors', 'skipped')}))
    checks['javaTests'] = sum(t['tests'] for t in tests) == 33 and all(
        not(t['failures'] or t['errors'] or t['skipped']) for t in tests)
    # PowerShell redirected native logs may be UTF-16 on Windows PowerShell 5.
    raw = args.python_log.read_bytes()
    log = raw.decode('utf-16' if raw.startswith((b'\xff\xfe', b'\xfe\xff')) else 'utf-8')
    checks['realDecoderTests'] = 'Ran 3 tests' in log and '\nOK' in log and 'FAILED' not in log and 'skipped=' not in log
    result = dict(schemaVersion=1, status='PASS' if all(checks.values()) else 'NOT_PASSED',
                  scope='PHASE4_VERSIONED_WEATHER_PARAMETRIC_RESEARCH', measuredAt=datetime.now(timezone.utc).isoformat(),
                  checks=checks, protocolSha256=sha(args.protocol), forecastSha256=protocol['forecastSha256'],
                  graphVersion=report['graphVersion'], runtimeReportSha256=sha(args.runtime/'runtime-report.json'),
                  portCensusSha256=sha(census_path), javaTests=tests, decoderTests=3,
                  connectedPortReferences=1114, weatherAvailableAtDeparture=report['weatherAvailableAtDeparture'],
                  missingWeatherAtDeparture=1114-report['weatherAvailableAtDeparture'],
                  limitations=['Forecast point coverage is not port-to-port route availability',
                               'User-approved parametric research model, not calibrated vessel ETA',
                               'No fuel/emissions estimates, global operational permission, anchorage or AIS validation',
                               'Finite forecast horizon, numerical sampling and explicitly modelled waiting'])
    if args.output.exists():
        raise ValueError('Evidence output exists; use a new version')
    args.output.mkdir(parents=True)
    (args.output/'status.json').write_bytes(encode(result))
    for path in [args.protocol, args.runtime/'runtime-report.json', census_path, args.python_log, *test_files]:
        shutil.copyfile(path, args.output/path.name)
    # Record exact authoritative inputs without duplicating the ~1 GB forecast archive.
    bindings = dict(protocol=sha(args.protocol), implementation=protocol['implementation'],
                    sourceReceipt=dict(path=str(original), sha256=sha(original)),
                    forecastManifest=dict(path=str(manifest_path), sha256=sha(manifest_path)),
                    graph=dict(path=protocol['graph'], sha256=protocol['graphSha256']),
                    closureImplementationSha256=sha(Path(__file__)))
    (args.output/'bindings.json').write_bytes(encode(bindings))
    bundle = args.output.with_suffix('.zip')
    if bundle.exists():
        raise ValueError('Evidence bundle exists')
    with zipfile.ZipFile(bundle, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(args.output.iterdir()):
            archive.write(path, path.name)
    (args.output/'bundle-receipt.json').write_bytes(encode(dict(file=bundle.name, sha256=sha(bundle), bytes=bundle.stat().st_size)))
    print(json.dumps(result))
    if result['status'] != 'PASS':
        raise SystemExit(1)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--protocol', type=Path, required=True)
    parser.add_argument('--runtime', type=Path, required=True)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--python-log', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    close(parser.parse_args())
