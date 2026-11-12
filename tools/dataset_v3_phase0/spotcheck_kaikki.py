#!/usr/bin/env python3
"""30-item spot-check for kaikki_relations.json.
Samples 30 rows (stratified, fixed seed) and re-verifies each one against a fresh
streaming pass over ko-extract.jsonl.gz: the dump must contain a lang_code=='ko'
entry for the headword whose synonyms/antonyms list contains the target word with
the same sense text (or no sense). Writes spotcheck_kaikki.json.
"""
import gzip
import json
import os
import random

BASE = os.path.expanduser("~/workspace/hangulmate/tools/dataset_v3_phase0")
DUMP = os.path.join(BASE, "downloads", "ko-extract.jsonl.gz")
OUT = os.path.join(BASE, "kaikki_relations.json")
SPOT = os.path.join(BASE, "spotcheck_kaikki.json")

d = json.load(open(OUT, encoding="utf-8"))
words = d["words"]
pools = {"verified similar": [], "verified opposite": [],
         "unverified similar": [], "unverified opposite": []}
for hw, w in words.items():
    for r in w.get("similar", []):
        pools["verified similar"].append((hw, "similar", r))
    for r in w.get("opposite", []):
        pools["verified opposite"].append((hw, "opposite", r))
    for r in w.get("sense_unverified", []):
        pools["unverified " + r["kind"]].append((hw, r["kind"], r))

rng = random.Random(20261111)
sample = []
for name, n in (("verified similar", 10), ("verified opposite", 5),
                ("unverified similar", 10), ("unverified opposite", 5)):
    pool = pools[name]
    take = rng.sample(pool, min(n, len(pool)))
    for hw, kind, r in take:
        sample.append({"bucket": name, "headword": hw, "kind": kind,
                       "target": r["korean"], "sense": r.get("sense"),
                       "dump_pos": r.get("dump_pos")})

wanted = set(s["headword"] for s in sample)
# fresh dump pass: collect every (kind, target, sense, pos) for wanted headwords
found = {}
for line in gzip.open(DUMP, "rt", encoding="utf-8"):
    e = json.loads(line)
    if e.get("lang_code") != "ko":
        continue
    w = e.get("word")
    if w not in wanted:
        continue
    for kind, key in (("similar", "synonyms"), ("opposite", "antonyms")):
        for r in e.get(key) or []:
            t = (r.get("word") or "").strip()
            found.setdefault((w, kind, t), []).append(
                {"sense": r.get("sense") or None, "pos": e.get("pos")})

results = []
for s in sample:
    hits = found.get((s["headword"], s["kind"], s["target"]), [])
    # match = same sense text (or both senseless)
    match = [h for h in hits if h["sense"] == s["sense"]]
    ok = bool(match)
    results.append({**s,
                    "confirmed_in_dump": ok,
                    "dump_hits_for_target": len(hits),
                    "matching_sense_hits": len(match),
                    "dump_pos_of_match": match[0]["pos"] if match else None})
    print(("OK  " if ok else "FAIL"), s["bucket"], "|",
          s["headword"], "->", s["target"], "| kind:", s["kind"],
          "| sense:", (s["sense"] or "(none)")[:50])

n_ok = sum(1 for r in results if r["confirmed_in_dump"])
print(f"spot-check: {n_ok}/30 confirmed")
with open(SPOT, "w", encoding="utf-8") as f:
    json.dump({"seed": 20261111, "sampled": 30, "confirmed": n_ok,
               "results": results}, f, ensure_ascii=False, indent=1)
