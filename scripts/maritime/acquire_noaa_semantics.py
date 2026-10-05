"""Bounded immutable NOAA Coast Pilot evidence snapshot. No legal permission inferred."""
import argparse
import hashlib
from pathlib import Path

from build_graph import encode
from enc_snapshot import SourceClient

URL = "https://nauticalcharts.noaa.gov/publications/coast-pilot/files/cp2/CPB2_WEB.pdf"
LICENSE = "https://nauticalcharts.noaa.gov/data/data-licensing.html"


def acquire(output):
    if output.exists():
        raise ValueError("Evidence directory exists; select a new immutable name")
    client = SourceClient(max_bytes=100000000, max_response_bytes=95000000)
    notice = client.request(LICENSE, text=True)
    raw = client.request(URL, text=True)
    if not raw.startswith(b"%PDF-"):
        raise ValueError("NOAA did not return a PDF")
    response = client.responses[-1]
    receipt = dict(id="noaa-coast-pilot-2", url=URL,
                   license="NOAA federal publication; preserve attribution and third-party notices; local research snapshot, no repository redistribution",
                   licenseUrl=LICENSE, version="Exact PDF snapshot SHA256; printed edition must be checked before rule review",
                   acquiredAt=response["completedAt"], unit="Regulatory text and original published units, no inferred permission",
                   coverage="U.S. Coast Pilot 2 regional coverage; regulation sections have their own geographic and temporal scope",
                   limitations=["Publication not a live authorization or complete closure feed", "Review printed edition and current applicability; receipt time is not legal effective time",
                                "External graphics and source notices may have separate rights; retained locally only"],
                   sha256=response["sha256"], file="coast-pilot-2.pdf", bytes=len(raw), status="SOURCE_DATA",
                   acquirerSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest())
    output.mkdir(parents=True, exist_ok=False)
    (output / receipt["file"]).write_bytes(raw)
    (output / "coast-pilot-2.pdf.receipt.json").write_bytes(encode(receipt) + b"\n")
    (output / "noaa-licensing.html").write_bytes(notice)
    (output / "requests.json").write_bytes(encode([{k: v for k, v in r.items() if k != "data"} for r in client.responses]) + b"\n")
    print(encode(receipt).decode(), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, type=Path)
    acquire(parser.parse_args().output)
