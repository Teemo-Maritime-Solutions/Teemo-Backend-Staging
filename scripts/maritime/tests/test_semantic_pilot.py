"""SYNTHETIC rules, declarations, terminal associations and coordinates ONLY."""
from copy import deepcopy
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from build_enc_pilot import PilotConfig, mesh
from semantic_pilot import Resolution, connect_endpoints, digest, instant, shortest
from synthetic_fixtures import synthetic_enc_source

START, AT, END = "2000-01-01T00:00:00Z", "2000-01-02T00:00:00Z", "2000-01-03T00:00:00Z"


def review():
    return dict(reviewer="SYNTHETIC REVIEWER", reason="SYNTHETIC evidence", reviewedAt=START,
                validFrom=START, validUntil=END, sourceIds=["SYNTHETIC"])


def citation():
    return dict(sourceId="SYNTHETIC", locator="SYNTHETIC fixture", explanation="SYNTHETIC assertion")


def rule(key, feature, kind="CONDITIONAL_RESTRICTION"):
    return dict(review(), featureId=key, featureSha256=digest(feature), classification=kind,
                regulatoryEvidence=citation(), regulatoryCitation="SYNTHETIC regulation",
                conditions=[dict(key="transitOnly", equals=True)] if kind in ("CONDITIONAL_RESTRICTION", "NAVIGABLE_WITH_RULES") else [])


def curation(rules):
    return dict(schemaVersion=1, sourceSha256="SYNTHETIC", baseGraphVersion="SYNTHETIC", rules=rules, endpoints=[])


def context(value=True):
    return dict(at=AT, facts=dict(transitOnly=dict(value=value, source="USER_SUPPLIED", providedBy="SYNTHETIC USER",
                                                  providedAt=START, validFrom=START, validUntil=END)))


