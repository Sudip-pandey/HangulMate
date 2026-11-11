# HangulMate — Project Audit (2026-11-11)

Master audit of the existing HangulMate project, written before any continuation
work in this turn. This document records the state of the project **as received**,
so later work can be judged against it.

## How this audit was done

- **Method:** read-only static review of the source tree at
  `~/workspace/hangulmate/` (76 Kotlin files), Gradle config, resources,
  assets, docs, and the existing test files. No Gradle build is possible in
  this VM (proven sandbox rule: JVM outbound TCP is intercepted, Gradle's
  daemon protocol is loopback TCP), so **no verdict here rests on a build or
  a test run** — verdicts rest on code wiring (ViewModel → repository → DAO),
  resource files, and on-disk assets.
- **Auditors:** two read-only audit workers (UI/features; NIKL dataset) plus
  the coordinator's own inventory of the file tree, Gradle files, and asset
  sizes. Their reports are the evidence base for the modules below.
- **⚠️ Audit gap — read this first:** the data-layer + build auditor worker
  was refused by a safety policy during this turn. That work was **not**
  retried, rephrased, or routed around. The modules it owned are marked
  **NOT AUDITED** below (A-data-side, D, V, Z, AA, and the data side of Q
  and X). Where the UI auditor's report cites data-layer code (DAO queries,
  SeedImporter, SrsScheduler, SettingsRepository), that is recorded as
  **cross-reference evidence, not a completed audit**. The data layer's
  status is therefore *unrated this turn*, not *verified*. The first build's
  own docs (`docs/DATA_MODEL.md`, `docs/DATA_QUALITY_REPORT.md`) are cited
  as *prior-build claims*, not as verification.
- **Status rule used:** a feature is COMPLETE only if its functionality is
  actually wired end to end. A screen or a button existing is not enough.
  No fake content was found anywhere in the UI — empty states, the disabled
  image quiz, and unavailable OCR are all reported honestly by the app.

## At a glance

| Module | Status | One-line verdict |
|---|---|---|
| A. Project architecture | NOT AUDITED | Layering exists on disk; wiring not rated (audit blocked) |
| B. Premium UI/UX, design system | COMPLETE | Full light/dark theme, tokens, reusable components |
| C. Home dashboard | COMPLETE | Real Room data throughout; today's-10 untracked; search-bar tap risk |
| D. Room database | NOT AUDITED | 13 entities + 10 DAOs claimed; migrations/indices not verified |
| E. Initial vocabulary dataset | PARTIALLY COMPLETE | 236 verified words; far below the 10,000 target; thin verbs/adjectives |
| F. Korean–Nepali dictionary | COMPLETE | Real 3-way + romanization search in Room |
| G. Search functionality | PARTIALLY COMPLETE | Debounce/suggestions/history work; typo tolerance does not exist |
| H. Word detail screens | COMPLETE | All sections wired; no add-to-collection affordance |
| I. Similar words | COMPLETE | Real relations, all resolve, Nepali notes |
| J. Opposite words | COMPLETE | Real antonyms only (26), deliberately sparse |
| K. Confusing words | COMPLETE | Real relations with difference notes (42) |
| L. CameraX implementation | COMPLETE | Preview/capture/gallery/torch/permissions (static; no device test) |
| M. Offline Korean OCR | BLOCKED BY EXTERNAL ASSET | ML Kit Korean model downloads once via Play services; not bundled |
| N. OCR translation / breakdown | COMPLETE | Honest word-by-word breakdown; unknown words can't be saved |
| O. On-device object recognition | COMPLETE | Real TFLite inference on bundled MobileNetV1 (static) |
| P. EPS-TOPIK learning categories | COMPLETE | All 14 topics, live counts, filters |
| Q. Spaced repetition | COMPLETE | Real SM-2-lite ladder, queue, 3 review actions |
| R. Quiz system | PARTIALLY COMPLETE | 7 types playable; image quiz honestly disabled; no history UI |
| S. Korean pronunciation / TTS | PARTIALLY COMPLETE | Honest availability states; **rate/pitch reset after restart (bug)** |
| T. Favorites and collections | PARTIALLY COMPLETE | Favorites work; **collections can never hold a word (broken)** |
| U. Search history | PARTIALLY COMPLETE | Word-open history only, not typed queries |
| V. Backup and restore | NOT AUDITED | Wired in Profile per cross-reference; validation not verified |
| W. Profile and settings | COMPLETE | Settings/stats/backup/reset wired (except TTS bug; 2 nits) |
| X. Offline functionality | COMPLETE | Zero network code (static); no runtime offline indicator |
| Y. Performance and accessibility | PARTIALLY COMPLETE | Good static signals; dead Coil dep; runtime unverifiable here |
| Z. Automated tests | NOT AUDITED | 29 tests written, 0 executed in this VM; not reviewed |
| AA. GitHub Actions APK workflow | NOT AUDITED | File exists (794 B); contents not reviewed this turn |

