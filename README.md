# HangulMate

HangulMate is a native Android app for Nepali EPS-TOPIK students learning Korean.
Offline-first: the dictionary, learning progress and on-device ML all work with
no network connection. The interface is Nepali by default, with an English
option in Profile → Language.

## Setup

Requirements:

- JDK 17 (the coordinator's toolchain lives at `~/android/jdk17`)
- Android SDK with API 34 installed (SDK at `~/android/sdk`)
- Gradle 8.7 (via the committed wrapper — `./gradlew`)

```bash
cp local.properties.example local.properties
# Adjust sdk.dir inside local.properties if your SDK lives elsewhere.
```

## Build

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

## Build status — 2026-11-11 (v2 continuation)

**GitHub Actions run #1 (commit `0651316`, "Initial commit: HangulMate v1",
pushed by Byte): FAILED in 7 seconds — exit code 126.** That means
`./gradlew` could not execute: the `gradlew` file in the repo is mode
`100644`, not executable (`100755`) — the executable bit was lost when the
project was extracted/pushed from Windows. The fix is one command on the
machine that owns the git checkout: `git update-index --chmod=+x gradlew`
then commit and push. It cannot be fixed through GitHub's API (the API
cannot set file modes), so this step is Byte's. Once that lands, the next
run compiles the app for real — that is when any real build errors will
show, and they will be fixed in follow-up commits. See
`docs/PROJECT_AUDIT.md` for the audit and the iteration record.

**Local build in this VM: NOT PRODUCED — no APK exists yet.**

- `./gradlew --version` works (Gradle 8.7, JVM 17.0.12). The wrapper
  (`gradlew`, `gradle/wrapper/gradle-wrapper.jar`, `gradle-wrapper.properties`
  → `gradle-8.7-bin.zip`) is committed and verified.
- `./gradlew assembleDebug` and `./gradlew testDebugUnitTest` both fail
  before any compilation, with the same error every time:

  ```
  FAILURE: Build failed with an exception.
  * What went wrong:
  Could not receive a message from the daemon.
  ```

  Root cause (proven, not guessed): this VM's sandbox blocks **outbound TCP
  connections from JVM processes**. A plain Java client talking to a plain
  Java server on `127.0.0.1` receives the sandbox policy text instead of the
  server's reply:

  > muse: Other TCP connections is turned off for this assistant. To allow
  > it, ask the user to open Muse settings -> Permissions -> Direct network
  > protocols and switch other_tcp from Deny to Ask.

  Gradle's daemon protocol is TCP on loopback, so no Gradle task that runs a
  build can execute here — daemon mode, `--no-daemon`, and repeated retries
  all fail identically. Python loopback works; only JVM outbound connects
  are intercepted. This is an environment permission, not a project bug:
  **nothing in the HangulMate source was found to cause it.** The same
  failure hit this VM's earlier ISP Toolkit build (2026-10-04).

- **Fix:** in Muse settings → Permissions → Direct network protocols, switch
  `other_tcp` from Deny to Ask (then allow Gradle when prompted), and re-run
  `./gradlew assembleDebug`. Once the daemon can talk, dependency resolution
  still has to go through this VM's authenticated egress proxy: the
  integration step put the proxy settings in `~/.gradle/gradle.properties`
  (outside the project, never committed) and the ISP Toolkit's
  `tools/fwdproxy.py` (unauthenticated localhost forwarder → upstream proxy)
  is the proven workaround if Gradle's own proxy auth fails.
- **Meanwhile, CI is the real build path.** GitHub Actions runs
  `./gradlew assembleDebug` and `./gradlew testDebugUnitTest` on
  `ubuntu-latest` with JDK 17 Temurin, and uploads `app-debug.apk` as an
  artifact. The workflow in the repo is `.github/workflows/android.yml`
  (pushed by Byte with the first commit). The improved workflow in this
  checkout is `.github/workflows/android-build.yml` (explicit Android SDK
  setup, Gradle caching, tests first, test-report upload on failure,
  manual `workflow_dispatch` trigger) — it cannot be pushed through
  GitHub's API (workflow files are blocked for API integrations), so to
  use it, replace `android.yml` via the web UI or a git push from a
  machine. See `AP_BUILD_STATUS.md` in the deliverables folder for the
  full local-build record.

**Unit tests: not run here** (same daemon block). 29 `@Test` methods exist
and are counted from source: Room 7, Search 8, SeedImport 14. They have
never executed anywhere yet — the first green run is expected from CI.

