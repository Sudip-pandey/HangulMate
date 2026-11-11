#!/usr/bin/env python3
"""Validate HangulMate vocabulary_seed.json. Stdlib only.
Usage: python3 validate_seed.py [path-to-vocabulary_seed.json]
Exit non-zero on any failure; prints a summary of counts.
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
REQUIRED = ["korean", "nepali", "english", "romanization", "partOfSpeech",
            "topic", "difficulty", "epsImportance", "exampleKo", "exampleNe", "exampleEn"]
POS = {"noun", "verb", "adjective", "adverb", "expression", "number",
       "pronoun", "particle", "interjection"}
TOPICS = {"daily", "workplace", "agriculture", "manufacturing", "construction",
          "food", "transport", "health", "conversation", "numbers",
          "directions", "tools", "safety", "general"}


def main() -> int:
    path = Path(sys.argv[1]) if len(sys.argv) > 1 else (
        Path(__file__).resolve().parent.parent
        / "app/src/main/assets/seed/vocabulary_seed.json")
    errors = []
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:  # noqa: BLE001
        print(f"FAIL: could not load {path}: {exc}")
        return 1
    if not isinstance(data, list) or not data:
        print("FAIL: dataset must be a non-empty JSON array")
        return 1

    ids, koreans = set(), {}
    for i, e in enumerate(data):
        where = f"entry[{i}] id={e.get('id', '?')} korean={e.get('korean', '?')}"
        if not isinstance(e, dict):
            errors.append(f"{where}: not an object")
            continue
        # id
        eid = e.get("id")
        if not isinstance(eid, str) or not re.fullmatch(r"HM-\d{4}", eid):
            errors.append(f"{where}: bad id {eid!r}")
        elif eid in ids:
            errors.append(f"{where}: duplicate id {eid}")
        else:
            ids.add(eid)
        # required fields non-empty
        for f in REQUIRED:
            v = e.get(f)
            if v is None or (isinstance(v, str) and not v.strip()):
                errors.append(f"{where}: required field {f!r} missing/empty")
        # korean Hangul + uniqueness
        k = e.get("korean")
        if isinstance(k, str):
            if not HANGUL_RE.match(k):
                errors.append(f"{where}: korean not Hangul-only: {k!r}")
            if k in koreans:
                errors.append(f"{where}: duplicate korean {k!r} (also {koreans[k]})")
            else:
                koreans[k] = eid
        # nepali Devanagari
        if isinstance(e.get("nepali"), str) and not DEVANAGARI_RE.search(e["nepali"]):
            errors.append(f"{where}: nepali lacks Devanagari: {e['nepali']!r}")
        # romanization ASCII
        if isinstance(e.get("romanization"), str) and not ASCII_ROMAN_RE.match(e["romanization"]):
            errors.append(f"{where}: romanization not ASCII RR-like: {e['romanization']!r}")
        # enums
        if e.get("partOfSpeech") not in POS:
            errors.append(f"{where}: bad partOfSpeech {e.get('partOfSpeech')!r}")
        if e.get("topic") not in TOPICS:
            errors.append(f"{where}: bad topic {e.get('topic')!r}")
        if e.get("difficulty") not in (1, 2, 3):
            errors.append(f"{where}: bad difficulty {e.get('difficulty')!r}")
        if e.get("epsImportance") not in ("high", "medium", "low"):
            errors.append(f"{where}: bad epsImportance {e.get('epsImportance')!r}")
        # syllables join to korean (spaces stripped)
        syl = e.get("syllables")
        if not isinstance(syl, list) or not syl:
            errors.append(f"{where}: syllables must be a non-empty array")
        elif isinstance(k, str):
            if any(not isinstance(s, str) or not SYLLABLE_RE.match(s) for s in syl):
                errors.append(f"{where}: syllables must each be one Hangul block: {syl!r}")
            elif "".join(syl) != k.replace(" ", ""):
                errors.append(f"{where}: syllables {''.join(syl)!r} != korean {k!r}")
        # relations must be lists of objects with non-empty strings
        for rel, note_key in (("similar", "noteNe"), ("opposite", "noteNe"), ("confusing", "differenceNe")):
            items = e.get(rel)
            if not isinstance(items, list):
                errors.append(f"{where}: {rel} must be an array")
                continue
            for r in items:
                if not isinstance(r, dict) or not isinstance(r.get("korean"), str) or not r["korean"].strip():
                    errors.append(f"{where}: {rel} item missing korean: {r!r}")
                elif not isinstance(r.get(note_key), str) or not r[note_key].strip():
                    errors.append(f"{where}: {rel} {r.get('korean')} missing {note_key}")
                elif not DEVANAGARI_RE.search(r[note_key]):
                    errors.append(f"{where}: {rel} {r.get('korean')} note lacks Devanagari")
        # tags
        tags = e.get("tags")
        if not isinstance(tags, list) or any(not isinstance(t, str) or not t.strip() for t in (tags or [])):
            errors.append(f"{where}: tags must be an array of non-empty strings")

    # cross-entry relation checks (only if koreans collected)
    for i, e in enumerate(data):
        if not isinstance(e, dict):
            continue
        where = f"entry[{i}] id={e.get('id', '?')} korean={e.get('korean', '?')}"
        sim = {r["korean"] for r in e.get("similar", []) if isinstance(r, dict) and isinstance(r.get("korean"), str)}
        opp = {r["korean"] for r in e.get("opposite", []) if isinstance(r, dict) and isinstance(r.get("korean"), str)}
        for rel, targets in (("similar", sim), ("opposite", opp),
                             ("confusing", {r["korean"] for r in e.get("confusing", []) if isinstance(r, dict) and isinstance(r.get("korean"), str)})):
            for t in targets:
                if t not in koreans:
                    errors.append(f"{where}: dangling {rel} target {t!r} (not in dataset)")
        both = sim & opp
        if both:
            errors.append(f"{where}: word(s) both similar and opposite: {sorted(both)}")
        if e.get("korean") in sim or e.get("korean") in opp:
            errors.append(f"{where}: self-relation")

    # summary
    print(f"Entries: {len(data)}")
    print(f"Topics: {dict(sorted(Counter(x.get('topic') for x in data if isinstance(x, dict)).items()))}")
    print(f"Difficulty: {dict(sorted(Counter(x.get('difficulty') for x in data if isinstance(x, dict)).items()))}")
    print(f"EPS importance: {dict(sorted(Counter(x.get('epsImportance') for x in data if isinstance(x, dict)).items()))}")
    print(f"Part of speech: {dict(sorted(Counter(x.get('partOfSpeech') for x in data if isinstance(x, dict)).items()))}")
    print(f"Relations: similar={sum(len(x.get('similar', [])) for x in data if isinstance(x, dict))} "
          f"opposite={sum(len(x.get('opposite', [])) for x in data if isinstance(x, dict))} "
          f"confusing={sum(len(x.get('confusing', [])) for x in data if isinstance(x, dict))}")
    if errors:
        print(f"\nFAIL: {len(errors)} error(s):")
        for err in errors:
            print(f"  - {err}")
        return 1
    print("\nPASS: all checks passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