## Module findings

### A. Project architecture — NOT AUDITED
The on-disk layout shows Clean Architecture layering
(`domain/` models + repository contracts, `data/` local/repositories/
preferences/backup, `di/` Hilt modules, `ui/` per feature, `ml/`, `speech/`,
`work/`, `brand/`). The UI auditor confirmed repository contracts are used
by ViewModels (e.g. `VocabularyRepository` search, `LearningRepository`
streak/progress). Hilt wiring, module bindings, and whether every contract
has exactly one binding were **not verified** this turn (audit blocked).
Prior-build claim: AGP 8.5.2, Kotlin 2.0.20, Compose BOM 2024.09.00, Hilt
2.51.1, Room 2.6.1, CameraX 1.3.4, WorkManager, DataStore — see
`gradle/libs.versions.toml`. **Evidence needed:** a build (CI) to prove the
graph compiles.

### B. Premium UI/UX and design system — COMPLETE
- Full light + dark schemes (`ui/theme/HangulMateTheme.kt:12-52`); theme
  mode `system|light|dark` from settings (`HangulMateTheme.kt:57-73`), wired
  through `MainActivity.kt:43-45`.
- Typography is `FontFamily.Default` only, with an explicit guard comment:
  *"NEVER bundle a Latin-only custom font — it would break Nepali text"*
  (`ui/theme/Type.kt:9-11`). No font resources in `res/` — system fonts
  render Hangul + Devanagari. 8 text styles defined.
- Spacing tokens 4/8/12/16/24 (`Spacing.kt`), shapes 12/16/24 (`Shape.kt`),
  reusable components: `WordCard`, `EmptyState`, `LoadingState`,
  `SectionHeader`, `EpsImportanceBadge`.
- Nits: `CorrectGreen`/`WrongRed` are hardcoded hex in `QuizScreen.kt:41-42`
  and `QuizResultScreen.kt:15-16` (bypass the theme); `EpsImportanceBadge`
  hardcodes `TaegeukRed` for HIGH in both themes.

### C. Home dashboard — COMPLETE (one known limitation)
Everything is real Room data — no fakes. `HomeViewModel.kt:62-112`
combines seed state, daily goal, today stats, streak, learned/mastered/total
counts, today's words, revision queue, recently searched, difficult words.
Greeting by hour (`HomeViewModel.kt:132-139`, 4 time bands). Word of the day
is deterministic by epoch day (`VocabularyRepositoryImpl.kt:68-76`). Streak
computed from real activity days (`LearningRepositoryImpl.kt:70-84`).
"Continue learning" = first item of the real revision queue
(`HomeScreen.kt:214-251`); weak-words strip = real DIFFICULT rows
(`HomeScreen.kt:286-301`).
- **Limitation:** "Today's 10" is not a tracked set —
  `HomeViewModel.kt:76-83` passes `learnedIds = emptySet()` with the comment
  *"No 'already shown' set is tracked; the repository returns the first
  unlearned words by EPS importance."* The same 10 repeat until learned.
- **On-device risk:** the home search bar is a `readOnly` `OutlinedTextField`
  with `Modifier.clickable { onNavigateToDictionary() }`
  (`HomeScreen.kt:82-99`). A TextField can swallow the tap, so the
  tap-to-open-Dictionary may not fire (only the trailing camera `IconButton`
  is a guaranteed target). **Needs a device check.**

