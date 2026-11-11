# HangulMate Vocabulary — Data Quality Report

- **Dataset:** `app/src/main/assets/seed/vocabulary_seed.json`
- **Schema:** `app/src/main/assets/seed/schema.json`
- **Validator:** `tools/validate_seed.py` (Python 3, stdlib only)
- **Date:** 2026-11-11
- **Total entries:** **236** (IDs `HM-0001`–`HM-0236`, sequential, unique)

236 is slightly above the 200–230 target. The overshoot is not padding: it comes
from including both Sino-Korean and native Korean number systems (both are real
EPS-TOPIK vocabulary) and from the 48-word camera core list, which is mostly
distinct from the topic lists. No near-duplicate verb forms were added to reach
the count.

## Counts per topic

| Topic | Entries |
|---|---:|
| general | 39 |
| numbers | 19 |
| directions | 18 |
| daily | 17 |
| conversation | 16 |
| food | 16 |
| workplace | 16 |
| transport | 14 |
| health | 14 |
| manufacturing | 14 |
| construction | 14 |
| tools | 14 |
| agriculture | 13 |
| safety | 12 |
| **Total** | **236** |

`general` is the largest topic because the camera core list (animals, objects,
nature, people) and the core verbs/adjectives were placed there. `agriculture`
(13) and `safety` (12) are just below the 14–17-per-topic aim — see Known gaps.

## Counts per difficulty

| Difficulty | Entries |
|---|---:|
| 1 (beginner) | 130 |
| 2 (intermediate) | 84 |
| 3 (advanced) | 22 |
| **Total** | **236** |

## Counts per EPS importance

| EPS importance | Entries |
|---|---:|
| high | 144 |
| medium | 76 |
| low | 16 |
| **Total** | **236** |

## Counts per part of speech

| Part of speech | Entries |
|---|---:|
| noun | 181 |
| number | 19 |
| verb | 16 |
| expression | 11 |
| adjective | 8 |
| adverb | 1 |
| pronoun / particle / interjection | 0 |
| **Total** | **236** |

## Relations

| Relation | Count |
|---|---:|
| similar | 12 |
| opposite | 26 |
| confusing | 42 |
| **Total** | **80** |

Opposites are deliberately sparse: an `opposite` entry exists only where a true
antonym exists (가다/오다, 좋다/나쁘다, 크다/작다, 빠르다/느리다, 앞/뒤, 위/아래,
왼쪽/오른쪽, 근처/멀리, 오전/오후, 출근/퇴근, 사다/팔다, 안전/위험, 네/아니요).
Most entries have `opposite: []` rather than an invented antonym.

Anchor relations verified by hand:

- **가다 / 오다 / 떠나다 / 이동하다** — 가다/오다 are the deictic pair
  (away from / toward the speaker); 떠나다 stresses leaving a place, often
  for good; 이동하다 is the formal/neutral "move location". Each distinction
  is explained in Nepali in `noteNe` / `differenceNe`.
- **일 / 하나, 십 / 열** — Sino-Korean vs native Korean number systems are
  cross-linked as `confusing`, with the system difference explained in Nepali.
- **해 / 태양** — listed as `similar` with the register difference noted
  (해 is everyday, 태양 is formal/scientific).
- **도구 / 공구, 밥 / 음식, 돈 / 월급, 안전 / 조심** — `similar` only where
  genuinely close, with the exact difference in `noteNe`.

## Camera core list

All 48 required camera words are present (verified programmatically, missing: none):
개, 고양이, 새, 자동차, 버스, 자전거, 오토바이, 기차, 비행기, 배, 의자, 책상,
침대, 문, 창문, 시계, 전화, 컴퓨터, 책, 가방, 신발, 모자, 안경, 우산, 사과,
바나나, 빵, 밥, 물, 우유, 커피, 차, 꽃, 나무, 산, 바다, 해, 달, 별, 사람,
남자, 여자, 아이, 선생님, 의사, 경찰, 소방관.

## Methodology and sources

- Curated from standard EPS-TOPIK vocabulary lists (greetings and survival
  vocabulary, numbers in both systems, days/time, workplace and safety
  essentials, agriculture / manufacturing / construction / tools wordlists).
- Nepali meanings are standard Nepali in Devanagari, cross-checked against
  standard Korean–Nepali EPS study vocabulary (the meanings Nepali EPS
  learners are actually taught, e.g. 회사 = कम्पनी, 공장 = कारखाना,
  월급 = तलब, 헬멧 = हेल्मेट).
- Romanization is Revised Romanization throughout (ㅓ = eo, ㅡ = eu,
  ㅐ = ae, double consonants doubled, e.g. 빵 = ppang, 꽃 = kkot).
- Every entry has one natural Korean example sentence with Nepali and
  English translations written for that sentence (not machine-translated
  in bulk); example sentences use only vocabulary appropriate to the entry.