**What was verified here without Gradle:**
- `tools/validate_seed.py` — PASS (236 entries, relations, schema).
- Static integration review: every `com.hangulmate.*` import resolves to a
  declared symbol; every NavHost call site matches its screen's signature;
  all 8 `@HiltViewModel` constructors have their dependencies bound
  (`DatabaseModule` / `AppModule` / `@Inject` constructors).
- Integration gaps fixed in this step (see below).

**What is NOT verified anywhere yet:** the app has never compiled, never
run on a device or emulator, and no APK exists. CameraX behaviour, Korean
TTS voice availability on device, and TFLite inference on a real phone are
all unverified. The ML Kit Korean OCR model is *bundled* in the app (no
download — see "ML / OCR honesty notes" below). Byte's phone is the
runtime proof.

## Integration fixes applied 2026-11-11

- `HangulMateApp.kt` now injects `SeedOnFirstLaunch` and touches it in
  `onCreate()`, so the one-time seed import starts at app launch (Hilt
  singletons are lazy — without this the import only started when
  `HomeViewModel` was created).
- `MainActivity.kt` bottom bar now navigates via
  `Screen.Dictionary.createRoute()` instead of the raw route pattern
  `"dictionary?query={query}"` (which arrived at the destination as the
  literal string `"{query}"`). The defensive `"{query}"` guard in
  `HangulMateNavHost.kt` was left in place.
- Test dependencies added: `androidx.test:core` 1.6.1
  (`ApplicationProvider`, used by `RoomTest`/`SeedImportTest`),
  `kotlinx-coroutines-test` 1.8.1 (`runTest`), in
  `gradle/libs.versions.toml` + `app/build.gradle.kts`
  (`testImplementation`).
- `app/build.gradle.kts` now sets `room.schemaLocation` to
  `"$projectDir/schemas"` via the `ksp` block (the database has
  `exportSchema = true`).
- Gradle wrapper committed: `gradlew`, `gradle/wrapper/gradle-wrapper.jar`,
  `gradle-wrapper.properties` (distribution `gradle-8.7-bin.zip`).

## Architecture

Kotlin + Jetpack Compose (Material 3), MVVM, Hilt, Room, DataStore, Navigation Compose.
See `ARCHITECTURE.md` for the full picture (layers, packages, data flow, SRS,
camera pipelines, offline guarantees, DI, navigation).

```text
com.hangulmate
├── HangulMateApp.kt            @HiltAndroidApp Application; starts seed import
├── MainActivity.kt             edge-to-edge, bottom nav (5 tabs) + NavHost
├── brand/BrandConfig.kt        the ONE place the app name lives
├── di/AppModule.kt             SettingsRepository + TtsManager bindings
├── di/DatabaseModule.kt        Room DB, DAOs, repositories, SeedOnFirstLaunch
├── data/local/                 Room entities, DAOs, SeedImporter (236-word seed)
├── data/repository/            Repository impls + SrsScheduler (SM-2-lite)
├── data/preferences/           SettingsRepository (DataStore, "hangulmate_settings")
├── data/backup/                BackupManager (JSON export / import)
├── domain/model/               VocabularyWord, LearningState, Topic, QuizResult, …
├── domain/repository/          Repository interfaces
├── domain/practice/            QuizGenerator (7 playable quiz types + timed EPS mock; image quiz honestly disabled)
├── speech/TtsManager.kt        real TextToSpeech wrapper, Locale.KOREAN
├── locale/LocaleManager.kt     per-app locale (ne / en) via AppCompatDelegate
├── ml/                         OcrService (ML Kit), TfLiteClassifier (MobileNet),
│                               LabelMapper, TranslationBreakdown
├── work/                       ReminderWorker + ReminderScheduler (WorkManager)
├── ui/theme/                   Material 3 light + dark, shapes, spacing
├── ui/navigation/              Routes + NavHost (Home, Dictionary, WordDetail,
│                               Camera, Practice, Profile, Collections)
├── ui/home/                    Home (daily goal, due reviews, seed state)
├── ui/dictionary/              Dictionary (3-way search) + WordDetail
├── ui/camera/                  Camera Scan (OCR translate + object visual dict)
├── ui/practice/                Practice (quiz, revision, EPS topics, results)
├── ui/collections/             User collections (favorites groups)
├── ui/eps/                     EPS topic study screens
├── ui/components/              WordCard, SectionHeader, EmptyState, badges
└── ui/profile/                 Settings, stats, backup/restore, reminders
```

