# PanLex join audit — HangulMate seed v3, Phase 0

**Date:** 2026-11-11 · **Dataset:** `gtak1/panlex-meanings`, revision `95d5b0e9941a5aee02559169ae906e25921385e6`, licence **CC0-1.0** (per dataset README)
**Method:** Korean subset `kor` (Hangul-script rows only) joined to Nepali subsets `npi` (primary, langvar 503) and `npl` (secondary, langvar 7556) on the shared PanLex `meaning` id. Exact match on the seed Korean headword string. No MT, no invented translations. The v2 seed was not modified.

## Coverage

| Measure | Count |
|---|---|
| Seed words total | 10,000 |
| With verified Nepali (untouched) | 851 |
| **Target set (missing Nepali)** | **9,149** |
| Target headwords present in PanLex `kor` (Hangul) at all | 7,916 (86.5%) |
| Distinct `kor` meanings shared with `npi` | 5,835 of 355,911 |
| Distinct `kor` meanings shared with `npl` | 1 of 355,911 |
| **Matched (≥1 Nepali candidate)** | **470 / 9,149 = 5.14%** |
| …of which exactly one candidate | 367 |
| …of which multiple candidates | 103 (2: 72 · 3: 21 · 4: 6 · 5: 1 · 6: 1 · 7: 1 · 8: 1) |
| Via `npi` only | 470 · via `npl` only: 0 · via both: 0 |

Raw table sizes: `kor` 435,144 rows (393,455 Hangul), `npi` 51,009 rows, `npl` 2,567 rows.
Downloaded TSVs kept in `downloads/` (`kor.tsv` 28.5 MB, `npi.tsv` 3.5 MB, `npl.tsv` 0.2 MB).

**Sensitivity check:** space-stripped matching adds 0 words. Verb-stem matching (strip trailing 다) would add only 47 more (517/9,149 = 5.65%) — and those would be *less* reliable, not more. The 5.14% ceiling is real: PanLex's Nepali side simply does not cover these meaning ids. (`npl` contributed nothing — its 2,567 rows share exactly 1 meaning with Korean.)

## Hand audit — random 50 of the 470 matched words

Sample drawn with `random.seed(20261111)`; full sample data in `audit_sample.json`.
Verdict rules (strict, per the brief): a candidate is **wrong** if it is a transliteration where a real Nepali word exists, a Latin-script romanization, the wrong part of speech/form, or a different sense of the Korean word. **Accurate** means the candidate is a correct Nepali equivalent of the seed's sense (spelling variants and lost honorific nuance tolerated).

Rule applied with one documented judgement call: for proper nouns and established loanwords (country/continent names, e.g. अफ्रिका, एसिया; and पिज्जा) the transliterated form **is** the standard Nepali form — no native alternative exists — so those count as accurate. Transliteration counts as wrong only where a real Nepali word exists (बुके vs फूलको गुच्छा, कोरियन vs कोरियाली भाषा) or the candidate is Latin script (phalam, putali).

