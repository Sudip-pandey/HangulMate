package com.hangulmate.data.local

import android.util.Log
import androidx.room.withTransaction
import com.hangulmate.domain.model.Topic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

/** Observable state of the one-time seed import. */
sealed class SeedState {
    data object Idle : SeedState()
    data object Importing : SeedState()
    data class Success(val report: SeedImportReport) : SeedState()
    data class Failed(val message: String) : SeedState()
}

/** Counts produced by one seed-import run. */
data class SeedImportReport(
    val wordsImported: Int,
    val wordsSkipped: Int,
    val translationsInserted: Int,
    val examplesInserted: Int,
    val relationsResolved: Int,
    val relationsSkipped: Int,
    val categoriesInserted: Int,
    val errors: Int,
    /** Seed entries whose Nepali is still missing (English shown instead). */
    val nepaliMissing: Int = 0,
    /** Existing rows refreshed with the seed-v2 columns on re-import. */
    val wordsUpdated: Int = 0
)

/** Persistence for the "seed already imported" guard flag. */
interface SeedImportPrefs {
    fun isImported(): Boolean
    fun markImported()
}

/**
 * One-time importer for `assets/seed/vocabulary_seed.json` (seed v2).
 *
 * - Guarded by [SeedImportPrefs] (SharedPreferences flag
 *   "seed_imported_v2"). A second run is a no-op. Users upgrading from
 *   the v1 seed re-import once: their 236 existing rows are skipped by
 *   IGNORE and refreshed with the v2 columns instead of being
 *   re-inserted, so learning progress, favorites and collections
 *   survive the upgrade.
 * - Inserts in ONE [withTransaction] block, so a failure never leaves a
 *   half-imported database.
 * - Idempotent: re-running an import inserts words with IGNORE, replaces
 *   translations, and deletes + re-inserts each word's relations and
 *   example rows, so no duplicates can appear.
 * - Nepali is nullable in the v2 seed: an entry with
 *   nepaliStatus "missing" stores the English gloss in the `nepali`
 *   column as a display fallback and gets NO "ne" translation row, so
 *   the UI shows English rather than a blank or an invented meaning.
 * - Never crashes on empty or malformed JSON: bad input is caught, logged
 *   and reported as [SeedState.Failed], and the guard flag is left unset
 *   so a later launch can retry.
 * - Relations are resolved by Korean spelling (korean → id lookup built
 *   after the words are inserted). An unresolvable target skips that one
 *   relation and increments [SeedImportReport.relationsSkipped].
 *
 * The JSON source is injected as a [seedJsonProvider] (production: the
 * asset stream; unit tests: a test-resource stream) so the importer is
 * testable on the JVM without Robolectric.
 */
