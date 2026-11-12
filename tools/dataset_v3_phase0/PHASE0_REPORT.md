# HangulMate dataset — Phase 0 report (seed v3, no-key sources)

Date: 2026-11-11. Coordinator: Phase 0 data coordinator (2 workers + parent costing).
Byte's model, confirmed 22:52 NPT: fetch once from the APIs/dumps, build OUR OWN dataset (seed v3), bundle it in the app, display from local data offline — no API calls at app runtime.
Scope: Phase 0 only. The v2 seed was NOT edited; nothing was pushed; no API key was created.

## 1. Kaikki relations backfill (Wiktionary dumps) — usable but thin

Source: Kaikki.org Korean Wiktionary extract, `ko-extract.jsonl.gz`, 26,869,664 B, last modified 30 Oct 2026, SHA-256 `7ce75eec9ac9d198a510eb9db57e1831810bba941099d3580ae59a7b026aaec1`, 356,341 lines, 109,000 `lang_code == "ko"` entries. English Wiktionary skipped with evidence: its rawdata page 404s, no per-language Korean extract exists, and the whole-edition dump is 534 MB gz / 3.4 GB uncompressed.

| Metric | Count | % of 10,000 |
|---|---|---|
| Found in the Korean Wiktionary dump | 8,493 | 84.9% |
| With any synonym/antonym in the dump | 2,489 | 24.9% |
| **Verified similar, merge-ready** | **126 rows / 108 words** | **1.08%** |
| **Verified opposite, merge-ready** | **62 rows / 51 words** | **0.51%** |
| Sense-unverified (review list, NOT merge rows) | 2,864 rows / 1,535 words | 15.4% |
| Words gaining anything in the file | 1,658 | 16.6% |

Dropped, counted not hidden: 2,843 targets not in our 10,000-word set (2,643 similar + 200 opposite), 22 duplicates of existing v1 relations (20 opposite — independent confirmation of the v1 set), 1 self-relation, 40 cross-entry duplicates.

### Post-backfill totals (independently re-verified by the coordinator)
- Similar: 12 → **120 words (1.20%)**. Opposite: 26 → **77 words (0.77%)**. Zero overlap between the new verified words and the existing v1 words — purely additive.
- Validation on the output file: 0 out-of-set targets, 0 self-relations, 0 duplicate rows, 0 verified rows duplicating a v1 relation. Seed untouched (10,000 words; similar 12, opposite 26, confusing 40; Nepali verified 851).

### Why so thin — the strict rule + one honest demotion round
` sense_verified=true` requires: sense-agnostic relation OR single-sense headword + POS match + dump sense matching the seed's English gloss. The worker then hand-reviewed all 271 strict-rule rows against both English glosses and **demoted 83** (69 similar, 14 opposite) — e.g. 구매→사기 (our 사기 = "morale", not "buying"), 가방→백 (our 백 = "one hundred", not "bag"), 긍정→부정 (our 부정 = "irregularity", not "negation"). The 188 surviving rows were each read by a human against both glosses. Spot-check: 30/30 sampled relations confirmed present in the dump (caveat: presence proves dump membership, not sense fitness — the manual review afterwards demoted 3 of the 30, e.g. 구매→사기, 긍정→부정, 일본→왜).

### Two seed gloss bugs found (flagged, NOT fixed — they are seed edits, out of Phase 0 scope)
- **악** glossed as "energy one musters up" — should be "evil". Blocks the relation 죄→악.
- **공원** glossed as "a person who works at a factory" — should be "park". Blocks the relation 뜰→공원.
Fix these before the v3 merge; they mislead learners on their own.

### Review pile (the 2,864 unverified rows)
2,074 carry at least one review signal (mutual-in-dump and/or shared English gloss word); 649 carry both — the suggested human-review order. The block is the Korean-gloss ↔ English-gloss gap, which the NIKL API (Phase 2) closes by design: NIKL `rel_info` is per-sense via `target_code`/`sup_no`. Opposite yield is structurally low: the dump lists antonyms for only 710 headwords — Kaikki cannot supply more antonyms; NIKL is the only route.

## 2. PanLex join test — NO-GO, drop it from seed v3

Source: `gtak1/panlex-meanings`, rev `95d5b0e`, CC0. Korean side `kor` (393,455 Hangul rows); Nepali side `npi` (51,009) + `npl` (2,567). Joined on `meaning` id, exact headword match.

- **Coverage: 470 / 9,149 = 5.14%.** Bottleneck is PanLex's Nepali side: only 5,835 of 355,911 Korean meanings have any Nepali counterpart. `npl` contributed literally nothing (1 shared meaning). Sensitivity: space-stripping adds 0 words; verb-stem stripping (drop trailing 다) adds 47 (5.65% max). The ceiling is real.
- **50-item hand audit: accurate 27 / wrong 22 / unclear 1 (54% / 44% / 2%).** Even where it matches, it's a coin flip. Projected usable yield after hand-reviewing all 470: ~254 words ≈ 2.8% of the target set. Wrong-match examples: 사자 "a dead person" → सिंह (lion, homonym); 독 "earthenware jar" → बिख (poison, homonym); 동사 "death by freezing" → क्रिया (grammatical "verb", homonym); 비우다 "to empty" → खानु (to eat); 수박 "watermelon" → खरबुजा (muskmelon); 쇠 "iron" → `phalam` (Latin script, not Devanagari); 꽃다발 "bouquet" → बुके (transliteration); 젖다 "to become wet" → भिजेको (adjective instead of verb भिज्नु). Data order does not put the good candidate first either (불어's first candidate is the misspelling फ्रांसिसी).
- **Verdict: drop PanLex.** Its ~250 salvageable words are not worth a 470-row hand review, and `panlex_join.json` is kept only as a reference artefact, never to be merged as-is. The MT path is the way to fill the 9,149.

