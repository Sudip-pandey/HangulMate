#!/usr/bin/env python3
"""Kaikki (Korean Wiktionary, wiktextract) relations extraction for HangulMate seed v3.
Reads ko-extract.jsonl.gz line by line, extracts synonyms/antonyms for the 10,000
seed headwords, applies the in-set / dedup / sense-verification rules, and writes
kaikki_relations.json. Does NOT modify the v2 seed.
"""
import gzip
import hashlib
import json
import os
from datetime import datetime, timezone

BASE = os.path.expanduser("~/workspace/hangulmate/tools/dataset_v3_phase0")
SEED = os.path.expanduser(
    "~/workspace/hangulmate/app/src/main/assets/seed/vocabulary_seed.json")
DUMP = os.path.join(BASE, "downloads", "ko-extract.jsonl.gz")
OUT = os.path.join(BASE, "kaikki_relations.json")

POS_MAP = {
    "noun": "noun", "verb": "verb", "adjective": "adjective",
    "adverb": "adverb", "pronoun": "pronoun", "numeral": "number",
    "interjection": "interjection",
}

seed = json.load(open(SEED, encoding="utf-8"))
assert len(seed) == 10000
headwords = set(e["korean"] for e in seed)
by_kw = {e["korean"]: e for e in seed}
# definition counts per headword (seed senses, English NIKL/v1 glosses)
def_count = {}
for e in seed:
    def_count[e["korean"]] = sum(
        len(s.get("definitions", [])) for s in e.get("senses", []))
# existing v1 relations (first 236 entries, HM-0001..HM-0236) to never duplicate
v1_existing = set()
for e in seed[:236]:
    for r in e.get("similar", []):
        v1_existing.add((e["korean"], "similar", r["korean"]))
    for r in e.get("opposite", []):
        v1_existing.add((e["korean"], "opposite", r["korean"]))
v1_words = set(e["korean"] for e in seed[:236])

sha = hashlib.sha256()
with open(DUMP, "rb") as f:
    for chunk in iter(lambda: f.read(1 << 20), b""):
        sha.update(chunk)

stats = {
    "dump_lines_total": 0, "dump_ko_entries": 0,
    "dump_ko_with_synonyms": 0, "dump_ko_with_antonyms": 0,
    "headwords_found_in_dump": 0, "headwords_with_raw_relations": 0,
    "raw_relation_rows": {"similar": 0, "opposite": 0},
    "dropped_out_of_set": {"similar": 0, "opposite": 0},
    "dropped_self": {"similar": 0, "opposite": 0},
    "dropped_dup_v1": {"similar": 0, "opposite": 0},
    "dropped_dup_across_entries": {"similar": 0, "opposite": 0},
    "kept_verified": {"similar": 0, "opposite": 0},
    "kept_unverified": {"similar": 0, "opposite": 0},
    "unverified_reasons": {},
    "v1_word_new_relations": {"similar": 0, "opposite": 0},
}

# headword -> kind -> target -> row (first occurrence wins)
kept = {}
found = set()
with_raw = set()


def add_row(hw, bucket, row, stat_kind=None, key=None):
    kept.setdefault(hw, {}).setdefault(bucket, {})
    key = key or row["korean"]
    if key in kept[hw][bucket]:
        stats["dropped_dup_across_entries"][stat_kind or bucket] += 1
        return False
    kept[hw][bucket][key] = row
    return True


for line in gzip.open(DUMP, "rt", encoding="utf-8"):
    stats["dump_lines_total"] += 1
    e = json.loads(line)
    if e.get("lang_code") != "ko":
        continue
    stats["dump_ko_entries"] += 1
    word = e.get("word")
    if word not in headwords:
        continue
    found.add(word)
    syn = e.get("synonyms") or []
    ant = e.get("antonyms") or []
    if syn:
        stats["dump_ko_with_synonyms"] += 1
    if ant:
        stats["dump_ko_with_antonyms"] += 1
    if not syn and not ant:
        continue
    with_raw.add(word)
    pos = e.get("pos")
    seed_pos = by_kw[word].get("partOfSpeech")
    single_sense = def_count[word] == 1
    pos_ok = POS_MAP.get(pos) == seed_pos
    entry_glosses = [g for s in e.get("senses", []) for g in s.get("glosses", [])]
    for kind, arr in (("similar", syn), ("opposite", ant)):
        for rel in arr:
            target = (rel.get("word") or "").strip()
            stats["raw_relation_rows"][kind] += 1
            if not target:
                continue
            if target == word:
                stats["dropped_self"][kind] += 1
                continue
            if target not in headwords:
                stats["dropped_out_of_set"][kind] += 1
                continue
            if (word, kind, target) in v1_existing:
                stats["dropped_dup_v1"][kind] += 1
                continue
            sense = rel.get("sense") or None
            # Sense verification (strict, documented in KAIKKI_NOTES.md):
            # verified ONLY if the dump lists the relation with NO sense text
            # (sense-agnostic), the headword has exactly ONE sense in the seed,
            # and the dump entry's POS maps to the seed partOfSpeech.
            if sense:
                verified, reason = False, "sense-tagged (Korean gloss; cannot match to English NIKL gloss)"
            elif not single_sense:
                verified, reason = False, "multi-sense headword (%d seed senses); sense-agnostic relation cannot be pinned to one sense" % def_count[word]
            elif not pos_ok:
                verified, reason = False, "POS mismatch/unmapped (dump pos=%s, seed pos=%s)" % (pos, seed_pos)
            else:
                verified, reason = True, None
            row = {
                "korean": target,
                "noteNe": "",
                "source": "kaikki-ko",
                "sense": sense,
                "sense_verified": verified,
                "dump_pos": pos,
                "dump_sense_matches_entry_gloss": (
                    any(sense and (sense in g or g in sense) for g in entry_glosses)
                    if sense else None),
            }
            if not verified:
                row["unverified_reason"] = reason
                stats["unverified_reasons"][reason.split("(")[0].strip()] = \
                    stats["unverified_reasons"].get(reason.split("(")[0].strip(), 0) + 1
                stats["kept_unverified"][kind] += 1
                if word in v1_words:
                    stats["v1_word_new_relations"][kind] += 1
                row["_kind"] = kind
                add_row(word, "sense_unverified", row, stat_kind=kind,
                        key=kind + "|" + row["korean"])
            else:
                stats["kept_verified"][kind] += 1
                if word in v1_words:
                    stats["v1_word_new_relations"][kind] += 1
                add_row(word, kind, row)

