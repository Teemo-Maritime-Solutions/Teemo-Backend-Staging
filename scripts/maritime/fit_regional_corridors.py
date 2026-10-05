"""Fit a pinned, isolated March AIS preference model without changing the physical graph."""
import argparse
from pathlib import Path
from regional_corridor_model import fit

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('artifact', 'chart-receipt', 'training-receipt', 'output'): parser.add_argument('--'+name, type=Path, required=True)
    args = parser.parse_args()
    fit(args.artifact, args.chart_receipt, args.training_receipt, args.output)
