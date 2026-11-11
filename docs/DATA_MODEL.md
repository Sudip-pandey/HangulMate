# HangulMate — Data Model

Data layer for HangulMate (native Android, Kotlin + Jetpack Compose + Room 2.6.1, KSP).
Package `com.hangulmate`. Database version **1**.

Source of truth for the vocabulary: `app/src/main/assets/seed/vocabulary_seed.json`
(236 verified entries, schema in `app/src/main/assets/seed/schema.json`).
This document is owned by the data-layer agent; feature agents code against the
repository interfaces in `domain/repository/` and must not touch DAOs directly.

## 1. Entity–relationship model

Database: `HangulMateDatabase` (`hangulmate.db`), `exportSchema = true`.
All foreign keys use `onDelete = CASCADE`, so deleting a word never leaves orphans.

| Table | Primary key | Key columns / notes |
|---|---|---|
| `vocabulary` | `id` (TEXT, e.g. `HM-0001`) | `korean` UNIQUE index. Columns: `nepali`, `english`, `romanization`, `syllablesJson` (JSON array), `partOfSpeech`, `topic`, `difficulty` (1–3), `epsImportance` (`high`/`medium`/`low`), `exampleKo/Ne/En`, `tagsJson` (JSON array), `isUserCreated` (false for seed words). |
| `vocabulary_translations` | `id` (auto) | `vocabId` FK → `vocabulary.id`; `language` (`ne` \| `en`); `meaning`. Unique on (`vocabId`, `language`). Nepali/English meanings are stored BOTH as columns on `vocabulary` (fast display) AND here (normalized table, genuinely searched — see §4). |
| `vocabulary_relations` | `id` (auto) | `sourceId` FK, `targetId` FK (nullable per spec; the importer skips unresolvable targets, so stored nulls should not occur), `targetKorean` (denormalized display text), `kind` (`SIMILAR` \| `OPPOSITE` \| `CONFUSING`), `noteNe` (for CONFUSING this is the seed `differenceNe`). |
| `vocabulary_examples` | `id` (auto) | `vocabId` FK; `ko`, `ne`, `en`. One row per word from the seed; more examples can be added later without a migration. |
| `vocabulary_categories` | `topic` (TEXT key) | `nameNe`, `nameEn`, `iconName`. One row per `Topic` (14 rows, populated at seed import). |
| `vocabulary_images` | `id` (auto) | `vocabId` FK; `assetPath` (e.g. `images/words/HM-0001.jpg`); `caption`. Reserved for the camera/image feature; empty until that agent fills it. |
| `learning_progress` | `vocabId` | `state` (`NEW` \| `LEARNING` \| `KNOWN` \| `DIFFICULT` \| `MASTERED`), `easiness` (2.5 start, [1.3, 2.5]), `intervalDays`, `repetitions`, `nextReviewEpochDay`, `lastReviewedEpochDay` (nullable), `wrongCount`, `correctCount`. |
| `favorite_words` | `vocabId` | `addedEpoch` (millis, drives most-recent-first ordering). |
| `search_history` | `id` (auto) | `vocabId` FK, UNIQUE index on `vocabId` (one row per word); `searchedEpoch` (millis, updated on re-search via REPLACE). |
| `quiz_results` | `id` (auto) | `type` (e.g. `multiple_choice`), `score`, `total`, `epochMillis`, `wrongWordIdsJson` (JSON array). |
| `daily_activity` | `dateEpochDay` | `wordsLearned`, `wordsReviewed`. One row per calendar day with study activity; drives the streak. A quiz-only day is stored as a (0, 0) row so it still counts as a study day. |
| `user_collections` | `id` (auto) | `name`, `createdEpoch`. |
| `collection_words` | (`collectionId`, `vocabId`) composite | Both columns FKs with CASCADE. |

Relations (domain view): a `VocabularyWord` embeds its relations as
`similar` / `opposite` / `confusing: List<WordRelation>` (`targetWordId`,
`targetKorean`, `kind`, `noteNe`, optional `noteEn`). Relations are embedded
only by `VocabularyRepository.getById` / `getByKorean`; list queries return
words with `isFavorite` and `learningState` joined in, relations empty.

### DAOs (10)

`VocabularyDao`, `RelationDao`, `ExampleDao`, `CategoryDao`,
`TranslationDao`, `ProgressDao`, `FavoriteDao`, `HistoryDao`, `QuizDao`,
`DailyActivityDao`, `CollectionDao` — in `data/local/Daos.kt`.
(`ExampleDao` and `DailyActivityDao` are additions to the 8 named in the
brief: examples need their own delete-for-reimport, and streaks need a
daily-activity table; both are documented here, not hidden.)

### Domain models (`domain/model/`)