stats["headwords_found_in_dump"] = len(found)
stats["headwords_with_raw_relations"] = len(with_raw)

words = {}
for hw in sorted(kept):
    w = {}
    for kind in ("similar", "opposite"):
        rows = kept[hw].get(kind, {})
        if rows:
            w[kind] = [{k: v for k, v in r.items() if not k.startswith("_")}
                       for r in rows.values()]
    unv = kept[hw].get("sense_unverified", {})
    if unv:
        w["sense_unverified"] = [
            dict({"kind": r["_kind"]},
                 **{k: v for k, v in r.items() if not k.startswith("_")})
            for r in unv.values()]
    if w:
        words[hw] = w

words_with_verified_sim = sum(1 for w in words.values() if w.get("similar"))
words_with_verified_opp = sum(1 for w in words.values() if w.get("opposite"))
words_with_any_unv = sum(1 for w in words.values() if w.get("sense_unverified"))
words_with_any = len(words)

header = {
    "dump": "Kaikki.org Korean Wiktionary extract (wiktextract)",
    "dump_url": "https://kaikki.org/dictionary/downloads/ko/ko-extract.jsonl.gz",
    "dump_last_modified": "Fri, 30 Oct 2026 20:51:01 GMT",
    "dump_size_bytes_gz": os.path.getsize(DUMP),
    "dump_sha256": sha.hexdigest(),
    "dump_lines_total": stats["dump_lines_total"],
    "dump_ko_entries": stats["dump_ko_entries"],
    "generated_utc": datetime.now(timezone.utc).isoformat(),
    "seed": "vocabulary_seed.json (10,000 entries, HM-0001..HM-10000)",
    "enwiktionary": "SKIPPED — full English-Wiktionary dump is 534 MB gz / 3,431,211,190 B (3.4 GB) uncompressed, and kaikki.org publishes no per-language Korean extract of the English edition (see KAIKKI_NOTES.md).",
    "sense_verification_rule": (
        "sense_verified=true ONLY when ALL hold: (a) the dump relation carries NO sense text "
        "(sense-agnostic), (b) the headword has exactly ONE sense (one definition) in the seed, "
        "(c) the dump entry POS maps to the seed partOfSpeech. Relations with a Korean sense gloss "
        "cannot be matched to the seed's English (NIKL) glosses without translation, so they go to "
        "sense_unverified. Multi-sense headwords and POS mismatches are likewise unverified. "
        "Unverified rows are NOT merge rows; they are a review list."),
    "counts": {
        "headwords_total": 10000,
        "headwords_found_in_dump": stats["headwords_found_in_dump"],
        "headwords_with_raw_relations_in_dump": stats["headwords_with_raw_relations"],
        "words_with_any_relation_in_output": words_with_any,
        "words_with_verified_similar": words_with_verified_sim,
        "words_with_verified_opposite": words_with_verified_opp,
        "words_with_only_unverified_relations": sum(
            1 for w in words.values()
            if w.get("sense_unverified") and not w.get("similar") and not w.get("opposite")),
        "words_with_unverified_relations": words_with_any_unv,
        "verified_similar_rows": stats["kept_verified"]["similar"],
        "verified_opposite_rows": stats["kept_verified"]["opposite"],
        "unverified_similar_rows": stats["kept_unverified"]["similar"],
        "unverified_opposite_rows": stats["kept_unverified"]["opposite"],
        "raw_relation_rows": stats["raw_relation_rows"],
        "dropped_out_of_set": stats["dropped_out_of_set"],
        "dropped_self": stats["dropped_self"],
        "dropped_dup_existing_v1": stats["dropped_dup_v1"],
        "dropped_dup_across_dump_entries": stats["dropped_dup_across_entries"],
        "unverified_reasons": stats["unverified_reasons"],
        "new_relations_for_v1_words_included": stats["v1_word_new_relations"],
    },
}

out = {"header": header, "words": words}
with open(OUT, "w", encoding="utf-8") as f:
    json.dump(out, f, ensure_ascii=False, indent=1)
print(json.dumps(stats, ensure_ascii=False, indent=1))
print("words in output:", words_with_any,
      "| verified sim words:", words_with_verified_sim,
      "| verified opp words:", words_with_verified_opp,
      "| only-unverified words:", sum(1 for w in words.values() if w.get("sense_unverified") and not w.get("similar") and not w.get("opposite")))
print("out bytes:", os.path.getsize(OUT))
