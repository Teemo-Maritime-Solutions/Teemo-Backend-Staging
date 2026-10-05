"""Bounded NOAA ENC Direct regional snapshot; no coordinate input or silent mirrors."""
import argparse
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path
import re
from urllib.parse import urlencode, urlsplit
from urllib.request import Request, urlopen
import zipfile
import xml.etree.ElementTree as ET

from build_graph import encode

ROOT = "https://encdirect.noaa.gov/arcgis/rest/services/encdirect/"
COVERAGE = ROOT + "enc_coverage/MapServer/4"
HARBOUR = ROOT + "enc_harbour/MapServer"
AGREEMENT = "https://charts.noaa.gov/ENCs/ENC_Agreement.shtml"
HELP = "https://nauticalcharts.noaa.gov/learn/encdirect/"
CATALOG = "https://charts.noaa.gov/ENCs/NY_ENCProdCat.xml"
# IDs AND names verified against the published layer registry; renumbering fails closed.
LAYERS = {
    49: "Berth_point", 57: "Pilot_Boarding_Place_point", 84: "Coastline_line",
    85: "Shoreline_Construction_line", 87: "Bridge_line", 116: "Berth_line",
    121: "Gate_line", 133: "Recommended_Route_Centerline_line", 134: "Recommended_Track_line",
    138: "Shoreline_Construction_area", 141: "Bridge_area", 156: "Obstruction_area",
    158: "Wreck_area", 169: "Berth_area", 177: "Gate_area", 197: "Restricted_Area_area",
    208: "Fairway_area", 214: "Traffic_Separation_Zone_area",
    215: "Traffic_Separation_Scheme_Lane_Part_area", 219: "Coverage_area",
    223: "Quality_of_Data_area", 224: "Sounding_Datum_area", 227: "Depth_Area",
    228: "Dredged_Area", 230: "Unsurveyed_Area", 233: "Land_Area",
    33: "Obstruction_point", 34: "Underwater_Awash_Rock_point", 36: "Wreck_point",
    99: "Obstruction_line",
}
# Versioned expansion: never reinterpret the completeness of archived V1 snapshots.
LAYERS_V2 = {**LAYERS, 18: "Shoreline_Construction_point", 28: "Pylon_Bridge_Support_point",
             38: "Land_Area_point", 53: "Gate_point", 55: "Hulk_point", 58: "Pile_point",
             149: "Pylon_Bridge_Support_area", 154: "Caution_Area",
             176: "Floating_Dock_area", 180: "Hulk_area"}


def layer_registry(version):
    if type(version) is not int or version not in (1, 2):
        raise ValueError("Unsupported source layer registry version")
    return LAYERS if version == 1 else LAYERS_V2


class SourceClient:
    def __init__(self, max_bytes=200000000, max_response_bytes=25000000):
        self.responses = []
        self.max_bytes, self.max_response_bytes = max_bytes, max_response_bytes
        self.bytes = 0

    def request(self, base, parameters=None, text=False):
        url = base + ("?" + urlencode(parameters) if parameters else "")
        parsed = urlsplit(url)
        if parsed.scheme != "https" or parsed.hostname not in ("encdirect.noaa.gov", "charts.noaa.gov", "nauticalcharts.noaa.gov") or parsed.username:
            raise ValueError("Only public fixed NOAA HTTPS sources allowed")
        started = datetime.now(timezone.utc).isoformat()
        with urlopen(Request(url, headers={"User-Agent": "Teemo-Maritime-Research/1.0"}), timeout=45) as response:
            final = urlsplit(response.url)
            if final.scheme != "https" or final.hostname not in ("encdirect.noaa.gov", "charts.noaa.gov", "nauticalcharts.noaa.gov"):
                raise ValueError("Unexpected NOAA redirect")
            data = response.read(self.max_response_bytes + 1)
            modified = response.headers.get("Last-Modified")
        self.bytes += len(data)
        if len(data) > self.max_response_bytes or self.bytes > self.max_bytes:
            raise ValueError("Download resource bound exceeded; snapshot not published")
        parsed_data = None if text else json.loads(data)
        if not text and (not isinstance(parsed_data, dict) or "error" in parsed_data):
            raise ValueError("NOAA API error or unexpected schema; snapshot not published")
        self.responses.append(dict(url=url, acquiredAt=started, completedAt=datetime.now(timezone.utc).isoformat(),
                                   lastModified=modified, sha256=hashlib.sha256(data).hexdigest(), data=data))
        return data if text else parsed_data


