package com.hangulmate.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hangulmate.data.local.HangulMateDatabase
import com.hangulmate.data.local.VocabularyEntity
import com.hangulmate.data.local.VocabularyRelationEntity
import com.hangulmate.data.local.VocabularyTranslationEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Room integration tests on an in-memory database.
 *
 * Required test dependencies (foundation-owned Gradle setup):
 * junit, androidx.test:core (ApplicationProvider), kotlinx-coroutines-test.
 */
class RoomTest {

    private lateinit var db: HangulMateDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HangulMateDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun word(
        id: String, korean: String, nepali: String, english: String,
        romanization: String, topic: String = "general"
    ) = VocabularyEntity(
        id = id, korean = korean, nepali = nepali, english = english,
        romanization = romanization,
        syllablesJson = "[\"$korean\"]",
        partOfSpeech = "noun", topic = topic, difficulty = 1,
        epsImportance = "high",
        exampleKo = "$korean.", exampleNe = "$nepali।", exampleEn = "$english.",
        tagsJson = "[]", isUserCreated = false
    )

    @Test
    fun insertAndGetById_roundTrips() = runTest {
        val dao = db.vocabularyDao()
        dao.insert(word("HM-0001", "안녕하세요", "नमस्ते", "Hello", "annyeonghaseyo"))
        val found = dao.getById("HM-0001")
        assertNotNull(found)
        assertEquals("안녕하세요", found!!.korean)
        assertEquals("नमस्ते", found.nepali)
        assertEquals("annyeonghaseyo", found.romanization)
    }

    @Test
    fun getById_unknownId_returnsNull() = runTest {
        assertNull(db.vocabularyDao().getById("HM-9999"))
    }

    @Test
    fun koreanUniqueIndex_preventsDuplicateInsert() = runTest {
        val dao = db.vocabularyDao()
        dao.insert(word("HM-0001", "안녕하세요", "नमस्ते", "Hello", "annyeonghaseyo"))
        val rowId = dao.insert(
            word("HM-0002", "안녕하세요", "नमस्ते", "Hello", "annyeonghaseyo")
        )
        assertEquals(-1L, rowId)
        assertEquals(1, dao.getTotalCountOnce())
    }

    @Test
    fun search_likeMatchesKoreanAndTranslations() = runTest {
        val dao = db.vocabularyDao()
        dao.insert(word("HM-0001", "안녕하세요", "नमस्ते", "Hello", "annyeonghaseyo"))
        dao.insert(word("HM-0002", "감사합니다", "धन्यवाद", "Thank you", "gamsahamnida"))
        db.translationDao().insertAll(
            listOf(
                VocabularyTranslationEntity(vocabId = "HM-0001", language = "ne", meaning = "नमस्ते"),
                VocabularyTranslationEntity(vocabId = "HM-0001", language = "en", meaning = "Hello"),
                VocabularyTranslationEntity(vocabId = "HM-0002", language = "ne", meaning = "धन्यवाद"),
                VocabularyTranslationEntity(vocabId = "HM-0002", language = "en", meaning = "Thank you")
            )
        )

        val koreanHits = dao.search("%안녕%", "안녕").first()
        assertEquals(listOf("HM-0001"), koreanHits.map { it.id })

        val nepaliHits = dao.search("%धन्य%", "धन्य").first()
        assertEquals(listOf("HM-0002"), nepaliHits.map { it.id })

        val englishHits = dao.search("%Thank%", "Thank").first()
        assertEquals(listOf("HM-0002"), englishHits.map { it.id })
    }

    @Test
    fun relations_resolveByTargetId() = runTest {
        val dao = db.vocabularyDao()
        dao.insert(word("HM-0004", "네", "हो", "Yes", "ne"))
        dao.insert(word("HM-0005", "아니요", "होइन", "No", "aniyo"))
        db.relationDao().insertAll(
            listOf(
                VocabularyRelationEntity(
                    sourceId = "HM-0004", targetId = "HM-0005",
                    targetKorean = "아니요", kind = "OPPOSITE", noteNe = "ने भनेको हो हो।"
                )
            )
        )
        val relations = db.relationDao().getForSourceOnce("HM-0004")
        assertEquals(1, relations.size)
        assertEquals("HM-0005", relations[0].targetId)
        assertEquals("OPPOSITE", relations[0].kind)
        assertEquals("아니요", relations[0].targetKorean)
    }

    @Test
    fun wordOfTheDay_offsetIsDeterministic() = runTest {
        val dao = db.vocabularyDao()
        dao.insert(word("HM-0001", "안녕하세요", "नमस्ते", "Hello", "annyeonghaseyo"))
        dao.insert(word("HM-0002", "감사합니다", "धन्यवाद", "Thank you", "gamsahamnida"))
        dao.insert(word("HM-0003", "네", "हो", "Yes", "ne"))
        // 3 words: day 4 -> offset 1, day 7 -> offset 1 again.
        assertEquals("HM-0002", dao.getByOffset((4 % 3).toInt())!!.id)
        assertEquals("HM-0002", dao.getByOffset((7 % 3).toInt())!!.id)
        assertEquals("HM-0003", dao.getByOffset((5 % 3).toInt())!!.id)
    }

    @Test
    fun progressAndFavorites_roundTrip() = runTest {
        val dao = db.vocabularyDao()
        dao.insert(word("HM-0001", "안녕하세요", "नमस्ते", "Hello", "annyeonghaseyo"))
        db.progressDao().upsert(
            com.hangulmate.data.local.LearningProgressEntity(
                vocabId = "HM-0001", state = "KNOWN",
                nextReviewEpochDay = 20_000L, repetitions = 1, intervalDays = 1
            )
        )
        assertEquals("KNOWN", db.progressDao().getProgress("HM-0001")!!.state)
        assertEquals(1, db.progressDao().getLearnedCount().first())

        db.favoriteDao().insert(
            com.hangulmate.data.local.FavoriteWordEntity(vocabId = "HM-0001", addedEpoch = 123L)
        )
        assertTrue(db.favoriteDao().isFavorite("HM-0001").first())
        assertEquals(listOf("HM-0001"), db.vocabularyDao().getFavorites().first().map { it.id })
    }
}
