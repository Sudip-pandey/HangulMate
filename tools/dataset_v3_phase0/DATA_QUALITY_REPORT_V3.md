# HangulMate dataset — DATA QUALITY REPORT v3 (seed v3, NIKL + Kaikki)

Date: 2026-11-11. Pipeline: `tools/dataset_v3_phase0/fetch_nikl.py` (NIKL Basic Dictionary Open API) + `merge_v3.py`; validation by `tools/dataset_v3_phase0/validate_seed_v3.py` (PASS).

## 1. What changed vs v2

- v2 relations were sparse by design (hand-built v1 only): similar 12 rows, opposite 26 rows, confusing 42 rows.
- v3 relations: **similar 1109 rows on 966 words (9.66%), opposite 532 rows on 511 words (5.11%)**, confusing unchanged (42 rows, v1).
- New rows by source: NIKL 1498, Kaikki 105, v1 80 (untouched).
- Three gloss fixes: 공원 "a person who works at a factory" → "park" and 악 "what energy that one musters up" → "evil" (wrong homograph sense had been picked first; other senses kept); 오르막길 "an inclined path from high to low terrain" → "from low to high terrain" (the v2 definition was backwards vs NIKL's own definition — an audit find, verified against the stored NIKL view).
- Nepali coverage is UNCHANGED in v3 (851 verified, 9,149 missing): Phase 2 is relations only. Nepali machine translation is Phase 3.

## 2. Sources and licences (separately-licensed data file)

- NIKL Basic Korean Dictionary (한국어기초사전), National Institute of Korean Language — **CC BY-SA 2.0 KR**. v2 entries came from binjang's Hugging Face parse (`2024_01.csv`); v3 relations were fetched live from the NIKL Open API (`/api/view`, per-sense `rel_info`) in November 2026. Raw XML is stored with the build tooling (`fetch_raw/`).
- Korean Wiktionary extract (Kaikki.org, `ko-extract.jsonl.gz`, 30 Oct 2026) — **CC BY-SA 4.0**. Only the 188 sense-verified rows were merged; the 2,864-row review pile stays out.
- The 236 v1 hand-built entries/relations are HangulMate's own data.
- Never present this data as MIT: the Hugging Face `license: mit` tag covers binjang's parser code only.

## 3. Fetch (NIKL Open API)

- Search + view calls for 10,000 headwords (translated search for gloss matching). Index entries: 10000; view done: 9873; unresolved (no matching dictionary entry, mostly the 11 expression words): 127; errors: 0.
- Resolution: exact headword + POS + English-gloss similarity (Jaccard on content words) against the search's English `trans_dfn`; single-candidate fallback; ambiguous low-score matches flagged in the index (`match_method`).
- rel_info types seen: 유의어 → similar, 반대말 → opposite. Other types are NOT merged: 낮춤말: 5, 높임말: 48, 본말: 146, 센말: 67, 여린말: 78, 작은말: 232, 준말: 195, 참고어: 1636, 큰말: 156.
- Drops (counted, not hidden): NIKL rows whose `link_target_code` does not resolve to a seed entry: 2050 (code points at an entry outside the 10,000-word set) + 210 (no code given) — these are GATE A drops (see §4); same-entry dedup 358; NIKL rows duplicating v1 rows 22. Total NIKL API calls for the fetch: 19,836 of the 50,000/day limit. The fetch ran in three resume-safe segments (the runner was killed twice by the environment, SIGTERM at ~19 min and ~114 min in; the index + raw XML on disk made every restart pick up exactly where it stopped — no word was fetched twice).

## 4. Merge rules

- v1 relations untouched (byte-identical after the merge).
- Dedup per (entry, kind, target). A NIKL row duplicating a v1 or earlier row is skipped; a Kaikki row duplicating a NIKL/v1 row is skipped.
- GATE A (target resolution): a NIKL row is merged ONLY when its `link_target_code` resolves through the fetch index to a seed entry whose Korean matches the rel word. A bare Korean-string match is NOT accepted. Reason, measured on the first 100-row audit (pre-gate merge): of 16 string-resolved rows, 11 were wrong (the rel points at a different homograph entry than the seed carries, e.g. 수요일~수 'male gender', 국수~면 'township', 강국↔약국 'pharmacy', 죽음~사 'four'), 1 unclear, 4 correct; code-resolved rows were ~90% correct, 1% wrong. A stricter English-overlap gate was also tested and REJECTED: it would have killed 21 of 80 correct rows (synonyms often share no content word, e.g. 생전↔사후, 살다↔죽다, 화폐~돈). Dropped string rows are recoverable: every rel_info row stays in `fetch_raw/fetch_index.json`.
- New rows: `{"korean": target, "noteNe": "", "source": "nikl" | "kaikki-ko"}` — neither source gives a Nepali note, so the note is empty rather than invented.
- The Kaikki review pile (`sense_unverified`, 2,864 rows) is never merged.

## 5. Random 100-relation audit

- Sample: 100 rows drawn at random (seed 20261111) from all newly added rows — see `audit_sample_v3.json`. Each row carries the source sense's Korean definition and the target's seed English gloss so sense fit can be judged.
- Human verdict: correct 90, wrong 4, unclear 6.
- Notes: unclear: 고기 similar -> 물고기; wrong: 발끝 similar -> 발꿈치; wrong: 행 similar -> 줄; wrong: 역할 similar -> 역; note: 내리막길 — relation correct; seed gloss for 오르막길 is backwards (v2 bug, fixed as gloss fix 3); unclear: 층 similar -> 계층; unclear: 명분 similar -> 이름; unclear: 글자 similar -> 글; unclear: 즉 similar -> 곧; wrong: 의도 similar -> 의사; unclear: 명목 similar -> 명분

## 6. Validation

- `validate_seed_v3.py`: PASS (uniqueness, schema, Hangul/Devanagari checks, no duplicate Korean, every relation target resolves into the dataset, no self-relations, no word both similar and opposite).
- v1 relation rows verified byte-identical to v2 after the merge.

## 7. Known limits (honest)

- Byte's "every word" is not achieved and cannot be without inventing data: most words have no opposite, and many have no editorial synonym. Empty relation sections stay hidden in the app.
- Relation sense-fit rests on NIKL's per-sense `rel_info` and the Kaikki strict sense rule; the 100-row audit above is the measured error check. Residual wrongs (4/100) are all cases where the relation is true for a secondary sense of the target entry while the app displays only the entry's primary gloss (e.g. 역할~역 'Station', 행~줄 'Rope/string', 의도~의사 'Doctor'), plus one NIKL quirk (발끝~발꿈치, a figurative-sense rel_info). The word-detail screen currently shows only `word.english` — it never displays the `senses` array already stored in the database. Showing all senses would make these rows legible and would also let a later merge restore the ~237 Gate-A-dropped rows (of which ~25% were correct). That is a small UI change; it is NOT part of this data delivery.
- Nepali translations are still 851/10,000 verified — Phase 3 (MT, all `mt_review`) is a separate step.
- The 악 example sentence still illustrates the "energy" sense (now the second sense); it was left as-is rather than inventing a new Korean example.
