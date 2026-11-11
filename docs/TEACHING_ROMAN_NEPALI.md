# HangulMate — Sikne Guide (Simple Roman Nepali)

Yo document Byte ko lagi ho — Android developer banna khojne beginner ko
lagi. Yaha technical kura simple Roman Nepali ma explain gariyeko cha.
Theory dherai chaina; ke baneko cha, kina baneko cha, kun file ma cha,
kasari kaam garcha, ra kasari test garne — yahi kura matra.

> Note: Yo guide app ko v2 state (2026-11-11) anusar lekheko ho: 10,000
> words, Room v2, workflow `android-build.yml`. Pahila ko version ko kura
> farak cha bhane README/ARCHITECTURE hernus.

---

## 0. App k ho? (What are we building?)

**HangulMate** — Nepali student le Korean (EPS-TOPIK exam) sikaune app ho.

- **Ke garcha?** Korean word ko Nepali ra English meaning dekhauncha,
  camera le Korean text padhcha, object chinchha, quiz lincha, ra
  revision schedule garcha.
- **Kina offline?** Student ko phone ma internet huna pani sakcha, na
  huna pani. App airplane mode ma pani chalna parcha — sabai data phone
  bhitra huncha.
- **Kun bhasha?** Interface Nepali (default), English option cha.
- **Test kasari?** GitHub Actions le APK banauchha; phone ma install
  garera test garnus. (Herne: Section 16.)

---

## 1. Room Database — Phone bhitra ko local database

**Room vaneko mobile bhitrai vocabulary save garne local database ho.**

- Kina chahiyo? 10,000 words, timro progress, favorite, revision schedule
  — sabai phone bhitra save garnu parcha. Internet chaina bhane pani
  app chalna parcha.
- Kun files? `app/src/main/java/com/hangulmate/data/local/`
  - `Entities.kt` — table haru ko structure
  - `Daos.kt` — database sanga kura garne interface
  - `HangulMateDatabase.kt` — database ko main class (version 2)
  - `SeedImporter.kt` — pahilo choti 10,000 words import garne code
- Kasari kaam garcha? App first time kholda `SeedImporter` le
  `assets/seed/vocabulary_seed.json` bata words padhera Room ma halcha.
  Ek choti matra — `seed_imported_v2` flag le feri import huna dindaina.
  Pachi app le words Room bata matra padhcha (JSON bata hoina).
- Ke bujhne? Room = table haru; jastai Excel sheet — euta sheet words
  ko, arko sheet timro progress ko.
- Test kasari? App kholda Home ma "10,000 words" jati count dekhaune?
  Dictionary ma `사랑` search garnus — result aunu parcha. Airplane
  mode ma pani test garnus.

## 2. Entity — Euta word ko complete record

**Entity vaneko euta Korean word ko complete record (table ko euta row) ho.**

- Example: `VocabularyEntity` — id, korean, nepali, english,
  romanization, partOfSpeech, topic, difficulty, epsImportance, examples,
  `nepaliStatus` (verified / missing), senses.
- `nepaliStatus` ke ho? 10,000 ma 851 words ko Nepali verified cha;
  aru 9,149 ko Nepali chhaina — tini haru ko `nepali` column ma English
  gloss rakheko cha (khali na hos bhanera). App le detail screen ma
  "Nepali translation not ready yet" bhanera dekhaunchha.
- Kun file? `data/local/Entities.kt`
- Ke bujhne? Entity = euta word ko form — jastai euta student ko record
  (naam, class, roll no).

## 3. DAO — Database bata data line/dine interface

**DAO vaneko database bata word search garne ra data insert/update garne
interface ho.**

- Example: `VocabularyDao.search(...)` — Korean/Nepali/English ma search
  garcha (SQL LIKE use garera). `LearningDao` — progress save garcha.
- Kun file? `data/local/Daos.kt`
- Kasari kaam garcha? Tapai le DAO lai " '사랑' khojideu" bhanda, usle
  Room bata result dincha. Aafai SQL lekhnu pardaina — Room le garidincha.
- Ke bujhne? DAO = librarian. Tapai le kitab magnu huncha, usle
  library bata nikalera dincha.

## 4. Repository — Database ra app ko bich ko layer

**Repository vaneko database ra app ko aru layer bich data manage garne
layer ho.**

- Example: `VocabularyRepository` — search, word detail, favorites,
  collections. `LearningRepository` — progress, streak, quiz results.
- Kun files? `domain/repository/` (interface — "ke garne" bhancha) ra
  `data/repository/` (implementation — "kasari garne" bhancha).
