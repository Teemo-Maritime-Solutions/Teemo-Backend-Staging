"""Decode a pinned ECMWF snapshot without filling missing ocean values."""
import argparse
from datetime import datetime, timedelta
import json
from pathlib import Path
import shutil
import eccodes as ec
import numpy as np
from acquire_forecast import encode, sha

PARAMETERS = [('swh', 140229, 'm'), ('mwd', 140230, 'Degree true'), ('mwp', 140232, 's'),
              ('10u', 165, 'm s**-1'), ('10v', 166, 'm s**-1'),
              ('sve', 262140, 'm s**-1'), ('svn', 262139, 'm s**-1')]
GRID = {'Ni': 1440, 'Nj': 721, 'latitudeOfFirstGridPointInDegrees': 90,
        'longitudeOfFirstGridPointInDegrees': 180, 'latitudeOfLastGridPointInDegrees': -90,
        'longitudeOfLastGridPointInDegrees': 179.75, 'iDirectionIncrementInDegrees': .25,
        'jDirectionIncrementInDegrees': .25, 'iScansNegatively': 0, 'jScansPositively': 0,
        'jPointsAreConsecutive': 0, 'alternativeRowScanning': 0}


def decode(path, parameter, run, step):
    name, param_id, units = parameter
    with path.open('rb') as stream:
        g = ec.codes_grib_new_from_file(stream)
        if g is None:
            raise ValueError('Missing GRIB message')
        try:
            expected = dict(GRID, shortName=name, paramId=param_id, units=units, step=step,
                            dataDate=int(run.strftime('%Y%m%d')), dataTime=int(run.strftime('%H%M')),
                            validityDate=int((run+timedelta(hours=step)).strftime('%Y%m%d')),
                            validityTime=int((run+timedelta(hours=step)).strftime('%H%M')),
                            gridType='regular_ll', edition=2)
            for key, value in expected.items():
                if ec.codes_get(g, key) != value:
                    raise ValueError(f'Unexpected {key} for {path.name}')
            values = ec.codes_get_values(g).astype('<f4')
            if ec.codes_get(g, 'bitmapPresent'):
                bitmap = ec.codes_get_array(g, 'bitmap')
                values[bitmap == 0] = np.nan
            if values.size != 1440*721 or np.isinf(values).any():
                raise ValueError('Invalid grid values')
            finite = values[np.isfinite(values)]
            packing_error = float(ec.codes_get(g, 'packingError'))
            if not finite.size or name in ('swh', 'mwp') and finite.min() < -packing_error:
                raise ValueError('Invalid physical values')
            if name == 'mwd' and (finite.min() < -packing_error or finite.max() > 360+packing_error):
                raise ValueError('Invalid wave direction')
            # GRIB quantization can encode 360 as 360.0008 degrees. This is an
            # angular wrap, never a missing-value fill; reject beyond packing error.
            if name == 'mwd':
                values %= 360
            elif name in ('swh', 'mwp'):
                values = np.maximum(values, 0)
            if np.count_nonzero(np.isnan(values)) != ec.codes_get(g, 'numberOfMissing'):
                raise ValueError('Missing bitmap mismatch')
            if stream.read(1):
                raise ValueError('Multiple messages are not supported')
            return values
        finally:
            ec.codes_release(g)


def build(source, output):
    receipt_path = source / 'source-receipt.json'
    receipt = json.loads(receipt_path.read_bytes())
    if receipt['probeOnly'] or receipt['source'] != 'ECMWF_IFS_OPEN_DATA':
        raise ValueError('Complete original ECMWF snapshot required')
    run = datetime.fromisoformat(receipt['runTime'].replace('Z', '+00:00'))
    steps = receipt['stepsHours']
    if len(steps) < 2 or steps != list(range(0, steps[-1]+1, 6)):
        raise ValueError('A regular six-hour forecast is required')
    if output.exists():
        raise ValueError('Output exists; forecast artifacts are immutable')
    records = {}
    for record in receipt['responses']:
        name = record['file']
        if Path(name).name != name or name in records or sha(source/name) != record['sha256']:
            raise ValueError('Source receipt integrity failed')
        records[name] = record
    output.mkdir(parents=True)
    frames, max_current = [], 0.0
    for step in steps:
        arrays, stats = [], {}
        for parameter in PARAMETERS:
            name = parameter[0]
            stem = 'wave' if name in ('swh', 'mwd', 'mwp') else 'oper'
            filename = f'{stem}-{step:03d}-{name}.grib2'
            record = records.get(filename)
            if record is None or record['parameter'] != name or record['forecastHour'] != step:
                raise ValueError('Missing or mixed source parameter')
            values = decode(source/filename, parameter, run, step)
            finite = values[np.isfinite(values)]
            stats[name] = dict(valid=int(finite.size), missing=int(values.size-finite.size),
                               minimum=float(finite.min()), maximum=float(finite.max()))
            arrays.append(values)
        currents = np.hypot(arrays[5].astype('float64'), arrays[6].astype('float64'))
        max_current = max(max_current, float(np.nanmax(currents)))
        filename = f'frame-{step:03d}.f32'
        with (output/filename).open('wb') as stream:
            for values in arrays:
                stream.write(values.tobytes())
        frames.append(dict(file=filename, forecastHour=step, sha256=sha(output/filename),
                           bytes=(output/filename).stat().st_size, statistics=stats))
        print(json.dumps(dict(forecastHour=step, missingWave=stats['swh']['missing'])), flush=True)
    shutil.copyfile(receipt_path, output/'source-receipt.json')
    manifest = dict(schemaVersion=1, purpose='REAL_FORECAST_RESEARCH_ONLY', source='ECMWF_IFS_OPEN_DATA',
                    runTime=receipt['runTime'], stepHours=6, nx=1440, ny=721, longitudeFirst=180,
                    latitudeFirst=90, resolutionDegrees=.25, layout='FIELD_LAT_LON_FLOAT32_LE_NAN_MISSING',
                    fields=[p[0] for p in PARAMETERS], units=[p[2] for p in PARAMETERS],
                    waveDirectionConvention='FROM_TRUE_NORTH_CLOCKWISE',
                    spatialSampling='NEAREST_CELL_NO_MISSING_FILL', temporalSampling='LINEAR_CIRCULAR_DIRECTION',
                    quantizationHandling='Angle modulo 360 and nonnegative scalar clipping only within GRIB packingError',
                    sourceReceiptSha256=sha(receipt_path), builderSha256=sha(Path(__file__)),
                    decoderVersion=ec.codes_get_api_version(), numpyVersion=np.__version__,
                    maximumCurrentMps=max_current + 1e-6, frames=frames,
                    attribution='ECMWF IFS Open Data, CC-BY-4.0; decoded and temporally interpolated for research',
                    licenseUrl=receipt['licenseUrl'])
    path = output/'manifest.json'
    path.write_bytes(encode(manifest)+b'\n')
    print(json.dumps(dict(manifest=str(path), sha256=sha(path), frames=len(frames), maximumCurrentMps=max_current)))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    build(args.source, args.output)
