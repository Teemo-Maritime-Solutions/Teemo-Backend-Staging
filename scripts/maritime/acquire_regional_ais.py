"""Acquire the prespecified March AIS holdout; reuse bounded official downloader."""
import argparse
from pathlib import Path
from acquire import SOURCES, acquire
from regional_experiment import APRIL_ID, APRIL_URL

SOURCE_ID = "noaa-ais-regional-march-holdout"
SOURCES[SOURCE_ID] = {
    **SOURCES["noaa-ais-holdout"],
    "url": "https://noaaocm.blob.core.windows.net/ais/csv2/csv2024/ais-2024-03-01.csv.zst",
    "filename": "ais-2024-03-01.csv.zst",
    "version": "2024-03-01 UTC observations; prospectively frozen regional physical-mesh evaluation",
}
SOURCES[APRIL_ID] = {**SOURCES[SOURCE_ID], 'url': APRIL_URL,
    'filename': 'ais-2024-04-01.csv.zst',
    'version': '2024-04-01 UTC observations; frozen independent regional physical-model comparison'}

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument('--source', choices=(SOURCE_ID, APRIL_ID), default=SOURCE_ID)
    args = parser.parse_args()
    acquire(args.source, args.output)
