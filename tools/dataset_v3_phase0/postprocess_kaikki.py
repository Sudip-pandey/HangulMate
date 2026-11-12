#!/usr/bin/env python3
"""Post-process kaikki_relations.json:
- mutual_in_dump: does the target's own Korean-Wiktionary entry list the headword
  back under the same kind? (second streaming pass over the dump)
- seed_gloss_overlap_words: distinctive English content words shared between the
  headword's and the target's seed definitions. REVIEW-PRIORITISATION SIGNAL ONLY
  -- it is NOT sense verification and must never alone promote a row to verified.
Rewrites kaikki_relations.json in place and updates header counts.
"""
import gzip
import json
import os
import re

BASE = os.path.expanduser("~/workspace/hangulmate/tools/dataset_v3_phase0")
SEED = os.path.expanduser(
    "~/workspace/hangulmate/app/src/main/assets/seed/vocabulary_seed.json")
DUMP = os.path.join(BASE, "downloads", "ko-extract.jsonl.gz")
OUT = os.path.join(BASE, "kaikki_relations.json")

STOP = set("""a an the of to in on at for with by from as and or but is are was were be been being
have has had do does did will would shall should can could may might must
i you he she it we they this that these those there here what which who whom whose
when where why how all any both each few more most other some such no nor not only own same so
than too very just also because until while about against between into through during before after
above below up down out off over under again further then once
person people thing things place time way ways kind kinds form forms state action act activity
process amount number part parts type types sort example case cases event events point points
one two three first second one one another other others use uses used using make makes made
take takes give gives get gets put set go goes come comes see sees know think feel feel felt
something someone anyone anything everybody everything nobody nothing
""".split())

TOKEN = re.compile(r"[a-z]+")


def stem(tok):
    if len(tok) > 5 and tok.endswith("ing"):
        return tok[:-3]
    if len(tok) > 4 and tok.endswith("ed"):
        return tok[:-2]
    if len(tok) > 4 and tok.endswith("es"):
        return tok[:-2]
    if len(tok) > 3 and tok.endswith("s"):
        return tok[:-1]
    return tok


def content_words(defs):
    out = set()
    for d in defs:
        for tok in TOKEN.findall(d.lower()):
            if len(tok) < 3 or tok in STOP:
                continue
            out.add(stem(tok))
    return out


seed = json.load(open(SEED, encoding="utf-8"))
by_kw = {e["korean"]: e for e in seed}
headwords = set(by_kw)
gloss_words = {}
for e in seed:
    defs = [d for s in e.get("senses", []) for d in s.get("definitions", [])]
    gloss_words[e["korean"]] = content_words(defs)
zero_def = [e["korean"] for e in seed if not any(
    s.get("definitions") for s in e.get("senses", []))]
print("headwords with zero seed definitions:", len(zero_def), zero_def[:5])

# Second dump pass: for in-set words, the sets of synonyms/antonyms they list.
syn_map, ant_map = {}, {}
for line in gzip.open(DUMP, "rt", encoding="utf-8"):
    e = json.loads(line)
    if e.get("lang_code") != "ko":
        continue
    w = e.get("word")
    if w not in headwords:
        continue
    s = syn_map.setdefault(w, set())
    a = ant_map.setdefault(w, set())
    for r in e.get("synonyms") or []:
        t = (r.get("word") or "").strip()
        if t:
            s.add(t)
    for r in e.get("antonyms") or []:
        t = (r.get("word") or "").strip()
        if t:
            a.add(t)

d = json.load(open(OUT, encoding="utf-8"))
words = d["words"]
mutual = {"similar": 0, "opposite": 0}
overlap_rows = {"similar": 0, "opposite": 0}
overlap_words_total = 0
for hw, w in words.items():
    for kind in ("similar", "opposite"):
        for r in w.get(kind, []):
            tgt = r["korean"]
            m = hw in (syn_map if kind == "similar" else ant_map).get(tgt, set())
            r["mutual_in_dump"] = bool(m)
            if m:
                mutual[kind] += 1
            ov = sorted(gloss_words[hw] & gloss_words[tgt])
            r["seed_gloss_overlap_words"] = ov
            if ov:
                overlap_rows[kind] += 1
                overlap_words_total += len(ov)
    for r in w.get("sense_unverified", []):
        kind = r["kind"]
        tgt = r["korean"]
        m = hw in (syn_map if kind == "similar" else ant_map).get(tgt, set())
        r["mutual_in_dump"] = bool(m)
        if m:
            mutual[kind] += 1
        ov = sorted(gloss_words[hw] & gloss_words[tgt])
        r["seed_gloss_overlap_words"] = ov
        if ov:
            overlap_rows[kind] += 1
            overlap_words_total += len(ov)

d["header"]["review_signals"] = {
    "mutual_in_dump_note": "Target's own Korean-Wiktionary entry lists the headword back under the same kind. Confirms the relation exists in Wiktionary from both sides; does NOT verify the sense against our seed entry.",
    "mutual_rows": mutual,
    "seed_gloss_overlap_note": "Distinctive English content words shared between the headword's and the target's seed (NIKL/v1) definitions. REVIEW-PRIORITISATION SIGNAL ONLY -- never a verification, never a merge criterion on its own. Tokenised lowercase, light stemming (ing/ed/es/s stripped), standard + generic-gloss stopwords removed.",
    "rows_with_gloss_overlap": overlap_rows,
}
with open(OUT, "w", encoding="utf-8") as f:
    json.dump(d, f, ensure_ascii=False, indent=1)
print("mutual rows:", mutual)
print("rows with gloss overlap:", overlap_rows)
print("out bytes:", os.path.getsize(OUT))
