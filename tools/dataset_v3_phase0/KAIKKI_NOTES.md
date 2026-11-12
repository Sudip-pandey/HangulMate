# Kaikki relations extraction — Phase 0 notes (seed v3 preparation)

Date: 2026-11-11 (NPT). Worker: Kaikki relations worker. The v2 seed was NOT modified, nothing was pushed, no app code touched.

## Source dump actually used

- **Korean Wiktionary extract (wiktextract)**: https://kaikki.org/dictionary/downloads/ko/ko-extract.jsonl.gz
- Last-Modified: **Fri, 30 Oct 2026 20:51:01 GMT**; size **26,869,664 B** gz; SHA-256 `7ce75eec9ac9d198a510eb9db57e1831810bba941099d3580ae59a7b026aaec1`
- **356,341 lines** total; **109,000 `lang_code == "ko"` entries** (the Korean edition also contains other languages — filtered out).
- Raw download kept at `downloads/ko-extract.jsonl.gz` (26.9 MB; no disk pressure, 142 GB free).
- The `kor.tsv` / `npi.tsv` / `npl.tsv` files in `downloads/` were already there (another Phase 0 worker's files) — not mine, not touched.

## English Wiktionary — SKIPPED (with evidence)

- https://kaikki.org/enwiktionary/rawdata.html returns **404 Not Found** (nginx).
- The dictionary rawdata page (https://kaikki.org/dictionary/rawdata.html) lists per-language extracts for 19 editions (cs, de, el, es, fr, id, it, ja, **ko**, ku, ms, nl, pl, pt, ru, simple, th, tr, vi, zh) — **the full English edition is not among them**; only Simple English (`simple-extract.jsonl`, 36.2 MB) is listed.
- The English edition's only dump is the whole-edition file https://kaikki.org/dictionary/English/kaikki.org-dictionary-English.jsonl.gz — **534,186,395 B gz / 3,431,211,190 B (3.4 GB) uncompressed**, Last-Modified 30 Oct 2026. That is multi-GB, and kaikki.org publishes **no per-language Korean extract of the English edition**, so per the task rules it was NOT downloaded. English-Wiktionary synonyms/antonyms for our headwords are therefore **not covered** by this output — that is the single biggest coverage gap (see "Blockers").

## Coverage (of 10,000 headwords)

| Metric | Count | % of 10,000 |
|---|---|---|
| Headwords found anywhere in the Korean dump (`lang_code=="ko"`) | 8,493 | 84.9% |
| Headwords with any synonym/antonym listed in the dump | 2,489 | 24.9% |
| Words in `kaikki_relations.json` (gained ≥1 kept relation) | 1,658 | 16.6% |
| Words with ≥1 **verified** similar | 108 | 1.08% |
| Words with ≥1 **verified** opposite | 51 | 0.51% |
| Words with unverified relations only | 1,516 | 15.2% |

## Relation counts

| Bucket | Similar | Opposite | Total |
|---|---|---|---|
| Raw dump rows for our headwords | 5,054 | 904 | 5,958 |
| **Verified (merge-ready)** | **126** | **62** | **188** |
| Sense-unverified (review list, NOT merge rows) | 2,253 | 611 | 2,864 |
| Dropped: target NOT in our 10,000-word set | 2,643 | 200 | 2,843 |
| Dropped: self-relation (target == headword) | 1 | 0 | 1 |
| Dropped: duplicate of an existing v1 relation | 2 | 20 | 22 |
| Dropped: duplicate across dump entries | 29 | 11 | 40 |

Notes on the drops:
- **2,843 out-of-set targets** are real Wiktionary relations whose target word is simply not one of our 10,000 headwords (e.g. hanja forms, rare words, words outside the EPS/NIKL set). They were counted, not merged, per the rules. If seed v3 ever grows past 10,000 words, re-running extraction would pick many of these up.
- The 20 dropped opposite duplicates are existing v1 antonyms that Kaikki also lists (good independent confirmation of the v1 hand-verified set); the 2 similar duplicates likewise.
- 1 self-relation dropped (a word listing itself as its own synonym).
- Junk dump artefacts (e.g. `{"word": "유의어: 아(A)"}` — a "synonym:" label leaked into a word field) were all eliminated by the in-set filter; none reached the output.

## Sense verification rule (and why the unverified pile is big)

`sense_verified=true` ONLY when ALL hold: (a) the dump relation carries **no sense text** (sense-agnostic), (b) the headword has **exactly one sense** (one definition) in the seed, (c) the dump entry's POS maps to the seed partOfSpeech. Dump `sense` text is a **Korean** gloss, and our seed glosses are **English (NIKL)** — they cannot be matched without translation, so every sense-tagged relation is unverified. Unverified reasons (2,781 pre-review rows): sense-tagged 2,105; multi-sense headword 576; POS mismatch/unmapped 100.

- All 2,105 sense-tagged rows have sense text that matches one of the headword's own dump-entry glosses (`dump_sense_matches_entry_gloss=true` for 2,105/2,105) — i.e. the sense attribution is internally consistent in Wiktionary; the block is purely the Korean-gloss ↔ English-gloss gap, not dump noise.
- The seed's own sense problem is visible in the canonical example: 가격 → 값 (sense "물건에 정해진 돈의 양" = the amount of money set for a thing) is the *correct* price sense and sits in the unverified pile only because of the language gap, while 가격하다 → 치다 (sense "때리거나 치다" = to hit/strike) is the *wrong* sense — both correctly kept out of the merge rows.

## Manual review: 83 "verified" rows demoted (important)

The strict rule has a hole it cannot see: **single-sense in the seed ≠ single-sense in Wiktionary**. A sense-agnostic dump relation can belong to a Wiktionary sense our seed entry does not carry, and the target's own seed gloss exposes it (e.g. 구매 → 사기 is real only for 사기="buying", but our 사기 means "morale/spirit"). So **all 271 strict-rule verified rows were reviewed by hand** against the seed's English glosses (`verified_review.txt`, `demote_reviewed.py`).

- **83 rows demoted** (69 similar, 14 opposite) to `sense_unverified` with a `manual_review` reason; they are NOT deleted, so a human can re-examine them.
- Main demotion causes: target-sense mismatch (the commonest — e.g. 가방→백 where our 백="one hundred", not "bag"; 긍정→부정 where our 부정="irregularity", not "negation"; 일본→왜 where our 왜="why", not the old name for Japan); outright wrong pairs (검→신 "shoes", 눈동자→모자/수확/안정); loose associations, not synonyms (가방→지갑 wallet, 버스→승합차 van, 스타킹→양말 socks); not-an-opposite (누이→형/오빠/언니, 장가→시집).
- After demotion, verified rows are **126 similar + 62 opposite = 188**, covering 108 + 51 words. Only **6 verified similar rows** belong to v1 words (0 opposite) — the v1 set was already hand-verified, so this is expected.
- Lesson for the merge step: **do not trust "verified" from any automated rule alone** — the dump's sense-agnostic listings are the risk. The 188 surviving rows have each been read by a human against both English glosses.

## Seed gloss bugs found during review (NOT fixed — flagging for the seed owner)

- **악** is glossed in the seed as "What energy that one musters up and uses to the fullest." — should be "evil". This blocks the otherwise-real relation 죄 → 악 (kept in unverified with this note). Fix the 악 gloss first, then the relation is usable.
- **공원** is glossed in the seed as "A person who works at a factory." — should be "park". This blocks 뜰 → 공원 (kept in unverified with this note).

## Review signals on every row (provenance, NOT verification)

Each row carries: `source` ("kaikki-ko"), `sense` (dump sense text or null), `sense_verified`, `dump_pos`, `dump_sense_matches_entry_gloss`, plus two **review-prioritisation signals** that must never alone promote a row to verified:

- `mutual_in_dump`: the target's own Korean-Wiktionary entry lists the headword back under the same kind. Verified rows: 38 similar + 35 opposite. Unverified rows: 586 similar + 349 opposite.
- `seed_gloss_overlap_words`: distinctive English content words shared between the headword's and the target's seed definitions (lowercased, lightly stemmed, stopwords removed). Verified rows with overlap: 94 similar + 42 opposite. Unverified rows with overlap: 1,393 similar + 395 opposite; 649 unverified rows have BOTH signals. **2,074 of 2,864 unverified rows have at least one signal** — that is the suggested review order for any human pass over the unverified pile. Caveat: overlap is a weak signal (e.g. 나이 → 살 shares "year" via the flesh/age homograph and is still wrong; 가게 → 상점 has no overlap and is still right), so it ranks review, it does not decide it.
- `noteNe` is `""` on every row (no Nepali note from these sources), per the seed schema.

## 30-item spot-check — 30/30 confirmed in the dump

Stratified random sample (seed 20261111): 10 verified similar, 5 verified opposite, 10 unverified similar, 5 unverified opposite. Each was re-checked against a **fresh streaming pass** over the dump: the dump must contain a `lang_code=="ko"` entry for the headword whose synonyms/antonyms list contains the target with the same sense text. Full record in `spotcheck_kaikki.json`. **All 30 confirmed.**

| Bucket | Headword | Kind | Target | Dump sense | Result |
|---|---|---|---|---|---|
| verified similar | 속도 | similar | 속력 | (no sense in dump) | CONFIRMED |
| verified similar | 도처 | similar | 이곳저곳 | (no sense in dump) | CONFIRMED |
| verified similar | 배우 | similar | 연기자 | (no sense in dump) | CONFIRMED |
| verified similar | 기여 | similar | 공헌 | (no sense in dump) | CONFIRMED |
| verified similar | 슈퍼마켓 | similar | 슈퍼 | (no sense in dump) | CONFIRMED |
| verified similar | 무덤 | similar | 묘지 | (no sense in dump) | CONFIRMED |
| verified similar | 지불 | similar | 지급 | (no sense in dump) | CONFIRMED |
| verified similar | 일본 | similar | 왜 | (no sense in dump) | CONFIRMED |
| verified similar | 구매 | similar | 사기 | (no sense in dump) | CONFIRMED |
| verified similar | 나이 | similar | 연령 | (no sense in dump) | CONFIRMED |
| verified opposite | 긍정 | opposite | 부정 | (no sense in dump) | CONFIRMED |
| verified opposite | 거짓말 | opposite | 진실 | (no sense in dump) | CONFIRMED |
| verified opposite | 출국 | opposite | 입국 | (no sense in dump) | CONFIRMED |
| verified opposite | 출생 | opposite | 죽음 | (no sense in dump) | CONFIRMED |
| verified opposite | 승자 | opposite | 패자 | (no sense in dump) | CONFIRMED |
| unverified similar | 당신 | similar | 자신 | "앞에서 말한 사람을 높여서 강조하며 다시 가리킬 때 쓰는 재귀칭 대명사." | CONFIRMED |
| unverified similar | 성명 | similar | 성 | (no sense in dump) | CONFIRMED |
| unverified similar | 예컨대 | similar | 가령 | "보기를 들자면." | CONFIRMED |
| unverified similar | 네모 | similar | 사각형 | (no sense in dump) | CONFIRMED |
| unverified similar | 산 | similar | 산성 | (no sense in dump) | CONFIRMED |
| unverified similar | 말 | similar | 글 | (no sense in dump) | CONFIRMED |
| unverified similar | 글씨 | similar | 글자 | "쓰여진 글의 생김새." | CONFIRMED |
| unverified similar | 맞은편 | similar | 건너편 | "마주 바라보이는 쪽. 또는 상대 사람." | CONFIRMED |
| unverified similar | 기울다 | similar | 기울이다 | "바로 서 있던 무엇이 한쪽으로 비스듬하게 되다." | CONFIRMED |
| unverified similar | 곧바로 | similar | 어서 | "(말하거나 무엇을 하는) 바로 그 순간에." | CONFIRMED |
| unverified opposite | 종 | opposite | 주인 | "옛 봉건사회에서 지체가 높은 집안에서 시중을 드는 사람." | CONFIRMED |
| unverified opposite | 아니요 | opposite | 예 | "윗사람이나 어른 혹은 낯선 사람이 묻는 말에 그렇지 않다고 대답할 때 쓰는 감탄사." | CONFIRMED |
| unverified opposite | 내적 | opposite | 외적 | "안쪽을 중시하는." | CONFIRMED |
| unverified opposite | 다르다 | opposite | 같다 | (no sense in dump) | CONFIRMED |
| unverified opposite | 여성 | opposite | 남성 | "여자를 일컫는 성." | CONFIRMED |

**Spot-check caveat (read this):** the check confirms the relation **exists in the dump** — it does NOT confirm sense fitness for our seed entry. The manual review afterwards demoted **3 of the 30 sampled rows** (구매 → 사기, 긍정 → 부정, 일본 → 왜) for target-sense mismatch. That is the honest limit of the spot-check: dump presence ≠ usable in seed v3.

## Dump / coverage problems hit

1. **English Wiktionary unusable at this scale** (see above): no per-language Korean extract; full dump 3.4 GB uncompressed. Korean-Wiktionary-only coverage is therefore partial: only 24.9% of headwords have any synonym/antonym in the Korean edition at all.
2. **Korean Wiktionary relation coverage is thin**: of 109,000 ko entries, only 8,424 (7.7%) have synonyms and 1,383 (1.3%) have antonyms. Among our headwords the yield is better (2,489/10,000) but still low.
3. **Homographs / multiple dump entries per headword**: common words have several entries (different POS/senses); relations were merged across all of them, deduped per (headword, kind, target). POS is recorded per row in `dump_pos`.
4. **Seed entries are English-glossed, dump senses are Korean-glossed** — the structural reason most relations land in the unverified pile. Closing that gap needs either Korean-capable review or the NIKL API (Phase 2), not more dump data.
5. No rate-limiting, blocks, or download failures on kaikki.org.

## Files written (all under `~/workspace/hangulmate/tools/dataset_v3_phase0/`)

| File | Size | Contents |
|---|---|---|
| `kaikki_relations.json` | 1,472,338 B | Header (dump provenance, rule, counts) + 1,658 headwords → verified `similar`/`opposite` rows and `sense_unverified` review rows. Merge-ready schema `{"korean", "noteNe": ""}` + provenance fields. |
| `KAIKKI_NOTES.md` | this file | Coverage, drops, rule, review, spot-check, blockers. |
| `spotcheck_kaikki.json` | 8,880 B | The 30 sampled relations + per-item confirmation. |
| `extract_kaikki.py` | 10,435 B | Streaming extractor (re-runnable; v2 seed untouched). |
| `postprocess_kaikki.py` | 5,478 B | Adds `mutual_in_dump` + `seed_gloss_overlap_words`. |
| `spotcheck_kaikki.py` | 3,222 B | The 30-item verification pass. |
| `demote_reviewed.py` | 13,170 B | The 83 manual-review demotions (auditable list with reasons). |
| `verified_review.txt` | 49,230 B | All 271 strict-rule verified rows with both English glosses, as reviewed. |
| `downloads/ko-extract.jsonl.gz` | 26,869,664 B | Raw dump, kept. |

## What blocks using this data in seed v3

1. **Nothing blocks the 188 verified rows** — they are merge-ready (188 rows, 159 words). Merge rule: append each row to the headword's `similar`/`opposite` array; rows already carry `noteNe: ""`. V1 words: only 6 verified similar rows touch them and none duplicates an existing v1 relation (checked).
2. **The 2,864 unverified rows are NOT merge-ready.** They need either (a) Korean-capable human review (suggested order: the 649 rows with both review signals first, then the rest with one signal), or (b) the NIKL Open API in Phase 2 — NIKL's own `rel_info` gives synonyms/antonyms already sense-aligned to the seed's own sense inventory, which is exactly the gap this dump cannot close.
3. **Opposite-word yield is very low** (62 verified antonym rows, 51 words; even the raw dump only lists antonyms for 710 of our headwords). If seed v3 wants opposite for more words, Kaikki cannot supply it — the NIKL API (or Wiktionary English edition, if a per-language extract ever appears) is the only route.
4. **Two seed glosses should be fixed first** (악, 공원 — see above); they currently block otherwise-real relations and mislead learners on their own.
5. Kaikki is **usable but thin**: it is a legitimate, licensed (CC BY-SA) source, the extraction is faithful (30/30 spot-check), and the verified set is small because the rules are strict — that is the correct trade for Byte's accuracy-over-quantity rule, not a failure of the source.