| # | Korean | POS | English gloss (seed sense) | PanLex Nepali candidate(s) | Verdict | Reasoning |
|---|---|---|---|---|---|---|
| 1 | 불어 | noun | The language spoken mainly by the French people. | फ्रांसिसी / फ्रान्सेली भाषा | **accurate** | फ्रान्सेली भाषा is exactly "French language" (फ्रांसिसी is a misspelling of फ्रान्सेली — noisy extra). |
| 2 | 대양 | noun | A vast ocean like the Pacific, the Atlantic… | महासागर | **accurate** | Standard word for (vast) ocean. |
| 3 | 화요일 | noun | The second day of a week that starts from Monday. | मंगलवार / मङ्गल्बार / मङ्गलबार | **accurate** | All three are Tuesday (spelling variants). |
| 4 | 과 | noun | In a school or hospital, the unit that divides academic departments… | र | **wrong** | र means "and" — PanLex matched the particle sense; seed means "academic department". |
| 5 | 왕 | noun | A king who is a ruler of a country… | राजा | **accurate** | राजा = king. |
| 6 | 사자 | noun | A person who is dead. | सिंह | **wrong** | सिंह = lion; seed sense is "a dead person" (homonym). |
| 7 | 저 | pronoun | The humble form used by the speaker to refer to himself/herself… | म | **accurate** | म = I; humble nuance lost, acceptable. |
| 8 | 수화기 | noun | The part where one puts one's ear to listen on the phone. | टेलिफोन रिसिभर | **unclear** | The English phrase transliterated; no native word exists, but it is not a clean Nepali translation either — borderline loanword phrase. |
| 9 | 독 | noun | A tall, bulging earthenware jar in which jang… are stored. | बिख | **wrong** | बिख = poison; seed sense is the jar (homonym). |
| 10 | 다다르다 | verb | To reach a place. | पछार | **wrong** | पछार = a throw/fall; nothing to do with "to reach". |
| 11 | 철 | noun | One of four divisions of a year… spring, summer, fall, winter. | phalam / फलाम | **wrong** | फलाम = iron (homonym); seed sense is "season". phalam is also Latin-script romanization. |
| 12 | 수박 | noun | A big, green, round fruit… red, watery flesh. | खरबुजा | **wrong** | खरबुजा = muskmelon/cantaloupe — a different fruit from watermelon. |
| 13 | 잎 | noun | A vegetative organ of a plant, green, flat… | पात | **accurate** | पात = leaf. |
| 14 | 당뇨병 | noun | A disease in which urine contains an abnormally high level of sugar. | मधुमेह | **accurate** | मधुमेह = diabetes. |
| 15 | 볼펜 | noun | A writing instrument which disperses ink by using the rotation of a small metal ball… | कलम | **accurate** | कलम = pen (generic term; a ballpoint pen is a कलम). |
| 16 | 돌 | noun | One year from the day a baby was born. | ढुङ्गा / पत्थर | **wrong** | Both mean "stone" (homonym); seed sense is a baby's first birthday. |
| 17 | 속눈썹 | noun | Hair that grows from the edges of the eyelids. | परेला | **accurate** | परेला = eyelash. |
| 18 | 쇠 | noun | A silver-white solid matter with magnetic properties… | phalam | **wrong** | Sense (iron) is right but the only candidate is Latin-script "phalam", not Devanagari Nepali. |
| 19 | 피자 | noun | Food originating in Italy, made by baking a round, flat piece of dough… | पिज्जा | **accurate** | पिज्जा is the standard Nepali rendering of pizza (established loanword). |
| 20 | 얼음 | noun | Frozen and hardened water. | बरफ | **accurate** | बरफ = ice. |
| 21 | 황색 | noun | Yellow color having both a dark and a light tone. | पहेंलो | **accurate** | पहेंलो = yellow. |
| 22 | 꽃다발 | noun | Flowers tied up in a bundle. | बुके | **wrong** | बुके is a transliteration of "bouquet"; the Nepali word is फूलको गुच्छा. |
| 23 | 나비 | noun | Horizontal length of cloth, paper, etc. | putali / पुतली | **wrong** | पुतली = butterfly/doll (homonym) and putali is Latin-script; seed sense is cloth width. |
| 24 | 스포츠 | noun | The act of moving the body according to certain rules… | खेल | **accurate** | खेल = sport/game. |
| 25 | 사생활 | noun | A person's personal, everyday life. | गोपनियता | **wrong** | गोपनियता = privacy/secrecy, not "personal life" (व्यक्तिगत जीवन). |
| 26 | 한국어 | noun | The language used by the Korean people. | कोरियाली / कोरियन | **wrong** | Bare transliterated adjectives with no भाषा; correct is कोरियाली भाषा. |
| 27 | 러시아 | noun | A country between Eastern Europe and Siberia; the largest country in the world… | रसिया / रूस | **accurate** | Both are standard Nepali names for Russia. |
| 28 | 점 | noun | An unscientific way of guessing what happened in the past or telling someone's fortune… | बिन्दु | **wrong** | बिन्दु = dot/point (homonym); seed sense is fortune-telling. |
| 29 | 주무시다 | verb | (honorific) To sleep. | सुत्नु | **accurate** | सुत्नु = to sleep; honorific nuance lost, acceptable. |
| 30 | 봉투 | noun | A bag made of paper, into which a letter, document, etc., are put. | खाम | **accurate** | खाम = envelope. |
| 31 | 아시아 | noun | One of the six continents in the northeastern part of the earth… | एसिया / एशिया | **accurate** | Both are standard Nepali spellings of Asia. |
| 32 | 주일 | noun | In Christianity, Sunday when services are held. | हप्ता | **wrong** | हप्ता = week, not Sunday (आइतबार). |
| 33 | 기 | noun | The force that acts as the basis for doing something. | झण्डा | **wrong** | झण्डा = flag (homonym); seed sense is vital energy. |
| 34 | 빨간색 | noun | A red color like that of blood, or a ripe apple or red pepper. | रातो | **accurate** | रातो = red. |
| 35 | 목소리 | noun | The sound that comes out of the human throat. | स्वर | **accurate** | स्वर = voice. |
| 36 | 소비자 | noun | A person who pays money to buy products, services, etc. | उपभोक्ता | **accurate** | उपभोक्ता = consumer. |
| 37 | 비우다 | verb | To make something empty by eliminating its contents. | खा / खानु | **wrong** | खा/खानु = to eat — a completely different verb. |
| 38 | 아프리카 | noun | One of the six continents; the equator passes through the continent… | अफ्रीका / अफ्रिका | **accurate** | Both are standard Nepali names for Africa. |
| 39 | 고리 | noun | Something made by connecting long metal objects, strings or cords in the form of a circle. | टोकरी / कर्ली लुप | **wrong** | टोकरी = basket; कर्ली लुप = transliterated "curly loop". Neither is "ring/loop" (घेरा/वलय). |
| 40 | 가루 | noun | A very small grain of something hard, as a result of grinding or pounding it. | पिठो / आटो / पीठो | **wrong** | All three mean "flour"; seed sense is generic powder (chalk powder in the example). |
| 41 | 상 | noun | The first in a class, level, order, etc. | टेबुल / टेबिल | **wrong** | Both mean "table" (homonym); seed sense is "first/top in a class". |
| 42 | 자정 | noun | Twelve o'clock at night. | मध्यरात / अधरात | **accurate** | Both mean midnight. |
| 43 | 법 | noun | A set of regulations and rules that the state institutes for all citizens to comply with. | कानून | **accurate** | कानून = law. |
| 44 | 종료 | noun | The state of a certain action or task being finished, or the act of finishing… | सकिएको संकेत गर्ने वाण | **wrong** | A garbled phrase, not the word for "end/finish". |
| 45 | 화폐 | noun | A coin or bill that is a standard for evaluating the value of a product… | मुद्रा | **accurate** | मुद्रा = currency. |
| 46 | 팔월 | noun | The eighth month of a year, out of twelve months. | अगस्त | **accurate** | अगस्त = August. |
| 47 | 젖다 | verb | To become wet by absorbing liquid. | भिजेको | **wrong** | भिजेको is the adjective/participle "wet"; the verb is भिज्नु — wrong part of speech/form. |
| 48 | 동사 | noun | A death caused by freezing | क्रिया | **wrong** | क्रिया = grammatical "verb" (homonym); seed sense is "death by freezing". |
| 49 | 식물 | noun | A living organism that cannot move by itself, such as plants and trees. | बिरूवा | **accurate** | बिरूवा = plant. |
| 50 | 소나무 | noun | A tree with long, sharp, needle-like leaves that are always green. | सल्ला / सदाबहार | **accurate** | सल्ला is the standard Nepali word for pine (सदाबहार = "evergreen" is a wrong extra candidate). |

