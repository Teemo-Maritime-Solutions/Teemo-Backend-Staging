"""Synthetic failure, provenance and cohort cases; not real phase-3 evidence."""
from collections import Counter
from dataclasses import replace
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from audit_external_access import classify_dock, read_evidence
from build_enc_pilot import mesh, PilotConfig
from build_graph import encode
from phase3_sources import download_features, id_set, Links, validate_url
from validate_regional_ais import acceptance, FROZEN_CONFIG, regional_tracks, validate
from dataclasses import asdict
from synthetic_fixtures import synthetic_enc_source


class Replies:
    def __init__(self, values): self.values = iter(values)
    def request(self, *args, **kwargs): return next(self.values)


class Phase3SourcesTests(unittest.TestCase):
    def test_public_source_destinations_are_bounded_before_redirect(self):
        validate_url("https://www.navcen.uscg.gov/msi")
        for url in ("http://www.navcen.uscg.gov/msi", "https://example.com", "https://www.navcen.uscg.gov.evil.test", "https://a:b@www.navcen.uscg.gov/msi", "https://www.navcen.uscg.gov:444/msi"):
            with self.assertRaises(ValueError): validate_url(url)

    def test_id_set_rejects_missing_duplicate_truncated_and_boolean_ids(self):
        valid = dict(objectIdFieldName="OID", objectIds=[2, 1])
        self.assertEqual([1, 2], id_set(valid, "OID"))
        self.assertEqual([], id_set(dict(valid, objectIds=None), "OID"))
        for reply in ({}, dict(valid, objectIds=[True]), dict(valid, objectIds=[1, 1]), dict(valid, exceededTransferLimit=True)):
            with self.assertRaises(ValueError): id_set(reply, "OID")

    def test_feature_acquisition_rejects_batch_loss_and_membership_change(self):
        ids = dict(objectIdFieldName="OID", objectIds=[1])
        feature = dict(type="Feature", properties=dict(OID=1), geometry=dict(type="Point", coordinates=[0, 0]))
        batch = dict(type="FeatureCollection", features=[feature])
        actual = download_features(Replies([ids, batch, ids]), "SYNTHETIC", {}, "OID", [-1, -1, 1, 1])
        self.assertEqual([feature], actual["features"])
        for changed in (dict(batch, features=[]), dict(batch, features=[feature, feature]), dict(batch, exceededTransferLimit=True)):
            with self.assertRaises(ValueError): download_features(Replies([ids, changed, ids]), "SYNTHETIC", {}, "OID", [-1, -1, 1, 1])
        with self.assertRaises(ValueError): download_features(Replies([ids, batch, dict(ids, objectIds=[2])]), "SYNTHETIC", {}, "OID", [-1, -1, 1, 1])

    def test_published_notice_links_preserve_all_geometry_types(self):
        links = Links()
        links.feed('<a href="/sites/default/files/msi/safeZoneLine_1.geojson">L</a><a href="/sites/default/files/msi/safeZonePoly_1.geojson">P</a><a href="/unrelated.geojson">X</a>')
        self.assertEqual(2, len(links.links))
        self.assertTrue(any("Line" in url for url in links.links))

    def test_water_reference_never_becomes_verified_terminal(self):
        nodes, _, _, _, domain = mesh(synthetic_enc_source(), dict(id="SYNTHETIC"), PilotConfig())
        point = next(n["point"] for n in nodes if n["kind"] == "REGIONAL_RESEARCH_MESH")
        feature = dict(type="Feature", geometry=dict(type="Point", coordinates=point), properties=dict(OBJECTID=1, NAV_UNIT_ID="SYNTHETIC", NAV_UNIT_NAME="SYNTHETIC DOCK"))
        row = classify_dock(feature, domain, nodes)
        self.assertEqual(point, row["point"])
        self.assertEqual("NOT_VERIFIED", row["terminalAccess"])
        self.assertEqual("NOT_VERIFIED", row["noaaAssociation"])
        self.assertGreater(len(row["connectors"]), 0)

    def test_resealed_receipt_cannot_hide_changed_response_hash(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            archive = root / "source.zip"
            with zipfile.ZipFile(archive, "x") as z:
                z.writestr("response.raw", b"CHANGED")
                z.writestr("requests.json", json.dumps([dict(file="response.raw", sha256=hashlib.sha256(b"ORIGINAL").hexdigest())]))
                z.writestr("source.json", json.dumps(dict(chartSourceSha256="SYNTHETIC_CHART")))
            receipt = dict.fromkeys(["id", "url", "license", "licenseUrl", "version", "acquiredAt", "unit", "coverage"], "SYNTHETIC")
            receipt.update(status="SOURCE_DATA", limitations=["SYNTHETIC"], file=archive.name, chartSourceSha256="SYNTHETIC_CHART", sha256=hashlib.sha256(archive.read_bytes()).hexdigest())
            path = root / "receipt.json"
            path.write_text(json.dumps(receipt))
            with self.assertRaises(ValueError): read_evidence(path, "SYNTHETIC", "SYNTHETIC_CHART")


class RegionalAisAcceptanceTests(unittest.TestCase):
    def measured(self, error=0):
        return dict(status="PHYSICAL_MODEL_MEASURED", discreteHausdorffM=error, discreteFrechetM=error)

    def test_no_empty_or_success_only_gate(self):
        self.assertEqual("FAIL", acceptance([])["status"])
        self.assertEqual("FAIL", acceptance([self.measured()]*9)["status"])
        cases = [self.measured()]*7 + [dict(status="NO_CLEAR_ANCHOR")]*3
        self.assertIn("INSUFFICIENT_MEASURABLE_COVERAGE", acceptance(cases)["reasons"])
        self.assertEqual(10, acceptance(cases)["selectedCases"])

    def test_error_and_water_conflicts_cannot_hide_in_mean(self):
        self.assertEqual("PASS", acceptance([self.measured(1000)]*10)["status"])
        for error in (1000.001, float("nan"), float("inf"), -1):
            self.assertEqual("FAIL", acceptance([self.measured()]*9 + [self.measured(error)])["status"])
        self.assertEqual("FAIL", acceptance([self.measured()]*9 + [dict(status="PREDICTED_CHART_WATER_CONFLICT")])["status"])

    def test_envelope_exit_and_conflicting_timestamp_split_observations(self):
        config = replace(FROZEN_CONFIG, min_track_distance_m=100, max_jump_m=5000)
        bbox = [-.01, -.01, .01, .01]
        west = [(0, (-.009, 0), 1), (60, (-.005, 0), 2), (120, (-.001, 0), 3)]
        east = [(240, (.001, 0), 5), (300, (.005, 0), 6), (360, (.009, 0), 7)]
        for middle in ([(180, (.02, 0), 4)], [(180, (.02, 0), 4), (180, (0, 0), 8)]):
            counts = Counter()
            tracks = list(regional_tracks(west + middle + east, config, bbox, counts))
            self.assertEqual(2, len(tracks))
            self.assertTrue(all(len(t) == 3 for t in tracks))

    def test_full_physical_diagnostic_keeps_restrictions_and_global_gate(self):
        """Exercise the real metric/search path, including a land-conflicting case."""
        source = synthetic_enc_source()
        chart = dict(id="SYNTHETIC_CHART", sha256="SYNTHETIC_CHART_HASH")
        nodes, edges, _, _, _ = mesh(source, chart, PilotConfig())
        # Both sides of the island, with an observed detour through northern water.
        clear = [(0, (-.005, 0), 1), (60, (-.005, .007), 2), (120, (.005, .007), 3), (180, (.005, 0), 4)]
        blocked = [(0, (-.005, 0), 5), (60, (0, 0), 6), (120, (.005, 0), 7)]
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            artifact, ais_path = root / "SYNTHETIC.zip", root / "SYNTHETIC.zst"
            ais_path.write_bytes(bytes.fromhex("28b52ffd"))
            payloads = {name: b"".join(encode(row)+b"\n" for row in records) for name, records in (("nodes.jsonl", nodes), ("edges.jsonl", edges))}
            manifest = dict(validationProfile="NOAA_ENC_REGIONAL_V1", parameters=asdict(PilotConfig()), sources=[chart], tables={k: hashlib.sha256(v).hexdigest() for k, v in payloads.items()})
            manifest["graphVersion"] = hashlib.sha256(encode(manifest)).hexdigest()
            with zipfile.ZipFile(artifact, "x") as archive:
                archive.writestr("manifest.json", encode(manifest))
                for name, value in payloads.items(): archive.writestr(name, value)
            config = root / "config.json"
            config.write_bytes(encode(asdict(FROZEN_CONFIG)))
            protocol = root / "SYNTHETIC.md"
            protocol.write_text("SYNTHETIC experiment")
            output = root / "result.json"
            ais = dict(id="noaa-ais-regional-march-holdout", url="https://SYNTHETIC/2024-03-01.csv.zst", sha256="SYNTHETIC_AIS")
            with patch("validate_regional_ais.verify_receipt", return_value=(ais_path, ais)), patch("validate_regional_ais.load_snapshot", return_value=(source, chart)), patch("validate_regional_ais.chart_bounds", return_value=([-.01, -.01, .01, .01], chart)), patch("validate_regional_ais.regional_observations", return_value=({"SYNTHETIC_CLEAR": clear, "SYNTHETIC_BLOCKED": blocked}, Counter())):
                validate(artifact, root / "ais.receipt", root / "chart.receipt", config, protocol, output)
            report = json.loads(output.read_text())
            self.assertEqual(1, report["statuses"]["PHYSICAL_MODEL_MEASURED"])
            self.assertEqual(1, report["statuses"]["OBSERVATION_CHART_WATER_CONFLICT"])
            measured = next(c for c in report["cases"] if c["status"] == "PHYSICAL_MODEL_MEASURED")
            self.assertTrue(measured["unevaluatedRestrictionIds"])
            self.assertEqual("PASSAGE_NOT_EVALUATED", measured["passageStatus"])
            self.assertGreaterEqual(measured["discreteFrechetM"], 0)
            self.assertEqual(0, report["operationalRoutes"])
            self.assertEqual("NOT_PASSED", report["validationGate"])
            with self.assertRaises(ValueError): validate(artifact, root / "ais.receipt", root / "chart.receipt", config, protocol, output)


if __name__ == "__main__": unittest.main()
