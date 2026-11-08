"""Tag political spam texts by party, using sender-side signals (donation
platforms, campaign domains, self-identification), not topic words: Democratic
texts mention Trump constantly, so "Trump" alone says nothing.

Usage: python -I tools/party_tags.py [dataset.jsonl] -> data/party_tags.jsonl
"""
import collections
import json
import re
import sys

R = [
    r"winred", r"donaldjtrump\.com", r"\btnc20\d\d\b", r"\b4[57]\.(?:tnc|donaldjtrump)", r"^(?:from )?trump\s*[:\-]",
    r"this is (?:a message from )?donald trump", r"it'?s (?:donald|president) trump", r"\bnrsc\b", r"\bnrcc\b",
    r"\brnc\b", r"republican national", r"\bteam trump\b", r"trump (?:2024|campaign|national committee)",
    r"\bmaga (?:inc|pac)\b", r"save america", r"conservative (?:pac|fund)", r"\bgop\.com\b", r"\bjd vance here\b",
    r"\bfrom trump\b", r"\btrump: ",
]
D = [
    r"actblue", r"\bdccc\b", r"\bdscc\b", r"\bdnc\b", r"democratic (?:national|party|senate|congressional)",
    r"stopgopfund", r"stop gop", r"demturnout", r"dem turnout", r"turnoutpac", r"turnout pac", r"fwdblue", r"forwardblue",
    r"\bbold-pac\b", r"stand up pac", r"indigenous women lead", r"\bhmp(?:dems)?\b", r"house majority pac",
    r"kamala|harris for president|biden ?/? ?harris|biden victory fund", r"jamestalarico|james talarico", r"scott ?colom",
    r"shomari figures", r"ready ?for ?the ?fight", r"jb pritzker", r"keisha", r"angie nixon", r"jon tester", r"sherrod brown",
    r"no kings", r"planned parenthood", r"giffords", r"women'?s march", r"\bdemocrats?\b", r"\bdems\b", r"bernie",
    r"\bpeltola\b|\bhaaland\b|sharice davids|peggy flanagan", r"robert garcia", r"stop trump", r"beat trump", r"defeat trump",
    r"elect (?:more )?democrats", r"flip (?:the )?(?:house|senate)",
]
r_re = re.compile("|".join(R), re.I | re.M)
d_re = re.compile("|".join(D), re.I | re.M)


def tag(text):
    r, d = bool(r_re.search(text)), bool(d_re.search(text))
    return "R" if r and not d else "D" if d and not r else ("both" if r and d else None)


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else "data/dataset.jsonl"
    rows = [json.loads(l) for l in open(path, encoding="utf-8")]
    out = []
    for i, x in enumerate(rows):
        if x["label"] != "block":
            continue
        out.append({"i": i, "party": tag(x["text"]) or "unknown", "text": x["text"]})
    with open("data/party_tags.jsonl", "w", encoding="utf-8") as f:
        for r in out:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    print(collections.Counter(r["party"] for r in out))


if __name__ == "__main__":
    main()
