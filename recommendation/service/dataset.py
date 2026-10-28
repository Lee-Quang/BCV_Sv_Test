"""Validate the reviewed source snapshot before training, exporting or serving it.

Zero means an unrecorded tag, not proof that a place lacks that characteristic.
Source URLs are checked for presence/shape here; this is not a new human review
of those sources or of the weak labels.
"""
from __future__ import annotations

from collections import Counter, defaultdict
import csv
from dataclasses import dataclass
import hashlib
import math
from pathlib import Path
import re
from urllib.parse import urlsplit


VOCABULARIES = {
    "KindTags": ("nature", "heritage", "religious", "museum", "structure", "market",
                 "craft", "agriculture", "community", "recreation"),
    "LandformTags": ("coast", "island", "mountain", "cave", "river", "lake", "waterfall",
                     "stream", "lagoon", "dunes", "valley"),
    "EcosystemTags": ("forest", "flooded_habitat", "grassland"),
    "ThemeTags": ("history", "culture", "architecture", "spirituality", "ecology", "geology",
                  "craft", "cuisine", "games", "relaxation", "agriculture"),
}
GROUPS = tuple(VOCABULARIES)
PREFIXES = ("kind", "landform", "ecosystem", "theme")
FEATURE_NAMES = tuple(f"{prefix}__{tag}" for prefix, tags in zip(PREFIXES, VOCABULARIES.values()) for tag in tags)
MODEL_VERSION = "35_features_4_groups_no_activity"


def read_csv(path: Path) -> tuple[list[str], list[dict[str, str]]]:
    with path.open(encoding="utf-8-sig", newline="") as source:
        reader = csv.DictReader(source)
        columns = list(reader.fieldnames or [])
        rows = list(reader)
    if not columns or len(set(columns)) != len(columns):
        raise ValueError(f"{path.name}: missing or duplicated column names")
    if any(None in row or any(value is None for value in row.values()) for row in rows):
        raise ValueError(f"{path.name}: malformed CSV row")
    return columns, rows


def number(value: str, field: str, code: str) -> float:
    try:
        result = float(value)
    except (ValueError, TypeError):
        raise ValueError(f"{code}: invalid {field}") from None
    if not math.isfinite(result):
        raise ValueError(f"{code}: {field} must be finite")
    return result


def http_url(value: str) -> bool:
    try:
        parsed = urlsplit(value)
        return parsed.scheme in ("http", "https") and bool(parsed.netloc)
    except ValueError:
        return False


@dataclass
class ReviewedDataset:
    place_columns: list[str]
    places: list[dict[str, str]]
    features: list[dict[str, str]]
    canonical_indices: list[int]
    aliases: dict[str, str]
    report: dict