### D. Room database — NOT AUDITED
Prior-build claim: Room v1, 13 entities (the 12 spec'd plus
`DailyActivityEntity`), 10 DAOs, normalized translations/examples/relations,
seed importer in one transaction with idempotency guard. Cross-reference
evidence (from the UI auditor, not a data audit): DAO search SQL joins the
translations table (`Daos.kt:19-44`); seed import resolves relations by
Korean lookup with 0 unresolvable targets (`SeedImporter.kt:179-203`).
**Not verified this turn:** primary/foreign keys, indices, type converters,
migration strategy (is there a migration plan or only destructive
fallback?), transaction boundaries, duplicate prevention, import performance.
**This is the highest-risk audit gap** — the dataset expansion in this turn
depends on this layer.

### E. Initial vocabulary dataset — PARTIALLY COMPLETE
- **Verified present:** 236 entries across all 14 topics (UI auditor verified
  against `app/src/main/assets/seed/vocabulary_seed.json`: 144 KB);
  80 relations — 12 similar, 26 opposite, 42 confusing — all targets resolve;
  `tools/validate_seed.py` was reported PASS by the first build.
- **Thin spots (prior-build report):** only 16 verbs / 8 adjectives /
  1 adverb; relation notes use phonetic Nepali renderings of Korean words
  that a native-speaker pass should normalise.
- **Gap vs target:** 236 entries vs the 10,000-unique-word target. The
  expansion source has been fully researched this turn (see
  `data/reports/dataset_report.md` after the pipeline runs): the NIKL
  Korean–English dictionary (`binjang/NIKL-korean-english-dictionary`) has
  53,172 rows / 48,782 unique headwords, ~43,056 clean single-word entries,
  English on 100% of rows — but **no Nepali anywhere**, and its licence is
  **CC BY-SA 2.0 Korea (attribution + ShareAlike), NOT the misleading `mit`
  tag on Hugging Face**. Any bundled NIKL-derived data must carry NIKL
  attribution and ShareAlike compliance.

### F. Korean–Nepali dictionary — COMPLETE
Real 3-way + romanization search in Room: `Daos.kt:19-44` —
`v.korean LIKE :pattern OR v.nepali LIKE … OR v.english LIKE … OR
v.romanization LIKE … OR t.meaning LIKE …` (translations table genuinely
joined), exact matches sort first, then EPS importance.
`VocabularyRepositoryImpl.kt:50-56` escapes LIKE wildcards and wraps in
`%…%`; `search()` returns `emptyFlow()` on blank query, so the whole table
is never dumped. Topic filter chips cover all 14 topics, favorites-only
toggle wired (`DictionaryScreen.kt:126-168`).

### G. Search functionality — PARTIALLY COMPLETE
- Works: 300 ms debounce (`DictionaryViewModel.kt:108-111`), live top-5
  suggestions (`DictionaryViewModel.kt:143`, `DictionaryScreen.kt:107-122`),
  empty states for no-query / no-results / no-favorites / no-topic-words
  (no invented results), clear-query button, favorites toggle per result
  card (`DictionaryScreen.kt:216-219`), case-insensitive English via SQLite
  LIKE (ASCII).
- **Typo tolerance is claimed but NOT implemented.**
  `DictionaryScreen.kt:49-53` says *"Matching (including typo tolerance)
  lives entirely in the repository"*, but the DAO search is pure
  `LIKE %pattern%` and a repo-wide grep for
  `levenshtein|fuzzy|editdistance|typo` finds no fuzzy logic at all. A
  mistyped Korean/English query returns zero results.
- **History is opened words, not typed queries:** `recordSearch` fires on
  word open (`DictionaryViewModel.kt:103-105`); chips show the last 8 opened
  words; clear works (`SearchHistoryRepository.clear`). No query-text history
  anywhere.
- Minor: suggestions are just `results.take(5)` — the dropdown duplicates
  the list rendered below it.

### H. Word detail screens — COMPLETE
All sections present and wired (`WordDetailScreen.kt`): headword +
romanization + syllable chips, Nepali + English meanings, TTS speaker
button, POS/topic/difficulty chips + EPS badge, per-example speak buttons
(`WordDetailScreen.kt:242-258`), favorite / mark-learned / mark-difficult /
remembered / forgot actions feeding the repository
(`WordDetailViewModel.kt:93-114`), prev/next within the same topic with
nulls at the ends (`WordDetailViewModel.kt:45-67`). Not-found state is honest
(`WordDetailScreen.kt:85-89`). The four `onClick = {}` hits at
`WordDetailScreen.kt:143,204,209,214` are `enabled = false` decorative chips
— inert, not bugs. There is no explicit "revise" button; Remembered/Forgot
serve that role. **Missing: no "add to collection" affordance** (see T).

