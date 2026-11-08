"""Does public data help? Compares
  A) model trained on the user's texts only
  B) model trained on the user's texts + licensed public data (uci, mishra, jpm)
B is scored on the user's texts with 5-fold CV (public data always in training),
and both are scored on SpamHunter, which is never trained on (no license).

Usage: .venv/Scripts/python -I tools/eval_public.py
"""
import json
import os
import sys

import numpy as np
from sklearn.model_selection import StratifiedKFold

sys.path.insert(0, os.path.dirname(__file__))
from eval_classifier import build

THR = 0.7  # the threshold the app ships with


def load(path):
    return [json.loads(l) for l in open(path, encoding="utf-8")]


def summarize(name, y, p):
    pred = p >= THR
    sb, sk = (y == 1).sum(), (y == 0).sum()
    caught = int((pred & (y == 1)).sum())
    wrongly = int((pred & (y == 0)).sum())
    parts = []
    if sb: parts.append(f"spam caught {caught}/{sb} ({caught / sb:.1%})")
    if sk: parts.append(f"good BLOCKED {wrongly}/{sk} ({wrongly / sk:.1%})")
    print(f"  {name:<34} " + " | ".join(parts))


def main():
    own = load("data/dataset.jsonl")
    pub = load("data/public/combined.jsonl")
    licensed = [r for r in pub if r["source"] in ("uci", "mishra", "jpm")]
    hunter = [r for r in pub if r["source"] == "spamhunter"]

    X_own = [r["text"] for r in own]
    y_own = np.array([r["label"] == "block" for r in own], dtype=int)
    X_lic = [r["text"] for r in licensed]
    y_lic = np.array([r["label"] == "block" for r in licensed], dtype=int)
    X_h = [r["text"] for r in hunter]
    y_h = np.ones(len(X_h), dtype=int)

    by_src = {}
    for r in licensed:
        by_src.setdefault(r["source"], []).append(r)

    print("A) trained on your texts only")
    a = build().fit(X_own, y_own)
    for src, rs in by_src.items():
        summarize(src, np.array([r["label"] == "block" for r in rs], dtype=int),
                  a.predict_proba([r["text"] for r in rs])[:, 1])
    summarize("spamhunter (never trained on)", y_h, a.predict_proba(X_h)[:, 1])

    print("\nB) your texts + licensed public data")
    cv = StratifiedKFold(5, shuffle=True, random_state=0)
    p_own = np.zeros(len(X_own))
    for tr, te in cv.split(X_own, y_own):
        m = build().fit([X_own[i] for i in tr] + X_lic, np.concatenate([y_own[tr], y_lic]))
        p_own[te] = m.predict_proba([X_own[i] for i in te])[:, 1]
    summarize("your texts (5-fold CV)", y_own, p_own)

    # Baseline for the same CV on your texts only, for a fair comparison.
    p_base = np.zeros(len(X_own))
    for tr, te in cv.split(X_own, y_own):
        m = build().fit([X_own[i] for i in tr], y_own[tr])
        p_base[te] = m.predict_proba([X_own[i] for i in te])[:, 1]
    summarize("  (A on same CV, for comparison)", y_own, p_base)

    b = build().fit(X_own + X_lic, np.concatenate([y_own, y_lic]))
    summarize("spamhunter (never trained on)", y_h, b.predict_proba(X_h)[:, 1])

    # Which of your good texts does B block that A didn't, and vice versa?
    print("\nyour good texts blocked by B but not A:")
    for i in np.where((y_own == 0) & (p_own >= THR) & (p_base < THR))[0][:10]:
        print("   ", repr(X_own[i][:100]))
    print("your spam caught by B but missed by A:", int(((y_own == 1) & (p_own >= THR) & (p_base < THR)).sum()))
    print("your spam caught by A but missed by B:", int(((y_own == 1) & (p_base >= THR) & (p_own < THR)).sum()))


if __name__ == "__main__":
    main()
