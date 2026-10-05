"""Bounded acquisition of original public ECMWF forecast GRIB messages and indexes."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import ssl
from urllib.request import Request, build_opener, HTTPSHandler, HTTPRedirectHandler
from urllib.parse import urlsplit

import certifi

ROOTS = {"ecmwf": "https://data.ecmwf.int/forecasts", "aws": "https://ecmwf-forecasts.s3.eu-central-1.amazonaws.com"}
FIELDS = {"wave": ("swh", "mwd", "mwp"), "oper": ("10u", "10v", "sve", "svn")}
MAX_BYTES = 800_000_000


def encode(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False).encode()


def sha(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


class NoRedirects(HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        raise ValueError("Unexpected forecast redirect; review publisher URL")


class Client:
    def __init__(self):
        self.total = 0
        self.opener = build_opener(NoRedirects(), HTTPSHandler(context=ssl.create_default_context(cafile=certifi.where())))

    def fetch(self, url, expected=None, offset=None):
        if urlsplit(url).hostname not in {urlsplit(v).hostname for v in ROOTS.values()}:
            raise ValueError("Unknown forecast publisher")
        headers = {"User-Agent": "Teemo-Maritime-Research/1.0"}
        if offset is not None:
            headers["Range"] = f"bytes={offset}-{offset + expected - 1}"
        with self.opener.open(Request(url, headers=headers), timeout=90) as response:
            if offset is not None and (response.status != 206 or not response.headers.get("Content-Range", "").startswith(f"bytes {offset}-{offset+expected-1}/")):
                raise ValueError("Publisher did not honor bounded GRIB range")
            limit = expected if expected is not None else 4_000_000
            raw = response.read(limit + 1)
        if len(raw) > limit or expected is not None and len(raw) != expected:
            raise ValueError("Forecast response size mismatch")
        self.total += len(raw)
        if self.total > MAX_BYTES:
            raise ValueError("Forecast download byte budget exceeded")
        return raw


def url(base, date, cycle, step, stream):
    return f"{ROOTS[base]}/{date}/{cycle:02d}z/ifs/0p25/{stream}/{date}{cycle:02d}0000-{step}h-{stream}-fc"


def acquire(args):
    datetime.strptime(args.date, "%Y%m%d")
    if args.cycle not in (0, 12) or not 0 <= args.end_hour <= 360 or args.step_hours not in (3, 6):
        raise ValueError("Unsupported bounded forecast schedule")
    if args.end_hour % args.step_hours:
        raise ValueError("End hour must be a sampled step")
    if args.output.exists():
        raise ValueError("Snapshot path exists; preserve immutable inputs")
    args.output.mkdir(parents=True)
    client = Client()
    responses = []
    started = datetime.now(timezone.utc).isoformat()
    steps = [0] if args.probe else list(range(0, args.end_hour + 1, args.step_hours))
    for step in steps:
        for stream, fields in FIELDS.items():
            stem = url(args.source, args.date, args.cycle, step, stream)
            raw = client.fetch(stem + ".index")
            index_path = args.output / f"{stream}-{step:03d}.index"
            index_path.write_bytes(raw)
            index = [json.loads(line) for line in raw.splitlines() if line.strip()]
            selected = [record for record in index if record.get("param") in fields and str(record.get("levtype")) == "sfc"]
            available = sorted({record.get("param") for record in index})
            responses.append(dict(file=index_path.name, url=stem+".index", sha256=sha(index_path)))
            print(json.dumps(dict(step=step, stream=stream, wanted=list(fields), selected=selected,
                                  available=available if args.probe else None)), flush=True)
            if args.probe:
                continue
            if len(selected) != len(fields) or {r["param"] for r in selected} != set(fields):
                raise ValueError("Required forecast parameters are unavailable or duplicated")
            for record in selected:
                offset, length = record["_offset"], record["_length"]
                if type(offset) is not int or type(length) is not int or offset < 0 or not 16 <= length <= 10_000_000:
                    raise ValueError("Invalid GRIB range in publisher index")
                body = client.fetch(stem + ".grib2", length, offset)
                if not body.startswith(b"GRIB") or body[7] != 2 or not body.endswith(b"7777") or int.from_bytes(body[8:16], "big") != length:
                    raise ValueError("Invalid GRIB2 message framing")
                name = f"{stream}-{step:03d}-{record['param']}.grib2"
                path = args.output / name
                path.write_bytes(body)
                responses.append(dict(file=name, url=stem+".grib2", offset=offset, length=length,
                                      sha256=sha(path), parameter=record["param"], forecastHour=step))
    receipt = dict(schemaVersion=1, purpose="SOURCE_FORECAST_NOT_OBSERVATIONS", source="ECMWF_IFS_OPEN_DATA",
                   runTime=f"{args.date[:4]}-{args.date[4:6]}-{args.date[6:]}T{args.cycle:02d}:00:00Z",
                   acquiredAt=datetime.now(timezone.utc).isoformat(), acquisitionStartedAt=started,
                   probeOnly=args.probe, stepsHours=steps, responses=responses, bytes=client.total,
                   license="CC-BY-4.0 with ECMWF terms of use", licenseUrl="https://www.ecmwf.int/en/forecasts/datasets/open-data",
                   attribution="Contains modified Copernicus/ECMWF forecast information; ECMWF IFS Open Data",
                   implementationSha256=sha(Path(__file__)),
                   limitations=["Numerical predictions, not observed weather or a navigational safety assessment",
                                "Finite forecast horizon and missing land/ice/coastal grid values must be preserved",
                                "GRIB mean wave direction is the meteorological FROM convention"])
    (args.output / "source-receipt.json").write_bytes(encode(receipt) + b"\n")
    print(json.dumps(dict(receipt=str(args.output / "source-receipt.json"), bytes=client.total)), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--date", required=True)
    parser.add_argument("--cycle", type=int, default=0)
    parser.add_argument("--end-hour", type=int, default=168)
    parser.add_argument("--step-hours", type=int, default=6)
    parser.add_argument("--source", choices=ROOTS, default="ecmwf")
    parser.add_argument("--probe", action="store_true")
    parser.add_argument("--output", type=Path, required=True)
    acquire(parser.parse_args())