class SemanticPilotTests(unittest.TestCase):
    def test_all_five_classifications_have_explicit_behavior(self):
        features = {"A": dict(type="SYNTHETIC")}
        for kind, expected in {"ABSOLUTE_BLOCK": False, "CONDITIONAL_RESTRICTION": True,
                               "NAVIGABLE_WITH_RULES": True, "INFORMATIONAL": True, "UNRESOLVED_BLOCK": False}.items():
            resolution = Resolution(curation([rule("A", features["A"], kind)]), "SYNTHETIC", "SYNTHETIC", features, {"SYNTHETIC"})
            self.assertEqual(expected, resolution.evaluate(["A"], context())["allowed"])

    def test_unknown_conditions_and_false_values_never_allow(self):
        features = {"SYNTHETIC": dict(type="SYNTHETIC")}
        r = Resolution(curation([rule("SYNTHETIC", features["SYNTHETIC"])]), "SYNTHETIC", "SYNTHETIC", features, {"SYNTHETIC"})
        self.assertTrue(r.evaluate(["SYNTHETIC"], context())["allowed"])
        for ctx in (dict(at=AT), context(False), context(1)):
            self.assertFalse(r.evaluate(["SYNTHETIC"], ctx)["allowed"])
        self.assertFalse(r.evaluate(["UNKNOWN"], context())["allowed"])

    def test_informational_does_not_override_a_superimposed_block(self):
        features = {k: dict(type="SYNTHETIC") for k in ("A", "B")}
        r = Resolution(curation([rule("A", features["A"], "INFORMATIONAL"), rule("B", features["B"], "ABSOLUTE_BLOCK")]),
                       "SYNTHETIC", "SYNTHETIC", features, {"SYNTHETIC"})
        self.assertTrue(r.evaluate(["A"], context())["allowed"])
        self.assertFalse(r.evaluate(["A", "B"], context())["allowed"])

    def test_missing_provenance_changed_source_and_empty_conditions_fail(self):
        features = {"A": dict(type="SYNTHETIC")}
        valid = rule("A", features["A"])
        for change in (dict(featureSha256="CHANGED"), dict(sourceIds=[]), dict(conditions=[]), dict(reviewer=""),
                       dict(classification="ALLOW"), dict(regulatoryEvidence={}), dict(validUntil=START)):
            with self.assertRaises(ValueError):
                Resolution(curation([dict(valid, **change)]), "SYNTHETIC", "SYNTHETIC", features, {"SYNTHETIC"})
        with self.assertRaises(ValueError): Resolution(curation([valid]), "CHANGED", "SYNTHETIC", features, {"SYNTHETIC"})
        with self.assertRaises(ValueError): Resolution(curation([valid, valid]), "SYNTHETIC", "SYNTHETIC", features, {"SYNTHETIC"})

    def test_expired_review_or_declaration_is_blocked(self):
        features = {"A": dict(type="SYNTHETIC")}
        r = Resolution(curation([rule("A", features["A"])]), "SYNTHETIC", "SYNTHETIC", features, {"SYNTHETIC"})
        self.assertFalse(r.evaluate(["A"], dict(context(), at=END))["allowed"])
        expired = context()
        expired["facts"]["transitOnly"]["validUntil"] = AT
        self.assertFalse(r.evaluate(["A"], expired)["allowed"])
        future = context()
        future["facts"]["transitOnly"]["providedAt"] = END
        self.assertFalse(r.evaluate(["A"], future)["allowed"])

    def test_permission_evidence_is_bound_to_document_zone_vessel_and_voyage(self):
        features = {"A": dict(type="SYNTHETIC")}
        entry = rule("A", features["A"])
        entry["conditions"][0].update(requiredSource="SOURCE_DATA", requiredScope="PASSAGE")
        r = Resolution(curation([entry]), "SYNTHETIC", "SYNTHETIC", features, {"SYNTHETIC": dict(sha256="SYNTHETIC_HASH")})
        ctx = context()
        ctx.update(vesselId="SYNTHETIC_VESSEL", voyageId="SYNTHETIC_VOYAGE")
        fact = ctx["facts"]["transitOnly"]
        fact.update(source="SOURCE_DATA", sourceId="SYNTHETIC", evidenceSha256="SYNTHETIC_HASH", locator="SYNTHETIC page 1",
                    scope=dict(featureId="A", vesselId=ctx["vesselId"], voyageId=ctx["voyageId"]))
        self.assertTrue(r.evaluate(["A"], ctx)["allowed"])
        for changed in (dict(evidenceSha256="CHANGED"), dict(source="USER_SUPPLIED"), dict(locator=""),
                        dict(scope=dict(fact["scope"], vesselId="ANOTHER_SYNTHETIC_VESSEL")),
                        dict(scope=dict(fact["scope"], featureId="B"))):
            broken = deepcopy(ctx)
            broken["facts"]["transitOnly"].update(changed)
            self.assertFalse(r.evaluate(["A"], broken)["allowed"])

    def test_water_endpoints_are_distinct_from_unchanged_terminal_labels(self):
        nodes, edges, terminals, _, domain = mesh(synthetic_enc_source(), dict(id="SYNTHETIC"), PilotConfig())
        original_terminals, original_nodes, original_edges = deepcopy(terminals), deepcopy(nodes), deepcopy(edges)
        endpoints = [dict(review(), id="SYNTHETIC_WATER_" + str(i), terminalId=terminals[i]["id"], point=point,
                          coordinateEvidence=citation(), associationEvidence=citation())
                     for i, point in enumerate(([ -.004, 0], [.004, 0]))]
        connected = connect_endpoints(nodes, edges, terminals, endpoints, domain, {"SYNTHETIC"}, instant(AT))
        self.assertTrue(all(e["status"] == "CONNECTED_WATER_APPROACH" for e in connected))
        self.assertEqual(original_terminals, terminals)
        self.assertEqual(original_nodes, nodes[:len(original_nodes)])
        self.assertEqual(original_edges, edges[:len(original_edges)])
        self.assertTrue(all(domain.allows(e["geometry"]) for e in edges))
        features = {key: dict(type="SYNTHETIC") for key, _, _ in domain.regulatory}
        resolution = Resolution(curation([rule(k, f) for k, f in features.items()]), "SYNTHETIC", "SYNTHETIC", features, {"SYNTHETIC"})
        decisions = [resolution.evaluate(e["restrictions"], context()) for e in edges]
        request = dict(originEndpointId=connected[0]["id"], destinationEndpointId=connected[1]["id"])
        route = shortest(nodes, edges, connected, request, decisions)
        self.assertEqual("SCENARIO_ROUTE_FOUND", route["status"])
        self.assertEqual("NOT_ESTABLISHED", route["legalPermission"])
        self.assertGreater(route["distanceM"], 0)
        blocked = [resolution.evaluate(e["restrictions"], dict(at=AT)) for e in edges]
        self.assertEqual("REJECTED", shortest(nodes, edges, connected, request, blocked)["status"])
        self.assertEqual("TERMINAL_APPROACH_UNAVAILABLE", shortest(nodes, edges, [],
            dict(originTerminalId=terminals[0]["id"], destinationTerminalId=terminals[1]["id"]), decisions)["reason"])

    def test_unassociated_and_on_land_curations_cannot_create_connectors(self):
        nodes, edges, terminals, _, domain = mesh(synthetic_enc_source(), dict(id="SYNTHETIC"), PilotConfig())
        row = dict(review(), id="SYNTHETIC_WATER", terminalId=terminals[0]["id"], point=[0, 0],
                   coordinateEvidence=citation(), associationEvidence=citation())
        count = len(edges)
        result = connect_endpoints(nodes, edges, terminals, [row], domain, {"SYNTHETIC"}, instant(AT))
        self.assertEqual("OUTSIDE_CONSERVATIVE_WATER", result[0]["status"])
        self.assertEqual(count, len(edges))
        for change in (dict(associationEvidence={}), dict(terminalId="UNKNOWN"), dict(point=[float("nan"), 0])):
            with self.assertRaises(ValueError): connect_endpoints(nodes, edges, terminals, [dict(row, **change)], domain, {"SYNTHETIC"}, instant(AT))


if __name__ == "__main__": unittest.main()
