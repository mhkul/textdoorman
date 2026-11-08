"""Export received SMS from senders not in the phone's contacts, via adb.

Usage: python -I tools/export_unknown_sms.py <adb.exe> <out.jsonl>

Only message text, a normalized sender key, and the date are kept. Nothing
leaves this PC.
"""
import json
import re
import subprocess
import sys
from datetime import datetime, timezone


def adb_query(adb, uri, projection):
    out = subprocess.run(
        [adb, "shell", f"content query --uri {uri} --projection {projection}"],
        capture_output=True, check=True,
    ).stdout.decode("utf-8", errors="replace")
    # Rows start with "Row: N "; a value may span several lines (multi-line bodies).
    rows, cur = [], None
    for line in out.replace("\r\n", "\n").split("\n"):
        m = re.match(r"^Row: \d+ (.*)$", line)
        if m:
            cur = m.group(1)
            rows.append(cur)
        elif rows:
            rows[-1] += "\n" + line
    return rows


def norm(num):
    digits = re.sub(r"\D", "", num or "")
    return digits[-10:] if len(digits) >= 10 else digits


def main(adb, out_path):
    contacts = set()
    for row in adb_query(adb, "content://com.android.contacts/data/phones", "data1"):
        n = norm(row.split("data1=", 1)[1] if "data1=" in row else "")
        if n:
            contacts.add(n)

    # body last so everything after "body=" is the message, commas and all.
    rows = adb_query(adb, "content://sms/inbox", "address:date:body")
    kept = skipped = 0
    with open(out_path, "w", encoding="utf-8") as f:
        for row in rows:
            m = re.match(r"^address=(.*?), date=(\d+), body=(.*)$", row, re.S)
            if not m:
                continue
            addr, date, body = m.groups()
            key = norm(addr)
            if key in contacts:
                skipped += 1
                continue
            kind = "letters" if not key else ("shortcode" if len(key) < 10 else "longcode")
            f.write(json.dumps({
                "sender_kind": kind,
                "date": datetime.fromtimestamp(int(date) / 1000, timezone.utc).strftime("%Y-%m-%d"),
                "text": body.strip(),
            }, ensure_ascii=False) + "\n")
            kept += 1
    print(f"contacts: {len(contacts)}  inbox: {len(rows)}  from contacts (skipped): {skipped}  unknown senders exported: {kept}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
