# HangulMate — Architecture

Native Android app (Kotlin, Jetpack Compose, Material 3) for Nepali
EPS-TOPIK students learning Korean. Package `com.hangulmate`, `minSdk 26`,
`targetSdk 34`, version `0.1.0` (versionCode 1). Offline-first: no account,
no server, no analytics. Interface Nepali by default, English toggle in
Profile.

> Status 2026-11-11: the app has **never compiled or run** — the local
> Gradle build is blocked by this VM's sandbox (JVM outbound TCP is denied;
> see README "Build status"). Everything below describes the code as
> written, statically reviewed, not the code as executed.
>
> v2 continuation (2026-11-11 evening): vocabulary expanded to 10,000
> entries (NIKL source, see §7); collections + TTS + search + OCR-first-use
> fixes applied (see §9); workflow renamed to
> `.github/workflows/android-build.yml` (repo still runs the earlier
> `android.yml`). CI run #1 failed fast (exit 126 — `gradlew` not
> executable in the repo); iteration is in progress, see README.

## 1. Layers

```text
UI (Compose screens + ViewModels, ui/*)
  │  StateFlow / events, hiltViewModel()
  ▼
Domain (domain/model, domain/repository, domain/practice)
  │  Repository interfaces, plain models, QuizGenerator
  ▼
Data (data/local Room, data/repository impls, data/preferences, data/backup)
  │  Room DAOs, SeedImporter, DataStore, WorkManager
  ▼
Platform (speech/TtsManager, ml/*, locale/LocaleManager, work/*)
```

Rules: UI never touches DAOs (only repository interfaces + ViewModels);
feature code never constructs repositories (Hilt does); the Room database
is the single source of truth for words, progress, history and collections.

## 2. Packages

| Package | Contents |
|---|---|
| `com.hangulmate` | `HangulMateApp` (`@HiltAndroidApp`, starts seed import), `MainActivity` (edge-to-edge, 5-tab bottom nav + `NavHost`) |
| `brand` | `BrandConfig` — the ONE place the app name lives |
| `di` | `AppModule` (SettingsRepository, TtsManager), `DatabaseModule` (Room DB, all DAOs, all repositories, `SeedOnFirstLaunch`, `@ApplicationScope` CoroutineScope) |
| `domain/model` | `VocabularyWord`, `LearningState` (+ `DailyStats`), `Topic`, `PartOfSpeech`, `EpsImportance`, `RelationKind`, `WordRelation`, `QuizResult`, `UserCollection`, `ImportReport` |
| `domain/repository` | `VocabularyRepository`, `LearningRepository`, `SearchHistoryRepository`, `CollectionRepository` |
| `domain/practice` | `QuizGenerator` — 7 playable quiz types (`QuizType`, `QuizQuestion`) + timed EPS mock; Nepali-dependent types quiz only words with a verified Nepali translation |
| `data/local` | Room: `HangulMateDatabase` (v2 + `MIGRATION_1_2`, 13 entities, `exportSchema = true`, schemas → `app/schemas/`), `Entities.kt` (incl. `nepaliStatus`, `sensesJson`), `Daos.kt` (11 DAOs), `Converters.kt`, `SeedImporter` + `SeedImportPrefs`/`SeedState`, `DateUtils` (device-local day boundaries) |
| `data/repository` | `VocabularyRepositoryImpl`, `LearningRepositoryImpl`, `SearchHistoryRepositoryImpl`, `CollectionRepositoryImpl`, `Mappers.kt`, `SrsScheduler` |
| `data/preferences` | `SettingsRepository` (+ impl): DataStore `"hangulmate_settings"` — theme, language, daily goal, TTS rate/pitch, reminders |
| `data/backup` | `BackupManager`: full JSON export / import via `VocabularyRepository.exportAll` / `importJson` |
| `speech` | `TtsManager` — real Android `TextToSpeech`, `Locale.KOREAN`, `TtsState` (`Ready` / `MissingVoice` / …); never fakes speech |
| `locale` | `LocaleManager` — per-app locale (`ne` / `en`) via `AppCompatDelegate` |
| `ml` | `OcrService` (ML Kit Korean text recognition, **bundled model** — no download; `warmUp()` on Camera open), `TfLiteClassifier` (bundled MobileNet v1 quantised + `LabelMapper`), `TranslationBreakdown` (OCR text → per-word dictionary lookup), `CameraModels` |
| `work` | `ReminderWorker` (plain `CoroutineWorker`, no Hilt worker) + `ReminderScheduler` (WorkManager daily reminder) + `BootReceiver` (re-asserts reminders after reboot) |
| `ui/theme` | Material 3 light + dark, `Color`, `Type`, `Shape`, `Spacing` |
| `ui/navigation` | `Routes.kt` (`Screen` sealed class) + `HangulMateNavHost` |
| `ui/home` | `HomeScreen` + `HomeViewModel` (greeting, daily goal, due reviews, seed-import state) |
| `ui/dictionary` | `DictionaryScreen` + `ViewModel` (3-way search + choseong/fuzzy second pass), `WordDetailScreen` + `ViewModel` (incl. add-to-collection picker), `AddWordScreen` + `ViewModel` (user words, prefilled from OCR unknowns), `FuzzySearch.kt` (choseong + Levenshtein ≤ 2) |
| `ui/camera` | `CameraScreen` + `CameraViewModel` (permission, OCR + object modes), `OcrOverlay` |
| `ui/practice` | `PracticeScreen` + `ViewModel` (internal destinations: quiz / revision / results / EPS topics / history), `QuizScreen`, `RevisionScreen`, `QuizResultScreen`, `QuizHistoryScreen` |
| `ui/eps` | `EpsTopicsScreen` + `ViewModel`, `EpsStudyScreen` |
| `ui/collections` | `CollectionsScreen` + `ViewModel` (user word collections) |
| `ui/profile` | `ProfileScreen` + `ViewModel` (settings, stats, backup/restore, reminders) |
| `ui/components` | `WordCard`, `SectionHeader`, `EmptyState`, `LoadingState`, `EpsImportanceBadge` |

