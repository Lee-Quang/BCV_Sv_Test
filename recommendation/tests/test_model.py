from pathlib import Path
import os
import sys
import tempfile
import unittest
from unittest.mock import patch
import csv
import shutil

# Set native-library limits before NumPy/Scikit-learn are imported. Importing
# NumPy first can initialize an unbounded BLAS pool before service.model gets
# a chance to configure it, making Windows test startup stall under load.
for _variable in ("OMP_NUM_THREADS", "OPENBLAS_NUM_THREADS", "MKL_NUM_THREADS"):
    os.environ[_variable] = "1"

# Also support discovery from the repository root, as documented in README.
RECOMMENDATION = Path(__file__).absolute().parents[1]
if str(RECOMMENDATION) not in sys.path:
    sys.path.insert(0, str(RECOMMENDATION))

import numpy as np
from fastapi.testclient import TestClient
from service.app import create_app
from service.model import ContentModel, normalize_blocks


DATA = Path(__file__).resolve().parents[1] / "service" / "data"


class ModelTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.model = ContentModel(DATA)

    def test_artifact_predicts_all_reviewed_rows_and_canonical_count(self):
        self.assertEqual(self.model.vectors.shape, (315, 35))
        self.assertEqual(len(self.model.by_code), 311)
        self.assertEqual(set(self.model.clusters.tolist()), set(range(15)))
        self.assertEqual(self.model.metadata["randomSeed"], 42)

    def test_l2_is_per_group_and_preserves_missing_groups(self):
        binary = np.array([[1, 1, 0, 0, 1, 0, 0, 0]], dtype=float)
        normalized = normalize_blocks(binary, [2, 2, 2, 2])
        np.testing.assert_allclose(normalized, [[2 ** -1.5, 2 ** -1.5, 0, 0, .5, 0, 0, 0]])
        self.assertAlmostEqual(float(np.linalg.norm(normalized)), 2 ** -.5)

    def test_rejects_nonbinary_or_wrong_number_of_groups(self):
        with self.assertRaises(ValueError):
            normalize_blocks(np.array([[1, 2, 0, 0]]), [1, 1, 1, 1])
        with self.assertRaises(ValueError):
            normalize_blocks(np.ones((1, 4)), [2, 2])

    def test_source_aliases_return_same_canonical_neighbours(self):
        for alias, canonical in [("VN-0060", "VN-0156"), ("VN-0304", "VN-0098"),
                                 ("VN-0183", "VN-0108"), ("VN-0228", "VN-0132")]:
            self.assertEqual(self.model.similar(alias), self.model.similar(canonical))

    def test_neighbours_are_unique_not_self_same_cluster_and_sorted(self):
        result = self.model.similar("VN-0001", 20)
        places = result["places"]
        self.assertGreater(len(places), 0)
        self.assertEqual(len({p["code"] for p in places}), len(places))
        self.assertTrue(all(p["code"] != "VN-0001" for p in places))
        self.assertTrue(all(p["cluster"] == result["source"]["cluster"] for p in places))
        keys = [(p["contentDistance"], p["code"]) for p in places]
        self.assertEqual(keys, sorted(keys))

    def test_requests_do_not_fit_or_mutate_fitted_model(self):
        centers = self.model.model.cluster_centers_.copy()
        with patch.object(self.model.model, "fit", side_effect=AssertionError("must not train")):
            self.model.similar("VN-0001")
            self.model.similar("VN-0002")
        np.testing.assert_array_equal(centers, self.model.model.cluster_centers_)

    def test_startup_rejects_corrupt_feature_values(self):
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp)
            for source in DATA.iterdir():
                shutil.copy2(source, folder / source.name)
            file = folder / "features.csv"
            with file.open(encoding="utf-8-sig", newline="") as handle:
                reader = csv.DictReader(handle)
                columns, rows = reader.fieldnames, list(reader)
            rows[0][self.model.features[0]] = "2"
            with file.open("w", encoding="utf-8", newline="") as handle:
                writer = csv.DictWriter(handle, fieldnames=columns)
                writer.writeheader()
                writer.writerows(rows)
            with self.assertRaisesRegex(ValueError, "binary"):
                ContentModel(folder)


class ApiTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.client_context = TestClient(create_app(DATA))
        cls.client = cls.client_context.__enter__()

    @classmethod
    def tearDownClass(cls):
        cls.client_context.__exit__(None, None, None)

    def test_health_and_model_metadata(self):
        self.assertEqual(self.client.get("/health").json()["canonicalPlaces"], 311)
        metadata = self.client.get("/model").json()
        self.assertEqual(metadata["featureCount"], 35)
        self.assertEqual(len(metadata["artifactSha256"]), 64)
        self.assertNotIn("accuracy", metadata)

    def test_similarity_endpoint_accepts_alias(self):
        response = self.client.get("/similar/VN-0060?limit=5")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["source"]["code"], "VN-0156")
        self.assertLessEqual(len(response.json()["places"]), 5)

    def test_unknown_code_and_invalid_limits(self):
        self.assertEqual(self.client.get("/similar/UNKNOWN").status_code, 404)
        self.assertEqual(self.client.get("/similar/VN-0001?limit=0").status_code, 422)
        self.assertEqual(self.client.get("/similar/VN-0001?limit=21").status_code, 422)


if __name__ == "__main__":
    unittest.main(verbosity=2)