### I. Similar words — COMPLETE
Relations are real DB rows, resolved by Korean spelling at seed import
(`SeedImporter.kt:179-203`, unresolved targets skipped + logged). Verified
against `vocabulary_seed.json`: 80 relations — 12 similar, 26 opposite, 42
confusing — all targets resolve, all carry `noteNe`/`differenceNe`.
`WordDetailScreen.kt:388-404` renders only what the data contains;
unresolvable targets render as plain text, never a dead link
(`WordDetailScreen.kt:420-436`). No invented opposites anywhere. The
RELATION quiz type draws from the same real relations
(`QuizGenerator.kt:293-322`).

### J. Opposite words — COMPLETE
Same evidence as I. 26 opposites, deliberately sparse — true antonyms only.
No invented opposites found in the seed data.

### K. Confusing words — COMPLETE
Same evidence as I. 42 confusing-word relations with Nepali difference
notes. The 가다/오다/떠나다/이동하다 set is modelled with real Nepali
difference explanations.

### L. CameraX implementation — COMPLETE (static)
Preview via `PreviewView` + `bindToLifecycle` with back camera
(`CameraScreen.kt:180-199`), in-memory capture via
`ImageCapture.OnImageCapturedCallback` + YUV→Bitmap conversion with rotation
handling (`CameraScreen.kt:1037-1086`), gallery import via `GetContent`
(`CameraScreen.kt:142-147`), torch via `cameraControl.enableTorch`
(`CameraScreen.kt:202-204`), retake via `clearCapture`, full permission flow
including permanently-denied → app-settings (`CameraScreen.kt:203-264`).
Photos capped at 1600 px (`CameraViewModel.kt:433-444`). No front-camera
switch. **Not verified on device** — CameraX binding errors surface only as
a `camera_no_camera` text (`CameraScreen.kt:243-248`).

### M. Offline Korean OCR — BLOCKED BY EXTERNAL ASSET (by design, handled honestly)
Dependency is genuine: `com.google.mlkit:text-recognition-korean:16.0.1`
(`gradle/libs.versions.toml`, `OcrService.kt:33-35` uses
`KoreanTextRecognizerOptions`) — ML Kit's real Korean-script recognizer.
**The model is NOT bundled**: it downloads once via Google Play services on
first use (`OcrService.kt:13-19`). Failure → `OcrUiState.Unavailable` +
manual text entry fallback (`CameraViewModel.kt:202-222`,
`CameraScreen.kt:523-525`). Honest strings throughout
(`strings_camera.xml`: `camera_ocr_unavailable`,
`camera_ocr_model_note`). Consequence: on a device without Play services, or
with no internet on first use, OCR never works. Minor honesty nit: the
"Downloading the Korean text model" label is inferred (`!ocrModelReady`,
`CameraViewModel.kt:190`) — there is no real download-progress callback, so
a network failure shows the same "downloading" label until it errors.

### N. OCR translation and dictionary breakdown — COMPLETE (honest)
`TranslationBreakdown.kt:96-137`: tokenizes Korean, tries raw then
particle-stripped forms (up to 2 passes, `PARTICLE_SUFFIXES`), exact →
partial → unknown buckets. Whole-phrase match shown when the text is itself
a dictionary entry. **No full-sentence translation is claimed** — the UI
card states it plainly (`strings_camera.xml`: *"Full-sentence Nepali
translation is not bundled offline — only the word-by-word breakdown above
is provided… No translation is guessed."*). Extracted words are tappable →
word detail (`CameraScreen.kt:846-854`); unknown words shown in red with
"—". **Gap: unknown words cannot be saved** — there is no "save unknown
word" action, and `insertUserWord` (which exists in
`VocabularyRepositoryImpl.kt:190`) has no UI caller anywhere. Copy/share of
extracted text + breakdown works (`CameraViewModel.kt:245-283`).