## 3. Data flow

### 3.1 First launch — seed import

```text
HangulMateApp.onCreate()
  ├─ apply persisted TTS rate/pitch to TtsManager (startup, not just slider drag)
  └─ @Inject SeedOnFirstLaunch (touched → Hilt creates the singleton)
       └─ ApplicationScope.launch { SeedImporter.importIfNeeded() }
            ├─ guard: SharedPreferences "seed_imported_v2" (set ONLY after success)
            ├─ read assets/seed/vocabulary_seed.json (10,000 entries)
            ├─ insert words, translations, relations, examples, categories
            │   (batched; entries with nepaliStatus "missing" store the
            │   English gloss in the nepali column as a display fallback and
            │   get no "ne" translation row)
            └─ state: Idle → Importing → Success | Failed
HomeViewModel observes SeedOnFirstLaunch.state and shows the import state.
On Failed the app keeps working with whatever data exists and retries next launch.
A v1 database upgrades via MIGRATION_1_2 (additive: nepaliStatus, sensesJson);
existing v1 words are refresh-matched by id so their Nepali stays verified.
```

### 3.2 Dictionary search (3-way)

`DictionaryViewModel` searches, in order of relevance: Korean (hangul /
romanization), Nepali meaning, English meaning. Search history is stored
via `SearchHistoryRepository`. Opening a word records it and navigates to
`WordDetailScreen(wordId)`. When the LIKE search returns fewer than 5
results, a second pass runs `FuzzySearch` (Hangul choseong matching +
Levenshtein ≤ 2 over korean/romanization/english) over the word list.
Quizzes that depend on Nepali (KO→NE, NE→KO, listening, spelling, EPS
mock) only use words whose `nepaliStatus` is `"verified"` when enough exist;
the word detail screen labels the English fallback for `"missing"` entries
instead of presenting it as Nepali.

### 3.3 Learning / SRS (SM-2-lite)

`SrsScheduler`: correct recall advances the interval ladder
1 → 3 → 7 → 15 → 30 days by consecutive-repetition count; a wrong answer
resets interval to 1 day and repetitions to 0. `LearningRepository` owns
progress rows (`NEW / LEARNING / KNOWN / DIFFICULT / MASTERED`), daily
activity and quiz results. No `fallbackToDestructiveMigration`, ever:
schema changes ship as versioned, additive migrations
(see `docs/DATA_MODEL.md`).

### 3.4 Quiz generation

`QuizGenerator` builds questions from stored words: 7 playable `QuizType`s
(Korean→Nepali, Nepali→Korean, listening via TTS, Hangul spelling,
fill-in-the-blank, similar/opposite identification, timed EPS mock); the
IMAGE type is implemented but honestly disabled (no image assets bundled).
Nepali-dependent types filter to `nepaliStatus == "verified"` words when
possible (see §3.2). `PracticeViewModel` runs the session; `QuizResultScreen`
shows the result and persists it via `LearningRepository`; `QuizHistoryScreen`
lists past results.

### 3.5 Camera pipelines

