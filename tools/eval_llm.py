"""Score a local Ollama model as a block/keep SMS screener.

Usage: python -I tools/eval_llm.py <model> [dataset.jsonl] [--limit N]

Writes per-message predictions to results/<model>.jsonl and prints a summary.
The worst error is a wrongly blocked "keep" text, so those are listed in full.
"""
import concurrent.futures as cf
import json
import os
import sys
import time
import urllib.request

OLLAMA = "http://127.0.0.1:11434/api/chat"

SYSTEM = """You screen text messages from phone numbers that are NOT in the user's contacts.
Decide whether to BLOCK (silence) or KEEP (notify) each message.

BLOCK:
- Political texts of any kind: campaign fundraising, donation "matches", petitions, polls/surveys, rally or protest invitations, get-out-the-vote reminders, advocacy groups.
- Scams and phishing: fake account/password/login alerts that say to call a number or tap a link, fake delivery or toll notices, prizes, crypto, loan or grant offers, job offers.
- Conversation openers from strangers that look like scam bait ("Hi, is this Sarah?", "Are you free for coffee?").
- Unsolicited marketing and sales pitches.

KEEP:
- One-time passcodes and verification codes from real services (they do not ask you to call a phone number).
- Transactional messages: orders, shipping, deliveries, bank or card alerts, bills, payments, appointments, prescriptions, health plan notices, school alerts, carrier/account notices.
- Genuine personal messages or replies about something the user is already doing.

When unsure whether a message is a real transactional notice, prefer KEEP.
Answer with JSON: {"verdict": "block"} or {"verdict": "keep"}."""

SCHEMA = {"type": "object", "properties": {"verdict": {"type": "string", "enum": ["block", "keep"]}},
          "required": ["verdict"]}


def classify(model, text):
    body = json.dumps({
        "model": model, "stream": False, "format": SCHEMA,
        "options": {"temperature": 0, "num_predict": 20},
        "messages": [{"role": "system", "content": SYSTEM},
                     {"role": "user", "content": f"Message:\n{text[:1200]}"}],
    }).encode()
    req = urllib.request.Request(OLLAMA, body, {"Content-Type": "application/json"})
    t = time.time()
    with urllib.request.urlopen(req, timeout=120) as r:
        content = json.loads(r.read())["message"]["content"]
    try:
        verdict = json.loads(content)["verdict"]
    except Exception:
        verdict = "keep"  # unparseable -> fail safe
    return verdict, time.time() - t


def main():
    args = sys.argv[1:]
    limit = None
    if "--limit" in args:
        i = args.index("--limit"); limit = int(args[i + 1]); del args[i:i + 2]
    model = args[0]
    path = args[1] if len(args) > 1 else "data/dataset.jsonl"
    rows = [json.loads(l) for l in open(path, encoding="utf-8")]
    if limit:
        rows = rows[:limit]

    classify(model, "warm up")  # load model before timing
    with cf.ThreadPoolExecutor(4) as ex:
        results = list(ex.map(lambda r: classify(model, r["text"]), rows))

    os.makedirs("results", exist_ok=True)
    out = f"results/{model.replace(':', '_')}.jsonl"
    tp = fp = fn = tn = 0
    with open(out, "w", encoding="utf-8") as f:
        for r, (pred, secs) in zip(rows, results):
            f.write(json.dumps({"label": r["label"], "pred": pred, "secs": round(secs, 3), "text": r["text"]},
                               ensure_ascii=False) + "\n")
            if r["label"] == "block":
                tp += pred == "block"; fn += pred == "keep"
            else:
                fp += pred == "block"; tn += pred == "keep"

    n_block, n_keep = tp + fn, fp + tn
    print(f"== {model}  ({len(rows)} texts, median {sorted(s for _, s in results)[len(results)//2]:.2f}s each)")
    print(f"spam caught:          {tp}/{n_block} ({tp / max(n_block, 1):.1%})")
    print(f"good texts kept:      {tn}/{n_keep} ({tn / max(n_keep, 1):.1%})")
    print(f"good texts BLOCKED:   {fp}   <- worst error")
    print(f"spam let through:     {fn}")
    print(f"predictions: {out}")


if __name__ == "__main__":
    main()
