# NOTICE — Vocabulary seed data (seed v3)

## Dictionary sources and licences

This dataset file is a **separately-licensed data file**. Do not merge it
into GPL-licensed code files; the licences below apply to the data itself.

### 1. NIKL Basic Korean Dictionary (한국어기초사전) — CC BY-SA 2.0 KR

The 9,764 NIKL-derived entries in `vocabulary_seed.json` come from the
**Basic Korean Dictionary (한국어기초사전)** published by the **National
Institute of Korean Language (NIKL, 국립국어원)**, as parsed and published
on Hugging Face by `binjang`:

- Dataset: https://huggingface.co/datasets/binjang/NIKL-korean-english-dictionary
- File used: `2024_01.csv` (33,888,840 bytes, 53,172 rows, accessed 2026-11-11)

The dictionary content is NIKL's and is shared under
**Creative Commons Attribution-ShareAlike 2.0 Korea (CC BY-SA 2.0 KR)**:
attribution to NIKL is required, and any adapted vocabulary data derived
from it is shared under the same licence.

**v3 addition — relations fetched live from NIKL:** the similar/opposite
word relations in this file were fetched in November 2026 from NIKL's
**Korean Basic Dictionary Open API** (`https://krdict.korean.go.kr/api`),
per-sense `rel_info` blocks (`유의어` → similar, `반대말` → opposite).
Raw API responses are stored with the build tooling; only relations whose
target word is one of this file's 10,000 headwords were kept (others were
counted and dropped, never invented). Same CC BY-SA 2.0 KR licence.

### 2. Wiktionary (Kaikki / wiktextract) — CC BY-SA 4.0

A second set of similar/opposite relations comes from the Korean
Wiktionary extract published by Kaikki.org
(`ko-extract.jsonl.gz`, 30 Oct 2026), parsed by
[wiktextract](https://github.com/tatuylonen/wiktextract) from Wiktionary
dumps. Wiktionary content is shared under
**Creative Commons Attribution-ShareAlike 4.0 (CC BY-SA 4.0)**.
Only rows that passed a strict sense check (single-sense headword, POS
match, hand review against both English glosses) were merged; the
unverified remainder was kept as a review pile and NOT bundled.

## WARNING: do not present this data as MIT

The Hugging Face dataset page carries a `license: mit` tag. That tag is
**misleading**: it covers binjang's parser code only, not the dictionary
content. This data must never be bundled, described or relicensed as
MIT or as proprietary data.

## What HangulMate derived (v3)

- 53,172 source rows merged by headword into 9,764 selected entries
  (plus the 236-entry hand-built v1 seed, which is HangulMate's own data).
- Each entry keeps its English gloss(es) verbatim from NIKL; Nepali
  translations are HangulMate's own work: 851 hand-translated by a human
  (status `verified`), the rest left empty (status `missing`) — never
  machine-generated or invented. (Nepali machine translation is a
  separate later step, Phase 3; v3 changes relations only.)
- Three gloss fixes in v3: 공원 "a person who works at a factory" →
  "park" and 악 "what energy that one musters up" → "evil" (wrong
  homograph sense had been picked first; the other senses are kept as
  secondary senses); 오르막길 "an inclined path from high to low terrain"
  → "from low to high terrain" (the v2 definition was backwards vs
  NIKL's own definition — found by the v3 audit).
- The 236 v1 hand-verified relations are unchanged. New relation rows
  carry `{"korean": target, "noteNe": "", "source": "nikl" | "kaikki-ko"}`:
  neither dictionary source gives a Nepali note, so the note is empty
  rather than invented.
- No relation was invented: a word with no source-backed similar or
  opposite word keeps empty arrays, and the app hides those sections.
- Derived vocabulary data in this app is shared under CC BY-SA 2.0 KR
  (NIKL content) and CC BY-SA 4.0 (Wiktionary content), per the
  ShareAlike terms above.

## Attribution in the app

The same credit appears in the app under Settings → About
(`about_dictionary_credit` string resource).
