"""Bounded public USACE/NAVCEN evidence acquisition for the existing NOAA pilot."""
import argparse
from datetime import datetime, timezone
import hashlib
from html.parser import HTMLParser
import json
from pathlib import Path
from urllib.parse import urlencode, urljoin, urlsplit
from urllib.error import HTTPError
from urllib.request import HTTPRedirectHandler, Request, build_opener
import zipfile

from shapely.geometry import shape
from build_enc_pilot import coverage_available, load_snapshot
from build_graph import encode

USACE_PAGE = "https://www.iwr.usace.army.mil/About/Technical-Centers/WCSC-Waterborne-Commerce-Statistics-Center/WCSC-Navigation-Infrastructure/"
ITEM_ROOT = "https://www.arcgis.com/sharing/rest/content/items/"
ITEMS = {
    "docks": ("23d91bd988ac4fc9943128965bddfa37", "Navigation Facilities (Dock)", "Docks", 0),
    "waterways": ("ace7645d305647448a84492a3b909d48", "Waterway Networks", "Waterway_Networks", 1),
}
SERVICE_ROOT = "https://services7.arcgis.com/n1YM8pTrFmm7L4hs/arcgis/rest/services/"
NAVCEN = "https://www.navcen.uscg.gov/msi"
HOSTS = {"www.iwr.usace.army.mil", "www.arcgis.com", "services7.arcgis.com", "www.navcen.uscg.gov", "spatial.usace.army.mil"}
EHYDRO = "https://spatial.usace.army.mil/opjarcgis/rest/services/ehydro/RecentSurveyBins/FeatureServer"


def validate_url(url):
    parsed = urlsplit(url)
    if parsed.scheme != "https" or parsed.hostname not in HOSTS or parsed.username or parsed.password or parsed.port not in (None, 443):
        raise ValueError("Unexpected public evidence URL")


class PublicRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        validate_url(newurl)
        return super().redirect_request(req, fp, code, msg, headers, newurl)


class Client:
    def __init__(self):
        self.responses = []
        self.total_bytes = 0
        self.opener = build_opener(PublicRedirect())

    def request(self, base, parameters=None, raw=False):
        url = base + ("?" + urlencode(parameters) if parameters else "")
        validate_url(url)
        started = datetime.now(timezone.utc).isoformat()
        with self.opener.open(Request(url, headers={"User-Agent": "Teemo-Maritime-Research/1.0"}), timeout=45) as response:
            data = response.read(25_000_001)
            modified = response.headers.get("Last-Modified")
        self.total_bytes += len(data)
        if len(data) > 25_000_000 or self.total_bytes > 100_000_000:
            raise ValueError("Public source byte budget exceeded")
        result = data if raw else json.loads(data)
        if not raw and (not isinstance(result, dict) or "error" in result):
            raise ValueError("Source API error or unknown schema")
        self.responses.append(dict(url=url, startedAt=started, completedAt=datetime.now(timezone.utc).isoformat(),
                                   lastModified=modified, data=data, sha256=hashlib.sha256(data).hexdigest()))
        return result


def chart_bounds(receipt_path):
    source, receipt = load_snapshot(receipt_path)
    bounds = [shape(f["geometry"]).bounds for f in source["layers"]["219"]["features"]
              if coverage_available(f["properties"].get("CATCOV"))]
    if not bounds:
        raise ValueError("No chart coverage")
    bbox = [min(b[0] for b in bounds), min(b[1] for b in bounds), max(b[2] for b in bounds), max(b[3] for b in bounds)]
    if bbox[2] - bbox[0] > 2 or bbox[3] - bbox[1] > 2:
        raise ValueError("Regional extent exceeded")
    return bbox, receipt