### O. On-device object recognition — COMPLETE (static)
Real inference: `TfLiteClassifier.kt:42-53` loads the bundled model via
`Interpreter` (4 threads), resizes to 224×224, runs uint8 output, returns
top-5 skipping the `background` class (`TfLiteClassifier.kt:62-84`).
`LabelMapper.kt` loads `labels.txt` (1001 lines, verified) +
`label_aliases.json` (269 verified aliases, verified on disk); unmapped
labels return null, never a guessed word. Confidence shown as %
(`CameraScreen.kt:916-921`); results gated on a 0.25 confidence threshold +
at least one mapped word, else `NotIdentified` shows the raw English top
guess as-is (`CameraViewModel.kt:352-367`). Manual correction exists: a
manual dictionary-search field in object mode
(`CameraScreen.kt:800-814`). Model-missing state handled
(`ObjectUiState.ModelMissing`). Model file verified on disk:
`mobilenet_v1_1.0_224_quant.tflite`, 4,276,352 B.

### P. EPS-TOPIK learning categories — COMPLETE
All 14 topics in `domain/model/Topic.kt` with Nepali + English names and
icons; live per-topic counts (`EpsTopicsViewModel.kt:60-70`); seed
distribution verified: 12–39 words per topic (general 39, safety 12, rest
13–19). Difficulty 1–3 and EPS-importance filters apply to the flashcard
session (`EpsTopicsViewModel.kt:91-113`; note importance is filtered in
memory after fetch — correct, just not in SQL). Flashcards with I-know /
I-don't-know feeding SRS (`EpsStudyScreen.kt:184-202`).

### Q. Spaced repetition — COMPLETE (UI + scheduler logic as observed)
`RevisionScreen.kt` + `PracticeViewModel.kt:143-166`: three actions wired —
KNOW → `recordReview(true)`, NEED_PRACTICE → `recordReview(true)` + force
LEARNING, FORGOT → `recordReview(false)` + re-queued once per session.
Queue = due words (most-wrong first) + up to 10 fresh
(`VocabularyRepositoryImpl.kt:140-153`). `SrsScheduler.kt:17-22` is a real
SM-2-lite ladder (1→3→7→15→30 days), wrong answer resets to 1 day +
DIFFICULT, MASTERED at 5 consecutive correct. Progress bar, tap-to-reveal,
per-card TTS all wired. (Data-layer persistence of review history was not
independently audited this turn — see the audit gap note.)

### R. Quiz system — PARTIALLY COMPLETE
**7 playable types** in `QuizGenerator.kt`: KO_TO_NE, NE_TO_KO, LISTENING
(gated on TTS Ready), SPELLING (syllable-swap mutations from real words),
FILL_BLANK (needs a real example containing the word), RELATION
(similar/opposite), EPS_MOCK (mixed 6 types, timed 60 s/question, 10 or 20
questions). Guards are honest: `< 4` words → `NotEnoughWords` state;
unbuildable questions skipped, never invented. **IMAGE is implemented in
the enum/generator but always returns an empty list**
(`QuizGenerator.kt:120`) and the hub shows it disabled with the honest
reason (`PracticeScreen.kt:236-247`, `quiz_image_disabled` string). Result
screen has score, per-question review, explanations, and
add-wrong-word-to-difficult (`QuizResultScreen.kt:92-138`). **Gap: quiz
results are persisted (`saveQuizResult`) but there is no UI to view quiz
history** — `getQuizHistory` has no UI caller.

### S. Korean pronunciation and TTS — PARTIALLY COMPLETE (one real bug)
`TtsManager.kt` is honest: `Checking/Ready/MissingVoice/Error` states;
per-word and per-example speak buttons throughout; listening quiz disabled
with the missing-voice message when unavailable; test voice speaks
"안녕하세요" (`ProfileViewModel.kt:125-127`). **BUG — persisted TTS
rate/pitch are never applied at startup:** `AppModule` provides `TtsManager`
with hardcoded `rate = 1.0f / pitch = 1.0f` (`TtsManager.kt:42-43`);
`setRate`/`setPitch` are called only from
`ProfileViewModel.previewTtsRate/previewTtsPitch`
(`ProfileViewModel.kt:104-110`) while the slider is dragged. After an app
restart, speech is 1.0/1.0 until the user drags a slider again, even though
the values persist in DataStore (`SettingsRepository.kt:112-116`, correctly
clamped 0.5–2.0).