- Kasari kaam garcha? Screen (UI) le sidhai DAO boldaina; Repository
  sanga bolcha. Repository le DAO, DataStore, backup — sabai milaera
  data dincha.
- Ke bujhne? Repository = manager. Waiter (UI) le customer sanga bolcha,
  manager (Repository) le kitchen (Database) sanga.

## 5. MVVM — UI, data ra logic separate rakhne architecture

**MVVM vaneko UI, data ra logic lai separate rakhne architecture ho.**

- **Model** — data (Room, Repository).
- **View** — screen (Compose UI) — dekhaune matra; logic rakhdaina.
- **ViewModel** — bich ko dimag: data lyaune, state hold garne, UI lai
  dine. Example: `HomeViewModel`, `DictionaryViewModel`.
- Kina? Euta thau bigriyo bhane aru thau nabigros; test garna sajilo.
- Kun files? `ui/home/HomeViewModel.kt` jastai — harek screen ko aftno
  ViewModel cha.
- Ke bujhne? ViewModel = TV ko remote: tapai button thichnu huncha
  (View), remote le TV (Model) lai signal pathaunchha.

## 6. Jetpack Compose + Material 3 — Screen banaune tarika

**Compose vaneko Android ko naya UI banaune system ho.**

- Purano system (XML layout) ko satta Kotlin code le UI banchha.
- Material 3 = Google ko design system (color, shape, button style).
- Kun files? `ui/theme/` (color, font, shape, spacing) ra `ui/*/` screen haru.
- Nepali + Hangul rendering: koi custom font bundle gareko chaina —
  system font le Devanagari ra Hangul duitai render garcha. Latin-only
  font halyo bhane Nepali bigranchha, tesaile kahilyai nahalnus.
- Light/dark theme: Profile → Theme bata change garna milcha.
- Test kasari? App kholera light/dark switch garnus; Nepali text ra
  Korean text ramrari dekhincha ki check garnus.

## 7. Hilt (Dependency Injection) — Chahine kura aafai dine system

**Hilt vaneko class lai chahine kura (database, repository) aafai banaera
dine system ho.**

- Tapai le `VocabularyRepository` banayera ViewModel lai dinu pardaina —
  Hilt le aafai dincha.
- Kun files? `di/AppModule.kt`, `di/DatabaseModule.kt`
- Ke bujhne? Jastai hotel: tapai le room magnu huncha, hotel le key,
  towel, sabai aafai dincha. Tapai le aafai banaunu pardaina.

## 8. Navigation — Screen to screen jaane system

**Navigation Compose vaneko euta screen bata arko screen ma jaane
system ho.**

- Bottom bar: Home, Dictionary, Camera, Practice, Profile (5 tab).
- Route haru: `ui/navigation/Routes.kt` ma cha (dictionary, word_detail,
  camera, practice, profile, collections, add_word).
- Test kasari? Har tab tap garnus; word tap garda detail khulcha ki
  check garnus; back button le aghillo screen ma laijanchha ki hernus.

## 9. DataStore — Small settings save garne thau

**DataStore vaneko small setting (theme, language, daily goal, TTS
speed) save garne thau ho.**

- Room bhanda sano kura ko lagi use huncha — settings jastai.
- Kun file? `data/preferences/SettingsRepository.kt`
- Example: daily goal 10 set garyo bhane, app banda garera kholda
  pani 10 nai rahanchha.

## 10. Seed JSON + Import — 10,000 words kasari aayo?

**Seed JSON vaneko app sanga bundle gariyeko words ko file ho.**

- File: `app/src/main/assets/seed/vocabulary_seed.json` (10,000 entries,
  8.75 MB). Schema: `schema.json`.
- Kata bata aayo? NIKL Basic Korean Dictionary (한국어기초사전) — 53,172
  rows bata 10,000 select gariyeko. Pipeline: `tools/nikl_pipeline.py`.
- Full report: `data/reports/dataset_report.md`
- Nepali: 851 words ko Nepali verified cha; 9,149 ko chhaina — tini
  haru ma English dekhaunchha, ra "Nepali translation not ready yet"
  bhanera lekhcha. Fake Nepali kahilyai rakheko chaina.
- Licence: NIKL ko data **CC BY-SA 2.0 KR** ho (attribution + ShareAlike).
  Hugging Face ko `mit` tag parser ko code ko lagi matra ho — data ko
  lagi hoina. Credit Profile → About ma cha.
- Test kasari? App first time kholda import huna kehi second lagcha;
  Home ma total words 10,000 dekhaunu parcha. Second time kholda feri
  import huna hunna.

## 11. Spaced Repetition (SRS) — Birsine bela ma feri revision

**SRS vaneko birsine bela ma word feri dekhaune revision system ho.**