def usace_layer(client, kind):
    item_id, title, service_name, layer_id = ITEMS[kind]
    item = client.request(ITEM_ROOT + item_id, dict(f="json"))
    expected_url = SERVICE_ROOT + service_name + "/FeatureServer"
    if item.get("id") != item_id or item.get("title") != title or item.get("owner") != "usace_iwr_cac" or item.get("url") != expected_url:
        raise ValueError("USACE publisher/catalogue/service changed")
    url = expected_url + "/" + str(layer_id)
    metadata = client.request(url, dict(f="json"))
    oid_fields = [f["name"] for f in metadata.get("fields", []) if f.get("type") == "esriFieldTypeOID"]
    if metadata.get("type") != "Feature Layer" or len(oid_fields) != 1:
        raise ValueError("Missing published feature identity")
    return item, url, metadata, oid_fields[0]


def id_set(reply, oid):
    ids = reply.get("objectIds")
    if ids is None and "objectIds" in reply:
        ids = []
    if reply.get("objectIdFieldName") != oid or reply.get("exceededTransferLimit") or not isinstance(ids, list) or len(ids) > 5000:
        raise ValueError("Invalid/truncated regional ID set")
    if any(type(i) is not int or i < 0 for i in ids) or len(ids) != len(set(ids)):
        raise ValueError("Duplicate/invalid regional IDs")
    return sorted(ids)


def download_features(client, url, metadata, oid, bbox):
    selection = dict(f="json", where="1=1", geometry=",".join(map(str, bbox)), geometryType="esriGeometryEnvelope",
                     inSR=4326, spatialRel="esriSpatialRelIntersects", returnIdsOnly="true")
    ids = id_set(client.request(url + "/query", selection), oid)
    features = []
    for start in range(0, len(ids), 50):
        batch = ids[start:start + 50]
        reply = client.request(url + "/query", dict(f="geojson", objectIds=",".join(map(str, batch)),
                               outFields="*", outSR=4326, returnGeometry="true", returnZ="false", returnM="false"))
        rows = reply.get("features")
        if reply.get("type") != "FeatureCollection" or reply.get("exceededTransferLimit") or not isinstance(rows, list):
            raise ValueError("Invalid/truncated regional features")
        actual = [r.get("properties", {}).get(oid) for r in rows]
        if any(type(i) is not int for i in actual) or sorted(actual) != batch:
            raise ValueError("Missing/duplicated/unrequested source feature")
        if any(r.get("geometry") is None for r in rows):
            raise ValueError("Missing source geometry")
        features.extend(rows)
    if id_set(client.request(url + "/query", selection), oid) != ids:
        raise ValueError("Source membership changed during acquisition")
    return dict(type="FeatureCollection", features=sorted(features, key=lambda f: f["properties"][oid]),
                objectIdField=oid, metadata=metadata, selection=selection)


class Links(HTMLParser):
    def __init__(self):
        super().__init__()
        self.links = set()

    def handle_starttag(self, tag, attrs):
        if tag == "a":
            href = dict(attrs).get("href", "")
            if href.endswith(".geojson") and "/msi/safeZone" in href:
                self.links.add(urljoin(NAVCEN, href))


