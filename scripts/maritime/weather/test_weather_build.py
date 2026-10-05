"""Decoder checks against preserved publisher messages; never manufacture weather evidence."""
from datetime import datetime, timezone
from pathlib import Path
import tempfile
import unittest
import numpy as np
from build_forecast import decode, PARAMETERS, build

SOURCE = Path('data/maritime-routing/weather/ifs-20260922-00z-168h')
RUN = datetime(2026, 9, 22, tzinfo=timezone.utc)


@unittest.skipUnless((SOURCE/'source-receipt.json').exists(), 'Requires preserved original ECMWF snapshot')
class RealDecoderTests(unittest.TestCase):
    def test_bitmap_missing_and_quantized_direction(self):
        height = decode(SOURCE/'wave-000-swh.grib2', PARAMETERS[0], RUN, 0)
        direction = decode(SOURCE/'wave-000-mwd.grib2', PARAMETERS[1], RUN, 0)
        self.assertEqual(height.size, 1440*721)
        self.assertEqual(int(np.isnan(height).sum()), 372612)
        self.assertTrue(np.array_equal(np.isnan(height), np.isnan(direction)))
        self.assertGreaterEqual(np.nanmin(direction), 0)
        self.assertLess(np.nanmax(direction), 360)

    def test_mixed_run_parameter_and_step_are_rejected(self):
        path = SOURCE/'wave-000-swh.grib2'
        for parameter, run, step in [(PARAMETERS[1], RUN, 0), (PARAMETERS[0], RUN.replace(day=23), 0),
                                      (PARAMETERS[0], RUN, 6)]:
            with self.assertRaisesRegex(ValueError, 'Unexpected'):
                decode(path, parameter, run, step)

    def test_extra_message_rejected_and_artifacts_immutable(self):
        with tempfile.TemporaryDirectory(dir='target') as directory:
            path = Path(directory)/'duplicate.grib2'
            raw = (SOURCE/'wave-000-swh.grib2').read_bytes()
            path.write_bytes(raw+raw)
            with self.assertRaisesRegex(ValueError, 'Multiple messages'):
                decode(path, PARAMETERS[0], RUN, 0)
            with self.assertRaisesRegex(ValueError, 'Output exists'):
                build(SOURCE, Path(directory))


if __name__ == '__main__':
    unittest.main()