`VocabularyWord`, `WordRelation` (+ `RelationKind`), `LearningState`
(`NEW, LEARNING, KNOWN, DIFFICULT, MASTERED`), `Topic` (14 values with
Nepali/English display names + icon name), `PartOfSpeech`,
`EpsImportance`, `QuizResult` (with `percentage`), `DailyStats`,
`ImportReport`.

### Repository contracts (`domain/repository/`)

- `VocabularyRepository` — search, getById/getByKorean, word of the day,
  today's new words, by-topic, favorites, difficult, recently searched,
  learning state, revision queue, review recording, counts, user-word
  insert, JSON export/import. `DAILY_NEW_WORD_GOAL = 10`.
- `LearningRepository` — streak, daily activity, today's stats, quiz
  save/history.
- `SearchHistoryRepository` — record / recent ids / clear.

All `Flow` results are cold and re-emit when the underlying tables change.
List enrichment (favorite flag + learning state) is done by combining the
vocabulary flow with `favoriteIdsFlow` and `progressFlow`, so a favorite
toggle or a review re-emits open lists.

## 2. Seed import flow

`SeedImporter` (`data/local/SeedImporter.kt`):

1. Trigger: `SeedOnFirstLaunch` (in `di/DatabaseModule.kt`) — an
   `@ApplicationScope` coroutine that runs `importIfNeeded()` once.
   **Integration note:** Hilt creates singletons lazily, so the
   foundation-owned `HangulMateApp` (Application) MUST inject
   `SeedOnFirstLaunch` in `onCreate` (e.g. `@Inject lateinit var
   seedOnFirstLaunch: SeedOnFirstLaunch`) or the import never starts.
   Asset prepopulation (`createFromAsset`) is deliberately NOT used.
2. Guard: SharedPreferences `hangulmate_seed_prefs`, flag
   `seed_imported_v1`. If the flag is set and the tables are non-empty,
   the import is a no-op. If the flag is set but the tables are empty
   (data cleared), it imports again.
3. Read: `assets/seed/vocabulary_seed.json` via an injected
   `() -> InputStream?` provider (production: the asset stream; tests: a
   test-resource stream). A missing asset yields `SeedState.Failed`,
   never a crash.
4. Insert in ONE `withTransaction` block (all-or-nothing): words
   (`INSERT OR IGNORE`), the 14 categories (upsert), translations
   (`ne` + `en` per word, REPLACE), examples (delete + re-insert per
   word), relations resolved by a korean → id lookup built AFTER the
   words are inserted. Unresolvable relation targets are skipped and
   counted (`relationsSkipped`); a bad single entry is skipped and
   counted (`errors`) without aborting the import.
5. Idempotent: re-running produces no duplicates (IGNORE inserts,
   REPLACE translations, delete + re-insert for relations/examples).
6. Failure behaviour: empty/malformed JSON is caught, logged and
   reported as `SeedState.Failed` via `SeedImporter.state`
   (`StateFlow<SeedState>`: `Idle → Importing → Success | Failed`).
   The guard flag is set ONLY after success, so a later launch retries.
   The app keeps working with whatever data exists in the meantime.

Verified against the real seed: 236 words, 472 translations, 236 examples,
80 relations resolved (12 similar + 26 opposite + 42 confusing,
0 skipped), 14 categories.

## 3. SRS algorithm (SM-2-lite)

Implemented in `data/repository/SrsScheduler.kt`, applied by
`VocabularyRepositoryImpl.recordReview` / `setLearningState`.

- **Correct recall:** `repetitions += 1`; interval by repetitions:
  1 → **1 day**, 2 → **3**, 3 → **7**, 4 → **15**, 5+ → **30** (capped).
  State becomes `KNOWN`, or `MASTERED` at 5 consecutive correct recalls.
  `easiness += 0.1` (capped at 2.5). `nextReview = today + interval`.
- **Wrong answer:** `interval = 1`, `repetitions = 0`,
  `wrongCount += 1`, state becomes `DIFFICULT`. `easiness −= 0.2`
  (floor 1.3). `nextReview = tomorrow`.
- **Direct state change:** marking `DIFFICULT` always forces
  `nextReview = tomorrow`, even from a long interval; marking
  `MASTERED` sets the 30-day interval. Other states keep the schedule.
- **Revision queue** (`getRevisionQueue(today)`): progress rows with
  `nextReviewEpochDay <= today` and state != `MASTERED`, ordered by
  `wrongCount DESC, nextReviewEpochDay ASC`, followed by up to
  `DAILY_NEW_WORD_GOAL` (10) brand-new words (no progress row) that are
  not already due.
- **Activity accounting:** `recordReview` adds 1 to `wordsReviewed` for
  the day, and 1 to `wordsLearned` when a `NEW` word is recalled
  correctly for the first time.
