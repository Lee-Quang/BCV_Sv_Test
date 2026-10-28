"""Offline data audit and reproducible K-Means experiment. Never run in a request.

python -m service.review audit
python -m service.review verify [--model-output /trusted/output/model.joblib]
"""
from __future__ import annotations

import argparse
import csv
import json
import os
from pathlib import Path

for _name in ("OMP_NUM_THREADS", "OPENBLAS_NUM_THREADS", "MKL_NUM_THREADS"):
    os.environ.setdefault(_name, "1")

from .dataset import export_canonical, load_dataset


def write_json(path: Path, value: object) -> None:
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False) + "\n", encoding="utf-8")


def audit(directory: Path, output: Path):
    dataset = load_dataset(directory)
    output.mkdir(parents=True, exist_ok=True)
    write_json(output / "data-audit.json", dataset.report)
    export_canonical(dataset, output / "canonical-places.csv")
    return dataset


def search_models(content, ks=range(2, 16), seeds=(42, 7, 2026)):
    """Fit source rows with inverse-multiplicity weights; assess all source labels.

    Evaluate Silhouette/ARI on 311 distinct canonical places so duplicated source
    rows do not inflate the metrics. Fit still uses all 315 rows and their weights.
    """
    import numpy as np
    from sklearn.cluster import KMeans
    from sklearn.metrics import adjusted_rand_score, silhouette_score
    from threadpoolctl import threadpool_limits

    summaries, runs, fitted = [], [], {}
    with threadpool_limits(limits=1):
        for k in ks:
            group = []
            for seed in seeds:
                model = KMeans(n_clusters=k, init="k-means++", n_init=20, max_iter=500,
                               tol=0.0001, algorithm="lloyd", random_state=seed)
                model.fit(content.vectors, sample_weight=content.training_weights)
                labels = model.labels_
                counts = np.bincount(labels[content.canonical_indices], minlength=k)
                run = {"k": k, "seed": seed, "silhouette": float(silhouette_score(
                    content.vectors[content.canonical_indices], labels[content.canonical_indices])),
                       "inertia": float(model.inertia_), "minCanonicalClusterSize": int(counts.min()),
                       "iterations": int(model.n_iter_)}
                runs.append(run)
                group.append(run)
                fitted[k, seed] = model
            ari = [adjusted_rand_score(fitted[k, seeds[i]].labels_[content.canonical_indices],
                                       fitted[k, seeds[j]].labels_[content.canonical_indices])
                   for i in range(len(seeds)) for j in range(i + 1, len(seeds))]
            summaries.append({"k": k, "meanSilhouette": float(np.mean([run["silhouette"] for run in group])),
                              "meanAdjustedRandIndex": float(np.mean(ari)) if ari else 1.0,
                              "minCanonicalClusterSize": min(run["minCanonicalClusterSize"] for run in group),
                              "eligible": all(run["minCanonicalClusterSize"] >= 3 for run in group)})
    eligible = [summary for summary in summaries if summary["eligible"]]
    if not eligible:
        raise ValueError("No configuration has at least three distinct places in each cluster for every seed")
    selected = min(eligible, key=lambda item: (-round(item["meanSilhouette"], 12),
                                             -round(item["meanAdjustedRandIndex"], 12), item["k"]))
    best_run = min((run for run in runs if run["k"] == selected["k"]),
                   key=lambda run: (-run["silhouette"], seeds.index(run["seed"])))
    return summaries, runs, best_run, fitted[best_run["k"], best_run["seed"]]