```text
CameraScreen (CameraX Preview + ImageAnalysis)
  ├─ Mode "Scan Text": ML Kit Korean OCR (on-device)
  │    └─ OcrService → recognised blocks → OcrOverlay (bounding boxes)
  │         └─ buildTranslationBreakdown(text, VocabularyRepository)
  │              └─ per-word dictionary lookup; tap a word → WordDetail
  └─ Mode "Scan Object": TFLite MobileNet v1 (bundled .tflite in assets/ml/)
       └─ TfLiteClassifier → top labels → LabelMapper (English label → Korean
            word, only where the mapping is verified) → visual dictionary card
```

ML Kit's Korean model is **bundled** in the app
(`com.google.mlkit:text-recognition-korean:16.0.1` is a bundled-model
library) — there is no download and no network use anywhere in the app.
`OcrService.warmUp()` runs a real recognizer call when the Camera screen
first opens so the first capture does not pay a cold-start surprise; manual
text entry remains as a fallback. (Earlier docs claimed a one-time
Play-services download; that was wrong — the dependency was verified
against Google's ML Kit docs during the v2 continuation.)

### 3.6 Backup / restore

`BackupManager.exportJson()` serialises words + progress + favorites to one
JSON string (share/save via the Profile screen); `importJson()` re-imports
with added / skipped / error counts (`ImportReport`). No cloud involved.

### 3.7 Settings & reminders

`SettingsRepository` (DataStore) holds: interface language (`ne` default),
theme mode, daily goal, TTS rate/pitch, reminder enabled + hour.
`ReminderScheduler` (WorkManager) schedules `ReminderWorker`, which posts a
daily notification; it silently skips when the notification permission is
revoked.

## 4. Navigation

`Screen` sealed class (`ui/navigation/Routes.kt`):

| Route | Args | Notes |
|---|---|---|
| `home` | — | start destination |
| `dictionary?query={query}` | `query` (default `""`) | **always** build with `Screen.Dictionary.createRoute(query)` — navigating with the raw pattern sends the literal string `"{query}"` (the NavHost still guards against it, but don't rely on that) |
| `word_detail/{wordId}` | `wordId` | `Screen.WordDetail.createRoute(wordId)` |
| `camera` | — | |
| `practice` | — | internal sub-destinations (quiz / revision / results / EPS topics) are `PracticeScreen` state, not nav routes |
| `profile` | — | → `collections` via `onNavigateToCollections` |
| `collections` | — | |

Bottom bar (5 tabs) in `MainActivity`: `Home`, `Dictionary`, `Camera`,
`Practice`, `Profile`. Tab taps navigate with `launchSingleTop`,
`popUpTo(startDestination) { saveState = true }`, `restoreState = true`.
Selected-tab matching compares destination `route` against
`tab.screen.route` (the Dictionary destination's route is the pattern
`"dictionary?query={query}"`, which still matches).

## 5. Dependency injection (Hilt)

- `HangulMateApp` is `@HiltAndroidApp`; `MainActivity` and all screens'
  ViewModels are `@AndroidEntryPoint` / `@HiltViewModel`.
- `AppModule`: `SettingsRepository`, `TtsManager` (both `@Singleton`).
- `DatabaseModule`: `HangulMateDatabase` (Room builder, no destructive
  migration), `SeedImportPrefs`, `SeedImporter`, all 11 DAOs, all 4
  repositories (`@Singleton`), `@ApplicationScope CoroutineScope`
  (`SupervisorJob + Dispatchers.Default`), and `SeedOnFirstLaunch`.
- `BackupManager` is `@Singleton` with an `@Inject` constructor
  (`@ApplicationContext Context`, `VocabularyRepository`).
- `ReminderWorker` is deliberately a plain `CoroutineWorker` (no
  hilt-work dependency; it needs no injected collaborators).
- `WordDetailViewModel` reads `wordId` from `SavedStateHandle`.

## 6. Offline guarantees

- Vocabulary (10,000 entries; 851 with verified Nepali), SRS, quizzes,
  collections, backup / restore, TFLite object recognition: fully offline,
  zero network.
- ML Kit Korean OCR: on-device inference with a bundled model — no
  download, no network.
- TTS: on-device Android `TextToSpeech`; missing Korean voice is reported
  honestly (`TtsState.MissingVoice`).
- No account, no server, no analytics, no ads. The app contains **no
  network code at all**; the `INTERNET` + `ACCESS_NETWORK_STATE`
  permissions were removed in the v2 continuation.

## 7. Vocabulary data

Source of truth: `app/src/main/assets/seed/vocabulary_seed.json`
(10,000 entries, 8.75 MB; schema: `app/src/main/assets/seed/schema.json`).
236 original verified EPS entries (`HM-0001`–`HM-0236`, Nepali verified) +
9,764 entries from NIKL's Basic Korean Dictionary (한국어기초사전) via
`binjang/NIKL-korean-english-dictionary` (53,172 source rows → 43,114
merged headwords → 10,000 selected EPS-first). Validated by
`tools/validate_seed_v2.py` (Python 3, stdlib only): 10,000 unique entries,
all 14 topics non-empty, relations similar 12 / opposite 26 / confusing 42
(all resolve), EPS importance high 1,750 / medium 8,166 / low 84. Nepali:
851 verified, 9,149 missing (English fallback, labelled in the UI).
Pipeline: `tools/nikl_pipeline.py` + `tools/romanization.py` (Revised
Romanization, 33/33 checks) + `tools/nepali_core.py` (615 hand-translated
EPS-core glosses). Topic distribution is skewed — "general" holds 4,776
entries while agriculture (26), construction (18), tools (47) and
manufacturing (47) are thin — because 87% of NIKL rows carry no semantic
category. Full report: `data/reports/dataset_report.md` (and
`docs/DATA_QUALITY_REPORT.md`). Data model: `docs/DATA_MODEL.md`.
ML assets (bundled MobileNet v1 quantised 4.3 MB, `labels.txt`,
`label_aliases.json`): `docs/ML_ASSETS.md`.
Licence: NIKL dictionary content is **CC BY-SA 2.0 Korea** (attribution +
ShareAlike). Attribution is in the app (Profile → About) and in
`app/src/main/assets/seed/NOTICE.md`. The Hugging Face `mit` tag covers
only the parser author's code and must not be relied on.

## 8. Build & test

- Gradle 8.7 (wrapper committed), AGP 8.5.2, Kotlin 2.0.20, KSP
  2.0.20-1.0.25, Compose BOM 2024.09.00, Hilt 2.51.1, Room 2.6.1,
  Navigation Compose 2.7.7, CameraX 1.3.4, WorkManager 2.9.0,
  DataStore 1.1.1, ML Kit Korean OCR 16.0.1, TFLite 2.14.0.
- Version catalog: `gradle/libs.versions.toml`; plugins via catalog aliases.
- Room schema export: `exportSchema = true`, location
  `app/schemas/` (set via `ksp { arg("room.schemaLocation", ...) }`).
- Unit tests (`app/src/test/`): 29 `@Test` — `RoomTest` 7,
  `SearchTest` 8, `SeedImportTest` 14. They use JUnit4,
  `androidx.test:core` (`ApplicationProvider`), `kotlinx-coroutines-test`
  (`runTest`). **Never executed yet** — first run is expected from CI
  (see README "Build status"). Note: `SeedImportTest` still targets the
  236-entry v1 fixture; a v2 fixture test is still to be added.
- CI: `.github/workflows/android-build.yml` in this checkout (explicit
  Android SDK setup, Gradle caching, tests before build, test-report
  upload on failure, `workflow_dispatch`, APK artifact). The repo
  currently runs the earlier `.github/workflows/android.yml` (same build +
  test steps, no caching/SDK step) — the improved workflow cannot be
  pushed through GitHub's API (workflow files are blocked for API
  integrations), so it must be swapped in via the web UI or a git push
  from a machine. CI run #1 (on `android.yml`) failed in 7 s with exit
  code 126: `gradlew` is mode `100644` in the repo, not executable.

## 9. Known gaps (as of v2 continuation, 2026-11-11)

1. No successful compile or test run anywhere; no APK. CI run #1 failed on
   the `gradlew` executable bit (one command on Byte's machine to fix).
2. Runtime behaviour on a real device is entirely unverified: CameraX,
   OCR warm-up, TTS Korean voice presence, TFLite inference, WorkManager
   reminders, seed import timing on first launch (10,000 rows now), Room
   migration v1→v2 on a device that already has v1 data.
3. `gradle.properties` in the project sets `org.gradle.jvmargs=-Xmx2g`;
   on memory-constrained machines lower it (or override via
   `GRADLE_USER_HOME/gradle.properties`).
4. The `"{query}"` defensive guard in `HangulMateNavHost` is belt-and-
   braces now that `MainActivity` uses `createRoute()`; it can stay.
5. Nepali coverage is partial (851 / 10,000) and topic balance is skewed
   (see §7); the image quiz stays honestly disabled (no image assets);
   the data-layer audit (Room migrations/indices, backup validation) was
   not completed this turn — see `docs/PROJECT_AUDIT.md`.
6. The data-layer audit gap from the audit (modules A/D/V/Z/AA) still
   stands: those areas were not rated because the audit worker was refused
   by a safety policy. The follow-up must verify Room migrations/indices,
   backup validation, and test execution (CI) before calling the app
   production-ready.