- **Streak:** consecutive study days ending today — or ending yesterday
  when the user has not studied yet today (the streak is not broken
  until a full day passes with no activity). Any `daily_activity` row
  counts, including quiz-only (0, 0) rows.
- **Epoch days:** days since 1970-01-01 UTC
  (`DateUtils.epochDay()` / `startOfDayMillis()`).

`easiness` is stored for future tuning; the interval ladder above — not
easiness — drives scheduling in this -lite variant.

## 4. Search matching rules

`VocabularyRepository.search(query)` / `VocabularyDao.search`:

- Blank query → an empty `Flow` (never the whole table; never an emission).
- Partial matching via SQL `LIKE` (`%query%`) on **four** fields:
  `korean`, `nepali`, `english`, `romanization`, **plus**
  `vocabulary_translations.meaning` (the normalized table is genuinely
  searched).
- LIKE wildcards in the query (`\`, `%`, `_`) are escaped, so they match
  literally.
- Ordering: **exact match first** (`korean`/`nepali`/`english`/
  `romanization` equal to the query), then EPS importance
  (`high` → `medium` → `low`), then Korean spelling ascending.
  Exact beats importance: an exact low-importance hit outranks a
  partial high-importance hit.
- Search is substring-based, not token-based: `안녕` matches `안녕하세요`;
  `annyeong` matches romanization `annyeonghaseyo`; `धन्य` matches
  Nepali `धन्यवाद`. There is no FTS table; for 236+ rows LIKE is fast
  enough and keeps the schema simple. (If the word list grows past a few
  thousand, migrate to FTS4 — see §5.)

## 5. Migration policy

- **Never destructive.** `fallbackToDestructiveMigration()` is NOT used
  anywhere: wiping the database would destroy learning progress,
  favorites and quiz history.
- Schema changes ship as **versioned `Migration` objects** (version 1 →
  2 → …), added to the `Room.databaseBuilder` in `DatabaseModule`.
  Additive changes only where possible (`ALTER TABLE … ADD COLUMN`
  with a default); renames go through a table-copy migration.
- Schema export is ON (`exportSchema = true`); generated schemas must
  be committed under `app/schemas/` — the Gradle module must set
  `room.schemaLocation` (`arguments += ["room.schemaLocation":
  "$projectDir/schemas"]`) or Room warns at build time.
- Migration stub plan for v1 → v2 (to be implemented when the first
  schema change lands): create `data/local/Migrations.kt` with
  `val MIGRATION_1_2 = object : Migration(1, 2) { … }`, cover it with a
  `MigrationTest` (Room `MigrationTestHelper`), and register it in
  `DatabaseModule` via `.addMigrations(MIGRATION_1_2)`.
- Type converters: `Converters` (JSON arrays for `List<String>` /
  `List<Int>`, via `org.json` — Android framework, no extra dependency).
  Seed/export JSON parsing also uses `org.json` only, so the data layer
  adds no serialization dependency. Room 2.6.1 + KSP is assumed
  (foundation-owned Gradle).

## 6. Export / import format

`exportAll()` returns one JSON object:
`{format: "hangulmate-export", version: 1, exportedEpochMillis, words: [...],
progress: [...], favorites: [...]}`. `words` uses the seed JSON shape
(id, korean, nepali, english, romanization, syllables, partOfSpeech,
topic, difficulty, epsImportance, exampleKo/Ne/En, similar/opposite/
confusing, tags) so an export can be re-imported anywhere. `importJson`
inserts with IGNORE (existing id → `skipped`), resolves relations by
Korean spelling, restores progress/favorites best-effort, and returns
`ImportReport(added, skipped, errors, errorMessages)` (first 10 messages).
User-created words get ids `HM-U<epochMillis>` when the caller leaves
the id blank, and `isUserCreated = true`.

## 7. Integration checklist (for the coordinator / integration agent)

- [ ] Gradle: Room 2.6.1 (runtime + ktx), KSP `room-compiler`, Hilt,
  `kotlinx-coroutines-android`; test: JUnit4, `androidx.test:core`
  (ApplicationProvider), `kotlinx-coroutines-test`.
- [ ] `room.schemaLocation` set to `$projectDir/schemas`.
- [ ] `HangulMateApp.onCreate` injects `SeedOnFirstLaunch` (see §2.1).
- [ ] Run `./gradlew testDebugUnitTest` — RoomTest (7), SearchTest (8),
  SeedImportTest (14). SeedImportTest reads the real seed from
  `app/src/test/resources/seed.json` (a copy of the asset, kept in sync
  by hand when the seed changes).
- [ ] The manifest must declare the `HangulMateApp` Application class
  (foundation-owned).
- [ ] Do NOT reference `BrandConfig` / `TtsManager` / `SettingsRepository`
  from the data layer — it references none of them.