- Kasari? Timile word review garyo bhane next review ko date milcha:
  1 din → 3 din → 7 din → 15 din → 30 din (SM-2-lite ladder).
- Birsyo bhane (I forgot) feri 1 din ma aauncha, ra word DIFFICULT
  bhanera mark huncha.
- 5 choti lagatar correct bhayo bhane MASTERED huncha.
- Kun files? `data/repository/SrsScheduler.kt`,
  `ui/practice/RevisionScreen.kt`
- Button haru: "I know this" / "I need practice" / "I forgot this".
- Test kasari? Practice → Revision kholnuhos; word review garnus;
  bholipalta due list ma change dekhincha ki hernus.

## 12. Quiz — Afno knowledge test garne

**Quiz vaneko local words bata baneko test ho.**

- Types: Korean → Nepali, Nepali → Korean, Listening (TTS), Hangul
  spelling, Fill in the blank, Similar/Opposite, timed EPS mock.
- Image quiz DISABLED cha — image assets chaina, tesaile honestly
  disabled dekhaunchha (fake image quiz chaina).
- **Important:** Nepali chaiyne quiz (KO→NE, NE→KO, Listening, Spelling)
  ma verified Nepali bhayeko words matra use huncha — natra English
  lai Nepali bhanera sodhincha, jo galat huncha.
- Kun file? `domain/practice/QuizGenerator.kt`,
  `ui/practice/QuizScreen.kt`, `QuizHistoryScreen.kt`
- Test kasari? Practice → Quiz khelnus; result screen ma score,
  correct answers, ra wrong words "difficult" ma add garna milcha ki
  hernus. History screen ma purana results dekhincha ki check garnus.

## 13. Camera — Photo bata Korean padhne ra chinne

**Camera ma dui mode cha:**

### Mode 1: Scan Text / Translate
- CameraX le photo lincha, ML Kit le Korean text padhcha (OCR).
- **OCR model phone bhitra nai cha (bundled)** — download chahinna,
  internet chahinna. `OcrService.warmUp()` le Camera kholda model
  ready garcha.
- Text padhepachi: tap garera word ko detail kholna milcha; word-by-word
  Nepali breakdown dekhaunchha. **Full sentence translation chaina** —
  app le testo claim gardaina; word-by-word matra ho, ra UI ma spashta
  lekheko cha.
- Unknown word cha bhane Save garera afno word list ma halna milcha
  (Add Word screen khulcha, Nepali tapai le lekhne).
- Kun files? `ui/camera/*`, `ml/OcrService.kt`,
  `ml/TranslationBreakdown.kt`, `ml/OcrOverlay.kt` (bounding boxes).
- Test kasari? Korean textbook ko page photo khichnus; Hangul text
  extract bhayo ki hernus; word tap garda detail khulcha ki check garnus.

### Mode 2: Scan Object / Visual Dictionary
- TensorFlow Lite (TFLite) le photo ko object chinchha — model phone
  bhitra cha (`assets/ml/mobilenet_v1_1.0_224_quant.tflite`).
- Top 5 guesses confidence % sanga dekhaunchha; 269 label mappings
  bata Korean word nikalchha. Confidence low bhayo bhane "Could not
  identify" bhancha — fake guess gardaina.
- Manual correction: tapai le aafai search गरेर correct object choose
  garna milcha.
- Kun files? `ml/TfLiteClassifier.kt`, `ml/LabelMapper.kt`,
  `assets/ml/labels.txt`, `assets/ml/label_aliases.json`
- Test kasari? Kera, kitab, kursi jastai common object scan garnus;
  Korean naam + Nepali meaning aayo ki hernus.

## 14. TTS (Text-to-Speech) — Korean pronunciation sunaune

**TTS vaneko phone le Korean word bolera sunaune system ho.**

- Android ko built-in `TextToSpeech`, `Locale.KOREAN` use garcha.
- Korean voice phone ma install chaina bhane app le "voice missing"
  bhanera imandari sanga dekhauchha — fake audio bajdaina.
- Speed/pitch Profile ma change garna milcha; app restart garepachi
  pani saved value use huncha (pahila bug thiyo — fix bhayo).
- Kun file? `speech/TtsManager.kt`
- Test kasari? Kunai word ko speaker button thichnus; Korean voice
  ayena bhane Profile → "Test voice" bata check garnus.

## 15. Backup / Restore — Data harauna nadine

**Backup vaneko timro words + progress + favorites JSON file ma save
garne ho.**

