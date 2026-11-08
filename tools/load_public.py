"""Normalize the downloaded public SMS datasets into one JSONL file.

Output rows: {"source", "label" (block/keep), "text", "license"}
  uci        UCI SMS Spam Collection (Almeida & Hidalgo 2011)        CC BY 4.0
  mishra     SMS Phishing Dataset (Mishra & Soni 2022, Mendeley)     CC BY 4.0
  jpm        JPM corpus, 2024 Democratic campaign texts (Figshare)   CC BY 4.0
  spamhunter SpamHunter tweet-reported spam (Tang et al. 2022)       NO LICENSE: evaluation only

Usage: .venv/Scripts/python -I tools/load_public.py
"""
import csv
import glob
import json
import os
import re

import openpyxl

ROOT = "data/public"
OUT = "data/public/combined.jsonl"

EN_WORDS = re.compile(r"\b(the|your|you|to|and|is|for|has|been|click|account|now|please|of)\b", re.I)


def is_english(t):
    letters = [c for c in t if c.isalpha()]
    if len(letters) < 15:
        return False
    ascii_ratio = sum(c.isascii() for c in letters) / len(letters)
    return ascii_ratio > 0.97 and len(EN_WORDS.findall(t)) >= 2


def main():
    rows = []

    for line in open(f"{ROOT}/uci/SMSSpamCollection", encoding="utf-8", errors="replace"):
        lab, _, text = line.rstrip("\n").partition("\t")
        if text:
            rows.append(("uci", "block" if lab == "spam" else "keep", text, "CC BY 4.0"))

    with open(f"{ROOT}/mishra/Dataset_5971.csv", encoding="utf-8", errors="replace", newline="") as f:
        for r in csv.DictReader(f):
            lab = r["LABEL"].strip().lower()
            rows.append(("mishra", "keep" if lab == "ham" else "block", r["TEXT"], "CC BY 4.0"))

    ws = openpyxl.load_workbook(f"{ROOT}/jpm/jpm.xlsx", read_only=True).active
    it = ws.iter_rows(values_only=True)
    header = next(it)
    ti = header.index("Text")
    for r in it:
        text = r[ti] if len(r) > ti else None
        if text and str(text).strip():
            rows.append(("jpm", "block", str(text), "CC BY 4.0"))

    for path in sorted(glob.glob(f"{ROOT}/spamhunter/tweet_sms_*.txt")):
        for line in open(path, encoding="utf-8", errors="replace"):
            t = line.strip()
            if is_english(t):
                rows.append(("spamhunter", "block", t, "none (eval only)"))

    seen, out = set(), []
    for src, lab, text, lic in rows:
        text = text.strip()
        key = re.sub(r"\W+", " ", text.lower()).strip()
        if not key or key in seen:
            continue
        seen.add(key)
        out.append({"source": src, "label": lab, "text": text, "license": lic})

    with open(OUT, "w", encoding="utf-8") as f:
        for r in out:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")

    from collections import Counter
    c = Counter((r["source"], r["label"]) for r in out)
    for k in sorted(c):
        print(f"{k[0]:<11} {k[1]:<6} {c[k]}")
    print(f"total {len(out)} -> {OUT}")


if __name__ == "__main__":
    main()
