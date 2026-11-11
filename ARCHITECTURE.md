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
| `domain/practice` | `QuizGenerator` — 5 quiz types (`QuizType`, `QuizQuestion`) |
| `data/local` | Room: `HangulMateDatabase` (v1, 13 entities, `exportSchema = true`, schemas → `app/schemas/`), `Entities.kt`, `Daos.kt` (11 DAOs), `Converters.kt`, `SeedImporter` + `SeedImportPrefs`/`SeedState`, `DateUtils` |
| `data/repository` | `VocabularyRepositoryImpl`, `LearningRepositoryImpl`, `SearchHistoryRepositoryImpl`, `CollectionRepositoryImpl`, `Mappers.kt`, `SrsScheduler` |
| `data/preferences` | `SettingsRepository` (+ impl): DataStore `"hangulmate_settings"` — theme, language, daily goal, TTS rate/pitch, reminders |
| `data/backup` | `BackupManager`: full JSON export / import via `VocabularyRepository.exportAll` / `importJson` |
| `speech` | `TtsManager` — real Android `TextToSpeech`, `Locale.KOREAN`, `TtsState` (`Ready` / `MissingVoice` / …); never fakes speech |
| `locale` | `LocaleManager` — per-app locale (`ne` / `en`) via `AppCompatDelegate` |
| `ml` | `OcrService` (ML Kit Korean text recognition), `TfLiteClassifier` (bundled MobileNet v1 quantised + `LabelMapper`), `TranslationBreakdown` (OCR text → per-word dictionary lookup), `CameraModels` |
| `work` | `ReminderWorker` (plain `CoroutineWorker`, no Hilt worker) + `ReminderScheduler` (WorkManager daily reminder) |
| `ui/theme` | Material 3 light + dark, `Color`, `Type`, `Shape`, `Spacing` |
| `ui/navigation` | `Routes.kt` (`Screen` sealed class) + `HangulMateNavHost` |
| `ui/home` | `HomeScreen` + `HomeViewModel` (greeting, daily goal, due reviews, seed-import state) |
| `ui/dictionary` | `DictionaryScreen` + `ViewModel` (3-way search), `WordDetailScreen` + `ViewModel` |
| `ui/camera` | `CameraScreen` + `CameraViewModel` (permission, OCR + object modes), `OcrOverlay` |
| `ui/practice` | `PracticeScreen` + `ViewModel` (internal destinations: quiz / revision / results / EPS topics), `QuizScreen`, `RevisionScreen`, `QuizResultScreen` |
| `ui/eps` | `EpsTopicsScreen` + `ViewModel`, `EpsStudyScreen` |
| `ui/collections` | `CollectionsScreen` + `ViewModel` (user word collections) |
| `ui/profile` | `ProfileScreen` + `ViewModel` (settings, stats, backup/restore, reminders) |
| `ui/components` | `WordCard`, `SectionHeader`, `EmptyState`, `LoadingState`, `EpsImportanceBadge` |

## 3. Data flow

### 3.1 First launch — seed import

```text
HangulMateApp.onCreate()
  └─ @Inject SeedOnFirstLaunch (touched → Hilt creates the singleton)
       └─ ApplicationScope.launch { SeedImporter.importIfNeeded() }
            ├─ guard: SharedPreferences "seed_imported_v1" (set ONLY after success)
            ├─ read assets/seed/vocabulary_seed.json (236 entries)
            ├─ insert words, translations, relations, examples, categories
            └─ state: Idle → Importing → Success | Failed
HomeViewModel observes SeedOnFirstLaunch.state and shows the import state.
On Failed the app keeps working with whatever data exists and retries next launch.
```

### 3.2 Dictionary search (3-way)

`DictionaryViewModel` searches, in order of relevance: Korean (hangul /
romanization), Nepali meaning, English meaning. Search history is stored
via `SearchHistoryRepository`. Opening a word records it and navigates to
`WordDetailScreen(wordId)`.

### 3.3 Learning / SRS (SM-2-lite)

`SrsScheduler`: correct recall advances the interval ladder
1 → 3 → 7 → 15 → 30 days by consecutive-repetition count; a wrong answer
resets interval to 1 day and repetitions to 0. `LearningRepository` owns
progress rows (`NEW / LEARNING / KNOWN / DIFFICULT / MASTERED`), daily
activity and quiz results. No `fallbackToDestructiveMigration`, ever:
schema changes ship as versioned, additive migrations
(see `docs/DATA_MODEL.md`).

### 3.4 Quiz generation

`QuizGenerator` builds questions from stored words: 5 `QuizType`s
(Korean→Nepali, Nepali→Korean, listening via TTS, etc.). `PracticeViewModel`
runs the session; `QuizResultScreen` shows the result and persists it via
`LearningRepository`.

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

ML Kit's Korean model is the **one optional download** in the app (Google
Play services may fetch it once). Everything else is bundled. If the model
is absent and the device is offline, OCR reports the missing-model state
instead of crashing — verify on hardware.

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

- Vocabulary (236 verified entries), SRS, quizzes, collections, backup /
  restore, TFLite object recognition: fully offline, zero network.
- ML Kit Korean OCR: on-device inference; one optional model download.
- TTS: on-device Android `TextToSpeech`; missing Korean voice is reported
  honestly (`TtsState.MissingVoice`).
- No account, no server, no analytics, no ads. The manifest's `INTERNET`
  permission exists only for the ML Kit model download and is commented
  as such.

## 7. Vocabulary data

Source of truth: `app/src/main/assets/seed/vocabulary_seed.json`
(236 entries, IDs `HM-0001`–`HM-0236`; schema:
`app/src/main/assets/seed/schema.json`). Validated by
`tools/validate_seed.py` (Python 3, stdlib only): sequential unique IDs,
14 topics (general 39, numbers 19, directions 18, daily 17, conversation
16, food 16, workplace 16, transport 14, health 14, manufacturing 14,
construction 14, tools 14, …), relations similar 12 / opposite 26 /
confusing 42, EPS importance high 144 / medium 76 / low 16. Full report:
`docs/DATA_QUALITY_REPORT.md`. Data model: `docs/DATA_MODEL.md`.
ML assets (bundled MobileNet v1 quantised 4.3 MB, `labels.txt`,
`label_aliases.json`): `docs/ML_ASSETS.md`.

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
  (see README "Build status").
- CI: `.github/workflows/android.yml` — `ubuntu-latest`, JDK 17 Temurin,
  `./gradlew assembleDebug`, `./gradlew testDebugUnitTest`, uploads
  `app-debug.apk`.

## 9. Known gaps (as of integration, 2026-11-11)

1. No successful compile or test run anywhere; no APK.
2. Runtime behaviour on a real device is entirely unverified: CameraX,
   ML Kit first-run model download, TTS Korean voice presence, TFLite
   inference, WorkManager reminders, seed import timing on first launch.
3. `gradle.properties` in the project sets `org.gradle.jvmargs=-Xmx2g`;
   on memory-constrained machines lower it (or override via
   `GRADLE_USER_HOME/gradle.properties`).
4. The `"{query}"` defensive guard in `HangulMateNavHost` is belt-and-
   braces now that `MainActivity` uses `createRoute()`; it can stay.
