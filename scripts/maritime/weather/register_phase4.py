"""Register engineering scenarios before executing real weather routing tests."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
from acquire_forecast import encode, sha


def register(output):
    if output.exists():
        raise ValueError('Registration is immutable; use a new version')
    graph = Path('data/maritime-routing/global-regional-research-v4-20260921.zip')
    manifest = Path('data/maritime-routing/weather/ifs-20260922-00z-168h-grid-v2/manifest.json')
    sources = [*Path('scripts/maritime/weather').glob('*.py'),
               *Path('src/main/java').glob('**/routing/weather/*.java'),
               *Path('src/test/java').glob('**/routing/weather/*.java')]
    phase3_path = Path('docs/maritime-routing/phase3-research-protocol-v3-20260921.json')
    phase3 = json.loads(phase3_path.read_bytes())
    for name, digest in phase3['implementation'].items():
        if sha(Path(name)) != digest:
            raise ValueError(f'Frozen phase 3 implementation changed: {name}')
    protocol = dict(schemaVersion=1, purpose='PHASE4_ENGINEERING_RESEARCH_NOT_AIS_OR_CALIBRATED_ETA',
                    registeredAt=datetime.now(timezone.utc).isoformat(),
                    graph=str(graph).replace('\\', '/'), graphSha256=sha(graph),
                    validation=str(graph).replace('\\', '/').replace('.zip', '.validation.json'),
                    manifest=str(manifest).replace('\\', '/'), forecastSha256=sha(manifest),
                    phase3ProtocolSha256=sha(phase3_path),
                    implementation={p.as_posix(): sha(p) for p in sorted(sources)},
                    departure='2026-09-22T00:00:00Z', mode='HISTORICAL_RESEARCH_REPLAY',
                    vessel=dict(calmSpeedKnots=18, headWaveLossKnotsPerM2=.08, beamWaveLossKnotsPerM2=.04,
                                followingWaveLossKnotsPerM2=.02, headWindLossKnotsPerMps2=.0005,
                                maximumWaveHeightM=8, maximumWindMps=35),
                    search=dict(timeStepSeconds=300, horizonHours=72, maximumExpandedStates=100000,
                                maximumLabels=300000, maximumRuntimeSeconds=120, acknowledgeModelledWaiting=True),
                    cases=[dict(basin='ATLANTIC', origin=[-30, 0], destination=[-26, 0]),
                           dict(basin='PACIFIC', origin=[178, 0], destination=[-178, 0]),
                           dict(basin='INDIAN', origin=[75, -10], destination=[79, -10])],
                    endpointSelection='Nearest existing physical graph node to declared ocean coordinates; ties by node index; not port claims',
                    coverageCensus='All connected source port references at every forecast frame, no nearest-valid filling',
                    acceptance=['Three basin scenarios produce routes within declared budgets',
                                'All returned sailing edges respect the phase 3 physical policy',
                                'All original restricted regional edges remain blocked',
                                'Endpoint metadata, ETA, sailing and holding, baseline distance and time, memory and runtime reported',
                                'Missing data, stale forecast, horizon, version, request schema and budgets fail explicitly'],
                    limitations=['Declared coefficients are uncalibrated engineering inputs',
                                 'Waiting and arrival rounding are acknowledged scenario assumptions, not anchorage validation',
                                 'No fuel or emissions curve: results must be null',
                                 'These scenarios are not an independent voyage validation'])
    output.write_bytes(encode(protocol)+b'\n')
    print(json.dumps(dict(protocol=str(output), sha256=sha(output))))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    register(parser.parse_args().output)
