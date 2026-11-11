#!/usr/bin/env python3
"""Validate HangulMate vocabulary_seed.json (v2, NIKL).

Usage: python3 tools/validate_seed_v2.py [path-to-vocabulary_seed.json]
Exit non-zero on any failure; prints exact counts.

v2 differences from v1 (tools/validate_seed.py):
- IDs are HM-0001..HM-0236 (v1, 4 digits) plus HM-10001.. (NIKL, 5 digits).
- `nepali` is nullable: null + nepaliStatus "missing" when no verified
  Nepali exists (never fabricated). "verified" Nepali must be Devanagari.
- New fields: nepaliStatus, senses (>=1 English sense, each a list of
  definitions), source ("NIKL" | "v1" | "NIKL+v1"), level, semanticCategory.
- exampleKo/exampleNe/exampleEn may be empty (NIKL usages are Korean-only;
  40 entries have no usable example at all -- reported, not fatal).
- Relations stay sparse and real: 80, all targets present in the dataset.
"""
import json
import re
import sys
from collections import Counter
from pathlib import Path

HANGUL_RE = re.compile(r"^[가-힣\s·\-]+$")
DEVANAGARI_RE = re.compile(r"[\u0900-\u097F]")
ASCII_ROMAN_RE = re.compile(r"^[a-zA-Z\s'\-]+$")
SYLLABLE_RE = re.compile(r"^[가-힣]$")
ID_RE = re.compile(r"^HM-\d{4,5}$")
POS = {"noun", "verb", "adjective", "adverb", "expression", "number",
       "pronoun", "particle", "interjection"}
TOPICS = {"daily", "workplace", "agriculture", "manufacturing", "construction",
          "food", "transport", "health", "conversation", "numbers",
          "directions", "tools", "safety", "general"}
STATUS = {"verified", "mt_review", "missing"}
SOURCE = {"NIKL", "v1", "NIKL+v1"}
LEVELS = {"초급", "중급", "고급", None}
REQUIRED_STR = ["korean", "english", "romanization", "partOfSpeech",
                "topic", "epsImportance"]
REQUIRED_ARR = ["syllables", "similar", "opposite", "confusing", "tags", "senses"]


