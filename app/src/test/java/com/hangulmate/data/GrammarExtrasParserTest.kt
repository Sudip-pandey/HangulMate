package com.hangulmate.data

import com.hangulmate.data.repository.GrammarJsonParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * KGIU-extras parser tests, fed by the REAL extras file copied to
 * `app/src/test/resources/grammar_seed_extras.json` (hermetic — no asset
 * access), mirroring [GrammarParserTest].
 *
 * Guards the extras shape: 37 KGIU sections not in the EPS 120, all
 * book 3, each with researched key_points including the follows-matrix
 * point ("पछिको वाक्य: ..."). The merged 157-entry seed is covered by
 * [GrammarParserTest] after `merge_extras_into_v03.py` runs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GrammarExtrasParserTest {

    private fun realExtrasJson(): String =
        javaClass.classLoader.getResourceAsStream("grammar_seed_extras.json")!!
            .bufferedReader().readText()

    @Test
    fun realExtras_parsesAll37Points() = runTest {
        val parsed = GrammarJsonParser.parse(realExtrasJson())

        assertEquals(37, parsed.points.size)
        assertEquals(0, parsed.skipped)
        assertEquals(37, parsed.points.count { it.book == 3 })
        assertEquals(0, parsed.points.count { it.book == 1 || it.book == 2 })
        assertEquals(37, parsed.points.map { it.id }.toSet().size)
        assertTrue(parsed.points.all { it.examples.isNotEmpty() })
        assertTrue(parsed.points.all { it.keyPoints.isNotEmpty() })
    }

    @Test
    fun realExtras_everyPointHasFollowsMatrix() = runTest {
        val parsed = GrammarJsonParser.parse(realExtrasJson())

        assertTrue(
            parsed.points.all { point ->
                point.keyPoints.any { it.ne.startsWith("पछिको वाक्य:") }
            },
        )
    }

    @Test
    fun realExtras_firstPointMatchesKgiuIntro() = runTest {
        val first = GrammarJsonParser.parse(realExtrasJson()).points.first()

        assertEquals("G-KG-U00-01", first.id)
        assertEquals(3, first.book)
        assertEquals(0, first.unit)
        assertTrue(first.form.isNotBlank())
        assertTrue(first.keyPoints.isNotEmpty())
    }

    @Test
    fun realExtras_formsDistinctExceptInaSenses() = runTest {
        val parsed = GrammarJsonParser.parse(realExtrasJson())
        val forms = parsed.points.map { it.form }

        // "(이)나" appears twice by design: U03-15 (choice/approximation)
        // and U03-16 (at least/as many as) are two senses of one form,
        // distinguished by ID. Every other form is unique.
        assertEquals(2, forms.count { it == "(이)나" })
        assertEquals(36, forms.toSet().size)
        val inaIds = parsed.points.filter { it.form == "(이)나" }.map { it.id }
        assertEquals(listOf("G-KG-U03-15", "G-KG-U03-16"), inaIds)
    }
}