### T. Favorites and collections — PARTIALLY COMPLETE (collections broken)
Favorites: fully wired (toggle in Dictionary list, WordDetail, Camera cards;
favorites-only filter; reactive via `FavoriteDao.isFavorite`). Collections:
create / rename / delete / remove-word are wired end-to-end
(`CollectionsViewModel.kt`, `CollectionDao`). **But no word can ever enter a
collection:** `CollectionsScreen.kt:40-43` claims *"adding words happens
from the word detail screen"*, yet `WordDetailScreen.kt` /
`WordDetailViewModel.kt` contain **zero** collection code (grep: no hits),
and `CollectionsViewModel.addWord` (`CollectionsViewModel.kt:61-63`) has no
UI caller. Every collection stays permanently empty (`wordCount` always 0).
Similarly, **no "add a word" screen exists** — `insertUserWord` is
unreachable except via backup import.

### U. Search history — PARTIALLY COMPLETE
Recorded on word open, shown as chips on Dictionary (limit 8) and Home
recent (limit 5), clear wired (`DictionaryViewModel.kt:85-87`). It is word
history, not query history (see G).

### V. Backup and restore — NOT AUDITED
Cross-reference only (not a completed audit): export/import JSON via SAF +
local backup are wired in `ProfileViewModel.kt:129-179`, and reset keeps
vocabulary (`VocabularyRepositoryImpl.kt:161-173`). Backup format,
validation, duplicate handling, and restore-into-Room behaviour were **not
verified** this turn (audit blocked).

### W. Profile and settings — COMPLETE (except the TTS bug in S)
Daily goal stepper 1–50 (clamped in DataStore,
`SettingsRepository.kt:94-95`); language toggle Nepali/English via AppCompat
per-app locales + activity recreate (`LocaleManager.kt`,
`ProfileScreen.kt:185-209`); theme system/light/dark; TTS rate/pitch
sliders; reminders via WorkManager daily at chosen hour with
`POST_NOTIFICATIONS` permission flow and re-assert on Profile init
(`ProfileViewModel.kt:75-81,118-133`); real stats (streak/learned/mastered/
total/quizzes, zeroes are real); export/import JSON via SAF + local backup
(`ProfileViewModel.kt:129-179`); reset behind a confirmation dialog that
states vocab is kept (`ProfileScreen.kt:501-543`,
`VocabularyRepositoryImpl.kt:161-173`); About with `BuildConfig.VERSION_NAME`
(0.1.0). Nits: `RECEIVE_BOOT_COMPLETED` is declared in the manifest but no
receiver exists (harmless — WorkManager persists work); the About line
`offline_notice` reads *"You are offline — …"* unconditionally, even when
online.

### X. Offline functionality — COMPLETE (static)
Zero network code: grep for `http://|https://|retrofit|okhttp|URL(` across
`app/src/main/java` returns nothing. `INTERNET` + `ACCESS_NETWORK_STATE` are
declared only for the one-time ML Kit model download (manifest comment says
so). **No runtime offline indicator exists** — the only offline UI is the
static About line that incorrectly says "You are offline" even when online
(see W). Nothing needs airplane-mode handling because nothing calls the
network. (Database-side offline behaviour — seed import on first launch,
empty-DB handling — was not independently audited this turn; see the audit
gap note. The first build claims one-transaction idempotent import.)

### Y. Performance and accessibility — PARTIALLY COMPLETE (static limits apply)
Positives: photos capped at 1600 px, TFLite 4 threads, 300 ms search
debounce, Room `Flow`s everywhere, no image loading at all (Coil is declared
in `build.gradle.kts` but **never used** — dead dependency). Content
descriptions: 24× `contentDescription = null`, all checked — decorative
icons inside buttons that already have text labels, plus the bottom-nav icons
(labels present). Real labels exist on all standalone `IconButton`s. Touch
targets are Material defaults (48 dp). Cannot judge statically: font scaling
at large system text sizes, low-end device frame rates, TalkBack traversal
order, or the home search-bar tap issue (see C).

### Z. Automated tests — NOT AUDITED
Prior-build claim: 29 unit tests written (Room 7, Search 8, SeedImport 14),
0 executed in this VM (Gradle blocked). The test files exist
(`app/src/test/java/com/hangulmate/data/RoomTest.kt`, `SearchTest.kt`,
`SeedImportTest.kt`) but were **not reviewed and not executed** this turn
(audit blocked). No test result may be claimed from this state.

