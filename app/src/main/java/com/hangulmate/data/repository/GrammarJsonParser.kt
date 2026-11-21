package com.hangulmate.data.repository

import com.hangulmate.domain.model.ExampleSource
import com.hangulmate.domain.model.GrammarExample
import com.hangulmate.domain.model.GrammarPoint
import com.hangulmate.domain.model.PersonRule
import com.hangulmate.domain.model.TenseRule
import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure parser for `assets/seed/grammar_seed.json` (v0.2).
 *
 * Uses org.json like SeedImporter — no extra dependency — and never
 * crashes on malformed input: an entry missing its required fields
 * (id / form) is skipped and counted instead of aborting the whole file.
 * An unknown `tense_past` / `person` value maps to NOT_STATED (the honest
 * reading: no rule is known), never to an invented restriction.
 *
 * Kept separate from the repository so unit tests can feed it the real
 * seed file (or malformed input) as a string, with no asset access.
 */
object GrammarJsonParser {

    data class ParsedGrammar(val points: List<GrammarPoint>, val skipped: Int)

    fun parse(json: String): ParsedGrammar {
        val root = JSONObject(json)
        val entries = root.optJSONArray("entries") ?: return ParsedGrammar(emptyList(), 0)
        val points = mutableListOf<GrammarPoint>()
        var skipped = 0
        for (i in 0 until entries.length()) {
            val obj = entries.optJSONObject(i)
            val point = obj?.let { parsePoint(it) }
            if (point == null) skipped++ else points += point
        }
        return ParsedGrammar(points, skipped)
    }

    private fun parsePoint(obj: JSONObject): GrammarPoint? {
        val id = obj.optString("id").trim()
        val form = obj.optString("form").trim()
        if (id.isEmpty() || form.isEmpty()) return null
        return GrammarPoint(
            id = id,
            book = obj.optInt("book", 1),
            unit = obj.optInt("unit", 0),
            unitTitle = obj.optString("unit_title"),
            unitTitleEn = obj.optString("unit_title_en"),
            form = form,
            structure = obj.optString("structure"),
            meaningKo = obj.optString("meaning_ko"),
            meaningEn = obj.optString("meaning_en"),
            meaningNe = obj.optString("meaning_ne"),
            tensePast = parseTense(obj.optString("tense_past")),
            tenseNote = obj.optString("tense_note"),
            person = parsePerson(obj.optString("person")),
            personNote = obj.optString("person_note"),
            examples = parseExamples(obj.optJSONArray("examples")),
            mistakes = obj.optString("mistakes"),
            sourcePage = obj.optInt("source_page", 0),
        )
    }

    private fun parseTense(raw: String): TenseRule = when (raw.trim().lowercase()) {
        "yes" -> TenseRule.YES
        "no" -> TenseRule.NO
        "na" -> TenseRule.NA
        else -> TenseRule.NOT_STATED
    }

    private fun parsePerson(raw: String): PersonRule = when (raw.trim().lowercase()) {
        "all" -> PersonRule.ALL
        "1st_only" -> PersonRule.FIRST_ONLY
        "2nd_only" -> PersonRule.SECOND_ONLY
        "1st_2nd" -> PersonRule.FIRST_SECOND
        "2nd_3rd" -> PersonRule.SECOND_THIRD
        else -> PersonRule.NOT_STATED
    }

    private fun parseExamples(array: JSONArray?): List<GrammarExample> {
        if (array == null) return emptyList()
        val out = mutableListOf<GrammarExample>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val ko = obj.optString("ko").trim()
            if (ko.isEmpty()) continue
            out += GrammarExample(
                ko = ko,
                ne = obj.optString("ne"),
                en = obj.optString("en"),
                source = if (obj.optString("source") == "book") {
                    ExampleSource.BOOK
                } else {
                    ExampleSource.AUTHORED
                },
            )
        }
        return out
    }
}
