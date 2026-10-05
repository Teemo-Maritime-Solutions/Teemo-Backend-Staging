"""Quarantine source country/coordinate mismatches without changing a single coordinate."""
import argparse
import hashlib
import io
import json
import math
import shutil
import zipfile
from collections import Counter, defaultdict
from pathlib import Path

import shapefile
from shapely.geometry import Point, shape
from shapely.ops import nearest_points

from build_graph import GEOD, encode, verify_receipt

AUDITOR_SHA256 = hashlib.sha256(Path(__file__).read_bytes()).hexdigest()


def read_units(path):
    units = defaultdict(list)
    with zipfile.ZipFile(path) as z:
        prefix = "ne_10m_admin_0_map_units"
        version = z.read(prefix + ".VERSION.txt").decode().strip()
        if version != "5.1.1":
            raise ValueError("Unexpected country source version")
        reader = shapefile.Reader(**{ext: io.BytesIO(z.read(prefix + "." + ext)) for ext in ("shp", "shx", "dbf")})
        for row in reader.iterShapeRecords():
            attributes = row.record.as_dict()
            code = attributes.get("ISO_A2_EH")
            if not isinstance(code, str) or len(code) != 2:
                continue
            geometry = shape(row.shape.__geo_interface__)
            if not geometry.is_valid or geometry.is_empty:
                # Missing QA is not a license to accept coordinates for this feature.
                continue
            units[code].append(geometry)
    return units


def consistent(point, country_geometries, max_distance_m):
    if not country_geometries:
        return "COUNTRY_GEOMETRY_UNAVAILABLE", None
    distance = math.inf
    for geometry in country_geometries:
        for shift in (-360, 0, 360):
            candidate = Point(point[0] + shift, point[1])
            if geometry.covers(candidate):
                return "COUNTRY_SANITY_PASS_NOT_BERTH_VALIDATION", 0
            nearest = nearest_points(geometry, candidate)[0]
            lon = (nearest.x + 180) % 360 - 180
            distance = min(distance, GEOD.inv(*point, lon, nearest.y)[2])
    return ("COUNTRY_SANITY_PASS_NOT_BERTH_VALIDATION" if distance <= max_distance_m
            else "COUNTRY_COORDINATE_MISMATCH"), distance


def audit(artifact, receipt_path, output, max_distance_m, validated_report=None):
    if not math.isfinite(max_distance_m) or not 0 < max_distance_m <= 500000:
        raise ValueError("Country QA threshold must be explicitly in (0, 500000] metres")
    country_path, source = verify_receipt(receipt_path)
    if source["id"] != "country-qa":
        raise ValueError("Unsupported country source")
    units = read_units(country_path)
    if output.exists():
        raise ValueError("Output already exists; snapshots are immutable")
    with zipfile.ZipFile(artifact) as z:
        manifest = json.loads(z.read("manifest.json"))
        base_version = manifest["graphVersion"]
        for name, digest in manifest["tables"].items():
            with z.open(name) as table:
                if hashlib.file_digest(table, "sha256").hexdigest() != digest:
                    raise ValueError("Corrupt source artifact")
        ports = [json.loads(row) for row in z.open("ports.jsonl")]
        results, excluded = Counter(), []
        for port in ports:
            if port["point"] is None:
                continue
            status, upper_bound = consistent(port["point"], units.get(port["country"]), max_distance_m)
            results[status] += 1
            port["countryQa"] = dict(status=status, distanceUpperBoundM=upper_bound, sourceId=source["id"])
            port["sourceIds"] = sorted(set(port["sourceIds"] + [source["id"]]))
            if status != "COUNTRY_SANITY_PASS_NOT_BERTH_VALIDATION":
                if port["status"] == "CONNECTED_REFERENCE_POINT":
                    excluded.append(dict(id=port["id"], name=port["name"], originalPoint=port["point"], reason=status))
                port["priorStatus"] = port["status"]
                port["status"] = status
        ports_bytes = b"".join(encode(p) + b"\n" for p in ports)
        manifest["sources"].append(source)
        manifest["tables"]["ports.jsonl"] = hashlib.sha256(ports_bytes).hexdigest()
        manifest["portCountryQa"] = dict(sourceId=source["id"], maxDistanceM=max_distance_m,
                                          auditorSha256=AUDITOR_SHA256,
                                          results=dict(results), previouslyConnectedExcluded=excluded,
                                          baseGraphVersion=base_version,
                                          limitations="Coarse country sanity only; no verified berth/approach or sovereignty determination")
        manifest["report"]["portStatuses"] = dict(Counter(p["status"] for p in ports))
        manifest["createdAt"] = max(s["acquiredAt"] for s in manifest["sources"])
        manifest.pop("graphVersion")
        manifest["graphVersion"] = hashlib.sha256(encode(manifest)).hexdigest()
        output.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(output, "x", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as target:
            for name in sorted(z.namelist()):
                entry = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
                entry.compress_type = zipfile.ZIP_DEFLATED
                if name == "manifest.json":
                    target.writestr(entry, encode(manifest))
                elif name == "ports.jsonl":
                    target.writestr(entry, ports_bytes)
                else:
                    # Same table bytes/hash: no new edge and no coordinate edit.
                    with z.open(name) as input_file, target.open(entry, "w") as output_file:
                        shutil.copyfileobj(input_file, output_file, 65536)
        if validated_report:
            validation = json.loads(validated_report.read_text())
            if validation["graphVersion"] != base_version or validation["geometricCheck"] != "PASS":
                raise ValueError("Can only inherit a passing geometric check from the exact unchanged topology")
            validation["graphVersion"] = manifest["graphVersion"]
            validation["inheritedUnchangedGeometryFrom"] = base_version
            validation["unchangedGeometryTables"] = {n: manifest["tables"][n] for n in ("nodes.jsonl", "edges.jsonl")}
            validation["coveredPortCount"] = sum(p["status"] == "CONNECTED_REFERENCE_POINT" for p in ports)
            covered = [p for p in ports if p["status"] == "CONNECTED_REFERENCE_POINT"]
            validation["countriesCovered"] = sorted(set(p["country"] for p in covered))
            validation["hemispheres"] = {"north": sum(p["point"][1] >= 0 for p in covered),
                                          "south": sum(p["point"][1] < 0 for p in covered),
                                          "east": sum(p["point"][0] >= 0 for p in covered),
                                          "west": sum(p["point"][0] < 0 for p in covered)}
            validation["artifactBytes"] = output.stat().st_size
            destination = output.with_suffix(".validation.json")
            with destination.open("xb") as f:
                f.write(encode(validation) + b"\n")
    print(json.dumps(dict(graphVersion=manifest["graphVersion"], portStatuses=manifest["report"]["portStatuses"],
                          previouslyConnectedExcluded=len(excluded), countryQaResults=dict(results))), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--artifact", type=Path, required=True)
    parser.add_argument("--country-receipt", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--max-country-distance-m", type=float, required=True)
    parser.add_argument("--validated-report", type=Path)
    args = parser.parse_args()
    audit(args.artifact, args.country_receipt, args.output, args.max_country_distance_m, args.validated_report)