def feature_ids(client, url, where, limit, oid_field="OBJECTID"):
    response = client.request(url + "/query", dict(f="json", where=where, returnIdsOnly="true"))
    ids = response.get("objectIds")
    # ArcGIS represents a successful zero-row query with null instead of [].
    if ids is None and response.get("objectIdFieldName") == oid_field and "objectIds" in response:
        ids = []
    if response.get("exceededTransferLimit") or not isinstance(ids, list) or len(ids) > limit:
        raise ValueError("Missing/truncated/oversized feature ID set: " + json.dumps(dict(
            idType=type(ids).__name__, count=len(ids) if isinstance(ids, list) else None,
            exceeded=response.get("exceededTransferLimit"), limit=limit)))
    if any(type(i) is not int or i < 0 for i in ids) or len(set(ids)) != len(ids):
        raise ValueError("Invalid or duplicate feature IDs")
    if response.get("objectIdFieldName") != oid_field:
        raise ValueError("Unsupported object ID schema")
    return sorted(ids)


def download_layer(client, url, where, expected_name, limit=20000, batch_size=50):
    # Bound encoded GET query size; large object-ID lists can hit server URL limits.
    if type(batch_size) is not int or not 1 <= batch_size <= 50:
        raise ValueError("Feature batch size must be an integer in [1, 50]")
    metadata = client.request(url, dict(f="json"))
    if metadata.get("name") != expected_name or metadata.get("type") != "Feature Layer":
        raise ValueError("NOAA layer registry changed: " + expected_name)
    fields = {f["name"] for f in metadata.get("fields", [])}
    oid_fields = [f["name"] for f in metadata.get("fields", []) if f.get("type") == "esriFieldTypeOID"]
    if "DSNM" not in fields or len(oid_fields) != 1:
        raise ValueError("Missing chart/feature provenance identifiers")
    oid_field = oid_fields[0]
    ids = feature_ids(client, url, where, limit, oid_field)
    features = []
    for offset in range(0, len(ids), batch_size):
        batch = ids[offset:offset + batch_size]
        response = client.request(url + "/query", dict(f="geojson", objectIds=",".join(map(str, batch)),
                                  outFields="*", outSR=4326, returnGeometry="true", returnZ="false", returnM="false"))
        if response.get("type") != "FeatureCollection" or response.get("exceededTransferLimit"):
            raise ValueError("Truncated/unexpected GeoJSON")
        rows = response.get("features", [])
        received = [r.get("properties", {}).get(oid_field) for r in rows]
        if sorted(received) != batch or len(set(received)) != len(received):
            raise ValueError("Missing/duplicate/unrequested features in batch")
        if any(r.get("geometry") is None for r in rows):
            raise ValueError("Source geometry unavailable")
        features.extend(rows)
    if feature_ids(client, url, where, limit, oid_field) != ids:
        raise ValueError("Live service membership changed during snapshot")
    return dict(metadata=metadata, objectIdField=oid_field, where=where, features=sorted(features, key=lambda f: f["properties"][oid_field]))


def select_catalog(data, title, max_cells):
    if b"<!DOCTYPE" in data.upper() or b"<!ENTITY" in data.upper():
        raise ValueError("DTD/entity declarations not accepted")
    root = ET.fromstring(data)
    if root.tag != "EncProductCatalogNY":
        raise ValueError("Unexpected NOAA NY catalogue schema")
    selected = []
    for cell in root.findall("cell"):
        name, label = cell.findtext("name", ""), cell.findtext("lname", "")
        if cell.findtext("status") == "Active" and name.startswith("US5") and title.casefold() in label.casefold():
            if not re.fullmatch(r"US5[A-Z0-9]{5}", name):
                raise ValueError("Unsupported source chart identifier")
            selected.append({k: cell.findtext(k) for k in ("name", "lname", "status", "cscale", "edtn", "updn", "uadt", "isdt")})
    if not selected or len(selected) > max_cells or len({c["name"] for c in selected}) != len(selected):
        raise ValueError("Missing/oversized/duplicate active harbour chart selection")
    return sorted(selected, key=lambda c: c["name"])


