"""Is the spam model partisan?

1. Real texts: catch rate on clearly-Republican campaign texts (Trump campaign
   domains, WinRed) vs clearly-Democratic ones (ActBlue etc.).
2. Counterfactual: mirror every party signal in each political spam text
   (ActBlue<->WinRed, Democrats<->Republicans, Harris<->Trump, ...) and compare
   the model's score on the original vs the mirror. A fair model barely moves.

Run for the personal model (5-fold CV, so texts are never scored by a model
trained on them) and the generic new-user model (never saw these texts).

Usage: .venv/Scripts/python tools/eval_fairness.py [--augment]
  --augment: also train with party-mirrored copies of political spam, the fix.
"""
import json
import os
import re
import sys

import numpy as np
from sklearn.model_selection import StratifiedKFold

sys.path.insert(0, os.path.dirname(__file__))
from eval_classifier import build
from party_tags import tag

THR = 0.7

PAIRS = [
    ("ActBlue", "WinRed"), ("Democrats", "Republicans"), ("Democratic", "Republican"),
    ("Democrat", "Republican"), ("Dems", "GOP"), ("Dem", "GOP"), ("DCCC", "NRCC"), ("DSCC", "NRSC"),
    ("DNC", "RNC"), ("Harris", "Trump"), ("Kamala", "Donald"), ("Biden", "Vance"), ("Obama", "Reagan"),
    ("Walz", "Vance"), ("progressive", "conservative"), ("liberal", "conservative"), ("blue", "red"),
    ("Pelosi", "McConnell"), ("Schumer", "Johnson"), ("AOC", "MTG"), ("Newsom", "DeSantis"),
    ("MAGA", "woke"),
]
_map = {}
for a, b in PAIRS:
    _map.setdefault(a.lower(), b)
    _map.setdefault(b.lower(), a)
_re = re.compile(r"\b(" + "|".join(sorted({re.escape(w) for p in PAIRS for w in p}, key=len, reverse=True)) + r")\b", re.I)


def mirror(text):
    def sub(m):
        w = m.group(0)
        r = _map[w.lower()]
        if w.isupper() and len(w) > 1: return r.upper()
        if w[0].isupper(): return r[0].upper() + r[1:]
        return r.lower() if r.lower() not in ("gop", "dnc", "rnc", "nrcc", "nrsc", "dccc", "dscc", "maga", "aoc", "mtg") else r
    return _re.sub(sub, text)


def load(p): return [json.loads(l) for l in open(p, encoding="utf-8")]


# Clearly-partisan real texts, by sender-side signal only (stricter than party_tags).
R_STRICT = re.compile(r"winred|donaldjtrump\.com|\btnc20\d\d\b|^(from )?trump\s*:|it'?s trump\b|message from donald trump", re.I | re.M)
D_STRICT = re.compile(r"actblue|\bdccc\b|\bdscc\b|stopgopfund|demturnout|turnoutpac|jamestalarico|scottcolom|biden victory fund", re.I)


def report(name, y, p, pol_idx, p_mirror, r_idx, d_idx):
    print(f"\n== {name}")
    def rate(ix): return (p[ix] >= THR).mean() if len(ix) else float("nan")
    print(f"  real Republican campaign texts: caught {int((p[r_idx] >= THR).sum())}/{len(r_idx)} ({rate(r_idx):.0%})")
    print(f"  real Democratic campaign texts: caught {int((p[d_idx] >= THR).sum())}/{len(d_idx)} ({rate(d_idx):.0%})")
    o, m = p[pol_idx], p_mirror
    changed = m != o
    print(f"  mirror test on {int(changed.sum())} political texts with party words:")
    print(f"    caught as written: {(o[changed] >= THR).mean():.1%}   caught when mirrored: {(m[changed] >= THR).mean():.1%}")
    print(f"    mean score as written {o[changed].mean():.3f} vs mirrored {m[changed].mean():.3f}; "
          f"flips to allowed: {int(((o >= THR) & (m < THR) & changed).sum())}, flips to caught: {int(((o < THR) & (m >= THR) & changed).sum())}")


def main():
    augment = "--augment" in sys.argv
    own = load("data/dataset.jsonl")
    pub = load("data/public/combined.jsonl")
    lic = [r for r in pub if r["source"] in ("uci", "mishra", "jpm")]
    syn = load("data/synthetic_keep.jsonl")

    X = [r["text"] for r in own]
    y = np.array([r["label"] == "block" for r in own], dtype=int)
    pol_idx = np.array([i for i, r in enumerate(own) if r["label"] == "block" and tag(r["text"]) is not None])
    r_idx = np.array([i for i, r in enumerate(own) if r["label"] == "block" and R_STRICT.search(r["text"])])
    d_idx = np.array([i for i, r in enumerate(own) if r["label"] == "block" and D_STRICT.search(r["text"]) and i not in set(r_idx)])
    X_m = [mirror(X[i]) for i in pol_idx]

    def aug(texts, labels):
        if not augment:
            return texts, labels
        extra = [mirror(t) for t, l in zip(texts, labels) if l == 1 and tag(t) is not None]
        extra = [t for t in extra if t not in set(texts)]
        return texts + extra, np.concatenate([labels, np.ones(len(extra), dtype=int)])

    print(f"{'WITH' if augment else 'WITHOUT'} party-mirrored training copies")
    print(f"political spam with party signals: {len(pol_idx)}; strict R: {len(r_idx)}; strict D: {len(d_idx)}")

    # Personal model, 5-fold CV.
    p = np.zeros(len(X)); pm = np.zeros(len(pol_idx))
    pos = {i: k for k, i in enumerate(pol_idx)}
    for tr, te in StratifiedKFold(5, shuffle=True, random_state=0).split(X, y):
        Xt, yt = aug([X[i] for i in tr], y[tr])
        m = build().fit(Xt, yt)
        p[te] = m.predict_proba([X[i] for i in te])[:, 1]
        te_pol = [i for i in te if i in pos]
        if te_pol:
            pm[[pos[i] for i in te_pol]] = m.predict_proba([X_m[pos[i]] for i in te_pol])[:, 1]
    report("personal model (your phone), 5-fold CV", y, p, pol_idx, pm, r_idx, d_idx)

    # Generic model: public + synthetic, never saw these texts.
    Xg = [r["text"] for r in lic + syn]
    yg = np.array([r["label"] == "block" for r in lic + syn], dtype=int)
    Xg, yg = aug(Xg, yg)
    g = build().fit(Xg, yg)
    report("generic model (testers / Play Store)", y, g.predict_proba(X)[:, 1], pol_idx,
           g.predict_proba(X_m)[:, 1], r_idx, d_idx)

    print("\nexample mirrors:")
    for i in pol_idx[:3]:
        print("   ", X[i][:90].replace("\n", " "))
        print(" ->", mirror(X[i])[:90].replace("\n", " "))


if __name__ == "__main__":
    main()
