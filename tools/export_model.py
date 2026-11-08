"""Train the TF-IDF + logistic regression screener on the full dataset and
export it as plain JSON the Android app can evaluate without any ML runtime.

Also writes parity test cases (text -> expected probability) so the Kotlin
port can be checked against scikit-learn exactly.

Usage: .venv/Scripts/python -I tools/export_model.py
"""
import json
import os
import random
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
from eval_classifier import build  # same pipeline that was evaluated

ASSET = "android/app/src/debug/assets/screener_model.json"  # personal model, developer's phone only
PARITY = "android/app/src/test/resources/parity.jsonl"
GENERIC = "models/generic_model.json"  # contains no personal data


def vec_json(v, coef, kind):
    vocab = v.vocabulary_
    terms = [None] * len(vocab)
    for t, i in vocab.items():
        terms[i] = t
    return {
        "analyzer": kind,
        "ngram_min": v.ngram_range[0], "ngram_max": v.ngram_range[1],
        "terms": terms,
        "idf": [round(float(x), 6) for x in v.idf_],
        "coef": [round(float(x), 6) for x in coef],
    }


def main():
    generic = "--generic" in sys.argv
    if generic:
        # New-user model: licensed public data + synthetic keep texts, none of the user's.
        pub = [json.loads(l) for l in open("data/public/combined.jsonl", encoding="utf-8")]
        rows = [r for r in pub if r["source"] in ("uci", "mishra", "jpm")]
        rows += [json.loads(l) for l in open("data/synthetic_keep.jsonl", encoding="utf-8")]
        # Party balance: the only public political data (JPM) is all Democratic, which made the
        # model slightly softer on Republican-flavored spam. Add party-mirrored copies of political
        # spam so party can't be learned as a signal (see tools/eval_fairness.py).
        from eval_fairness import mirror
        from party_tags import tag
        seen = {r["text"] for r in rows}
        mirrored = [mirror(r["text"]) for r in rows if r["label"] == "block" and tag(r["text"])]
        rows += [{"label": "block", "text": t} for t in mirrored if t not in seen]
    else:
        rows = [json.loads(l) for l in open("data/dataset.jsonl", encoding="utf-8")]
    texts = [r["text"] for r in rows]
    y = np.array([r["label"] == "block" for r in rows], dtype=int)
    model = build().fit(texts, y)

    union, lr = model.steps[0][1], model.steps[1][1]
    (_, word), (_, char) = union.transformer_list
    nw = len(word.vocabulary_)
    coef = lr.coef_[0]
    out = {
        "version": 1,
        "threshold": 0.7,
        "intercept": float(lr.intercept_[0]),
        "vectorizers": [vec_json(word, coef[:nw], "word"), vec_json(char, coef[nw:], "char_wb")],
    }
    dest = GENERIC if generic else ASSET
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    with open(dest, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, separators=(",", ":"))
    if not generic:
        print(f"personal model: {os.path.getsize(dest) / 1e6:.1f} MB -> {dest}")
        return
    print(f"generic model: {os.path.getsize(dest) / 1e6:.1f} MB from {len(texts)} texts -> {dest}")

    # Parity cases (generic model only, so they contain no personal texts):
    # public/synthetic texts plus tricky edge cases (unicode, spacing).
    random.seed(0)
    cases = random.sample(texts, 150) + [
        "", "   ", "a", "Hi", "Your code is 123456", "𝗕𝗼𝗹𝗱 text ✨ café naïve",
        "multiple   spaces\tand\nnewlines", "CHIP IN $5 NOW!!! >>> i.example.com/x Stop2End",
        "İstanbul ǅ ß ΣΑΣ", "emoji 👍🏽 test 😀😀", "under_score words_here and 12 34",
    ]
    os.makedirs(os.path.dirname(PARITY), exist_ok=True)
    probs = model.predict_proba(cases)[:, 1]
    with open(PARITY, "w", encoding="utf-8") as f:
        for t, p in zip(cases, probs):
            f.write(json.dumps({"text": t, "p": float(p)}, ensure_ascii=False) + "\n")

    print(f"parity cases: {len(cases)} -> {PARITY}")


if __name__ == "__main__":
    main()
