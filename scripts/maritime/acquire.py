"""Pinned public-source acquisition. No credentials, TLS bypass, or silent mirrors."""
import argparse
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path
from urllib.request import Request, urlopen

SOURCES = {
    "noaa-ais-validation": {
        "url": "https://noaaocm.blob.core.windows.net/ais/csv2/csv2024/ais-2024-01-01.csv.zst",
        "filename": "ais-2024-01-01.csv.zst",
        "license": "US government historical AIS; coastal/ocean planning use constraints. Raw data kept local; no unrestricted commercial redistribution assumed",
        "licenseUrl": "https://coast.noaa.gov/data/marinecadastre/ais/faq.pdf",
        "metadataUrl": "https://www.fisheries.noaa.gov/inport/item/73064",
        "version": "2024-01-01 UTC observations; 2026 Zstandard redistribution; exact bytes pinned by SHA-256",
        "unit": "longitude/latitude degrees WGS84; UTC timestamps; SOG knots",
        "coverage": "One day of US terrestrial AIS reception, not global or transoceanic voyages",
        "limitations": ["Held out from graph construction and tuning; regional diagnostic only", "Coastal/ocean planning, not enforcement or navigation", "AIS has gaps, erroneous positions and self-reported attributes", "No claim of legal passage, safe depth or verified port approaches", "Source not committed or redistributed; review publisher terms before external use"],
        "maxBytes": 300_000_000,
    },
    "country-qa": {
        "url": "https://naturalearth.s3.amazonaws.com/5.1.1/10m_cultural/ne_10m_admin_0_map_units.zip",
        "filename": "ne_10m_admin_0_map_units-5.1.1.zip",
        "license": "Public domain (Natural Earth)",
        "licenseUrl": "https://www.naturalearthdata.com/about/terms-of-use/",
        "version": "5.1.1",
        "unit": "longitude/latitude degrees",
        "coverage": "Global map units; coarse country/location sanity checks only",
        "limitations": ["Generalized de facto boundaries, not a position on disputed sovereignty", "Not maritime boundaries, port approaches or nautical charts", "Some territories lack a usable ISO code; mark unavailable"],
        "maxBytes": 25_000_000,
    },
    "unlocode": {
        "url": "https://opensource.unicc.org/un/unece/uncefact/vocab-locode/-/jobs/artifacts/2025-1/download?job=package-release",
        "filename": "unlocode-2025-1.zip",
        "license": "CC-BY-4.0 (UN/CEFACT publication website); retain attribution and archive licence notices",
        "licenseUrl": "https://unlocode.unece.org/publications/",
        "version": "2025-1",
        "unit": "longitude/latitude degrees; original coordinate precision retained",
        "coverage": "Global UN/LOCODE locations; importer selects function 1 with coordinates",
        "limitations": ["Trade location, not a berth or surveyed water approach", "Minute coordinate precision may put location on land", "Missing coordinates excluded, never geocoded or guessed"],
        "maxBytes": 100_000_000,
    },
    "gshhg": {
        "url": "https://ftp.soest.hawaii.edu/gshhg/gshhg-shp-2.3.7.zip",
        "filename": "gshhg-shp-2.3.7.zip",
        "license": "LGPL-3.0-or-later; retain upstream COPYING and notices",
        "licenseUrl": "https://www.soest.hawaii.edu/pwessel/gshhg/README.TXT",
        "version": "2.3.7 (2017-06-15)",
        "unit": "longitude/latitude degrees",
        "coverage": "Global L1 land and L5 Antarctic ice front",
        "limitations": ["Historical generalized shoreline, not nautical charts", "No depths, routing measures or port approach assurance"],
        "maxBytes": 250_000_000,
    },
    "wpi": {
        "url": "https://msi.nga.mil/api/publications/download?key=16694622%2FSFH00000%2FUpdatedPub150.csv&type=view",
        "filename": "UpdatedPub150.csv",
        "license": "NGA public WPI; no copyright claimed under Title 17 USC in publisher notice; raw data not redistributed by this repository",
        "licenseUrl": "https://msi.nga.mil/api/publications/download?key=16694622%2FSFH00000%2FPub150bk.pdf&type=view",
        "version": "undated official CSV snapshot; content SHA-256 identifies exact revision",
        "unit": "longitude/latitude degrees",
        "coverage": "Ports worldwide selected by NGA, not all terminals",
        "limitations": ["Reference point may be on land", "Not a surveyed water entrance", "CSV publication date unknown; retrieval date is not survey date", "2019 publisher notice retained as reference; review downstream redistribution separately"],
        "maxBytes": 25_000_000,
    },
}

# Independent temporal sample registered before inspecting its route outcomes.
SOURCES["noaa-ais-holdout"] = {
    **SOURCES["noaa-ais-validation"],
    "url": "https://noaaocm.blob.core.windows.net/ais/csv2/csv2024/ais-2024-02-01.csv.zst",
    "filename": "ais-2024-02-01.csv.zst",
    "version": "2024-02-01 UTC observations; 2026 Zstandard redistribution; exact bytes pinned by SHA-256",
}


def acquire(source_id, output):
    source = SOURCES[source_id]
    output.mkdir(parents=True, exist_ok=True)
    destination = output / source["filename"]
    receipt_path = destination.with_suffix(destination.suffix + ".receipt.json")
    if destination.exists() or receipt_path.exists():
        raise ValueError("Snapshot already exists. Use a new output directory; never overwrite source evidence.")
    # Buffer is bounded; no half-downloaded production file or misleading receipt.
    started = datetime.now(timezone.utc).isoformat()
    request = Request(source["url"], headers={"User-Agent": "Teemo-Maritime-Research/1.0"})
    with urlopen(request, timeout=60) as response:
        if not response.url.startswith("https://"):
            raise ValueError("Refusing non-HTTPS redirect")
        data = response.read(source["maxBytes"] + 1)
        modified = response.headers.get("Last-Modified")
    if len(data) > source["maxBytes"]:
        raise ValueError("Source exceeded bounded download size")
    if source_id in ("gshhg", "unlocode", "country-qa") and not data.startswith(b"PK"):
        raise ValueError("Expected a ZIP archive, not a portal/error page")
    if source_id in ("noaa-ais-validation", "noaa-ais-holdout", "noaa-ais-regional-march-holdout", "noaa-ais-regional-april-holdout") and not data.startswith(bytes.fromhex("28b52ffd")):
        raise ValueError("Expected a Zstandard stream, not a portal/error page")
    if source_id == "wpi" and not all(field in data[:10000] for field in (b"Latitude", b"Longitude")):
        raise ValueError("Expected the documented WPI CSV schema")
    receipt = {k: v for k, v in source.items() if k not in ("maxBytes", "filename")}
    receipt.update(id=source_id, acquiredAt=datetime.now(timezone.utc).isoformat(), acquisitionStartedAt=started,
                   sha256=hashlib.sha256(data).hexdigest(), file=destination.name,
                   lastModified=modified, bytes=len(data), status="SOURCE_DATA")
    destination.write_bytes(data)
    receipt_path.write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"source": source_id, "bytes": len(data), "sha256": receipt["sha256"],
                      "receipt": str(receipt_path)}), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", choices=SOURCES)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    acquire(args.source, args.output)
