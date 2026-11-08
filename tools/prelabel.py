"""Rule-based first-pass labels for exported texts. Rough by design: the
'unsure' bucket is for a human (or model) to review.

Usage: python -I tools/prelabel.py <in.jsonl> <out.jsonl>
"""
import collections
import json
import re
import sys

BLOCK = [
    r"stop\s*2\s*end", r"end\s*2\s*end", r"actblue", r"winred", r"chip in",
    r"\bdonat", r"\bpitch in\b", r"\bmatch(ed|ing)?\b.*\d+\s*%|\d+\s*%.*\bmatch",
    r"\bpac\b", r"for (congress|senate|governor)", r"\bpoll(s)? (show|are)",
    r"\b(democrat|republican|gop|maga|trump|biden|harris)\b",
]
KEEP = [
    r"\b(verification|security|login|one[- ]time|access|confirmation) code\b",
    r"\bcode( is|:)\s*[A-Z0-9-]{4,8}\b", r"\b\d{4,8}\b is your\b", r"\bdo not share\b",
    r"\b(appointment|prescription|pharmacy|rx)\b", r"\b(delivered|out for delivery|tracking)\b",
    r"\b(payment|statement|balance|deposit|transaction)\b.*\b(due|posted|received|alert)\b",
]
block_re = re.compile("|".join(BLOCK), re.I)
keep_re = re.compile("|".join(KEEP), re.I)


def dedupe_key(t):
    t = re.sub(r"https?://\S+|\b\S+\.(com|org|io|us|net|ly)/\S*", "<url>", t.lower())
    t = re.sub(r"\d+", "#", t)
    return re.sub(r"\W+", " ", t).strip()[:120]


def main(src, dst):
    rows = [json.loads(l) for l in open(src, encoding="utf-8")]
    seen, out = set(), []
    for r in rows:
        k = dedupe_key(r["text"])
        if not k or k in seen:
            continue
        seen.add(k)
        b, kp = bool(block_re.search(r["text"])), bool(keep_re.search(r["text"]))
        r["label"] = "block" if b and not kp else "keep" if kp and not b else "unsure"
        r["label_source"] = "rules"
        out.append(r)
    with open(dst, "w", encoding="utf-8") as f:
        for r in out:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    c = collections.Counter(r["label"] for r in out)
    print(f"input {len(rows)}  after dedupe {len(out)}  {dict(c)}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
