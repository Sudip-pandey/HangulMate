package com.hangulmate.data

import com.hangulmate.data.repository.GrammarJsonParser
import com.hangulmate.domain.model.ExampleSource
import com.hangulmate.domain.model.PersonRule
import com.hangulmate.domain.model.TenseRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Grammar-seed parser tests, fed by the REAL seed file copied to
 * `app/src/test/resources/grammar_seed.json` (hermetic — no asset
 * access), mirroring [SeedImportTest]. Guards the v0.2 shape: 120
 * points, 60 per book, and the honest NOT_STATED mapping for rules the
 * book does not state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GrammarParserTest {

    private fun realSeedJson(): String =
        javaClass.classLoader.getResourceAsStream("grammar_seed.json")!!
            .bufferedReader().readText()

    @Test
    fun realSeed_parsesAll120Points() = runTest {
        val parsed = GrammarJsonParser.parse(realSeedJson())

        assertEquals(120, parsed.points.size)
        assertEquals(0, parsed.skipped)
        assertEquals(60, parsed.points.count { it.book == 1 })
        assertEquals(60, parsed.points.count { it.book == 2 })
        assertEquals(120, parsed.points.map { it.id }.toSet().size)
        assertTrue(parsed.points.all { it.examples.isNotEmpty() })
    }

    @Test
    fun realSeed_firstPointMatchesBook() = runTest {
        val first = GrammarJsonParser.parse(realSeedJson()).points.first()

        assertEquals("G-B1-U01-01", first.id)
        assertEquals(1, first.book)
        assertEquals(1, first.unit)
        assertEquals("입니다 / 입니까?", first.form)
        assertEquals(TenseRule.YES, first.tensePast)
        assertEquals(PersonRule.ALL, first.person)
        assertEquals(3, first.examples.size)
        assertEquals(ExampleSource.AUTHORED, first.examples.first().source)
        assertTrue(first.meaningNe.isNotBlank())
    }

    @Test
    fun realSeed_notStatedRulesStayNotStated() = runTest {
        val points = GrammarJsonParser.parse(realSeedJson()).points

        val silentTense = points.first { it.id == "G-B1-U01-02" }
        assertEquals(TenseRule.NOT_STATED, silentTense.tensePast)

        val silentPerson = points.first { it.id == "G-B1-U17-02" }
        assertEquals(PersonRule.NOT_STATED, silentPerson.person)
    }

    @Test
    fun realSeed_bookExamplesKeepTheirSource() = runTest {
        val points = GrammarJsonParser.parse(realSeedJson()).points
        val withBookExample = points.first { it.id == "G-B1-U22-02" }

        assertTrue(withBookExample.examples.any { it.source == ExampleSource.BOOK })
    }

    @Test
    fun emptyObject_parsesToZeroPoints() = runTest {
        val parsed = GrammarJsonParser.parse("{}")

        assertEquals(0, parsed.points.size)
        assertEquals(0, parsed.skipped)
    }

    @Test
    fun malformedEntriesAreSkippedNotFatal() = runTest {
        val json = """
            {"entries": [
              {"id": "", "form": "입니다"},
              {"form": "-아/어요"},
              {"id": "G-TEST-01", "form": "-고 싶다", "book": 1, "unit": 5,
               "tense_past": "maybe", "person": "everyone",
               "examples": [{"ko": "가고 싶어요.", "source": "book"}]}
            ]}
        """.trimIndent()
        val parsed = GrammarJsonParser.parse(json)

        assertEquals(1, parsed.points.size)
        assertEquals(2, parsed.skipped)
        val point = parsed.points.single()
        assertEquals("G-TEST-01", point.id)
        // Unknown rule values fall back to NOT_STATED, never to an invention.
        assertEquals(TenseRule.NOT_STATED, point.tensePast)
        assertEquals(PersonRule.NOT_STATED, point.person)
        assertEquals(ExampleSource.BOOK, point.examples.single().source)
        assertNull(parsed.points.singleOrNull { it.id == "" })
    }
}
