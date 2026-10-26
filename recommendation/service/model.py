"""Load the reviewed model once and serve canonical content neighbours.

Only the trusted artifact shipped with this project is deserialized. Request
data never contains a model path, pickle, or instructions to train a model.
"""
from __future__ import annotations

import csv
import hashlib
import os
from pathlib import Path
import warnings

for _variable in ("OMP_NUM_THREADS", "OPENBLAS_NUM_THREADS", "MKL_NUM_THREADS"):
    os.environ.setdefault(_variable, "1")

import joblib
import numpy as np
import sklearn
from sklearn.exceptions import InconsistentVersionWarning


GROUPS = ("KindTags", "LandformTags", "EcosystemTags", "ThemeTags")
PREFIXES = ("kind", "landform", "ecosystem", "theme")


def read_csv(path: Path) -> tuple[list[str], list[dict[str, str]]]:
    with path.open(encoding="utf-8-sig", newline="") as source:
        reader = csv.DictReader(source)
        rows = list(reader)
        return list(reader.fieldnames or []), rows


def normalize_blocks(binary: np.ndarray, lengths: list[int]) -> np.ndarray:
    """L2 per group, weight 0.5; preserve empty groups without global L2."""
    binary = np.asarray(binary, dtype=np.float64)
    if binary.ndim != 2 or sum(lengths) != binary.shape[1] or len(lengths) != 4:
        raise ValueError("Expected four groups covering every feature column")
    if not np.isin(binary, [0, 1]).all():
        raise ValueError("Feature values must be binary")
    result = np.zeros_like(binary)
    offset = 0
    for length in lengths:
        block = binary[:, offset:offset + length]
        norm = np.linalg.norm(block, axis=1, keepdims=True)
        result[:, offset:offset + length] = np.divide(
            block, norm, out=np.zeros_like(block), where=norm != 0
        ) * 0.5
        offset += length
    return result


