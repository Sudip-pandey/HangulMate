# HangulMate Dataset Report — Seed v2 (NIKL)

- **Date:** 2026-11-11
- **Seed file:** `app/src/main/assets/seed/vocabulary_seed.json` — **10,000 entries, validated PASS**
- **Schema:** `app/src/main/assets/seed/schema.json` (v2)
- **Validator:** `tools/validate_seed_v2.py` — `PASS: all checks passed` (10,000 entries)
- **Pipeline:** `tools/nikl_pipeline.py` (stdlib only, reproducible in ~6 s) + `tools/romanization.py` + `tools/nepali_core.py`
- **Raw source:** `data/raw/2024_01.csv` (33,888,840 B, kept out of `app/src`)
- **Intermediate outputs:** `data/processed/vocabulary_seed_v2.json`, `data/processed/phrases.json`, `data/processed/stats.json`

## Source

binjang's parse of NIKL's Basic Korean Dictionary (한국어기초사전):

- **53,172 rows**, **48,782 unique Forms**, 50,503 unique (Form, POS)
- Columns: Form, Part of Speech, Korean Definition, English Definition, Usages, Vocabulary Level, Semantic Category
- https://huggingface.co/datasets/binjang/NIKL-korean-english-dictionary — file `2024_01.csv` (downloaded 2026-11-11, byte size verified: 33,888,840)
- English definitions present on 100% of rows; Usages empty (`[]`) on 1,093 rows (2.1%)
- No romanization, no hanja in the source

## Merge: rows → entries (polysemy preserved, never deleted)

Rows sharing a Form are merged into **one entry with a `senses` array**
(one sense per distinct (POS, first-definition) pair, max 8). Genuine
homonyms/polysemy are kept as separate senses — e.g. 이 (tooth / "two" /
louse / vowel ㅣ), 섬 (3 noun senses), 먹다 (incl. the "become deaf" sense).

| Step | Forms | Rows |
|---|---:|---:|
| Source rows / unique forms | 48,782 | 53,172 |
| Hyphen-prefixed forms (`-설` etc.) — dropped, counted | −1,560 | −1,813 |
| Affix/ending/particle-only forms (접사/어미/조사) — dropped, counted | −240 | −269 |
| Spaced idioms/phrases — moved to `phrases.json`, not deleted | −3,825 | −3,826 |
| Bare-jamo forms — dropped, counted | −40 | −42 |
| Non-Hangul form (`거짓말처럼[같이]`) — dropped | −1 | −1 |
| Usable forms | 43,116 | 47,221 |
| Forms with no English definition at all — dropped (`그스르다`, `옴쭉달싹`: NIKL lists `[None, None]`) | −2 | — |
| **Merged headwords** | **43,114** | — |

One source form contains U+FFFD replacement characters (source-data
corruption); it is a hyphen-prefixed form and was dropped with that set.

## Selection: exactly 10,000 unique headwords, EPS-first

| Band | Candidates (usable forms) | Selected (new) |
|---|---:|---:|
| 초급 (beginner) | 1,776 | 1,606 |
| 중급 (intermediate) | 3,575 | 3,544 |
| 고급 (advanced) | 4,556 | 4,546 |
| Unlevelled | 33,207 | 68 |
| **New NIKL headwords** | | **9,764** |
| v1 seed entries kept (HM-0001–HM-0236) | | 236 |
| **Final dataset** | | **10,000** |

- All usable 초급, then 중급, then 고급, then 68 unlevelled content words
  to fill the target. Within a band: content POS (noun/verb/adjective/
  adverb) first, categorised entries first, entries with an example first,
  then alphabetical (deterministic).
- 225 of the 236 v1 headwords also exist in NIKL; their NIKL senses, level
  and semantic category were attached, but the **v1 Nepali stays** (verified
  human-quality). 11 v1 entries have no NIKL headword (greetings such as
  안녕하세요, and 구급상자) and keep a single sense built from their own gloss.
