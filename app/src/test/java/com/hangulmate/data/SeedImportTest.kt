package com.hangulmate.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hangulmate.data.local.HangulMateDatabase
import com.hangulmate.data.local.SeedImportPrefs
import com.hangulmate.data.local.SeedImporter
import com.hangulmate.data.local.SeedState
import com.hangulmate.data.repository.SrsScheduler
import com.hangulmate.domain.model.LearningState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

/**
 * Seed-import tests, fed by the REAL seed file copied to
 * `app/src/test/resources/seed.json` (hermetic — no asset access).
 * Also covers the SRS schedule and the streak computation as pure logic.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SeedImportTest {

    private lateinit var db: HangulMateDatabase
    private var importedFlag = false
    private val prefs = object : SeedImportPrefs {
        override fun isImported(): Boolean = importedFlag
        override fun markImported() {
            importedFlag = true
        }
    }

    private fun realSeedJson(): String =
        javaClass.classLoader.getResourceAsStream("seed.json")!!.bufferedReader().readText()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HangulMateDatabase::class.java
        ).allowMainThreadQueries().build()
        importedFlag = false
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun realSeed_importsAtLeast200Rows() = runTest {
        val importer = SeedImporter(db, { ByteArrayInputStream(realSeedJson().toByteArray()) }, prefs)
        val state = importer.import()
        assertTrue(state is SeedState.Success)
        val report = (state as SeedState.Success).report
        assertEquals(236, report.wordsImported)
        assertEquals(0, report.wordsSkipped)
        assertEquals(0, report.errors)
        assertEquals(236, db.vocabularyDao().getTotalCountOnce())
        assertTrue(importedFlag)
    }

    @Test
    fun realSeed_populatesTranslationsExamplesAndCategories() = runTest {
        val importer = SeedImporter(db, { ByteArrayInputStream(realSeedJson().toByteArray()) }, prefs)
        importer.import()
        val translations = db.translationDao().getForWord("HM-0001")
        assertEquals(2, translations.size)
        assertTrue(translations.any { it.language == "ne" && it.meaning == "नमस्ते" })
        assertTrue(translations.any { it.language == "en" && it.meaning == "Hello" })
        val examples = db.exampleDao().getForWord("HM-0001")
        assertEquals(1, examples.size)
        assertEquals("안녕하세요, 저는 수딥입니다.", examples[0].ko)
        assertEquals(14, db.categoryDao().getAll().first().size)
        assertEquals("दैनिक जीवन", db.categoryDao().getByTopic("daily")!!.nameNe)
    }

    @Test
    fun realSeed_resolvesAllRelations() = runTest {
        val importer = SeedImporter(db, { ByteArrayInputStream(realSeedJson().toByteArray()) }, prefs)
        val state = importer.import() as SeedState.Success
        // 12 similar + 26 opposite + 42 confusing = 80, all resolvable.
        assertEquals(80, state.report.relationsResolved)
        assertEquals(0, state.report.relationsSkipped)
        assertEquals(80, db.relationDao().count())
        val hm0004 = db.relationDao().getForSourceOnce("HM-0004")
        assertEquals(1, hm0004.size)
        assertEquals("OPPOSITE", hm0004[0].kind)
    }

    @Test
    fun import_isIdempotent_secondRunImportsNothingNew() = runTest {
        val importer = SeedImporter(db, { ByteArrayInputStream(realSeedJson().toByteArray()) }, prefs)
        importer.import()
        importedFlag = false // force a re-import past the guard flag
        val second = importer.import() as SeedState.Success
        assertEquals(0, second.report.wordsImported)
        assertEquals(236, second.report.wordsSkipped)
        assertEquals(236, db.vocabularyDao().getTotalCountOnce())
        assertEquals(80, db.relationDao().count())
        assertEquals(1, db.exampleDao().getForWord("HM-0001").size)
    }

    @Test
    fun importIfNeeded_flagSetButTablesEmpty_stillImports() = runTest {
        importedFlag = true
        SeedImporter(db, { ByteArrayInputStream(realSeedJson().toByteArray()) }, prefs)
            .importIfNeeded()
        // Flag set but tables empty: the importer must still import.
        assertEquals(236, db.vocabularyDao().getTotalCountOnce())
    }

    @Test
    fun malformedJson_reportsFailed_withoutCrashing() = runTest {
        val importer = SeedImporter(db, { ByteArrayInputStream("{not json".toByteArray()) }, prefs)
        val state = importer.import()
        assertTrue(state is SeedState.Failed)
        assertEquals(0, db.vocabularyDao().getTotalCountOnce())
        assertTrue(!importedFlag) // flag stays unset so a later launch retries
    }

    @Test
    fun emptyJson_reportsFailed_withoutCrashing() = runTest {
        val importer = SeedImporter(db, { ByteArrayInputStream("".toByteArray()) }, prefs)
        val state = importer.import()
        assertTrue(state is SeedState.Failed)
        assertEquals(0, db.vocabularyDao().getTotalCountOnce())
    }

    @Test
    fun missingAsset_reportsFailed_withoutCrashing() = runTest {
        val importer = SeedImporter(db, { null }, prefs)
        val state = importer.import()
        assertTrue(state is SeedState.Failed)
    }

    @Test
    fun oneBadEntry_isSkipped_restImports() = runTest {
        val json = """[
            {"id":"HM-9001","korean":"사과","nepali":"स्याउ","english":"Apple",
             "romanization":"sagwa","syllables":["사","과"],"partOfSpeech":"noun",
             "topic":"food","difficulty":1,"epsImportance":"high",
             "exampleKo":"사과를 먹어요.","exampleNe":"स्याउ खान्छु।","exampleEn":"I eat an apple.",
             "similar":[],"opposite":[],"confusing":[],"tags":["food"]},
            {"id":"","korean":"","nepali":"","english":""},
            {"id":"HM-9002","korean":"바나나","nepali":"केरा","english":"Banana",
             "romanization":"banana","syllables":["바","나","나"],"partOfSpeech":"noun",
             "topic":"food","difficulty":1,"epsImportance":"medium",
             "exampleKo":"바나나를 먹어요.","exampleNe":"केरा खान्छु।","exampleEn":"I eat a banana.",
             "similar":[],"opposite":[],"confusing":[],"tags":[]}
        ]"""
        val importer = SeedImporter(db, { ByteArrayInputStream(json.toByteArray()) }, prefs)
        val state = importer.import() as SeedState.Success
        assertEquals(2, state.report.wordsImported)
        assertEquals(1, state.report.errors)
        assertEquals(2, db.vocabularyDao().getTotalCountOnce())
    }

    // -- SRS schedule (pure logic, no database) -------------------------

    @Test
    fun srs_correctRecall_advancesIntervalLadder() {
        assertEquals(1, SrsScheduler.intervalForRepetitions(1))
        assertEquals(3, SrsScheduler.intervalForRepetitions(2))
        assertEquals(7, SrsScheduler.intervalForRepetitions(3))
        assertEquals(15, SrsScheduler.intervalForRepetitions(4))
        assertEquals(30, SrsScheduler.intervalForRepetitions(5))
        assertEquals(30, SrsScheduler.intervalForRepetitions(9))
    }

    @Test
    fun srs_wrongAnswer_resetsIntervalAndMarksDifficult() {
        val before = com.hangulmate.data.local.LearningProgressEntity(
            vocabId = "HM-0001", state = LearningState.KNOWN.name,
            repetitions = 3, intervalDays = 7, nextReviewEpochDay = 20_007L,
            correctCount = 3
        )
        val after = SrsScheduler.applyReview(before, "HM-0001", remembered = false, todayEpochDay = 20_000L)
        assertEquals(LearningState.DIFFICULT.name, after.state)
        assertEquals(1, after.intervalDays)
        assertEquals(0, after.repetitions)
        assertEquals(20_001L, after.nextReviewEpochDay)
        assertEquals(1, after.wrongCount)
        assertEquals(3, after.correctCount)
    }

    @Test
    fun srs_fifthCorrectRecall_mastersWord() {
        val before = com.hangulmate.data.local.LearningProgressEntity(
            vocabId = "HM-0001", state = LearningState.KNOWN.name,
            repetitions = 4, intervalDays = 15, nextReviewEpochDay = 20_015L
        )
        val after = SrsScheduler.applyReview(before, "HM-0001", remembered = true, todayEpochDay = 20_000L)
        assertEquals(LearningState.MASTERED.name, after.state)
        assertEquals(30, after.intervalDays)
        assertEquals(20_030L, after.nextReviewEpochDay)
    }

    @Test
    fun srs_difficultState_forcesReviewTomorrow() {
        val before = com.hangulmate.data.local.LearningProgressEntity(
            vocabId = "HM-0001", state = LearningState.MASTERED.name,
            repetitions = 5, intervalDays = 30, nextReviewEpochDay = 20_030L
        )
        val after = SrsScheduler.applyStateChange(before, "HM-0001", LearningState.DIFFICULT, 20_000L)
        assertEquals(20_001L, after.nextReviewEpochDay)
    }

    @Test
    fun streak_countsConsecutiveDays_endingTodayOrYesterday() {
        val compute = com.hangulmate.data.repository.LearningRepositoryImpl.Companion::computeStreak
        assertEquals(3, compute(setOf(20_000L, 19_999L, 19_998L, 19_990L), 20_000L))
        assertEquals(2, compute(setOf(19_999L, 19_998L, 19_990L), 20_000L)) // no study today yet
        assertEquals(0, compute(setOf(19_998L, 19_990L), 20_000L)) // gap: streak broken
        assertEquals(0, compute(emptySet(), 20_000L))
        assertEquals(1, compute(setOf(20_000L), 20_000L))
    }
}