def load_dataset(directory: Path) -> ReviewedDataset:
    directory = Path(directory)
    feature_columns, features = read_csv(directory / "features.csv")
    place_columns, places = read_csv(directory / "places.csv")
    if feature_columns != ["PlaceID", "CanonicalPlaceID", "TrainingWeight", "ClusterID", *FEATURE_NAMES]:
        raise ValueError("Feature CSV columns must match the reviewed 35 features exactly and in order")
    required = {"PlaceID", "CanonicalPlaceID", "TrainingWeight", "Name", "Latitude", "Longitude",
                "Rating", "RatingCount", "ClusterID", "ModelVersion", "ActivityUsedForTraining", *GROUPS}
    if not required.issubset(place_columns):
        raise ValueError(f"Place CSV is missing columns: {sorted(required - set(place_columns))}")
    if len(features) != 315 or len(places) != 315:
        raise ValueError("Expected the reviewed 315 source rows")
    codes = [row["PlaceID"] for row in features]
    if len(set(codes)) != len(codes) or codes != [row["PlaceID"] for row in places]:
        raise ValueError("Source IDs are duplicated or metadata order differs")
    if any(not re.fullmatch(r"VN-\d{4}", code) for code in codes):
        raise ValueError("Invalid source place code")
    indices = {code: index for index, code in enumerate(codes)}
    aliases = {row["PlaceID"]: row["CanonicalPlaceID"] for row in features}
    if len(set(aliases.values())) != 311:
        raise ValueError("Expected 311 canonical places after source deduplication")
    multiplicities = Counter(aliases.values())
    canonical_indices, warnings = [], []
    for index, (feature, place) in enumerate(zip(features, places)):
        code, canonical = feature["PlaceID"], feature["CanonicalPlaceID"]
        if canonical not in indices or aliases.get(canonical) != canonical or place["CanonicalPlaceID"] != canonical:
            raise ValueError(f"{code}: invalid canonical-place mapping")
        if not place["Name"].strip():
            raise ValueError(f"{code}: place name is empty")
        weight = number(feature["TrainingWeight"], "TrainingWeight", code)
        if (not math.isclose(weight, 1 / multiplicities[canonical], abs_tol=1e-12)
                or weight != number(place["TrainingWeight"], "TrainingWeight", code)):
            raise ValueError(f"{code}: invalid training weight for canonical-place multiplicity")
        for name in FEATURE_NAMES:
            if feature[name] not in ("0", "1"):
                raise ValueError(f"{code}: feature values must be binary ({name})")
            if feature[name] != features[indices[canonical]][name]:
                raise ValueError(f"{code}: duplicate source rows disagree on canonical features")
        if not any(feature[name] == "1" for name in FEATURE_NAMES):
            raise ValueError(f"{code}: a place cannot have an empty content vector")
        for prefix, (group, vocabulary) in zip(PREFIXES, VOCABULARIES.items()):
            tags = {tag.strip() for tag in place[group].split(";") if tag.strip()}
            if not tags.issubset(vocabulary):
                raise ValueError(f"{code}: unknown {group} tags: {sorted(tags - set(vocabulary))}")
            if any((feature[f"{prefix}__{tag}"] == "1") != (tag in tags) for tag in vocabulary):
                raise ValueError(f"{code}: {group} tags disagree with feature CSV")
        latitude = number(place["Latitude"], "Latitude", code)
        longitude = number(place["Longitude"], "Longitude", code)
        if not -90 <= latitude <= 90 or not -180 <= longitude <= 180:
            raise ValueError(f"{code}: invalid place coordinates")
        rating, count = place["Rating"].strip(), place["RatingCount"].strip()
        if rating and not 0 <= number(rating, "Rating", code) <= 5:
            raise ValueError(f"{code}: rating must be between 0 and 5")
        if count and not re.fullmatch(r"0|[1-9]\d*", count):
            raise ValueError(f"{code}: rating count must be a nonnegative integer")
        if rating and not http_url(place.get("RatingSourceURL", "")):
            warnings.append({"code": code, "issue": "Rating source URL missing or invalid"})
        if not http_url(place.get("CoordinateSourceURL", "")):
            warnings.append({"code": code, "issue": "Coordinate source URL missing or invalid"})
        if (not re.fullmatch(r"\d+", feature["ClusterID"])
                or not 0 <= int(feature["ClusterID"]) < 15 or feature["ClusterID"] != place["ClusterID"]):
            raise ValueError(f"{code}: invalid or inconsistent cluster label")
        if place["ModelVersion"] != MODEL_VERSION or place["ActivityUsedForTraining"].casefold() != "false":
            raise ValueError(f"{code}: unexpected model version or activity used for training")
        if code == canonical:
            canonical_indices.append(index)
        elif any(place[field] != places[indices[canonical]][field] for field in ("Latitude", "Longitude", "Rating", "RatingCount")):
            warnings.append({"code": code, "canonical": canonical, "issue": "Duplicate metadata differs; export uses the canonical row"})

    canonical = [places[index] for index in canonical_indices]
    duplicates = defaultdict(list)
    for code, target in aliases.items():
        duplicates[target].append(code)
    report = {
        "status": "valid", "modelVersion": MODEL_VERSION, "sourceRows": len(places),
        "canonicalPlaces": len(canonical), "trainingWeightSum": sum(float(row["TrainingWeight"]) for row in features),
        "featureCount": len(FEATURE_NAMES), "featureOrder": list(FEATURE_NAMES),
        "groups": {group: len(tags) for group, tags in VOCABULARIES.items()},
        "duplicates": [{"canonical": target, "sources": members, "weightPerSource": 1 / len(members)}
                       for target, members in sorted(duplicates.items()) if len(members) > 1],
        "ratings": {"sourcePresent": sum(bool(row["Rating"].strip()) for row in places),
                    "sourceMissing": sum(not row["Rating"].strip() for row in places),
                    "canonicalPresent": sum(bool(row["Rating"].strip()) for row in canonical),
                    "canonicalMissing": sum(not row["Rating"].strip() for row in canonical),
                    "canonicalCountMissing": sum(not row["RatingCount"].strip() for row in canonical)},
        "canonicalMissingGroups": {group: sum(not row[group].strip() for row in canonical) for group in GROUPS},
        "featureOccurrences": {name: sum(features[index][name] == "1" for index in canonical_indices) for name in FEATURE_NAMES},
        "sourceSha256": {name: hashlib.sha256((directory / name).read_bytes()).hexdigest()
                         for name in ("places.csv", "features.csv")},
        "warnings": warnings,
        "scope": "Structural consistency and provenance fields; no new external source verification or human relabelling",
        "missingLabels": "Zero means unrecorded, not evidence of absence; missing ratings stay blank/null",
    }
    return ReviewedDataset(place_columns, places, features, canonical_indices, aliases, report)


def export_canonical(dataset: ReviewedDataset, output: Path) -> None:
    """Export one row per canonical place; numeric source IDs match the SQL import."""
    columns = ["PlaceID", "DatabasePlaceID", "Name", "Latitude", "Longitude", "Rating", "RatingCount", "ClusterID", *FEATURE_NAMES]
    with Path(output).open("w", encoding="utf-8", newline="") as target:
        writer = csv.DictWriter(target, fieldnames=columns)
        writer.writeheader()
        for index in dataset.canonical_indices:
            place, feature = dataset.places[index], dataset.features[index]
            writer.writerow({"PlaceID": place["PlaceID"], "DatabasePlaceID": int(place["PlaceID"].split("-")[1]),
                             **{name: place[name] for name in ("Name", "Latitude", "Longitude", "Rating", "RatingCount", "ClusterID")},
                             **{name: feature[name] for name in FEATURE_NAMES}})