- 33,125 usable candidate headwords were **not** selected (NIKL's long tail).
- No duplicate Korean+POS in the final set (one entry per Form; the
  validator enforces unique Korean and unique id). IDs: v1 keep HM-0001–
  HM-0236, new entries HM-10001–HM-19764.

## Nepali translation — what was actually done

**No machine translation was used.** The attempt was made and abandoned
deliberately:

- `Helsinki-NLP/opus-mt-en-ne` is not loadable via the HF API (it returns
  an auth error for that id); `facebook/nllb-200-distilled-600M` exists,
  but this VM cannot run it — no torch/transformers installed, 411 MB free
  RAM (a 600M-parameter model needs >1 GB), `/tmp` is a 512 MB tmpfs and a
  torch wheel alone exceeds it. One attempt was made (pip download failed
  with no space left), then the fallback was taken as instructed.

Instead the **top EPS core was hand-translated** (`tools/nepali_core.py`):

| Nepali status | Entries |
|---|---:|
| `verified` — v1 seed (human, pre-existing) | 236 |
| `verified` — hand-translated 초급 core (this pass) | 615 |
| `mt_review` — machine-translated | 0 |
| `missing` — Nepali null, English shown instead | 9,149 |
| **Total** | **10,000** |

- The 615 hand translations cover the everyday/workplace core (greetings,
  pronouns, family, time, food, body, health, numbers, transport,
  directions, workplace). Conventions follow v1: verbs end in -नु
  (읽다 → पढ्नु), adjectives in -हुनु (춥다 → चिसो हुनु).
- A word was **omitted** whenever NIKL's first sense is a rare homonym
  sense (e.g. 공원 listed first as "factory worker" instead of "park",
  과거 as "civil-service exam" instead of "the past", 실패 as a thread
  spool instead of "failure", 김 as "steam" instead of "laver"). Those
  stay `missing` rather than carry a Nepali that contradicts the displayed
  English gloss.
- Every entry keeps its English gloss. The importer stores the English
  gloss in the `nepali` column as a display fallback when Nepali is
  missing, and stores no `ne` translation row for those entries, so the
  app shows English rather than a blank or an invented meaning.

## Examples, senses, relations

| | Count |
|---|---:|
| Entries with `exampleKo` (first NIKL usage sentence) | 9,960 (99.6%) — 9,724 NIKL + 236 v1 |
| Entries with no usable example | 40 |
| Entries with `exampleNe` / `exampleEn` | 236 (v1 only; NIKL usages are Korean-only, so no Nepali/English example was invented) |
| Total senses | 12,201 (avg 1.22/entry; max 8) |
| Entries with >1 sense | 1,616 |
| Relations (similar / opposite / confusing) | 12 / 26 / 42 = **80** |

Relations are unchanged from v1: the 80 hand-written relations, all targets
still present in the dataset (validator checks for dangling targets). **No
new relations were invented** for the 9,764 NIKL entries — relations stay
sparse and real.

## Topic coverage (NIKL Semantic Category → app Topic)

Mapping lives in `topic_from_semantic_category()` in
`tools/nikl_pipeline.py` (139 NIKL categories mapped by their `A > B`
head, e.g. 식생활 → food, 사회 생활 > 직장 생활 → workplace,
사회 생활 > 교통 수단 → transport, 인간 > 신체 부위 → health,
삶 > 병과 증상 → health, 개념 > 수/세는 말 → numbers,
정치와 행정 > 사법 및 치안 → safety; 교육/문화/종교/동식물 → general).
When a row has no semantic category (87% of rows), a conservative
keyword check on the head of the first English gloss is used — and only
for non-number topics: the word "one" appears in glosses like "one's
hands", so number words are assigned from the semantic category or the
수사 POS only, never from keywords (an early version of the pipeline
over-assigned 846 entries to numbers this way; fixed and re-validated).
Unmapped entries fall back to `general`.

| Topic | Entries | | Topic | Entries |
|---|---:|---|---:|---:|
| daily | 1,592 | | conversation | 988 |
| workplace | 582 | | health | 723 |
| agriculture | 26 | | manufacturing | 47 |
| construction | 18 | | food | 355 |
| transport | 138 | | numbers | 257 |
| directions | 339 | | tools | 47 |
| safety | 112 | | general | 4,776 |

No topic is empty. `general` is the largest because most NIKL rows carry
no semantic category and the keyword fallback is deliberately narrow;
agriculture (26) and construction (18) are small because NIKL's leveled
core is general vocabulary, not trade vocabulary.

## Part of speech / difficulty / EPS importance

| POS | Entries | | Difficulty | Entries | | EPS | Entries |
|---|---:|---|---:|---|---:|---|---:|---:|
| noun | 7,174 | | 1 (초급) | 1,736 | | high | 1,750 |
| verb | 1,401 | | 2 (중급 / unlevelled) | 3,696 | | medium | 8,166 |
| adjective | 782 | | 3 (고급) | 4,568 | | low | 84 |
| adverb | 564 | | | | | | |
| expression | 11 | | Level | Entries | | | |
| number | 22 | | 초급 | 1,776 | | | |
| pronoun | 33 | | 중급 | 3,575 | | | |
| interjection | 13 | | 고급 | 4,556 | | | |
| particle | 0 | | (none) | 93 | | | |

Entry POS is the most frequent sense POS (content POS wins ties); forms
with a blank NIKL POS (9.3% of rows) default to noun, matching the app's
existing `PartOfSpeech.fromKey` default. Note: one entry per Form, so a
form that is both noun and verb (1,606 forms in NIKL) is stored under its
dominant POS with all senses preserved in the `senses` array.

## Romanization

NIKL ships no romanization, so `tools/romanization.py` implements Revised
Romanization (jamo decomposition; re-syllabification before vowels,
nasal assimilation before ㄴ/ㅁ, ㅎ aspiration and the silent intervocalic
ㅎ, ㄹ/ㄴ lateral rules, double-batchim rules; tensing deliberately not
applied, per RR).

- Self-test: **23/23 pass** (한국어 → hangugeo, 김치 → gimchi,
  학교 → hakgyo, 안녕하세요 → annyeonghaseyo, 감사합니다 → gamsahamnida,
  먹다 → meokda, 좋아요 → joayo, 좋다 → jota, 한국말 → hangungmal,
  신라 → silla, 읽다 → ikda, 읽어 → ilgeo, …) plus 10 further spot-checks
  (실례합니다 → sillyehamnida, 병원 → byeongwon, 동료 → dongnyo,
  법률 → beomnyul, 침략 → chimnyak, 대통령 → daetongnyeong, …) — 33/33.
- The pipeline's RR agrees with the v1 hand-written romanizations on
  **235/236** entries. The one difference is 괜찮아요 (v1: `gwaenchanhayo`,
  pipeline: `gwaenchanayo` — batchim ㅎ is silent before a vowel);
  v1 entries keep their hand-written romanization unchanged.

## Phrases (separate table, not in the seed)

`data/processed/phrases.json` — **3,825 entries** (1.19 MB): the spaced
idioms/phrases dropped from the main seed (e.g. multi-word expressions),
each with POS, English definitions and NIKL level. Kept for a future
phrase/idiom feature; not shipped in the app yet.

## Attribution and licence — verified

- **Dictionary content: © National Institute of Korean Language (NIKL),
  Basic Korean Dictionary (한국어기초사전) — CC BY-SA 2.0 KR**
  (attribution + ShareAlike). Derived vocabulary data in this app is
  shared under CC BY-SA 2.0 KR.
- **The Hugging Face `license: mit` tag is MISLEADING** and must not be
  relied on: it covers binjang's parser code only, not the dictionary
  content. This data is never bundled or described as MIT or proprietary.
  No evidence contradicting CC BY-SA 2.0 KR was found during this pass.
- Attribution appears in three places: `app/src/main/res/values/strings.xml`
  (`about_dictionary_credit`, for Settings → About — the About screen
  itself still needs to display it; that is UI work), the
  `app/src/main/assets/seed/NOTICE.md` shipped with the seed, and this report.

## Import behaviour (seed v2)

- Room database **v1 → v2** with additive `Migration(1, 2)`:
  `nepaliStatus TEXT NOT NULL DEFAULT 'verified'` and
  `sensesJson TEXT NOT NULL DEFAULT '[]'` on `vocabulary`. No
  `fallbackToDestructiveMigration`, no user data touched.
- Guard flag versioned to **`seed_imported_v2`** (v1 key retained as a
  constant for reference). A v1 install re-imports once: its 236 rows are
  skipped by INSERT IGNORE and refreshed with the v2 columns via a plain
  UPDATE (`VocabularyDao.updateSeedFields`) — no FK cascade, so learning
  progress, favorites and collections survive.
- Inserts are batched (one translations insert, one examples insert) inside
  the existing single transaction; per-word loop cost is gone. Expected
  import report on a fresh install: 10,000 imported, 0 skipped, 10,851
  translations (851 ne + 10,000 en), 10,000 examples, 80 relations
  resolved, 0 skipped, 9,149 Nepali missing.
- The importer still accepts v1-format JSON (Nepali present, no
  `nepaliStatus`/`senses`) with identical behaviour to before, so the
  existing `SeedImportTest` fixture (236 entries) keeps working.

## APK size impact

| File | Size |
|---|---:|
| `vocabulary_seed.json` v1 | 0.15 MB |
| `vocabulary_seed.json` v2 | **8.75 MB** raw, **~1.30 MB** deflated (gzip) |
| `phrases.json` (not shipped in APK) | 1.19 MB |

The seed is a JSON asset: the realistic APK addition is roughly the
deflated size (~1.3 MB) if AAPT compresses it, up to 8.75 MB if the build
stores `.json` uncompressed. If APK size becomes a concern, the seed can
be minified (measured: 8.78 MB → 7.15 MB, an 18.6% saving) or moved to a
binary/columnar format in a later pass.

## Validation

`python3 tools/validate_seed_v2.py` — **PASS** (exit 0):

- 10,000 entries; unique ids (HM-0001–HM-0236 + HM-10001–HM-19764);
  unique Korean; no duplicate Korean+POS (one entry per Form)
- Hangul-only Korean, ASCII romanization, syllables join to the headword
- Every entry has ≥1 English sense (12,201 senses total)
- Nepali status counts: verified 851 / mt_review 0 / missing 9,149;
  every `verified` Nepali contains Devanagari
- All 14 topics non-empty; all 80 relations resolve to entries in the set
- `schema.json` (v2) check: 0 errors across all 10,000 entries

## Remaining limitations (honest)

1. **Nepali coverage is 8.5%.** 9,149 entries show their English gloss
   where Nepali would go. The honest fix is a real en→ne model run on a
   machine with enough RAM, or continued hand translation — not invented
   meanings.
2. **First-sense ordering is NIKL's, not frequency.** For some headwords
   the first (displayed) English gloss is a rare homonym sense
   (공원 "factory worker", 과거 "civil-service exam", 가격 "hitting",
   이해 "gains and losses", 김 "steam", 수술 "flower organ"). All senses
   are preserved in the `senses` array; the displayed gloss may not be
   the commonest meaning. A frequency-based sense re-ordering pass
   (e.g. preferring the sense whose row carries a NIKL level, or the
   shortest gloss) was not done — the only re-ordering applied is moving
   "The name of the vowel/consonant" senses last (10 entries, e.g. 왜).
3. **NIKL semantic categories are occasionally wrong** (이 "two" is
   categorised under 인간 > 신체 부위 because 이 also means "tooth"),
   and 87% of rows have no category at all — hence the large `general`
   topic (4,776) and small trade topics.
4. **English glosses are full-sentence dictionary definitions**
   ("The act of …"), not the short glosses of the v1 seed ("Hello").
   They are accurate; they are just long (longest: 420 characters).
5. **No example translations.** `exampleNe`/`exampleEn` exist only on the
   236 v1 entries; NIKL usages are Korean-only.
6. **Romanization is rule-based** (no exception dictionary). Unusual
   proper nouns and Sino-Korean readings may deviate from NIKL's own
   pronunciation data.
7. **Not verified on device.** Gradle cannot run in this VM (the JVM
   loopback-TCP sandbox block already found for this project), so the
   Room migration, the 10,000-row import timing and the seed JSON parse
   on Android are verified by the Python validator and code review only —
   not by a compiled run. `SeedImportTest` still targets the 236-entry v1
   fixture; a v2 fixture test should be added when CI runs again.
