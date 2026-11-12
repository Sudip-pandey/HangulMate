# HangulMate dataset Phase 2 — NIKL API probe report

Date: 2026-11-11 (23:15 NPT). Connector: `custom.krdict`, verified working.
Probe files: `tools/dataset_v3_phase0/probe/` (raw XML for every call below).

## 1. The view endpoint contract (fixed, twice)

The connector helper originally sent `target_code=<code>` only → NIKL error 100
("Incorrect query request"). Adding `method=WORD_INFO` also errored 100.
The real contract, verified against the working third-party client
`hyungtaecf/korean_dictionary` (`ViewMethod.targetCode -> "target_code"`):

```
GET https://krdict.korean.go.kr/api/view?key=<key>&q=<target_code>&method=target_code
```

`krdict.py` and `CONNECTOR.md` are updated to this. The search endpoint was
already correct (`q`, `method=exact`, `part=word`, `num` 10..100).

## 2. rel_info: VERBATIM probe results

`rel_info` exists in the view response, attached per sense (`sense_info/rel_info`).
Structure (verbatim from 크다 adjective, target_code 66586):

```xml
<rel_info>
  <word>작다</word>
  <type>반대말</type>
  <link_type>C</link_type>
  <link_target_code>69513</link_target_code>
  <link>https://krdict.korean.go.kr/kor/dicSearch/SearchView?ParaWordNo=69513</link>
</rel_info>
```

| word | target_code | rel_info blocks | type (verbatim) | target word (link_target_code) |
|---|---|---|---|---|
| 크다 (verb) | 66584 | 0 | — | — |
| 크다 (adj) | 66586 | 7 | 반대말 ×7 | 작다 (69513) |
| 좋다 | 79033 | 1 | 반대말 ×1 | 나쁘다 (26816) |
| 가다 | 27500 | 5 | 반대말 ×5 | 오다 (69514) |
| 기쁘다 | 40078 | 1 | 반대말 ×1 | 슬프다 (65103) |
| 행복하다 | 72481 | 0 | — | — |
| 아름답다 | 71290 | 0 | — | — |
| 빠르다 | 68755 | 3 | 반대말 ×3 | 느리다 (23143) |

**Total: 17 rel_info blocks across 8 probe entries — ALL `type` = `반대말`
(antonym), ZERO `유의어` (synonym).** 3 of 8 entries carry no rel_info at all.

Sense behaviour: the same relation repeats once per sense that carries it
(크다 adj: 작다 on senses 1, 2, 3, 4, 5, 6, 13) — the merge MUST dedup
per headword + kind (7 blocks → 1 opposite). Sense text is Korean-only in
the view response; the seed's English gloss is the sense-match key, so the
fetch resolves the entry via search (`translated=y&trans_lang=1` gives
English `trans_dfn` per sense) and matches on gloss similarity + POS.

### Correction (found by the fetch smoke test, 17:17 UTC) — 유의어 DOES exist

The 8-word probe was misleading: its words just happened to carry only
antonyms. The first 10 seed words' fetch found **`유의어` (synonym)**:
이름 5× 유의어, 나이 1× 유의어 (+ 높임말), 한국 1× 유의어 — same
`<rel_info>` structure (word + link_target_code). Other type strings also
observed and EXCLUDED from the merge: `참고어` (reference word), `준말`
(abbreviated form), `높임말` (honorific). Only `유의어` → similar and
`반대말` → opposite are merged. The probe's "antonym-only" line is withdrawn;
the full fetch measures the true type distribution.

## 3. Probe verdict: PROCEED with the full fetch

- `rel_info` DOES carry opposites (type string `반대말`, verbatim confirmed).
- No `유의어` observed in 8 entries — the Basic Dictionary's rel_info looks
  antonym-only in practice. This is NOT proven at scale from 8 words; the
  full fetch measures the exact 유의어 rate (the merge code handles both
  types, and any 유의어 found is used). If the full fetch confirms ~0,
  similar words come from Kaikki only (188 verified rows).
- The fetch is still the best route for opposites: per-sense editorial
  antonyms with target codes; Kaikki yielded only 51 opposite words.
- View response also carries useful extras (der_info 파생어, conjugations,
  categories) — not used for seed v3.

## 4. Fetch plan (running)

- Per headword: 1 search (`method=exact`, `translated=y`, `trans_lang=1`,
  `num=10`) → resolve target_code by exact-word + POS + English-gloss
  similarity → 1 view. ~20,000 calls, inside the 50,000/day limit.
- Raw XML stored under `tools/dataset_v3_phase0/fetch_raw/` (`search/<id>.xml`,
  `view/<id>.xml`) + `fetch_index.json` (resolved target_code, match method,
  rel_info counts, status). Resume-safe: completed ids are skipped.
- Pacing: 2 worker threads, 0.5 s delay each (~4 calls/s peak).
- The seed carries NO target_code/sup_no (the binjang CSV has none either),
  so the search call per headword is unavoidable.
- POS map (seed → NIKL): noun→명사, verb→동사, adjective→형용사,
  adverb→부사, pronoun→대명사, number→수사, interjection→감탄사,
  expression→(no NIKL POS; word-only match, often unresolved).
