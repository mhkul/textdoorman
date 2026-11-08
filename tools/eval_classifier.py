"""Baseline: TF-IDF + logistic regression, scored with 5-fold cross-validation
so every text is predicted by a model that never saw it.

Usage: .venv/Scripts/python tools/eval_classifier.py [dataset.jsonl]
"""
import json
import pickle
import sys

import numpy as np
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import StratifiedKFold, cross_val_predict
from sklearn.pipeline import make_pipeline, make_union


def build():
    feats = make_union(
        TfidfVectorizer(analyzer="word", ngram_range=(1, 2), min_df=2, sublinear_tf=True, max_features=20000),
        TfidfVectorizer(analyzer="char_wb", ngram_range=(3, 5), min_df=2, sublinear_tf=True, max_features=30000),
    )
    return make_pipeline(feats, LogisticRegression(C=4, class_weight="balanced", max_iter=2000))


def report(name, y, p_block, thr):
    pred = p_block >= thr
    tp = int(((y == 1) & pred).sum()); fn = int(((y == 1) & ~pred).sum())
    fp = int(((y == 0) & pred).sum()); tn = int(((y == 0) & ~pred).sum())
    print(f"== {name} (threshold {thr:.2f})")
    print(f"spam caught:          {tp}/{tp + fn} ({tp / (tp + fn):.1%})")
    print(f"good texts kept:      {tn}/{tn + fp} ({tn / (tn + fp):.1%})")
    print(f"good texts BLOCKED:   {fp}   <- worst error")
    print(f"spam let through:     {fn}")


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else "data/dataset.jsonl"
    rows = [json.loads(l) for l in open(path, encoding="utf-8")]
    texts = [r["text"] for r in rows]
    y = np.array([r["label"] == "block" for r in rows], dtype=int)

    cv = StratifiedKFold(5, shuffle=True, random_state=0)
    p = cross_val_predict(build(), texts, y, cv=cv, method="predict_proba")[:, 1]

    report("tfidf+logreg", y, p, 0.5)
    # Stricter: smallest threshold that blocks no good text in cross-validation.
    strict = float(p[y == 0].max()) + 1e-6
    report("tfidf+logreg, strict", y, p, strict)

    print("\nhardest cases (default threshold):")
    for i in np.argsort(-np.abs(p - y))[:12]:
        if (p[i] >= 0.5) != y[i]:
            kind = "GOOD BLOCKED" if y[i] == 0 else "spam missed"
            print(f"  [{kind} p={p[i]:.2f}] {texts[i][:110]!r}")

    model = build().fit(texts, y)
    blob = pickle.dumps(model)
    print(f"\nfull model trained on all {len(texts)} texts: {len(blob) / 1e6:.1f} MB pickled")
    with open("results/tfidf_logreg_cv.jsonl", "w", encoding="utf-8") as f:
        for r, pb in zip(rows, p):
            f.write(json.dumps({"label": r["label"], "p_block": round(float(pb), 4), "text": r["text"]},
                               ensure_ascii=False) + "\n")


if __name__ == "__main__":
    main()