## 3. MT costing — full payload pinned down

Measured from the v2 seed, target set = 9,149 words missing verified Nepali.

| Payload | Characters |
|---|---|
| English gloss only (`english`) | 562,419 |
| Korean example sentence only (`exampleKo`) | 282,245 |
| English gloss + Korean example | **844,664** |
| Gloss + example + Korean headword | 868,727 |

Average gloss 61.5 chars (max 393); average example 30.8 chars (max 74). 0 lack an English gloss; 32 lack a Korean example.

**Free-tier check — the research estimate (~230k chars) was too low:**
- Azure Translator F0 (2,000,000 chars/month free, permanent): covers the full 844,664-char payload with ~1.15M to spare for re-runs. ✓
- Google Cloud Translation (500,000 chars/month free): does NOT cover the full payload in one month (844,664 > 500,000); even gloss-only (562,419) exceeds it. ✗

**Consequence for the Phase 3 two-engine plan:** a full "translate everything with both engines, keep where they agree" pass cannot run on free tiers in one month. Options: (a) Azure full batch + Google cross-check on a priority subset (EPS: High first — 500k covers ~59% of the payload); (b) split Google across two months; (c) NLLB-200-distilled-600M locally as a free third vote (note its CC-BY-NC / research-only licence). Every MT-filled entry keeps `nepaliStatus: "mt_review"` with a `nepaliSource` provenance field; the 851 verified entries are never overwritten; EPS: High words reviewed first.

## 4. What Phase 0 honestly delivers

- Relations: Kaikki verified rows add +108 similar words and +51 opposite words (similar 12→120, opposite 26→77). The 1,535-word review pile (2,864 rows) is staged for the NIKL sense-resolved fetch, not merged.
- Nepali: PanLex contributes nothing mergeable. The 9,149 stay English-fallback until Phase 3 MT (Azure F0, $0).
- Byte's "every word" remains unachievable without inventing data: even combining Kaikki + NIKL, similar lands at ~30–50% and opposite at ~10–25% of the 10,000. The app already hides empty relation sections.

## 5. Files written (all under `~/workspace/hangulmate/tools/dataset_v3_phase0/`)

| File | Size | What it is |
|---|---|---|
| `kaikki_relations.json` | 1,472,338 B | 1,658 headwords; verified + sense-unverified rows in seed schema + provenance |
| `KAIKKI_NOTES.md` | 15,835 B | Coverage, drops, rule, review order, spot-check, blockers |
| `spotcheck_kaikki.json` | 8,880 B | 30-item spot-check data |
| `extract_kaikki.py`, `postprocess_kaikki.py`, `spotcheck_kaikki.py`, `demote_reviewed.py` | — | Reproducible scripts |
| `verified_review.txt` | 49,230 B | Manual demotion review record |
| `panlex_join.json` | 90,173 B | 470 matched words (reference only — do NOT merge as-is) |
| `PANLEX_AUDIT.md` | 15,559 B | Coverage + full 50-row audit table + no-go verdict |
| `audit_sample.json` | 22,403 B | The 50 audited rows with full seed context |
| `panlex_join.py` | 6,253 B | Reproducible join script |
| `MT_COSTING.md` | 2,330 B | Character counts + free-tier verdict |
| `PHASE0_REPORT.md` (this file) | — | Synthesis |
| `downloads/ko-extract.jsonl.gz` | 26.9 MB | Kaikki Korean dump (kept) |
| `downloads/kor.tsv`, `npi.tsv`, `npl.tsv` | 28.5 / 3.5 / 0.2 MB | PanLex raw tables (kept) |
| (+ scraped kaikki.org listing pages, `kaikki-*.html`) | — | Source evidence for the enwiktionary skip |

## 6. What Phase 2 still needs

1. **Byte creates the free krdict API key** — https://krdict.korean.go.kr/eng/openApi/openApiRegister (only he can; login required). Nothing else needs his account.
2. **Probe call** on 크다 / 좋다 / 가다 via `/api/view` to confirm the `rel_info` type strings (유의어 / 반의어 vs other labels) and which senses carry relations.
3. **Full NIKL fetch** (~20,000 calls: 1 search + 1 view per headword; fits the 50,000/day limit in a single day, polite pacing). Store raw XML.
4. **Merge into seed v3**: keep the 236 v1 relations untouched; add the 188 verified Kaikki rows; add NIKL relations; keep the 2,864-row Kaikki review pile out unless sense-resolved by NIKL. Fix the 악 and 공원 seed glosses first. Dedup, drop self-relations and out-of-set targets (counted in the validation report). Re-run `validate_seed_v2.py`-style checks; produce a DATA_QUALITY_REPORT-style note. Licence: NIKL data CC BY-SA 2.0 KR + Wiktionary CC BY-SA 4.0 — the v3 dataset file stays a separately-licensed data file with both attributions.
5. **Phase 3 MT** for the 9,149 Nepali translations (Azure F0 full batch; Google cross-check on a priority subset or across two months); two-engine agreement filter + red-flag heuristics; EPS: High review first. PanLex is out.
