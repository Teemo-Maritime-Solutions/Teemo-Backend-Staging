"""Independent finer-shoreline check. Passing this is necessary, not sufficient, for phase 3."""
import argparse
import hashlib
import io
import json
import math
import time
import zipfile
from dataclasses import replace
from pathlib import Path

from build_graph import Config, GEOD, encode, geodesic, load_land, verify_receipt


def reciprocal_pairs(edges):
    """Validate both directions' geometry before claiming half the edges cover the whole mesh.

    This mesh validator deliberately rejects a non-reciprocal directed overlay; such
    an overlay needs a directed-edge validation report, not a misleading pair count.
    """
    seen = {}
    for edge in edges:
        a, b = edge["fromNode"], edge["toNode"]
        if a == b:
            raise ValueError("Self edge in physical mesh")
        key, flag = (min(a, b), max(a, b)), (1 if a < b else 2)
        geometry = edge["geometry"] if a < b else list(reversed(edge["geometry"]))
        digest = hashlib.sha256(encode([geometry, edge["distanceM"]])).digest()
        prior = seen.get(key)
        if prior:
            if prior[0] != digest or prior[1] & flag:
                raise ValueError("Reciprocal geometry mismatch or duplicate directed edge")
            seen[key] = (digest, prior[1] | flag)
        else:
            seen[key] = (digest, flag)
            yield edge
    if any(flag != 3 for _, flag in seen.values()):
        raise ValueError("Incomplete reciprocal mesh; directed overlays need their own validator")


def discrete_hausdorff_m(a, b):
    if not a or not b:
        raise ValueError("Nonempty source trajectories required")
    def directed(x, y):
        return max(min(GEOD.inv(*p, *q)[2] for q in y) for p in x)
    return max(directed(a, b), directed(b, a))


def discrete_frechet_m(a, b):
    if not a or not b:
        raise ValueError("Nonempty source trajectories required")
    previous = [math.inf] * len(b)
    for i, p in enumerate(a):
        current = [math.inf] * len(b)
        for j, q in enumerate(b):
            distance = GEOD.inv(*p, *q)[2]
            if i == j == 0:
                current[j] = distance
            else:
                current[j] = max(distance, min(previous[j], current[j - 1] if j else math.inf,
                                                previous[j - 1] if i and j else math.inf))
        previous = current
    return previous[-1]


def validate(artifact, receipt_path, output):
    started = time.perf_counter()
    source_path, source = verify_receipt(receipt_path)
    with zipfile.ZipFile(artifact) as z:
        manifest = json.loads(z.read("manifest.json"))
        config = replace(Config(**manifest["parameters"]), shoreline_resolution="f", geodesic_step_m=500)
        config.validate()
        land = load_land(source_path, config)
        for name, expected in manifest["tables"].items():
            with z.open(name) as file:
                if hashlib.file_digest(file, "sha256").hexdigest() != expected:
                    raise ValueError("Corrupt table: " + name)
        nodes = [json.loads(line) for line in z.open("nodes.jsonl")]
        ports = [json.loads(line) for line in z.open("ports.jsonl")]
        covered = [p for p in ports if p["status"] == "CONNECTED_REFERENCE_POINT"]
        invalid, samples, checked, bad_distances = 0, [], 0, 0
        for edge in reciprocal_pairs(json.loads(line) for line in z.open("edges.jsonl")):
            checked += 1
            distance = 0
            geometry = []
            for a, b in zip(edge["geometry"], edge["geometry"][1:]):
                metres, segment = geodesic(a, b, config.geodesic_step_m)
                distance += metres
                geometry.extend(segment if not geometry else segment[1:])
            if abs(edge["distanceM"] - distance) > max(.01, distance * 1e-8):
                bad_distances += 1
            if not land.clear(geometry):
                invalid += 1
                if len(samples) < 25:
                    samples.append(dict(fromId=nodes[edge["fromNode"]]["id"], toId=nodes[edge["toNode"]]["id"]))
            if checked % 50000 == 0:
                print(json.dumps({"checkedPairs": checked, "finerLandConflicts": invalid}), flush=True)
        report = dict(graphVersion=manifest["graphVersion"], independentShoreline="GSHHG full L1+L5",
                      sourceSha256=source["sha256"], validationGeodesicStepM=config.geodesic_step_m,
                      checkedReciprocalPairs=checked, finerLandConflicts=invalid, distanceMismatches=bad_distances,
                      reciprocalGeometryVerified=True,
                      validatorSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                      conflictExamples=samples, portCount=len(ports), coveredPortCount=len(covered),
                      countriesCovered=sorted(set(p["country"] for p in covered)),
                      hemispheres={"north": sum(p["point"][1] >= 0 for p in covered),
                                   "south": sum(p["point"][1] < 0 for p in covered),
                                   "east": sum(p["point"][0] >= 0 for p in covered),
                                   "west": sum(p["point"][0] < 0 for p in covered)},
                      geometricCheck="PASS" if invalid == bad_distances == 0 else "FAIL",
                      validationGate="NOT_PASSED", heldOutAis="UNAVAILABLE", channelValidation="UNAVAILABLE",
                      seconds=time.perf_counter() - started, artifactBytes=artifact.stat().st_size,
                      limitations=["Full GSHHG is finer but shares original lineage; not independent hydrographic truth",
                                   "No AIS ground truth or current channel permissions; not operationally validated"])
    if output.exists():
        raise ValueError("Validation report exists; choose a new path")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(encode(report) + b"\n")
    print(json.dumps(report), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--artifact", type=Path, required=True)
    parser.add_argument("--land-receipt", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    validate(args.artifact, args.land_receipt, args.output)