def publish(output, client, payload, source_id, url, license_text, license_url, chart_receipt):
    if output.exists():
        raise ValueError("Immutable evidence directory already exists")
    output.mkdir(parents=True, exist_ok=False)
    path = output / "source.zip"
    with zipfile.ZipFile(path, "x", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("source.json", encode(payload))
        requests = []
        for index, response in enumerate(client.responses):
            name = f"responses/{index:04d}.raw"
            archive.writestr(name, response["data"])
            requests.append(dict({k: v for k, v in response.items() if k != "data"}, file=name))
        archive.writestr("requests.json", encode(requests))
    receipt = dict(id=source_id, url=url, license=license_text, licenseUrl=license_url,
                   version="Exact response snapshot; original source metadata retained",
                   acquiredAt=client.responses[-1]["completedAt"], file=path.name,
                   sha256=hashlib.sha256(path.read_bytes()).hexdigest(), bytes=path.stat().st_size,
                   status="SOURCE_DATA", unit="GeoJSON WGS84 longitude/latitude degrees; original attributes retained",
                   coverage="USACE features intersecting pinned NOAA coverage envelope, or complete published NAVCEN safety-zone files",
                   chartSourceSha256=chart_receipt["sha256"],
                   limitations=["Source references do not certify water-side terminal access or passage permission",
                                "USACE national-scale points and representative network links may be unsuitable locally",
                                "NAVCEN publication status Approved is publication approval, not vessel passage authorization",
                                "Missing notice is not proof of absence; temporal fields do not fully encode enforcement conditions",
                                "Live sources are not transactional snapshots; source dates can precede retrieval"],
                   acquirerSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest())
    (output / "source.zip.receipt.json").write_bytes(encode(receipt) + b"\n")
    print(json.dumps(dict(source=source_id, sha256=receipt["sha256"], bytes=receipt["bytes"],
                          features=len(payload.get("features", [])), files=len(payload.get("files", [])))), flush=True)


def acquire(kind, chart_receipt_path, output):
    if output.exists():
        raise ValueError("Choose a new immutable evidence directory")
    bbox, chart_receipt = chart_bounds(chart_receipt_path)
    client = Client()
    if kind in ITEMS:
        discovery = dict(url=USACE_PAGE, status="DOWNLOADED")
        try:
            client.request(USACE_PAGE, raw=True)
        except HTTPError as exc:
            if exc.code != 403:
                raise
            # The public index is a discovery citation, not the actual data/terms.
            # ArcGIS publisher identity and original item licence remain mandatory.
            discovery.update(status="HTTP_403", checkedAt=datetime.now(timezone.utc).isoformat())
        item, url, metadata, oid = usace_layer(client, kind)
        payload = download_features(client, url, metadata, oid, bbox)
        payload.update(item=item, chartBounds=bbox, chartSourceSha256=chart_receipt["sha256"], discoveryPage=discovery)
        publish(output, client, payload, "usace-" + kind, url, item.get("licenseInfo") or "USACE public catalogue; retain original publisher notices; local research snapshot",
                ITEM_ROOT + item["id"], chart_receipt)
    else:
        page = client.request(NAVCEN, raw=True)
        links = Links()
        links.feed(page.decode("utf-8"))
        if not 3 <= len(links.links) <= 30:
            raise ValueError("NAVCEN safety-zone file registry unavailable or changed")
        files = []
        for url in sorted(links.links):
            try:
                reply = client.request(url)
            except HTTPError as exc:
                if exc.code != 404:
                    raise
                files.append(dict(url=url, status="HTTP_404", checkedAt=datetime.now(timezone.utc).isoformat()))
                continue
            if reply.get("type") != "FeatureCollection" or not isinstance(reply.get("features"), list):
                raise ValueError("NAVCEN feature schema changed")
            files.append(dict(url=url, status="DOWNLOADED", content=reply))
        publish(output, client, dict(files=files, completeness="COMPLETE_PUBLISHED_FILE_SET" if all(f["status"] == "DOWNLOADED" for f in files) else "INCOMPLETE_PUBLISHED_FILE_SET",
                                    chartBounds=bbox, chartSourceSha256=chart_receipt["sha256"]),
                "navcen-safety-zones", NAVCEN, "US Coast Guard public maritime safety information; local evidence snapshot retaining publisher text", NAVCEN, chart_receipt)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("kind", choices=[*ITEMS, "safety-zones", "survey-index"])
    parser.add_argument("--chart-receipt", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--inspect", action="store_true")
    args = parser.parse_args()
    if args.kind == "survey-index":
        if not args.inspect:
            parser.error("Survey index supports read-only --inspect only")
        response = Client().request(EHYDRO, dict(f="json"))
        print(json.dumps(response), flush=True)
    elif args.inspect and args.kind in ITEMS:
        item, url, metadata, oid = usace_layer(Client(), args.kind)
        print(json.dumps(dict(title=item["title"], url=url, objectIdField=oid, geometry=metadata.get("geometryType"),
                              fields=[dict(name=f["name"], alias=f.get("alias")) for f in metadata.get("fields", [])])), flush=True)
    else:
        if args.chart_receipt is None or args.output is None:
            parser.error("--chart-receipt and --output required")
        acquire(args.kind, args.chart_receipt, args.output)