- Profile → Export bata file save garnus; Import bata feri halna milcha.
- Sabai local ho — cloud chaina, account chaina.
- Kun file? `data/backup/BackupManager.kt`
- Test kasari? Export garnus, app data clear garnus (wa reset), ani
  Import garnus — progress farkecha ki hernus.

## 16. WorkManager (Reminders) — Daily padhne reminder

**WorkManager vaneko daily notification schedule garne system ho.**

- Profile ma reminder on garera time set garnus — daily notification aauncha.
- Phone restart garepachi pani reminder chalcha (`BootReceiver` le feri
  schedule garcha).
- Kun files? `work/ReminderWorker.kt`, `work/ReminderScheduler.kt`,
  `work/BootReceiver.kt`
- Test kasari? Reminder 1 minute pachi set garera notification aayo ki
  hernus (exact timing Android le manage garcha).

## 17. GitHub Actions — GitHub le aafai APK banaune system

**GitHub Actions vaneko GitHub le aafai code compile garera APK banaune
system ho.**

- Kina chahiyo? Tapai ko PC ma Android Studio install garnu pardaina;
  GitHub le build garisakcha, tapai le APK download matra garnus.
- Workflow file: `.github/workflows/android-build.yml`
  (repo ma ahile `android.yml` cha — improved version web UI bata
  replace garna milcha).
- Steps: code checkout → JDK 17 → Android SDK → unit tests → debug APK
  build → APK artifact upload.

### APK kasari download garne? (Step by step)

1. GitHub kholnuhos: https://github.com/Sudip-pandey/HangulMate
2. Mathi ko **Actions** tab click garnus.
3. Latest workflow run (sabai bhanda mathi) click garnus — green
   tick ✅ bhayo bhane success ho; red ❌ bhayo bhane fail.
4. Page ko tala **Artifacts** section ma `hangulmate-debug-apk` click
   garnus — ZIP download huncha.
5. ZIP extract garnus — bhitra `app-debug.apk` huncha.
6. Phone ma pathaunus (USB / WhatsApp / Drive) ra tap garera install
   garnus. "Unknown sources / Install unknown apps" allow garnu parcha
   bhane allow garnus.

### Build fail bhayo bhane ke garne?

- Actions run kholera **build** job click garnus; rato step ma error
  message huncha. Screenshot malaai pathaunus — ma fix garera push
  garidinchu.
- Common issue: `gradlew` executable chaina bhane exit code 126 aauncha.
  Fix: computer ma `git update-index --chmod=+x gradlew` run garera
  commit + push garnus.

## 18. Collections ra Favorites

- **Favorite:** kunai word ko star thichnus — favorite list ma bascha.
- **Collection:** afno group banaunus (e.g. "Exam words") — word detail
  bata "Add to collection" garera halna milcha.
- Kun files? `ui/collections/*`, `domain/repository/CollectionRepository`
- Test kasari? Word detail bata collection ma halnus; Collections tab ma
  count badheko dekhincha ki hernus.

## 19. Dataset ra Licence — Data kata bata aayo?

- Words NIKL Basic Korean Dictionary bata aayeko ho (53,172 rows bata
  10,000 select).
- **Licence: CC BY-SA 2.0 KR** — credit dinu parcha (app ma cha) ra
  derived data pani same licence ma share garnu parcha. MIT hoina.
- Nepali translation: 851 verified; baki ko lagi English fallback.
  Fake Nepali kahilyai haleko chaina.
- Full details: `data/reports/dataset_report.md`

## 20. Sano shabdakosh (Glossary)

| Term | Simple meaning |
|---|---|
| Room | Phone bhitra ko local database |
| Entity | Euta record (euta word ko full details) |
| DAO | Database sanga kura garne interface |
| Repository | Data manage garne layer (DB ra UI ko bich) |
| MVVM | UI / logic / data separate rakhne design |
| Compose | Kotlin code le UI banaune system |
| Hilt | Chahine kura aafai dine system |
| DataStore | Small settings save garne thau |
| SRS | Birsine bela ma revision garni system |
| TTS | Text lai voice ma bolne system |
| OCR | Photo ko text padhne technology |
| TFLite | Phone bhitra AI model chalaune system |
| APK | Android app install garne file |
| CI (GitHub Actions) | GitHub le aafai build garne system |
| Gradle | App compile garne build tool |

## 21. Kehi bigriyo bhane

1. App crash bhayo? Ke garda crash bhayo, kun screen ma — screenshot
   sahit malaai bhannus.
2. Word chaina? Dictionary ma search garnus; chaina bhane "Add word"
   bata aafai add garna milcha.
3. Camera khulena? App permissions ma Camera allow garnus.
4. Korean voice aayena? Phone Settings → Language → Text-to-speech →
   Korean voice download garnus.
