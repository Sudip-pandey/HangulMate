# NOTICE — Vocabulary seed data (seed v2)

## Dictionary source and licence

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

## WARNING: do not present this data as MIT

The Hugging Face dataset page carries a `license: mit` tag. That tag is
**misleading**: it covers binjang's parser code only, not the dictionary
content. This data must never be bundled, described or relicensed as
MIT or as proprietary data.

## What HangulMate derived

- 53,172 source rows merged by headword into 9,764 selected entries
  (plus the 236-entry hand-built v1 seed, which is HangulMate's own data).
- Each entry keeps its English gloss(es) verbatim from NIKL; Nepali
  translations are HangulMate's own work: 615 hand-translated by a human
  (status `verified`), the rest left empty (status `missing`) — never
  machine-generated or invented.
- Derived vocabulary data in this app is shared under CC BY-SA 2.0 KR,
  per the ShareAlike term above.

## Attribution in the app

The same credit appears in the app under Settings → About
(`about_dictionary_credit` string resource).