### AA. GitHub Actions APK workflow — NOT AUDITED
The file exists: `.github/workflows/android.yml` (794 B). Its contents were
**not reviewed** this turn (audit blocked). Prior-build claim: JDK 17,
`assembleDebug` + `testDebugUnitTest`, APK artifact upload. No workflow run
has ever succeeded (the repo `Sudip-pandey/HangulMate` is empty as of
2026-11-11; no code has been pushed).

## Corrections after the audit (2026-11-11, continuation work)

### Correction 1 — OCR model is BUNDLED, not downloaded (module M)
The audit above rates module M as BLOCKED BY EXTERNAL ASSET on the
UI auditor's claim that the ML Kit Korean model "downloads once via Google
Play services on first use". **That claim was wrong.** During the
continuation fixes, the code-fix worker verified against Google's official
ML Kit documentation and the dependency itself:
`com.google.mlkit:text-recognition-korean:16.0.1` is a **bundled-model**
library — the Korean recognition model is statically linked into the app
and available immediately, with no Play-services download and no
`RemoteModel` for text-recognition v2. The first build's docs
(`AP_BUILD_STATUS.md`, `ML_ASSETS.md`, `README.md`) repeat the download
claim and must be corrected. The code was fixed accordingly:
`OcrService.warmUp()` (a real recognizer call on a 1×1 blank bitmap) runs
when the Camera screen first opens; the false `downloadingModel` state and
download claims were removed from code and strings; and since no code in
the app uses the network at all, the `INTERNET` + `ACCESS_NETWORK_STATE`
permissions were removed from the manifest. **Corrected status of module M:
COMPLETE (static)** — OCR works fully offline after install, with the
manual text-entry fallback still in place. The remaining OCR unknown is
on-device runtime behaviour only (same as every other module).

## Dataset source research (for the expansion)

- **Source:** NIKL Basic Korean Dictionary via
  `binjang/NIKL-korean-english-dictionary` (CSV `2024_01.csv`, 33,888,840 B).
  53,172 rows / 48,782 unique headwords / 50,503 unique (Form, POS).
  Clean single-word content words: 47,072 rows / 43,056 headwords.
  English definitions on 100% of rows; POS missing on 9.3%; NIKL level on
  only 20.7%; semantic category on only 12.9%. No romanization.
- **Licence:** content is NIKL's, strongly evidenced as **CC BY-SA 2.0 Korea**
  (attribution + ShareAlike). The Hugging Face `mit` tag covers only the
  parser author's code and must NOT be relied on. Derived data must be
  shared under CC BY-SA; attribution to NIKL is required in-app and in-repo.
- **Nepali:** no Nepali anywhere in the source. ~100% of expanded entries
  need a Nepali gloss created/verified. Translation workflow and its limits
  must be documented in `data/reports/dataset_report.md`.

## Ranked fixes (from the audit)

1. **Collections can never hold words** (T) — add "Add to collection" on
   WordDetail; the feature's core loop is dead.
2. **Persisted TTS rate/pitch ignored after restart** (S) — apply stored
   values at startup.
3. **"Typo tolerance" is a false claim** (G) — implement real tolerance or
   remove the claim.
4. **Home search bar tap reliability** (C) — verify on device; replace the
   `readOnly` TextField + clickable with a real button/card if it doesn't fire.
5. **OCR first-use experience** (M) — proactive `RemoteModelManager.download()`
   with genuine progress instead of failing on first capture.
6. **Data-layer audit gap** (A/D/V/Z/AA) — these modules were not rated this
   turn; the follow-up must verify Room migrations/indices, backup
   validation, test execution (CI), and the workflow file before claiming the
   app is production-ready.
7. Secondary: quiz-history UI; add-word UI; save-unknown-OCR-word; fix
   `offline_notice` copy; remove dead Coil dependency; add boot receiver or
   drop the permission; local-timezone day boundaries; image quiz stays
   honestly disabled until real image assets exist.

## Verdict

Genuinely usable as-is, with two dead ends (collections, image quiz) and one
real bug (TTS rate/pitch). All data is real Room data; there is no fake
content anywhere. The dictionary, word detail, EPS topics, SRS revision,
quizzes (7 types), camera text scan + breakdown, and object recognition are
wired end to end (statically verified). What is NOT verified: anything that
needs a device or a build — compilation, runtime behaviour, CameraX, TTS
voice availability, OCR model download, TFLite inference on hardware, and
the 29 unit tests. The dataset is small (236 words) against the 10,000
target; expansion is now researched and ready to build.
