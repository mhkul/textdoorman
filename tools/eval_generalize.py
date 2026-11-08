"""Would a published model work for a NEW user? Train without the user's texts
and score on them (the user stands in for a stranger). SpamHunter is never
trained on (no license) and serves as a second, independent spam test.

Usage: .venv/Scripts/python -I tools/eval_generalize.py
"""
import json
import os
import sys

import numpy as np
from sklearn.model_selection import StratifiedKFold

sys.path.insert(0, os.path.dirname(__file__))
from eval_classifier import build

THR = 0.7


def load(p): return [json.loads(l) for l in open(p, encoding="utf-8")]
def xy(rows): return [r["text"] for r in rows], np.array([r["label"] == "block" for r in rows], dtype=int)


def line(name, y, p):
    pred = p >= THR
    nb, nk = int((y == 1).sum()), int((y == 0).sum())
    s = f"  {name:<40}"
    if nb: s += f" spam caught {int((pred & (y == 1)).sum())}/{nb} ({(pred & (y == 1)).sum() / nb:.1%})"
    if nk: s += f" | good BLOCKED {int((pred & (y == 0)).sum())}/{nk} ({(pred & (y == 0)).sum() / nk:.1%})"
    print(s)


def main():
    own = load("data/dataset.jsonl")
    pub = load("data/public/combined.jsonl")
    lic = [r for r in pub if r["source"] in ("uci", "mishra", "jpm")]
    hunter = [r for r in pub if r["source"] == "spamhunter"]
    syn = load("data/synthetic_keep.jsonl")

    Xo, yo = xy(own); Xh, yh = xy(hunter)
    own_keep = np.array([r["label"] == "keep" for r in own])

    for name, train in [("public only", lic), ("public + synthetic", lic + syn)]:
        X, y = xy(train)
        m = build().fit(X, y)
        print(f"\n== trained on {name} ({len(X)} texts) — you as a NEW user")
        po = m.predict_proba(Xo)[:, 1]
        line("your texts", yo, po)
        line("spamhunter (never trained on)", yh, m.predict_proba(Xh)[:, 1])
        if name.endswith("synthetic"):
            print("  your good texts it would block:")
            for i in np.argsort(-po):
                if own_keep[i] and po[i] >= THR:
                    print(f"    p={po[i]:.2f} {Xo[i][:95]!r}")
            print("  your spam it misses (sample):")
            miss = [i for i in range(len(Xo)) if not own_keep[i] and po[i] < THR]
            for i in miss[:8]:
                print(f"    p={po[i]:.2f} {Xo[i][:95]!r}")

    # And for the user's own phone: everything combined, CV over the user's texts.
    X2, y2 = xy(lic + syn)
    cv = StratifiedKFold(5, shuffle=True, random_state=0)
    p = np.zeros(len(Xo))
    for tr, te in cv.split(Xo, yo):
        m = build().fit([Xo[i] for i in tr] + X2, np.concatenate([yo[tr], y2]))
        p[te] = m.predict_proba([Xo[i] for i in te])[:, 1]
    print("\n== your texts + public + synthetic (5-fold CV on your texts)")
    line("your texts", yo, p)
    m = build().fit(Xo + X2, np.concatenate([yo, y2]))
    line("spamhunter (never trained on)", yh, m.predict_proba(Xh)[:, 1])


if __name__ == "__main__":
    main()