The brand name "HangulMate" is configurable from exactly one place:
`com.hangulmate.brand.BrandConfig.appName`. No developer branding anywhere.

## Data

- **Vocabulary:** 10,000 entries in
  `app/src/main/assets/seed/vocabulary_seed.json` (8.75 MB), schema in
  `app/src/main/assets/seed/schema.json`. 236 original verified EPS-TOPIK
  entries (`HM-0001`–`HM-0236`, Nepali verified) + 9,764 entries from
  NIKL's Basic Korean Dictionary (한국어기초사전). Validated by
  `tools/validate_seed_v2.py` — PASS (10,000 unique entries, all 14
  topics, all 80 relations resolve). Full report:
  `data/reports/dataset_report.md` (and `docs/DATA_QUALITY_REPORT.md`).
  Data model: `docs/DATA_MODEL.md`. ML assets: `docs/ML_ASSETS.md`.
- **Nepali coverage (honest):** 851 entries have a verified Nepali meaning
  (236 original + 615 hand-translated EPS-core words). The other 9,149 show
  the English meaning as a clearly labelled fallback on the word detail
  screen ("Nepali translation not ready yet") and are excluded from
  Nepali-dependent quizzes (KO→NE, NE→KO, listening, spelling) whenever
  enough verified words exist. Machine translation was attempted and
  abandoned (no verifiable offline EN→NE model could run in the build VM);
  nothing was machine-translated and nothing was fabricated.
- **Source & licence:** dictionary content is NIKL's, shared under
  **CC BY-SA 2.0 Korea** (attribution + ShareAlike). The Hugging Face
  `mit` tag on the source dataset covers only the parser author's code
  and must not be relied on. Attribution is in the app (Profile → About)
  and in `app/src/main/assets/seed/NOTICE.md`.
- **Database:** Room 2.6.1, version 2 (`MIGRATION_1_2`, no destructive
  migrations — learning progress must never be wiped), `exportSchema =
  true`, 13 entities. Seed import is one idempotent transaction guarded
  by `seed_imported_v2`.
- **SRS:** SM-2-lite interval ladder 1 → 3 → 7 → 15 → 30 days; a wrong
  answer resets to 1 day.

## Offline-first statement

Everything a student needs works offline: vocabulary, SRS revision, quizzes,
backup/restore and on-device camera scanning. There is no account, no server
and no analytics. The app contains **no network code at all** — the
`INTERNET` permission was removed because nothing uses it.

## ML / OCR honesty notes

- **ML Kit Korean text recognition** (`com.google.mlkit:text-recognition-korean`
  16.0.1) is a **bundled-model** library: the Korean OCR model is statically
  linked into the app and available immediately — there is **no download**,
  no Play-services fetch, and no network use. (Earlier docs claimed a
  one-time download; that was wrong and is corrected here.
  `OcrService.warmUp()` runs a real recognizer call when the Camera screen
  first opens; manual text entry remains as a fallback.)
- **TFLite object recognition** uses the model bundled at
  `app/src/main/assets/ml/` (MobileNet v1 quantised + labels). It never
  downloads anything.
- **Korean TTS** uses Android's built-in `TextToSpeech` with `Locale.KOREAN`.
  If no Korean voice is installed on the device, the app says so honestly
  (`TtsState.MissingVoice`) instead of pretending to speak. No voice is ever
  faked.

## Known limitations

- **Never compiled or run.** See "Build status" above — no APK, no test run,
  no device verification of any kind as of 2026-11-11. CI run #1 failed on
  the `gradlew` executable bit (see above); iteration is in progress.
- **Nepali coverage is partial** (851 / 10,000 verified). Topic balance is
  skewed by the source data: "general" holds 4,776 entries, while
  agriculture (26), construction (18), tools (47) and manufacturing (47)
  are thin — EPS topic browsing will feel sparse there until more verified
  translations land.
- **CameraX, TTS, TFLite inference** are unverified on hardware.
- **Reminder scheduling:** the Profile screen stores the reminder toggle +
  hour in DataStore and `ReminderScheduler` exists; the WorkManager wiring
  has not been exercised on a device. A boot receiver re-asserts reminders
  after reboot.
- **Tests:** the 29 original unit tests were written for the 236-entry
  dataset; `SeedImportTest` still targets the v1 fixture and a v2 fixture
  test is still to be added. No test has executed anywhere yet (CI is the
  first run).