- No fabricated entries: where a meaning, opposite, or synonym was uncertain,
  the entry or the relation was omitted (see below).
- `syllables` is derived mechanically from the Hangul spelling (one block
  per syllable; spaces stripped first), so it cannot drift from `korean`.

## Validation results

Run: `python3 tools/validate_seed.py` — exit code 0.

```text
Entries: 236
Topics: {'agriculture': 13, 'construction': 14, 'conversation': 16, 'daily': 17, 'directions': 18, 'food': 16, 'general': 39, 'health': 14, 'manufacturing': 14, 'numbers': 19, 'safety': 12, 'tools': 14, 'transport': 14, 'workplace': 16}
Difficulty: {1: 130, 2: 84, 3: 22}
EPS importance: {'high': 144, 'low': 16, 'medium': 76}
Part of speech: {'adjective': 8, 'adverb': 1, 'expression': 11, 'noun': 181, 'number': 19, 'verb': 16}
Relations: similar=12 opposite=26 confusing=42

PASS: all checks passed
```

The validator checks: unique ids; all required fields non-empty; `korean`
Hangul-only; `nepali` contains Devanagari; romanization ASCII; topic /
difficulty / epsImportance in the allowed sets; no duplicate `korean`;
every relation target exists in the dataset; no word is both `similar` and
`opposite` of the same entry; `syllables` joins exactly to `korean`.

Negative test performed: a copy of the dataset with a dangling `similar`
target (`없는단어`) was run through the validator — it exited non-zero with
`FAIL: 1 error(s): … dangling similar target '없는단어'`. The validator does
catch dangling relations; it does not (and cannot) verify that an opposite is
linguistically a true antonym — that check was done by hand during authoring.

## Entries deliberately omitted as uncertain

- **Homograph collisions (3 raw entries deduped):** 열 as "fever" (열 already
  = ten, native number) — replaced by 감기 (cold); 일 as "work" (일 already
  = one, Sino-Korean) — replaced by 업무 (work); 채소 / 과일 / 물 second
  occurrences under agriculture (same Korean spelling as the food entries)
  — kept once, under food. A dataset cannot hold two entries with the same
  `korean` value, so the second sense was not forced in.
- **Polite variants not added as separate entries:** 고맙습니다, 미안합니다,
  죄송해요, 감사해요 and similar register variants of 감사합니다 /
  죄송합니다 were left out to avoid near-duplicate padding; they are real
  words and are candidates for a future register-focused batch, not for
  this seed.
- **Native numbers 넷–아홉 (4–9):** only 하나, 둘, 셋 were included from the
  native system. The remaining native numerals are standard and could be
  added, but their example sentences (counting people/things with the
  correct counter) were not verified to the same standard in this pass, so
  they were omitted rather than guessed.
- **No invented opposites/synonyms:** e.g. 감사합니다 has no `similar`
  entry because 고맙습니다 was not included as an entry (a relation target
  must exist in the dataset), and most nouns have no antonym at all.

## Known gaps

1. **Verbs and adjectives are thin** (16 verbs, 8 adjectives, 1 adverb).
   The seed is noun-heavy (181/236). A follow-up batch should add common
   EPS verbs (열다/닫다, 켜다/끄다, 시작하다/끝나다, 배우다, 가르치다, …)
   and adjectives, with the same relation discipline.
2. **No pronoun / particle / interjection entries** even though the schema
   allows them (저, 이, 그, 은/는, 을/를, 아/야, …). Particles in particular
   need a different example format and were out of scope for this seed.
3. **Agriculture (13) and safety (12)** are below the 14–17 target range;
   `general` (39) is above it because it absorbs the camera list and verbs.
   Rebalancing would mean moving camera words into their natural topics
   (e.g. 사과/바나나 → food) rather than adding entries — a re-categorisation
   decision for the app team, not a data-accuracy issue.
4. **Sino-number examples are narrow:** 일–구 examples mostly use month
   readings (일 월 = January, 이 월 = February, …). Correct, but a learner
   also needs time/money readings; those patterns are already covered by
   other entries (사 시, 오 분, 백 원, 천 원, 만 원).
5. **Relation notes use phonetic Nepali renderings of Korean words**
   (e.g. 가다 rendered गादा in a note). The renderings are consistent
   (final -다 → -दा) but are not the same convention as the `romanization`
   field; a native-speaker review pass should normalise them.
6. **No audio, no Hanja, no verb conjugations.** The seed is a base
   vocabulary list only; conjugated forms and Hanja are intentionally out
   of scope for v1.
7. **"태양" vs "해" and "차" (tea):** 차 as "car" is a real homograph
   (자동차 is the unambiguous entry used here); 차 = tea is the entry
   included. The car sense is covered by 자동차, so no entry was invented.