def acquire(title, output, max_cells=16, registry_version=1):
    registry = layer_registry(registry_version)
    if not re.fullmatch(r"[A-Za-z0-9 -]{3,80}", title) or not 1 <= max_cells <= 64:
        raise ValueError("Explicit bounded chart-title selection required")
    if output.exists():
        raise ValueError("Snapshot path exists; select a new immutable name")
    client = SourceClient()
    agreement = client.request(AGREEMENT, text=True)
    client.request(HELP, text=True)
    catalog = select_catalog(client.request(CATALOG, text=True), title, max_cells)
    cells = [c["name"] + ".000" for c in catalog]
    where = "DSNM IN (" + ",".join("'" + c + "'" for c in cells) + ")"
    selection = download_layer(client, COVERAGE, where, "Harbor.Coverage_area", 200)
    if set(f["properties"]["DSNM"] for f in selection["features"]) != set(cells):
        raise ValueError("Active catalogue cells absent from GIS coverage")
    print(json.dumps(dict(stage="source_chart_selection", cells=cells,
                          titles=[c["lname"] for c in catalog])), flush=True)
    layers = {}
    for identifier, name in sorted(registry.items()):
        layers[str(identifier)] = download_layer(client, HARBOUR + "/" + str(identifier), where, "Harbor." + name)
        print(json.dumps(dict(stage="enc_layer", layer=identifier, name=name,
                              features=len(layers[str(identifier)]["features"]))), flush=True)
    payload = encode(dict(schemaVersion=1, layerRegistryVersion=registry_version, purpose="REGIONAL_RESEARCH_NOT_NAVIGATION", selection=selection, catalog=catalog, cells=cells, layers=layers))
    output.mkdir(parents=True, exist_ok=False)
    archive_path = output / "enc-source.zip"
    with zipfile.ZipFile(archive_path, "x", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("source.json", payload)
        archive.writestr("notices/ENC_Agreement.html", agreement)
        requests = []
        for i, response in enumerate(client.responses):
            name = f"responses/{i:04d}.raw"
            archive.writestr(name, response["data"])
            requests.append({**{k: v for k, v in response.items() if k != "data"}, "file": name})
        archive.writestr("requests.json", encode(requests))
    with archive_path.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256").hexdigest()
    receipt = dict(id="noaa-enc-regional", url=HARBOUR, license="NOAA ENC User Agreement; attribution/redistribution conditions retained; local research only, not official redistributed ENC",
                   licenseUrl=AGREEMENT, version="Live Harbour GIS snapshot, exact requests/features pinned by content hashes",
                   acquiredAt=client.responses[-1]["completedAt"], acquisitionStartedAt=client.responses[0]["acquiredAt"],
                   unit="GeoJSON longitude/latitude WGS84 degrees; attributes retain original S-57 encoding",
                   coverage="Harbour chart features selected by exact DSNM: " + ", ".join(cells),
                   coverageSelection=dict(titleSelection=title, cells=cells, scaleBand="Harbour", sourceFeatureSelection="DSNM"),
                   limitations=["GIS conversion not certified for navigation", "SORDAT may be old or absent; retrieval is not survey time",
                                "Live server has no atomic snapshot guarantee; ID membership checked before/after each layer",
                                "Empty layer means no features returned for these cells, not worldwide absence of hazards",
                                "No inferred current permissions, tide, vessel clearance, insurance or AIS observations"],
                   file=archive_path.name, sha256=digest, bytes=archive_path.stat().st_size, status="SOURCE_DATA",
                   acquirerSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest())
    with (output / "enc-source.zip.receipt.json").open("xb") as stream:
        stream.write(encode(receipt) + b"\n")
    print(json.dumps(dict(stage="complete", sha256=digest, bytes=receipt["bytes"], cells=cells)), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--title")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--inspect-coverage", action="store_true", help="Read-only chart naming/schema diagnostic")
    parser.add_argument("--inspect-catalog", action="store_true", help="Read-only official NY chart catalogue diagnostic")
    parser.add_argument("--max-cells", type=int, default=16)
    parser.add_argument("--registry-version", type=int, choices=(1, 2), default=1)
    args = parser.parse_args()
    if args.inspect_catalog:
        data = SourceClient().request(CATALOG, text=True)
        root = ET.fromstring(data)
        print(json.dumps(dict(root=root.tag, bytes=len(data), children=[dict(tag=c.tag, text=(c.text or '').strip()[:150]) for c in list(root)[:8]])))
        print(json.dumps(select_catalog(data, "New York Harbor", 16)), flush=True)
    elif args.inspect_coverage:
        result = SourceClient().request(COVERAGE + "/query", dict(f="json", where="1=1", outFields="DSNM,TITLE",
                                          returnGeometry="false", resultRecordCount=20, orderByFields="OBJECTID"))
        print(json.dumps(result), flush=True)
    else:
        if not args.title or args.output is None: parser.error("--title and --output required")
        acquire(args.title, args.output, args.max_cells, args.registry_version)
