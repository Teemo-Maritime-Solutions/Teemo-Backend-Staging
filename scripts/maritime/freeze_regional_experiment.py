"""Freeze April experiment before downloading or inspecting its AIS source."""
import argparse
from dataclasses import asdict
from datetime import datetime, timezone
import json
from pathlib import Path
import zipfile

from build_enc_pilot import load_snapshot
from build_graph import encode
from regional_experiment import APRIL_ID, APRIL_URL, REGISTERED_OBJECTIVES, bindings, sha
from validate_regional_ais import FROZEN_CONFIG, CRITERIA


def freeze(artifact, chart_receipt, config, protocol, geometry_report, ais_output, output):
    if output.exists() or (ais_output / 'ais-2024-04-01.csv.zst').exists() or (ais_output / 'ais-2024-04-01.csv.zst.receipt.json').exists():
        raise ValueError('Registration or designated holdout already exists')
    _, chart = load_snapshot(chart_receipt)
    if json.loads(config.read_text()) != asdict(FROZEN_CONFIG):
        raise ValueError('Not the declared unchanged cohort configuration')
    geometry = json.loads(geometry_report.read_text())
    with zipfile.ZipFile(artifact) as archive:
        manifest = json.loads(archive.read('manifest.json'))
    if (geometry.get('geometricCheck') != 'PASS' or geometry.get('artifactSha256') != sha(artifact)
        or geometry.get('sourceSha256') != chart['sha256'] or geometry.get('graphVersion') != manifest['graphVersion']
        or geometry.get('reciprocalGeometryVerified') is not True
        or geometry.get('checkedReciprocalPairs', -1) * 2 != manifest['report']['directedEdges']
        or manifest.get('validationProfile') != 'NOAA_ENC_REGIONAL_V2'):
        raise ValueError('Matching complete V2 geometry validation required before freezing')
    record = dict(schemaVersion=1, purpose='FROZEN_REGIONAL_PHYSICAL_EXPERIMENT',
        createdAt=datetime.now(timezone.utc).isoformat(), aisSource=dict(id=APRIL_ID, url=APRIL_URL),
        objectives=REGISTERED_OBJECTIVES, parameters=asdict(FROZEN_CONFIG), criteria=CRITERIA,
        geometryReportSha256=sha(geometry_report), graphVersion=manifest['graphVersion'],
        **bindings(artifact, chart['sha256'], config, protocol),
        limitations=['Local preregistration, not a third-party timestamp attestation',
                     'No global or operational acceptance; April is not used for model tuning'])
    with output.open('xb') as stream: stream.write(encode(record) + b'\n')
    print(json.dumps(dict(registrationSha256=sha(output), createdAt=record['createdAt'], graphVersion=record['graphVersion'])), flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for flag in ('artifact', 'chart-receipt', 'config', 'protocol', 'geometry-report', 'ais-output', 'output'):
        parser.add_argument('--' + flag, type=Path, required=True)
    args = parser.parse_args()
    freeze(args.artifact, args.chart_receipt, args.config, args.protocol, args.geometry_report, args.ais_output, args.output)