class SeedImporter(
    private val database: HangulMateDatabase,
    private val seedJsonProvider: () -> InputStream?,
    private val prefs: SeedImportPrefs
) {
    private val _state = MutableStateFlow<SeedState>(SeedState.Idle)
    val state: StateFlow<SeedState> = _state.asStateFlow()

    /**
     * Imports the seed only when it has never succeeded before.
     * Returns the resulting state (Success, or Failed with the reason).
     */
    suspend fun importIfNeeded(): SeedState {
        if (prefs.isImported()) {
            val already = _state.value
            if (already is SeedState.Success) return already
            if (database.vocabularyDao().getTotalCountOnce() > 0) {
                val report = SeedImportReport(
                    wordsImported = 0, wordsSkipped = 0, translationsInserted = 0,
                    examplesInserted = 0, relationsResolved = 0, relationsSkipped = 0,
                    categoriesInserted = 0, errors = 0
                )
                _state.value = SeedState.Success(report)
                return _state.value
            }
            // Flag is set but the tables are empty (e.g. data was cleared):
            // fall through and import again.
        }
        return import()
    }

    /** Forces a (re-)import, regardless of the guard flag. */
    suspend fun import(): SeedState {
        _state.value = SeedState.Importing
        val json: String = try {
            val stream = seedJsonProvider()
                ?: run {
                    val failure = SeedState.Failed("Seed asset not found: seed/vocabulary_seed.json")
                    _state.value = failure
                    return failure
                }
            stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read seed JSON", e)
            val failure = SeedState.Failed("Could not read seed file: ${e.message}")
            _state.value = failure
            return failure
        }
        if (json.isBlank()) {
            val failure = SeedState.Failed("Seed file is empty")
            _state.value = failure
            return failure
        }
        return try {
            val report = importFromJsonString(json)
            prefs.markImported()
            val success = SeedState.Success(report)
            _state.value = success
            success
        } catch (e: Exception) {
            Log.e(TAG, "Seed import failed", e)
            val failure = SeedState.Failed("Seed import failed: ${e.message}")
            _state.value = failure
            failure
        }
    }

    /**
     * Parses [json] and inserts it in one transaction. Testable entry
     * point: unit tests feed it the real seed file (or malformed input)
     * as a string, with no Android asset access.
     *
     * @return the import report.
     * @throws org.json.JSONException when the top-level JSON is not an array.
     */
    suspend fun importFromJsonString(json: String): SeedImportReport {
        val parsed = parseSeedJson(json)
        val report = database.withTransaction {
            val vocabDao = database.vocabularyDao()
            val translationDao = database.translationDao()
            val relationDao = database.relationDao()
            val exampleDao = database.exampleDao()
            val categoryDao = database.categoryDao()

            // 1. Words (IGNORE: re-imports skip existing ids). Rows that
            // already exist (e.g. the 236 v1 rows on the v1 -> v2 upgrade)
            // are refreshed with the seed-v2 columns instead: a plain
            // UPDATE, so no FK cascade fires and user data survives.
            val inserted = vocabDao.insertAll(parsed.words)
            val wordsImported = inserted.count { it != -1L }
            val wordsSkipped = parsed.words.size - wordsImported
            var wordsUpdated = 0
            for ((index, rowId) in inserted.withIndex()) {
                if (rowId == -1L) {
                    val word = parsed.words[index]
                    vocabDao.updateSeedFields(word.id, word.nepaliStatus, word.sensesJson)
                    wordsUpdated++
                }
            }

            // 2. Categories: all 14 topics, always populated.
            val categories = Topic.entries.map {
                VocabularyCategoryEntity(it.key, it.nameNe, it.nameEn, it.iconName)
            }
            categoryDao.upsertAll(categories)

            // 3. Translations (ne only when a real Nepali exists; en
            // always) and examples, batched into single inserts so the
            // 10,000-row import stays fast.
            val allTranslations = mutableListOf<VocabularyTranslationEntity>()
            val allExamples = mutableListOf<VocabularyExampleEntity>()
            var nepaliMissing = 0
            for (word in parsed.words) {
                if (word.nepaliStatus == "missing") {
                    nepaliMissing++
                } else {
                    allTranslations += VocabularyTranslationEntity(
                        vocabId = word.id, language = "ne", meaning = word.nepali
                    )
                }
                allTranslations += VocabularyTranslationEntity(
                    vocabId = word.id, language = "en", meaning = word.english
                )
                exampleDao.deleteForVocab(word.id)
                allExamples += VocabularyExampleEntity(
                    vocabId = word.id,
                    ko = word.exampleKo, ne = word.exampleNe, en = word.exampleEn
                )
            }
            if (allTranslations.isNotEmpty()) translationDao.insertAll(allTranslations)
            if (allExamples.isNotEmpty()) exampleDao.insertAll(allExamples)
            val translations = allTranslations.size
            val examples = allExamples.size

            // 4. Relations, resolved by korean -> id lookup built AFTER insert.
            val koreanToId = vocabDao.getKoreanToIdMap()
                .associate { it.korean to it.id }
            var resolved = 0
            var skippedRelations = 0
            for ((sourceId, rawRelations) in parsed.relationsBySource) {
                relationDao.deleteForSource(sourceId)
                val rows = mutableListOf<VocabularyRelationEntity>()
                for (raw in rawRelations) {
                    val targetId = koreanToId[raw.targetKorean]
                    if (targetId == null) {
                        skippedRelations++
                        Log.w(TAG, "Skipping relation: target not found: ${raw.targetKorean} (from $sourceId)")
                    } else {
                        rows += VocabularyRelationEntity(
                            sourceId = sourceId,
                            targetId = targetId,
                            targetKorean = raw.targetKorean,
                            kind = raw.kind,
                            noteNe = raw.noteNe
                        )
                        resolved++
                    }
                }
                if (rows.isNotEmpty()) relationDao.insertAll(rows)
            }

            SeedImportReport(
                wordsImported = wordsImported,
                wordsSkipped = wordsSkipped,
                translationsInserted = translations,
                examplesInserted = examples,
                relationsResolved = resolved,
                relationsSkipped = skippedRelations,
                categoriesInserted = categories.size,
                errors = parsed.errors,
                nepaliMissing = nepaliMissing,
                wordsUpdated = wordsUpdated
            )
        }
        return report
    }

    // ------------------------------------------------------------------
    // Parsing (org.json — Android framework, no extra dependency)
    // ------------------------------------------------------------------

    private data class RawRelation(val targetKorean: String, val kind: String, val noteNe: String)

    private data class ParsedSeed(
        val words: List<VocabularyEntity>,
        val relationsBySource: Map<String, List<RawRelation>>,
        val errors: Int
    )

    /**
     * Parses the seed array. An entry missing its required fields
     * (id / korean / english) is skipped and counted as an error
     * instead of aborting the whole import.
     *
     * Seed v2: `nepali` may be absent/null (nepaliStatus "missing") --
     * the [VocabularyEntity.nepali] column then carries the English
     * gloss as a display fallback. A v1-format entry (nepali present,
     * no nepaliStatus / senses) imports unchanged with status
     * "verified" and an empty senses list.
     */
    private fun parseSeedJson(json: String): ParsedSeed {
        val array = JSONArray(json)
        val words = mutableListOf<VocabularyEntity>()
        val relationsBySource = mutableMapOf<String, MutableList<RawRelation>>()
        var errors = 0
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i)
            if (obj == null) {
                errors++
                continue
            }
            val id = obj.optString("id").trim()
            val korean = obj.optString("korean").trim()
            val nepaliRaw = if (obj.isNull("nepali")) null else obj.optString("nepali").trim()
            val english = obj.optString("english").trim()
            if (id.isEmpty() || korean.isEmpty() || english.isEmpty()) {
                errors++
                continue
            }
            val nepaliStatus = when {
                !nepaliRaw.isNullOrEmpty() -> obj.optString("nepaliStatus", "verified")
                    .trim().lowercase().takeIf { it in setOf("verified", "mt_review") }
                    ?: "verified"
                else -> "missing"
            }
            // Display fallback: the UI reads `nepali` directly, so a
            // missing Nepali shows the English gloss instead of a blank.
            val nepali = nepaliRaw ?: english
            val sensesJson = obj.optJSONArray("senses")?.toString() ?: "[]"
            words += VocabularyEntity(
                id = id,
                korean = korean,
                nepali = nepali,
                english = english,
                romanization = obj.optString("romanization"),
                syllablesJson = obj.optStringArray("syllables").toJsonArray(),
                partOfSpeech = obj.optString("partOfSpeech", "noun"),
                topic = obj.optString("topic", "general"),
                difficulty = obj.optInt("difficulty", 1).coerceIn(1, 3),
                epsImportance = obj.optString("epsImportance", "medium"),
                exampleKo = obj.optString("exampleKo"),
                exampleNe = obj.optString("exampleNe"),
                exampleEn = obj.optString("exampleEn"),
                tagsJson = obj.optStringArray("tags").toJsonArray(),
                isUserCreated = false,
                nepaliStatus = nepaliStatus,
                sensesJson = sensesJson
            )
            val relations = relationsBySource.getOrPut(id) { mutableListOf() }
            for (entry in obj.optJSONArray("similar")?.toRawRelations("SIMILAR").orEmpty()) {
                relations += entry
            }
            for (entry in obj.optJSONArray("opposite")?.toRawRelations("OPPOSITE").orEmpty()) {
                relations += entry
            }
            // Confusing entries carry `differenceNe` instead of `noteNe`.
            obj.optJSONArray("confusing")?.let { confusing ->
                for (j in 0 until confusing.length()) {
                    val rel = confusing.optJSONObject(j) ?: continue
                    val target = rel.optString("korean").trim()
                    if (target.isEmpty()) continue
                    relations += RawRelation(target, "CONFUSING", rel.optString("differenceNe"))
                }
            }
        }
        return ParsedSeed(words, relationsBySource, errors)
    }

    private fun JSONArray.toRawRelations(kind: String): List<RawRelation> {
        val out = mutableListOf<RawRelation>()
        for (i in 0 until length()) {
            val rel = optJSONObject(i) ?: continue
            val target = rel.optString("korean").trim()
            if (target.isEmpty()) continue
            out += RawRelation(target, kind, rel.optString("noteNe"))
        }
        return out
    }

    private fun JSONObject.optStringArray(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return List(array.length()) { i -> array.optString(i) }
    }

    private fun List<String>.toJsonArray(): String {
        val array = JSONArray()
        forEach { array.put(it) }
        return array.toString()
    }

    companion object {
        private const val TAG = "SeedImporter"

        /** SharedPreferences flag name guarding the one-time import. */
        const val PREFS_NAME = "hangulmate_seed_prefs"
        /** Superseded by v2 (seed v1, 236 entries). */
        const val KEY_SEED_IMPORTED_V1 = "seed_imported_v1"
        /** Current guard flag (seed v2, 10,000 entries). */
        const val KEY_SEED_IMPORTED_V2 = "seed_imported_v2"
    }
}