class ContentModel:
    def __init__(self, data_dir: Path):
        self.data_dir = Path(data_dir)
        artifact = self.data_dir / "model.joblib"
        with warnings.catch_warnings():
            warnings.simplefilter("error", InconsistentVersionWarning)
            bundle = joblib.load(artifact)
        self.model = bundle["model"]
        self.features = list(bundle["feature_names"])
        self.blocks = [(name, list(vocab)) for name, vocab in bundle["blocks"]]
        self.cluster_mapping = {int(k): int(v) for k, v in bundle["cluster_mapping"].items()}
        if (
            len(self.features) != 35
            or [group for group, _ in self.blocks] != list(GROUPS)
            or self.model.n_clusters != 15
            or self.model.random_state != 42
            or self.model.n_features_in_ != 35
            or set(self.cluster_mapping) != set(range(15))
            or set(self.cluster_mapping.values()) != set(range(15))
        ):
            raise ValueError("Artifact does not match the reviewed 35-feature K15 seed42 model")
        expected_features = [
            f"{prefix}__{tag}" for prefix, (_, vocab) in zip(PREFIXES, self.blocks) for tag in vocab
        ]
        if expected_features != self.features:
            raise ValueError("Artifact feature order differs from its vocabularies")
        columns, feature_rows = read_csv(self.data_dir / "features.csv")
        _, place_rows = read_csv(self.data_dir / "places.csv")
        if columns != ["PlaceID", "CanonicalPlaceID", "TrainingWeight", "ClusterID", *self.features]:
            raise ValueError("Feature CSV columns must match the artifact exactly and in order")
        if len(feature_rows) != 315 or len(place_rows) != 315:
            raise ValueError("Expected the reviewed 315 source rows")
        source_codes = [row["PlaceID"] for row in feature_rows]
        if len(set(source_codes)) != 315 or source_codes != [row["PlaceID"] for row in place_rows]:
            raise ValueError("Source IDs are duplicated or metadata order differs")
        self.aliases = {row["PlaceID"]: row["CanonicalPlaceID"] for row in feature_rows}
        if len(set(self.aliases.values())) != 311:
            raise ValueError("Expected 311 canonical places after source deduplication")
        self.lengths = [len(vocab) for _, vocab in self.blocks]
        self.binary = np.array([[float(row[name]) for name in self.features] for row in feature_rows])
        self.vectors = normalize_blocks(self.binary, self.lengths)
        if np.any(np.count_nonzero(self.binary, axis=1) == 0):
            raise ValueError("A place cannot have an empty content vector")
        # Lloyd K-Means predicts the nearest fitted centroid. This vectorized
        # calculation avoids threadpoolctl's ANSI Windows DLL-path inspection,
        # which fails when a virtualenv lives under a Vietnamese folder name.
        # Validate every resulting label against the reviewed snapshot below.
        centers = np.asarray(self.model.cluster_centers_, dtype=np.float64)
        if centers.shape != (15, 35) or not np.isfinite(centers).all():
            raise ValueError("Invalid fitted cluster centers")
        self.raw_clusters = np.argmin(
            np.sum((self.vectors[:, None, :] - centers[None, :, :]) ** 2, axis=2), axis=1
        )
        self.clusters = np.array([self.cluster_mapping[int(label)] for label in self.raw_clusters])
        if not np.array_equal(self.clusters, [int(row["ClusterID"]) for row in feature_rows]):
            raise ValueError("CSV cluster labels do not match the fitted artifact")
        self.canonical_indices = []
        self.by_code = {}
        indices = {code: index for index, code in enumerate(source_codes)}
        for index, (feature, place) in enumerate(zip(feature_rows, place_rows)):
            code = feature["CanonicalPlaceID"]
            if place["CanonicalPlaceID"] != code or code not in indices or self.aliases[code] != code:
                raise ValueError("Invalid canonical-place mapping")
            canonical_index = indices[code]
            if not np.array_equal(self.binary[index], self.binary[canonical_index]):
                raise ValueError("Duplicate source rows disagree on canonical features")
            latitude, longitude = float(place["Latitude"]), float(place["Longitude"])
            if not -90 <= latitude <= 90 or not -180 <= longitude <= 180:
                raise ValueError("Invalid place coordinates")
            if feature["PlaceID"] == code:
                self.canonical_indices.append(index)
                self.by_code[code] = {
                    "index": index,
                    "code": code,
                    "name": place["Name"],
                    "latitude": latitude,
                    "longitude": longitude,
                    "cluster": int(self.clusters[index]),
                }
        self.metadata = {
            "modelVersion": "35_features_4_groups_no_activity",
            "featureCount": 35,
            "clusterCount": 15,
            "randomSeed": 42,
            "inferenceImplementation": "NumPy nearest fitted Lloyd centroid; all 315 labels checked against snapshot",
            "sourceRows": 315,
            "canonicalPlaces": 311,
            "featureNames": self.features,
            "normalization": "L2 per group multiplied by 0.5; empty group stays zero; no global L2",
            "groups": [{"name": name, "columns": len(vocab)} for name, vocab in self.blocks],
            "scikitLearnVersion": sklearn.__version__,
            "artifactSha256": hashlib.sha256(artifact.read_bytes()).hexdigest(),
            "purpose": "Content similarity within a fitted cluster, not personalized recommendation accuracy",
        }

    def similar(self, source_code: str, limit: int = 5) -> dict:
        if not 1 <= limit <= 20:
            raise ValueError("limit must be between 1 and 20")
        code = self.aliases.get(source_code.upper())
        if code is None:
            raise KeyError(source_code)
        origin = self.by_code[code]
        candidates = [
            index for index in self.canonical_indices
            if self.clusters[index] == origin["cluster"] and index != origin["index"]
        ]
        # The corpus is small; explicit code sorting makes equal-distance output reproducible.
        inverse = {place["index"]: place for place in self.by_code.values()}
        candidates.sort(key=lambda index: (
            float(np.linalg.norm(self.vectors[index] - self.vectors[origin["index"]])), inverse[index]["code"]
        ))
        places = []
        for index in candidates[:limit]:
            place = inverse[index]
            places.append({
                key: value for key, value in place.items() if key != "index"
            } | {"contentDistance": float(np.linalg.norm(self.vectors[index] - self.vectors[origin["index"]]))})
        return {
            "source": {key: value for key, value in origin.items() if key != "index"},
            "places": places,
            "scope": "CONTENT_SIMILARITY_IN_CLUSTER",
            "modelVersion": self.metadata["modelVersion"],
        }
