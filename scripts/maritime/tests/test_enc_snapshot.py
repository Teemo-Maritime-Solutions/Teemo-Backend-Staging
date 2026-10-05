"""SYNTHETIC mocked NOAA replies; no source observations or coordinates fabricated in production."""
import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from enc_snapshot import download_layer, feature_ids, select_catalog
from synthetic_fixtures import WEST


class Replies:
    def __init__(self, replies): self.replies = iter(replies)
    def request(self, *args, **kwargs): return next(self.replies)


class EncSnapshotTests(unittest.TestCase):
    def test_id_loss_and_duplicates_fail_closed(self):
        for reply in ({"objectIds": [1, 1]}, {"objectIds": [1], "exceededTransferLimit": True}, {}, {"objectIds": [1.0]}):
            with self.assertRaises(ValueError): feature_ids(Replies([reply]), "SYNTHETIC", "SYNTHETIC", 20)

    def test_complete_batch_and_live_membership_are_verified(self):
        meta = dict(name="SYNTHETIC", type="Feature Layer", fields=[dict(name="OBJECTID", type="esriFieldTypeOID"), dict(name="DSNM", type="esriFieldTypeString")])
        ids = dict(objectIdFieldName="OBJECTID", objectIds=[1])
        feature = dict(type="Feature", properties=dict(OBJECTID=1, DSNM="SYNTHETIC"), geometry=dict(type="Point", coordinates=WEST))
        batch = dict(type="FeatureCollection", features=[feature])
        result = download_layer(Replies([meta, ids, batch, ids]), "SYNTHETIC", "SYNTHETIC", "SYNTHETIC")
        self.assertEqual([feature], result["features"])
        for broken in (dict(type="FeatureCollection", features=[]), dict(batch, exceededTransferLimit=True)):
            with self.assertRaises(ValueError): download_layer(Replies([meta, ids, broken]), "SYNTHETIC", "SYNTHETIC", "SYNTHETIC")
        with self.assertRaises(ValueError):
            download_layer(Replies([meta, ids, batch, dict(ids, objectIds=[2])]), "SYNTHETIC", "SYNTHETIC", "SYNTHETIC")

    def test_registry_change_is_not_silently_accepted(self):
        with self.assertRaises(ValueError): download_layer(Replies([dict(name="CHANGED")]), "SYNTHETIC", "SYNTHETIC", "SYNTHETIC")

    def test_oid_name_is_read_from_published_schema_and_null_ids_mean_empty(self):
        meta = dict(name="SYNTHETIC", type="Feature Layer", fields=[dict(name="SYNTHETIC.TABLE.FID", type="esriFieldTypeOID"), dict(name="DSNM")])
        ids = dict(objectIdFieldName="SYNTHETIC.TABLE.FID", objectIds=None)
        result = download_layer(Replies([meta, ids, ids]), "SYNTHETIC", "SYNTHETIC", "SYNTHETIC")
        self.assertEqual("SYNTHETIC.TABLE.FID", result["objectIdField"])
        self.assertEqual([], result["features"])

    def test_bounded_batches_do_not_drop_features(self):
        meta = dict(name="SYNTHETIC", type="Feature Layer", fields=[dict(name="OBJECTID", type="esriFieldTypeOID"), dict(name="DSNM")])
        ids = dict(objectIdFieldName="OBJECTID", objectIds=[1, 2])
        features = [dict(properties=dict(OBJECTID=i), geometry=dict(type="Point", coordinates=WEST)) for i in [1, 2]]
        replies = [meta, ids, *[dict(type="FeatureCollection", features=[f]) for f in features], ids]
        self.assertEqual(features, download_layer(Replies(replies), "SYNTHETIC", "SYNTHETIC", "SYNTHETIC", batch_size=1)["features"])
        for size in (0, 51, 1.5, True):
            with self.assertRaises(ValueError):
                download_layer(Replies([]), "SYNTHETIC", "SYNTHETIC", "SYNTHETIC", batch_size=size)

    def test_catalog_requires_active_harbour_cells_and_rejects_entities(self):
        cell = '<cell><name>US5TEST1</name><lname>SYNTHETIC</lname><status>Active</status></cell>'
        raw = ('<EncProductCatalogNY>' + cell + cell.replace('US5TEST1', 'US4TEST1') + cell.replace('Active', 'Cancelled') + '</EncProductCatalogNY>').encode()
        self.assertEqual(["US5TEST1"], [c["name"] for c in select_catalog(raw, "synthetic", 1)])
        for broken in (b'<!DOCTYPE x>' + raw, raw.replace(b'US4TEST1', b'US5TEST1'), b'<other/>'):
            with self.assertRaises(ValueError): select_catalog(broken, "synthetic", 2)


if __name__ == "__main__": unittest.main()