def main() -> int:
    path = Path(sys.argv[1]) if len(sys.argv) > 1 else (
        Path(__file__).resolve().parent.parent
        / "app/src/main/assets/seed/vocabulary_seed.json")
    errors, warnings = [], []
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:  # noqa: BLE001
        print(f"FAIL: could not load {path}: {exc}")
        return 1
    if not isinstance(data, list) or not data:
        print("FAIL: dataset must be a non-empty JSON array")
        return 1

    ids, koreans = set(), {}
    status_counts = Counter()
    with_example = 0
    for i, e in enumerate(data):
        where = f"entry[{i}] id={e.get('id', '?')} korean={e.get('korean', '?')}"
        if not isinstance(e, dict):
            errors.append(f"{where}: not an object")
            continue
        eid = e.get("id")
        if not isinstance(eid, str) or not ID_RE.match(eid):
            errors.append(f"{where}: bad id {eid!r}")
        elif eid in ids:
            errors.append(f"{where}: duplicate id {eid}")
        else:
            ids.add(eid)
        for f in REQUIRED_STR:
            v = e.get(f)
            if v is None or (isinstance(v, str) and not v.strip()):
                errors.append(f"{where}: required field {f!r} missing/empty")
        for f in REQUIRED_ARR:
            if not isinstance(e.get(f), list):
                errors.append(f"{where}: {f} must be an array")
        k = e.get("korean")
        if isinstance(k, str):
            if not HANGUL_RE.match(k):
                errors.append(f"{where}: korean not Hangul-only: {k!r}")
            if k in koreans:
                errors.append(f"{where}: duplicate korean {k!r} (also {koreans[k]})")
            else:
                koreans[k] = eid
        # --- nepali + status ------------------------------------------
        status = e.get("nepaliStatus")
        status_counts[status] += 1
        if status not in STATUS:
            errors.append(f"{where}: bad nepaliStatus {status!r}")
        nep = e.get("nepali")
        if status == "missing":
            if nep is not None:
                errors.append(f"{where}: status missing but nepali is set: {nep!r}")
        elif status in ("verified", "mt_review"):
            if not isinstance(nep, str) or not nep.strip():
                errors.append(f"{where}: status {status} but nepali empty")
            elif not DEVANAGARI_RE.search(nep):
                errors.append(f"{where}: nepali lacks Devanagari: {nep!r}")
        # --- romanization / enums --------------------------------------
        if isinstance(e.get("romanization"), str) and not ASCII_ROMAN_RE.match(e["romanization"]):
            errors.append(f"{where}: romanization not ASCII RR-like: {e['romanization']!r}")
        if e.get("partOfSpeech") not in POS:
            errors.append(f"{where}: bad partOfSpeech {e.get('partOfSpeech')!r}")
        if e.get("topic") not in TOPICS:
            errors.append(f"{where}: bad topic {e.get('topic')!r}")
        if e.get("difficulty") not in (1, 2, 3):
            errors.append(f"{where}: bad difficulty {e.get('difficulty')!r}")
        if e.get("epsImportance") not in ("high", "medium", "low"):
            errors.append(f"{where}: bad epsImportance {e.get('epsImportance')!r}")
        if e.get("source") not in SOURCE:
            errors.append(f"{where}: bad source {e.get('source')!r}")
        if e.get("level") not in LEVELS:
            errors.append(f"{where}: bad level {e.get('level')!r}")
        # --- syllables ---------------------------------------------------
        syl = e.get("syllables")
        if isinstance(syl, list) and isinstance(k, str):
            if any(not isinstance(s, str) or not SYLLABLE_RE.match(s) for s in syl):
                errors.append(f"{where}: syllables must each be one Hangul block: {syl!r}")
            elif "".join(syl) != k.replace(" ", ""):
                errors.append(f"{where}: syllables {''.join(syl)!r} != korean {k!r}")
        # --- senses: >=1 English sense ----------------------------------
        senses = e.get("senses")
        if isinstance(senses, list):
            if not senses:
                errors.append(f"{where}: senses must be non-empty (>=1 English sense)")
            for s in senses:
                if not isinstance(s, dict) or s.get("pos") not in POS:
                    errors.append(f"{where}: sense with bad pos: {s!r}")
                elif not isinstance(s.get("definitions"), list) or not s["definitions"]:
                    errors.append(f"{where}: sense without definitions: {s!r}")
                elif any(not isinstance(d, str) or not d.strip() for d in s["definitions"]):
                    errors.append(f"{where}: sense with empty definition: {s!r}")
        # --- examples (reported, not fatal) ------------------------------
        if isinstance(e.get("exampleKo"), str) and e["exampleKo"].strip():
            with_example += 1
        # --- relations -----------------------------------------------------
        for rel, note_key in (("similar", "noteNe"), ("opposite", "noteNe"),
                              ("confusing", "differenceNe")):
            items = e.get(rel)
            if not isinstance(items, list):
                continue
            for r in items:
                if not isinstance(r, dict) or not isinstance(r.get("korean"), str) \
                        or not r["korean"].strip():
                    errors.append(f"{where}: {rel} item missing korean: {r!r}")
                elif not isinstance(r.get(note_key), str) or not r[note_key].strip():
                    errors.append(f"{where}: {rel} {r.get('korean')} missing {note_key}")
                elif not DEVANAGARI_RE.search(r[note_key]):
                    errors.append(f"{where}: {rel} {r.get('korean')} note lacks Devanagari")
        tags = e.get("tags")
        if not isinstance(tags, list) or any(not isinstance(t, str) or not t.strip()
                                             for t in (tags or [])):
            errors.append(f"{where}: tags must be an array of non-empty strings")

    # cross-entry relation checks
    for i, e in enumerate(data):
        if not isinstance(e, dict):
            continue
        where = f"entry[{i}] id={e.get('id', '?')} korean={e.get('korean', '?')}"
        for rel in ("similar", "opposite", "confusing"):
            for r in e.get(rel, []) or []:
                if isinstance(r, dict) and isinstance(r.get("korean"), str):
                    if r["korean"] not in koreans:
                        errors.append(f"{where}: dangling {rel} target {r['korean']!r}")
        sim = {r["korean"] for r in e.get("similar", []) if isinstance(r, dict)}
        opp = {r["korean"] for r in e.get("opposite", []) if isinstance(r, dict)}
        if sim & opp:
            errors.append(f"{where}: word(s) both similar and opposite: {sorted(sim & opp)}")
        if e.get("korean") in sim or e.get("korean") in opp:
            errors.append(f"{where}: self-relation")

    # summary
    print(f"Entries: {len(data)}")
    print(f"Nepali status: {dict(sorted(status_counts.items()))}")
    print(f"With exampleKo: {with_example} ({100 * with_example // len(data)}%)")
    print(f"Topics: {dict(sorted(Counter(x.get('topic') for x in data if isinstance(x, dict)).items()))}")
    print(f"Difficulty: {dict(sorted(Counter(x.get('difficulty') for x in data if isinstance(x, dict)).items()))}")
    print(f"EPS importance: {dict(sorted(Counter(x.get('epsImportance') for x in data if isinstance(x, dict)).items()))}")
    print(f"Part of speech: {dict(sorted(Counter(x.get('partOfSpeech') for x in data if isinstance(x, dict)).items()))}")
    print(f"Source: {dict(sorted(Counter(x.get('source') for x in data if isinstance(x, dict)).items()))}")
    print(f"Level: {dict(sorted(Counter(str(x.get('level')) for x in data if isinstance(x, dict)).items()))}")
    print(f"Senses: total={sum(len(x.get('senses', [])) for x in data if isinstance(x, dict))} "
          f"multi-sense entries={sum(1 for x in data if isinstance(x, dict) and len(x.get('senses', [])) > 1)}")
    print(f"Relations: similar={sum(len(x.get('similar', [])) for x in data if isinstance(x, dict))} "
          f"opposite={sum(len(x.get('opposite', [])) for x in data if isinstance(x, dict))} "
          f"confusing={sum(len(x.get('confusing', [])) for x in data if isinstance(x, dict))}")
    missing_topics = TOPICS - {x.get("topic") for x in data if isinstance(x, dict)}
    if missing_topics:
        errors.append(f"empty topics: {sorted(missing_topics)}")
    if len(data) != 10_000:
        errors.append(f"expected exactly 10000 entries, got {len(data)}")
    if errors:
        print(f"\nFAIL: {len(errors)} error(s) (showing first 40):")
        for err in errors[:40]:
            print(f"  - {err}")
        return 1
    print("\nPASS: all checks passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
