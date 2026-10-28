from pathlib import Path
import csv
import os
import re
import shutil
import sys
import tempfile
import unittest

for variable in ("OMP_NUM_THREADS", "OPENBLAS_NUM_THREADS", "MKL_NUM_THREADS"):
    os.environ[variable] = "1"

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

import numpy as np
from sklearn.metrics import adjusted_rand_score, silhouette_score
from service.dataset import FEATURE_NAMES, load_dataset, read_csv
from service.model import ContentModel
from service.review import audit, search_models

DATA = ROOT / "service" / "data"


class DatasetTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        shutil.copytree(DATA, self.directory, dirs_exist_ok=True)

    def edit(self, name, action):
        path = self.directory / name
        columns, rows = read_csv(path)
        action(rows)
        with path.open("w", encoding="utf-8", newline="") as output:
            writer = csv.DictWriter(output, fieldnames=columns)
            writer.writeheader()
            writer.writerows(rows)

    def test_audit_exports_311_unique_rows_without_filling_missing_ratings(self):
        result = audit(self.directory, self.directory / "reports")
        _, rows = read_csv(self.directory / "reports" / "canonical-places.csv")
        self.assertEqual(len(rows), 311)
        self.assertEqual(len({row["PlaceID"] for row in rows}), 311)
        self.assertEqual(sum(not row["Rating"] for row in rows), 35)
        self.assertEqual(sum(not row["RatingCount"] for row in rows), 35)
        self.assertEqual(result.report["trainingWeightSum"], 311)
        self.assertEqual(result.report["warnings"], [])
        self.assertEqual(len(result.report["duplicates"]), 4)
        self.assertEqual(len(rows[0]), 8 + 35)

    def test_training_weight_must_balance_duplicate_rows(self):
        self.edit("features.csv", lambda rows: rows[0].update(TrainingWeight="0.5"))
        with self.assertRaisesRegex(ValueError, "training weight"):
            load_dataset(self.directory)

    def test_duplicate_metadata_weight_must_match_features(self):
        self.edit("places.csv", lambda rows: rows[0].update(TrainingWeight="NaN"))
        with self.assertRaisesRegex(ValueError, "finite"):
            load_dataset(self.directory)

    def test_nonfinite_or_out_of_range_coordinates_are_rejected(self):
        for latitude in ("NaN", "inf", "91"):
            with self.subTest(latitude=latitude):
                self.edit("places.csv", lambda rows: rows[0].update(Latitude=latitude))
                with self.assertRaises(ValueError):
                    load_dataset(self.directory)

    def test_invalid_rating_or_negative_count_are_rejected(self):
        self.edit("places.csv", lambda rows: rows[0].update(Rating="5.1"))
        with self.assertRaisesRegex(ValueError, "rating"):
            load_dataset(self.directory)
        self.edit("places.csv", lambda rows: rows[0].update(Rating="4.5", RatingCount="-1"))
        with self.assertRaisesRegex(ValueError, "nonnegative integer"):
            load_dataset(self.directory)

    def test_real_zero_count_is_distinct_from_missing_count(self):
        self.edit("places.csv", lambda rows: rows[2].update(RatingCount="0"))
        result = load_dataset(self.directory)
        self.assertEqual(result.report["ratings"]["canonicalCountMissing"], 34)
        self.assertEqual(result.report["ratings"]["canonicalMissing"], 35)

    def test_tags_must_match_the_feature_matrix(self):
        self.edit("places.csv", lambda rows: rows[0].update(KindTags="heritage"))
        with self.assertRaisesRegex(ValueError, "tags disagree"):
            load_dataset(self.directory)

    def test_unknown_tags_are_not_silently_ignored(self):
        self.edit("places.csv", lambda rows: rows[0].update(KindTags="unknown"))
        with self.assertRaisesRegex(ValueError, "unknown KindTags"):
            load_dataset(self.directory)

    def test_cluster_label_disagreement_is_rejected(self):
        self.edit("places.csv", lambda rows: rows[0].update(ClusterID="14"))
        with self.assertRaisesRegex(ValueError, "cluster label"):
            load_dataset(self.directory)

    def test_activity_cannot_enter_the_reviewed_model(self):
        self.edit("places.csv", lambda rows: rows[0].update(ActivityUsedForTraining="True"))
        with self.assertRaisesRegex(ValueError, "activity used"):
            load_dataset(self.directory)

    def test_nonbinary_and_empty_vectors_are_rejected(self):
        self.edit("features.csv", lambda rows: rows[0].update({FEATURE_NAMES[0]: "2"}))
        with self.assertRaisesRegex(ValueError, "binary"):
            load_dataset(self.directory)
        self.edit("features.csv", lambda rows: rows[0].update({name: "0" for name in FEATURE_NAMES}))
        with self.assertRaisesRegex(ValueError, "empty content"):
            load_dataset(self.directory)

    def test_missing_source_url_is_reported_without_inventing_a_rating(self):
        self.edit("places.csv", lambda rows: rows[0].update(RatingSourceURL=""))
        report = load_dataset(self.directory).report
        self.assertEqual(report["warnings"][0]["code"], "VN-0001")
        self.assertIn("source URL", report["warnings"][0]["issue"])

    def test_malformed_csv_cannot_be_loaded(self):
        path = self.directory / "features.csv"
        with path.open("a", encoding="utf-8") as target:
            target.write("broken,row\n")
        with self.assertRaisesRegex(ValueError, "malformed CSV"):
            load_dataset(self.directory)

    def test_nullable_migration_targets_exactly_the_reviewed_missing_counts(self):
        dataset = load_dataset(DATA)
        expected = {int(row["PlaceID"].split("-")[1]) for row in dataset.places
                    if row["PlaceID"] == row["CanonicalPlaceID"] and not row["RatingCount"]}
        sql = (ROOT.parent / "db" / "016_preserve_missing_rating_counts.sql").read_text(encoding="utf-8")
        backfill = re.search(r"p\.place_id IN \((.*?)\)", sql, re.S).group(1)
        self.assertEqual({int(value) for value in re.findall(r"\d+", backfill)}, expected)


class TrainingTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.content = ContentModel(DATA)

    def test_training_reproduces_the_saved_model_and_reported_metric(self):
        summaries, runs, selected, fitted = search_models(self.content, ks=(15,), seeds=(42,))
        self.assertEqual(selected["k"], 15)
        self.assertEqual(selected["seed"], 42)
        self.assertEqual(selected["minCanonicalClusterSize"], 6)
        self.assertAlmostEqual(selected["silhouette"], .418647, places=6)
        self.assertEqual(adjusted_rand_score(self.content.raw_clusters, fitted.labels_), 1)
        np.testing.assert_allclose(self.content.model.cluster_centers_, fitted.cluster_centers_, atol=1e-12)

    def test_metric_is_evaluated_on_distinct_places(self):
        indices = self.content.canonical_indices
        score = silhouette_score(self.content.vectors[indices], self.content.raw_clusters[indices])
        self.assertAlmostEqual(score, .418647, places=6)
        self.assertNotAlmostEqual(score, silhouette_score(self.content.vectors, self.content.raw_clusters), places=6)


if __name__ == "__main__":
    unittest.main(verbosity=2)