def verify(directory: Path, output: Path, model_output: Path | None = None) -> dict:
    import joblib
    import numpy as np
    import scipy
    import sklearn
    from sklearn.metrics import adjusted_rand_score, silhouette_score
    from scipy.optimize import linear_sum_assignment
    from .model import ContentModel

    dataset = audit(directory, output)
    content = ContentModel(directory)
    summaries, runs, selected_run, selected_model = search_models(content)
    ari = float(adjusted_rand_score(content.raw_clusters, selected_model.labels_))
    costs = np.linalg.norm(content.model.cluster_centers_[:, None, :] - selected_model.cluster_centers_[None, :, :], axis=2)
    left, right = linear_sum_assignment(costs)
    center_difference = float(costs[left, right].max()) if len(left) == 15 else None
    snapshot_silhouette = float(silhouette_score(content.vectors[content.canonical_indices],
                                               content.raw_clusters[content.canonical_indices]))
    selected_summary = next(row for row in summaries if row["k"] == selected_run["k"])
    checks = {
        "selectedK15": selected_run["k"] == 15,
        "selectedSeed42": selected_run["seed"] == 42,
        "snapshotSilhouetteMatchesReport": bool(np.isclose(snapshot_silhouette, 0.418647, atol=0.000001, rtol=0)),
        "meanSilhouetteMatchesReport": bool(np.isclose(selected_summary["meanSilhouette"], 0.413841, atol=0.000001, rtol=0)),
        "stabilityMatchesReport": bool(np.isclose(selected_summary["meanAdjustedRandIndex"], 0.909102, atol=0.000001, rtol=0)),
        "retrainedLabelsMatchSnapshot": bool(np.isclose(ari, 1.0, atol=1e-12, rtol=0)),
        "retrainedCentersMatchSnapshot": center_difference is not None and center_difference < 1e-9,
    }
    report = {"status": "verified" if all(checks.values()) else "mismatch", "checks": checks,
              "trainingRows": len(content.vectors), "evaluationCanonicalPlaces": len(content.canonical_indices),
              "selectionRule": "Highest mean silhouette; ties: higher mean ARI, then lower K; each cluster needs >=3 canonical places in every seed",
              "selected": selected_run, "selectedSummary": selected_summary,
              "snapshotSilhouette": snapshot_silhouette, "snapshotComparisonAri": ari,
              "maximumMatchedCenterDifference": center_difference, "artifactSha256": content.metadata["artifactSha256"],
              "sourceSha256": dataset.report["sourceSha256"],
              "versions": {"numpy": np.__version__, "scipy": scipy.__version__, "scikit-learn": sklearn.__version__, "joblib": joblib.__version__},
              "configurations": summaries, "runs": runs,
              "scope": "Content clustering reproducibility only; not personalized recommendation accuracy"}
    write_json(output / "model-verification.json", report)
    with (output / "model-search.csv").open("w", encoding="utf-8", newline="") as target:
        writer = csv.DictWriter(target, fieldnames=list(runs[0]))
        writer.writeheader()
        writer.writerows(runs)
    clusters = []
    for label in range(15):
        indices = [index for index in content.canonical_indices if content.clusters[index] == label]
        means = content.binary[indices].mean(axis=0)
        top_features = sorted(zip(content.features, means.tolist()), key=lambda item: (-item[1], item[0]))[:8]
        clusters.append({"cluster": label, "canonicalPlaces": len(indices),
                         "commonFeatures": [{"code": name, "share": value} for name, value in top_features if value > 0],
                         "places": [{"code": dataset.places[index]["PlaceID"], "name": dataset.places[index]["Name"]} for index in indices]})
    write_json(output / "clusters.json", clusters)
    if not all(checks.values()):
        raise ValueError("Model verification differs from the reviewed snapshot; see model-verification.json")
    if model_output is not None:
        if model_output.resolve() == (directory / "model.joblib").resolve():
            raise ValueError("Choose a separate output; the reviewed production artifact must not be overwritten")
        mapping = {}
        for raw_label in range(15):
            canonical_labels = set(content.clusters[selected_model.labels_ == raw_label].tolist())
            if len(canonical_labels) != 1:
                raise ValueError("Cannot preserve the reviewed canonical cluster IDs")
            mapping[raw_label] = canonical_labels.pop()
        model_output.parent.mkdir(parents=True, exist_ok=True)
        joblib.dump({"model": selected_model, "feature_names": content.features,
                     "blocks": content.blocks, "cluster_mapping": mapping,
                     "source_sha256": dataset.report["sourceSha256"], "versions": report["versions"]}, model_output)
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("audit", "verify"))
    parser.add_argument("--data-dir", type=Path, default=Path(__file__).parent / "data")
    parser.add_argument("--output", type=Path, default=Path(__file__).parents[1] / "reports")
    parser.add_argument("--model-output", type=Path, help="Optional separate artifact from a verified retraining")
    args = parser.parse_args()
    if args.command == "audit" and args.model_output is not None:
        parser.error("--model-output requires verify")
    try:
        if args.command == "audit":
            dataset = audit(args.data_dir, args.output)
            print(json.dumps({"status": dataset.report["status"], "canonicalPlaces": len(dataset.canonical_indices),
                              "warnings": len(dataset.report["warnings"])}))
        else:
            result = verify(args.data_dir, args.output, args.model_output)
            print(json.dumps({"status": result["status"], "selected": result["selected"], "checks": result["checks"]}))
    except (ValueError, OSError, KeyError) as error:
        parser.exit(1, f"Review failed: {error}\n")


if __name__ == "__main__":
    main()
