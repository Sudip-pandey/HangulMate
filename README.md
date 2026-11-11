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

## Build status — 2026-11-11 (integration)

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
- **Meanwhile, CI is the real build path.** GitHub Actions
  (`.github/workflows/android.yml`) runs `./gradlew assembleDebug` and
  `./gradlew testDebugUnitTest` on `ubuntu-latest` with JDK 17 Temurin, and
  uploads `app-debug.apk` as an artifact. Push to `main` (or open a PR) and
  the APK is built there. See `AP_BUILD_STATUS.md` in the deliverables
  folder for the full record.

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
TTS voice availability on device, the one-time ML Kit Korean model download,
and TFLite inference on a real phone are all unverified. Byte's phone is
the runtime proof.

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
├── domain/practice/            QuizGenerator (5 quiz types)
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

- **Vocabulary:** 236 verified EPS-TOPIK entries (`HM-0001`–`HM-0236`) in
  `app/src/main/assets/seed/vocabulary_seed.json`, schema in
  `app/src/main/assets/seed/schema.json`. Validated by
  `tools/validate_seed.py` — see `docs/DATA_QUALITY_REPORT.md`.
  Data model: `docs/DATA_MODEL.md`. ML assets: `docs/ML_ASSETS.md`.
- **Database:** Room 2.6.1, version 1, `exportSchema = true`, 13 entities,
  no destructive migrations (learning progress must never be wiped).
- **SRS:** SM-2-lite interval ladder 1 → 3 → 7 → 15 → 30 days; a wrong
  answer resets to 1 day.

## Offline-first statement

Everything a student needs works offline: vocabulary, SRS revision, quizzes,
backup/restore and on-device camera scanning. There is no account, no server
and no analytics.

## ML / OCR honesty notes

- **ML Kit Korean text recognition** (`com.google.mlkit:text-recognition-korean`)
  is on-device, but Google Play services may need to download the Korean OCR
  model once. That single optional download is the ONLY network use in the
  app — which is why the manifest requests `INTERNET` and
  `ACCESS_NETWORK_STATE`, and the manifest says so in a comment.
- **TFLite object recognition** uses the model bundled at
  `app/src/main/assets/ml/` (MobileNet v1 quantised + labels). It never
  downloads anything.
- **Korean TTS** uses Android's built-in `TextToSpeech` with `Locale.KOREAN`.
  If no Korean voice is installed on the device, the app says so honestly
  (`TtsState.MissingVoice`) instead of pretending to speak. No voice is ever
  faked.

## Known limitations

- **Never compiled or run.** See "Build status" above — no APK, no test run,
  no device verification of any kind as of 2026-11-11.
- **ML Kit model download** happens on first camera OCR use if the Korean
  model is not on the device; the app must handle offline-first-run
  gracefully (it reports the missing-model state rather than crashing —
  verify on a real phone).
- **CameraX, TTS, TFLite inference** are unverified on hardware.
- **Reminder scheduling:** the Profile screen stores the reminder toggle +
  hour in DataStore and `ReminderScheduler` exists; the WorkManager wiring
  has not been exercised on a device.