## Audit totals

- **Accurate: 27 / 50 (54%)**
- **Wrong: 22 / 50 (44%)**
- **Unclear: 1 / 50 (2%)**

Failure modes among the 22 wrong:
- Homonym / wrong sense of the Korean word: 10 — 과, 사자, 독, 철, 돌, 나비, 점, 기, 상, 동사. (PanLex joins on the surface string, not the seed's sense — exactly the multi-hop failure the dataset card warns about.)
- Transliteration / romanization where a real Nepali word exists, or Latin script: 3 — 쇠 (phalam), 꽃다발 (बुके), 한국어 (कोरियन); plus Latin-script extras inside rows 11 and 23.
- Completely different word: 4 — 다다르다 (पछार), 비우다 (खानु), 고리 (टोकरी), 종료 (garbled phrase).
- Wrong but related concept: 4 — 수박 (खरबुजा, wrong fruit), 사생활 (गोपनियता, privacy ≠ private life), 가루 (पिठो, flour ≠ powder), 주일 (हप्ता, week ≠ Sunday).
- Wrong part of speech / form: 1 — 젖다 (भिजेको adjective instead of भिज्नु verb).

Two extra hazards found:
- Even in **accurate** rows, extra candidates are often wrong or misspelled (불어 first candidate फ्रांसिसी is a misspelling; 소나무 carries सदाबहार). Data order does **not** put the good candidate first — blindly taking candidate #1 would still ship errors.
- Multi-candidate rows frequently mix spellings of the same word with genuinely different words (화요일: three spellings of Tuesday — fine; 가루: three words that are all flour — consistently wrong).

## Projected usable yield

470 matched × ~54% accurate ≈ **~254 usable words ≈ 2.8% of the 9,149 target words** — and only after hand-reviewing all 470 matches (each with up to 8 candidates, some needing a choice among them). The other 8,679 words (94.9%) get nothing from PanLex at all.

## Verdict: NO-GO — drop PanLex from the seed v3 pipeline

PanLex contributes nothing worth the review cost at bulk scale:

1. **Coverage is negligible** — 5.14%, and no matching trick lifts it past ~5.7%. PanLex's Nepali side (51k rows) simply does not cover these meaning ids.
2. **Accuracy is a coin flip** — 54% accurate / 44% wrong in a strict hand audit. The dominant failure is exactly the warned-about multi-hop/homonym problem: the join matches the Korean *string*, not the seed's *sense* (사자→lion for "dead person", 독→poison for "jar", 동사→"verb" for "death by freezing").
3. **It cannot be auto-merged** — candidates must each be hand-checked, and data order does not rank the right one first. Reviewing 470 rows to salvage ~254 words is a poor return next to the MT path (Azure F0 free tier covers the full 844,664-char gloss+example payload — see `MT_COSTING.md`).

`panlex_join.json` is kept as a reference artefact only. If anyone ever wants the ~250 salvageable words, they must be extracted by per-word human review from this file — never merged as-is, and every row keeps `source: "panlex"`, `status: "candidate"` (never `verified`).

## Files

- `panlex_join.py` — reproducible join script (6,253 B)
- `panlex_join.json` — 470 matched words with candidates + header (90,173 B)
- `audit_sample.json` — the 50 audited rows with full seed context (22,403 B)
- `PANLEX_AUDIT.md` — this file
- `downloads/kor.tsv` (28.5 MB), `downloads/npi.tsv` (3.5 MB), `downloads/npl.tsv` (0.2 MB) — raw PanLex tables, kept; disk had 142 GB free, no deletion needed.
