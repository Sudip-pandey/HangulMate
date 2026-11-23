package com.hangulmate.data.repository

import androidx.room.withTransaction
import com.hangulmate.data.gemini.GeminiWordSearchClient
import com.hangulmate.data.local.DateUtils
import com.hangulmate.data.local.HangulMateDatabase
import com.hangulmate.data.local.LearningProgressEntity
import com.hangulmate.data.local.VocabularyExampleEntity
import com.hangulmate.data.local.VocabularyRelationEntity
import com.hangulmate.data.local.VocabularyTranslationEntity
import com.hangulmate.domain.model.EpsImportance
import com.hangulmate.domain.model.ImportReport
import com.hangulmate.domain.model.LearningState
import com.hangulmate.domain.model.PartOfSpeech
import com.hangulmate.domain.model.RelationKind
import com.hangulmate.domain.model.Topic
import com.hangulmate.domain.model.VocabularyWord
import com.hangulmate.domain.model.WordRelation
import com.hangulmate.domain.repository.GeminiKeyStore
import com.hangulmate.domain.repository.OnlineSearchResult
import com.hangulmate.domain.repository.VocabularyRepository
import com.hangulmate.ml.GEMINI_TAG
import com.hangulmate.ml.GeminiRelatedWord
import com.hangulmate.ml.GeminiWordResult
import com.hangulmate.ml.GeminiWordSearchResult
import com.hangulmate.ml.KoreanWordMatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [VocabularyRepository] implementation backed by Room.
 *
 * Enrichment: list queries return words with their favorite flag and
 * learning state joined in (via [FavoriteDao.getAllIdsFlow] and
 * [ProgressDao.getAllFlow], so a favorite toggle or a review re-emits
 * the list). Relations are embedded ONLY by [getById] / [getByKorean]
 * (per the contract — list screens do not need them).
 */
@Singleton
class VocabularyRepositoryImpl @Inject constructor(
    private val db: HangulMateDatabase,
    /**
     * Online word search (Gemini). Null in unit tests that do not
     * cover it — [searchOnline] then reports [OnlineSearchResult.NoKey]
     * without calling anything. The app graph always provides both
     * (see [com.hangulmate.di.DatabaseModule]).
     */
    private val geminiClient: GeminiWordSearchClient? = null,
    private val geminiKeyStore: GeminiKeyStore? = null,
) : VocabularyRepository {

    private val vocabDao get() = db.vocabularyDao()
    private val relationDao get() = db.relationDao()
    private val progressDao get() = db.progressDao()
    private val favoriteDao get() = db.favoriteDao()
    private val historyDao get() = db.historyDao()

    // ------------------------------------------------------------------
    // Query
    // ------------------------------------------------------------------

    override fun search(query: String): Flow<List<VocabularyWord>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyFlow()
        val pattern = toLikePattern(trimmed)
        return enrichList(vocabDao.search(pattern, trimmed))
    }

    override fun getById(id: String): Flow<VocabularyWord?> {
        return combine(
            vocabDao.getByIdFlow(id),
            relationDao.getForSource(id),
            favoriteDao.isFavorite(id),
            progressDao.getProgressFlow(id)
        ) { entity, relations, isFavorite, progress ->
            entity?.toDomain(
                relations = relations.map { it.toDomain() },
                isFavorite = isFavorite,
                learningState = progress?.toLearningState() ?: LearningState.NEW
            )
        }
    }

    override suspend fun getByKorean(korean: String): VocabularyWord? {
        val entity = vocabDao.getByKorean(korean.trim()) ?: return null
        return enrichOne(entity, withRelations = true)
    }

    // ------------------------------------------------------------------
    // Online word search (Gemini fallback)
    // ------------------------------------------------------------------

    override suspend fun searchOnline(query: String): OnlineSearchResult {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return OnlineSearchResult.NotFound
        // Cache-first: a word already in the local database is returned
        // without any API call — seed words and previously saved Gemini
        // words alike.
        vocabDao.getByKorean(trimmed)?.let {
            return OnlineSearchResult.Found(enrichOne(it, withRelations = true), fromCache = true)
        }
        val client = geminiClient ?: return OnlineSearchResult.NoKey
        val key = geminiKeyStore?.getKey()?.takeIf { it.isNotBlank() }
            ?: return OnlineSearchResult.NoKey
        return when (val result = client.searchWord(key, trimmed)) {
            is GeminiWordSearchResult.NotFound -> OnlineSearchResult.NotFound
            is GeminiWordSearchResult.Failure -> OnlineSearchResult.Failed(result.error)
            is GeminiWordSearchResult.Success -> {
                val found = result.word
                // The model may normalize the spelling — re-check the
                // cache by the RETURNED Korean before saving anything,
                // so a differently-spelled query never duplicates a word.
                vocabDao.getByKorean(found.korean)?.let {
                    return OnlineSearchResult.Found(
                        enrichOne(it, withRelations = true),
                        fromCache = true,
                    )
                }
                // Nothing to show at all (no Nepali, no English) is a
                // miss, not a word worth saving.
                if (found.nepali.isBlank() && found.english.isBlank()) {
                    return OnlineSearchResult.NotFound
                }
                OnlineSearchResult.Found(saveGeminiWord(found))
            }
        }
    }

    /**
     * Saves a Gemini-found word into the local database, in one
     * transaction, with nepaliStatus "mt_review" and the "gemini" tag
     * (never presented as verified — see the AI badge on the detail
     * screen). ALL similar / opposite words are saved too: words
     * already in the database are reused, the rest are saved as
     * minimal Gemini entries (same mt_review marking), so every
     * relation resolves to a real row and the detail screen shows
     * the full lists. This is a per-phone local save only — the
     * bundled seed JSON asset is never touched.
     */
    private suspend fun saveGeminiWord(found: GeminiWordResult): VocabularyWord {
        val stamp = System.currentTimeMillis()
        val mainId = "HM-G$stamp"
        val mainKorean = found.korean.trim()
        val mainNormalized = KoreanWordMatcher.normalize(mainKorean)

        // Dedupe (by normalized spelling) and drop self-references,
        // keeping the first occurrence; drop entries with no meaning
        // at all — there would be nothing to show for them.
        fun cleanRelated(list: List<GeminiRelatedWord>): List<GeminiRelatedWord> {
            val seen = mutableSetOf<String>()
            return list.filter { rel ->
                val korean = rel.korean.trim()
                korean.isNotEmpty() &&
                    (rel.nepali.isNotBlank() || rel.english.isNotBlank()) &&
                    KoreanWordMatcher.normalize(korean) != mainNormalized &&
                    seen.add(KoreanWordMatcher.normalize(korean))
            }
        }
        val similar = cleanRelated(found.similar)
        val opposite = cleanRelated(found.opposite)

        db.withTransaction {
            val koreanToId = vocabDao.getKoreanToIdMap()
                .associate { it.korean to it.id }.toMutableMap()

            // Related words first, so every relation below resolves.
            var stubIndex = 0
            suspend fun ensureRelatedWord(rel: GeminiRelatedWord): String {
                koreanToId[rel.korean.trim()]?.let { return it }
                stubIndex++
                val stubId = "HM-G${stamp}S$stubIndex"
                val stub = geminiWord(
                    id = stubId,
                    korean = rel.korean.trim(),
                    nepali = rel.nepali.trim(),
                    english = rel.english.trim(),
                    romanization = "",
                    partOfSpeech = rel.partOfSpeech,
                )
                insertWordRow(stub)
                koreanToId[stub.korean] = stubId
                return stubId
            }
            val similarIds = mutableListOf<String>()
            for (rel in similar) similarIds += ensureRelatedWord(rel)
            val oppositeIds = mutableListOf<String>()
            for (rel in opposite) oppositeIds += ensureRelatedWord(rel)

            val main = geminiWord(
                id = mainId,
                korean = mainKorean,
                nepali = found.nepali.trim(),
                english = found.english.trim(),
                romanization = found.romanization.trim(),
                partOfSpeech = found.partOfSpeech,
            )
            insertWordRow(main)

            val rows = mutableListOf<VocabularyRelationEntity>()
            similar.zip(similarIds).forEach { (rel, targetId) ->
                rows += VocabularyRelationEntity(
                    sourceId = mainId,
                    targetId = targetId,
                    targetKorean = rel.korean.trim(),
                    kind = RelationKind.SIMILAR.name,
                    noteNe = rel.nepali.trim().ifBlank { rel.english.trim() },
                )
            }
            opposite.zip(oppositeIds).forEach { (rel, targetId) ->
                rows += VocabularyRelationEntity(
                    sourceId = mainId,
                    targetId = targetId,
                    targetKorean = rel.korean.trim(),
                    kind = RelationKind.OPPOSITE.name,
                    noteNe = rel.nepali.trim().ifBlank { rel.english.trim() },
                )
            }
            if (rows.isNotEmpty()) relationDao.insertAll(rows)
        }
        val entity = vocabDao.getById(mainId)
            ?: throw IllegalStateException("Gemini word was not saved: $mainId")
        return enrichOne(entity, withRelations = true)
    }

    /**
     * One Gemini-saved word: user-created, tagged "gemini",
     * nepaliStatus "mt_review" when a Nepali meaning exists, "missing"
     * when the Nepali column only holds the English gloss as a
     * display fallback (the detail screen says so plainly).
     */
    private fun geminiWord(
        id: String,
        korean: String,
        nepali: String,
        english: String,
        romanization: String,
        partOfSpeech: String,
    ): VocabularyWord = VocabularyWord(
        id = id,
        korean = korean,
        nepali = nepali.ifBlank { english },
        nepaliStatus = if (nepali.isNotBlank()) "mt_review" else "missing",
        english = english,
        romanization = romanization,
        syllables = splitSyllables(korean),
        partOfSpeech = PartOfSpeech.fromKey(partOfSpeech.trim().lowercase()),
        topic = Topic.GENERAL,
        difficulty = 1,
        epsImportance = EpsImportance.LOW,
        exampleKo = "",
        exampleNe = "",
        exampleEn = "",
        tags = listOf(GEMINI_TAG),
        isUserCreated = true,
    )

    /** Word row + its translation and (blank) example rows. */
    private suspend fun insertWordRow(word: VocabularyWord) {
        vocabDao.insert(word.toEntity())
        db.translationDao().insertAll(
            listOf(
                VocabularyTranslationEntity(vocabId = word.id, language = "ne", meaning = word.nepali),
                VocabularyTranslationEntity(vocabId = word.id, language = "en", meaning = word.english),
            ),
        )
        db.exampleDao().insertAll(
            listOf(
                VocabularyExampleEntity(
                    vocabId = word.id,
                    ko = word.exampleKo,
                    ne = word.exampleNe,
                    en = word.exampleEn,
                ),
            ),
        )
    }

    /** Splits a Korean word into its Hangul syllable blocks (one char each). */
    private fun splitSyllables(korean: String): List<String> =
        korean.filter { it in '\uAC00'..'\uD7A3' }.map { it.toString() }

    override suspend fun getWordOfTheDay(dateEpochDay: Long): VocabularyWord {
        val total = vocabDao.getTotalCountOnce()
        if (total == 0) throw IllegalStateException("Vocabulary is empty — seed import has not run yet")
        val offset = ((dateEpochDay % total) + total).toInt() % total
        val entity = vocabDao.getByOffset(offset)
            ?: throw IllegalStateException("Vocabulary is empty — seed import has not run yet")
        return enrichOne(entity, withRelations = true)
    }

    override fun getTodayNewWords(limit: Int, learnedIds: Set<String>): Flow<List<VocabularyWord>> {
        val flow = if (learnedIds.isEmpty()) {
            vocabDao.getNewWordsWithoutExclusions(limit)
        } else {
            vocabDao.getNewWords(limit, learnedIds.toList(), learnedIds.size)
        }
        return enrichList(flow)
    }

    override fun getByTopic(topic: Topic, difficulty: Int?, epsOnly: Boolean): Flow<List<VocabularyWord>> =
        enrichList(vocabDao.getByTopic(topic.key, difficulty, epsOnly))

    override fun getFavorites(): Flow<List<VocabularyWord>> =
        enrichList(vocabDao.getFavorites())

    override suspend fun toggleFavorite(id: String) {
        db.withTransaction {
            if (favoriteDao.get(id) != null) favoriteDao.delete(id)
            else favoriteDao.insert(
                com.hangulmate.data.local.FavoriteWordEntity(
                    vocabId = id, addedEpoch = System.currentTimeMillis()
                )
            )
        }
    }

    override fun getDifficult(): Flow<List<VocabularyWord>> =
        enrichList(vocabDao.getDifficult())

    override fun getRecentlySearched(limit: Int): Flow<List<VocabularyWord>> =
        enrichList(historyDao.getRecentlySearched(limit))

    override suspend fun recordSearch(wordId: String) {
        historyDao.upsert(
            com.hangulmate.data.local.SearchHistoryEntity(
                vocabId = wordId, searchedEpoch = System.currentTimeMillis()
            )
        )
    }

    override fun getLearningState(id: String): Flow<LearningState> =
        progressDao.getProgressFlow(id).map { it?.toLearningState() ?: LearningState.NEW }

    override suspend fun setLearningState(id: String, state: LearningState) {
        val today = DateUtils.today()
        db.withTransaction {
            val current = progressDao.getProgress(id)
            progressDao.upsert(SrsScheduler.applyStateChange(current, id, state, today))
        }
    }

    override fun getRevisionQueue(todayEpochDay: Long): Flow<List<VocabularyWord>> {
        val due = vocabDao.getDueForReview(todayEpochDay)
        val fresh = vocabDao.getNewWordsWithoutExclusions(VocabularyRepository.DAILY_NEW_WORD_GOAL)
        return enrichList(
            combine(due, fresh) { dueList, freshList ->
                val dueIds = dueList.map { it.id }.toSet()
                dueList + freshList
                    .filter { it.id !in dueIds }
                    .take(VocabularyRepository.DAILY_NEW_WORD_GOAL)
            }
        )
    }

    override suspend fun recordReview(id: String, remembered: Boolean) {
        val today = DateUtils.today()
        db.withTransaction {
            val current = progressDao.getProgress(id)
            val wasNew = current == null || current.state == LearningState.NEW.name
            val updated = SrsScheduler.applyReview(current, id, remembered, today)
            progressDao.upsert(updated)
            db.dailyActivityDao().addActivity(
                day = today,
                wordsLearned = if (remembered && wasNew) 1 else 0,
                wordsReviewed = 1
            )
        }
    }

    override fun getLearnedCount(): Flow<Int> = progressDao.getLearnedCount()

    override fun getMasteredCount(): Flow<Int> = progressDao.getMasteredCount()

    override fun getTotalCount(): Flow<Int> = vocabDao.getTotalCount()

    /**
     * Reset Progress: wipes progress-adjacent tables only. The vocabulary
     * table (seed + user-created words), relations, translations and
     * examples are never touched, so every word survives a reset.
     */
    override suspend fun clearAllProgress() {
        db.withTransaction {
            db.progressDao().clearAll()
            db.favoriteDao().clearAll()
            db.historyDao().clear()
            db.quizDao().clearAll()
            db.dailyActivityDao().clearAll()
            db.collectionDao().clearAll()
        }
    }

    // ------------------------------------------------------------------
    // User words, export / import
    // ------------------------------------------------------------------

    override suspend fun insertUserWord(word: VocabularyWord): String {
        val id = word.id.ifBlank { "HM-U${System.currentTimeMillis()}" }
        val wordWithId = word.copy(id = id, isUserCreated = true)
        db.withTransaction {
            vocabDao.insert(wordWithId.toEntity())
            db.translationDao().insertAll(
                listOf(
                    VocabularyTranslationEntity(vocabId = id, language = "ne", meaning = wordWithId.nepali),
                    VocabularyTranslationEntity(vocabId = id, language = "en", meaning = wordWithId.english)
                )
            )
            db.exampleDao().insertAll(
                listOf(
                    VocabularyExampleEntity(
                        vocabId = id,
                        ko = wordWithId.exampleKo,
                        ne = wordWithId.exampleNe,
                        en = wordWithId.exampleEn
                    )
                )
            )
            insertRelations(id, wordWithId.similar + wordWithId.opposite + wordWithId.confusing)
        }
        return id
    }

    /** Resolves relation targets by Korean spelling; unresolvable targets are skipped. */
    private suspend fun insertRelations(sourceId: String, relations: List<WordRelation>) {
        if (relations.isEmpty()) return
        val koreanToId = vocabDao.getKoreanToIdMap().associate { it.korean to it.id }
        val rows = relations.mapNotNull { relation ->
            val targetId = relation.targetWordId?.takeIf { it.isNotBlank() }
                ?: koreanToId[relation.targetKorean]
            if (targetId == null) null
            else VocabularyRelationEntity(
                sourceId = sourceId,
                targetId = targetId,
                targetKorean = relation.targetKorean,
                kind = relation.kind.name,
                noteNe = relation.noteNe
            )
        }
        if (rows.isNotEmpty()) relationDao.insertAll(rows)
    }

    override suspend fun exportAll(): String {
        val words = vocabDao.getAll()
        val progressById = progressDao.getAll().associateBy { it.vocabId }
        val favoriteIds = favoriteDao.getAllIds().toSet()
        val wordsJson = JSONArray()
        for (entity in words) {
            val relations = relationDao.getForSourceOnce(entity.id)
            val obj = JSONObject()
            obj.put("id", entity.id)
            obj.put("korean", entity.korean)
            obj.put("nepali", entity.nepali)
            obj.put("english", entity.english)
            obj.put("romanization", entity.romanization)
            obj.put("syllables", JSONArray(entity.syllablesJson.parseStringList()))
            obj.put("partOfSpeech", entity.partOfSpeech)
            obj.put("topic", entity.topic)
            obj.put("difficulty", entity.difficulty)
            obj.put("epsImportance", entity.epsImportance)
            obj.put("exampleKo", entity.exampleKo)
            obj.put("exampleNe", entity.exampleNe)
            obj.put("exampleEn", entity.exampleEn)
            obj.put("similar", relationsJson(relations, "SIMILAR", "noteNe"))
            obj.put("opposite", relationsJson(relations, "OPPOSITE", "noteNe"))
            obj.put("confusing", relationsJson(relations, "CONFUSING", "differenceNe"))
            obj.put("tags", JSONArray(entity.tagsJson.parseStringList()))
            wordsJson.put(obj)
        }
        val progressJson = JSONArray()
        for ((vocabId, progress) in progressById) {
            val obj = JSONObject()
            obj.put("vocabId", vocabId)
            obj.put("state", progress.state)
            obj.put("easiness", progress.easiness.toDouble())
            obj.put("intervalDays", progress.intervalDays)
            obj.put("repetitions", progress.repetitions)
            obj.put("nextReviewEpochDay", progress.nextReviewEpochDay)
            obj.put("lastReviewedEpochDay", progress.lastReviewedEpochDay ?: JSONObject.NULL)
            obj.put("wrongCount", progress.wrongCount)
            obj.put("correctCount", progress.correctCount)
            progressJson.put(obj)
        }
        return JSONObject()
            .put("format", "hangulmate-export")
            .put("version", 1)
            .put("exportedEpochMillis", System.currentTimeMillis())
            .put("words", wordsJson)
            .put("progress", progressJson)
            .put("favorites", JSONArray(favoriteIds.toList()))
            .toString()
    }

    private fun relationsJson(
        relations: List<com.hangulmate.data.local.VocabularyRelationEntity>,
        kind: String,
        noteKey: String
    ): JSONArray {
        val array = JSONArray()
        for (relation in relations.filter { it.kind == kind }) {
            array.put(
                JSONObject()
                    .put("korean", relation.targetKorean)
                    .put(noteKey, relation.noteNe)
            )
        }
        return array
    }

    override suspend fun importJson(json: String): ImportReport {
        val errorMessages = mutableListOf<String>()
        var added = 0
        var skipped = 0
        var errors = 0
        val parsed: JSONObject = try {
            JSONObject(json)
        } catch (e: Exception) {
            return ImportReport(0, 0, 1, listOf("Invalid JSON: ${e.message}"))
        }
        val wordsArray = parsed.optJSONArray("words")
            ?: return ImportReport(0, 0, 1, listOf("Missing \"words\" array"))
        db.withTransaction {
            for (i in 0 until wordsArray.length()) {
                val obj = wordsArray.optJSONObject(i)
                if (obj == null) {
                    errors++
                    errorMessages += "Entry $i is not an object"
                    continue
                }
                val id = obj.optString("id").trim()
                val korean = obj.optString("korean").trim()
                if (id.isEmpty() || korean.isEmpty()) {
                    errors++
                    errorMessages += "Entry $i is missing id/korean"
                    continue
                }
                try {
                    val word = VocabularyWord(
                        id = id,
                        korean = korean,
                        nepali = obj.optString("nepali"),
                        english = obj.optString("english"),
                        romanization = obj.optString("romanization"),
                        syllables = obj.optStringArray("syllables"),
                        partOfSpeech = com.hangulmate.domain.model.PartOfSpeech.fromKey(
                            obj.optString("partOfSpeech", "noun")
                        ),
                        topic = Topic.fromKey(obj.optString("topic", "general")),
                        difficulty = obj.optInt("difficulty", 1).coerceIn(1, 3),
                        epsImportance = com.hangulmate.domain.model.EpsImportance.fromKey(
                            obj.optString("epsImportance", "medium")
                        ),
                        exampleKo = obj.optString("exampleKo"),
                        exampleNe = obj.optString("exampleNe"),
                        exampleEn = obj.optString("exampleEn"),
                        tags = obj.optStringArray("tags"),
                        similar = obj.optRelations("similar", com.hangulmate.domain.model.RelationKind.SIMILAR),
                        opposite = obj.optRelations("opposite", com.hangulmate.domain.model.RelationKind.OPPOSITE),
                        confusing = obj.optRelations("confusing", com.hangulmate.domain.model.RelationKind.CONFUSING, "differenceNe"),
                        isUserCreated = true
                    )
                    val rowId = vocabDao.insert(word.toEntity())
                    if (rowId == -1L) {
                        skipped++
                    } else {
                        added++
                        db.translationDao().insertAll(
                            listOf(
                                VocabularyTranslationEntity(vocabId = id, language = "ne", meaning = word.nepali),
                                VocabularyTranslationEntity(vocabId = id, language = "en", meaning = word.english)
                            )
                        )
                        db.exampleDao().insertAll(
                            listOf(
                                VocabularyExampleEntity(
                                    vocabId = id, ko = word.exampleKo,
                                    ne = word.exampleNe, en = word.exampleEn
                                )
                            )
                        )
                        insertRelations(id, word.similar + word.opposite + word.confusing)
                    }
                } catch (e: Exception) {
                    errors++
                    errorMessages += "Entry $i ($id): ${e.message}"
                }
            }
            // Progress + favorites from the export are restored best-effort.
            val progressArray = parsed.optJSONArray("progress")
            if (progressArray != null) {
                for (i in 0 until progressArray.length()) {
                    val obj = progressArray.optJSONObject(i) ?: continue
                    val vocabId = obj.optString("vocabId")
                    if (vocabId.isEmpty()) continue
                    progressDao.upsert(
                        LearningProgressEntity(
                            vocabId = vocabId,
                            state = obj.optString("state", LearningState.NEW.name),
                            easiness = obj.optDouble("easiness", 2.5).toFloat(),
                            intervalDays = obj.optInt("intervalDays", 0),
                            repetitions = obj.optInt("repetitions", 0),
                            nextReviewEpochDay = obj.optLong("nextReviewEpochDay", DateUtils.today()),
                            lastReviewedEpochDay = if (obj.isNull("lastReviewedEpochDay")) null
                            else obj.optLong("lastReviewedEpochDay"),
                            wrongCount = obj.optInt("wrongCount", 0),
                            correctCount = obj.optInt("correctCount", 0)
                        )
                    )
                }
            }
            val favoritesArray = parsed.optJSONArray("favorites")
            if (favoritesArray != null) {
                for (i in 0 until favoritesArray.length()) {
                    val vocabId = favoritesArray.optString(i)
                    if (vocabId.isNotEmpty()) {
                        favoriteDao.insert(
                            com.hangulmate.data.local.FavoriteWordEntity(
                                vocabId = vocabId, addedEpoch = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
        }
        return ImportReport(added, skipped, errors, errorMessages.take(10))
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Enriches a list flow with per-word favorite + learning-state flags. */
    private fun enrichList(
        flow: Flow<List<com.hangulmate.data.local.VocabularyEntity>>
    ): Flow<List<VocabularyWord>> =
        combine(
            flow,
            favoriteDao.getAllIdsFlow(),
            progressDao.getAllFlow()
        ) { entities, favoriteIds, progressList ->
            val favorites = favoriteIds.toSet()
            val progressById = progressList.associateBy { it.vocabId }
            entities.map { entity ->
                entity.toDomain(
                    isFavorite = entity.id in favorites,
                    learningState = progressById[entity.id]?.toLearningState() ?: LearningState.NEW
                )
            }
        }

    /** One-shot enrichment (favorite + state, optionally relations). */
    private suspend fun enrichOne(
        entity: com.hangulmate.data.local.VocabularyEntity,
        withRelations: Boolean
    ): VocabularyWord {
        val relations = if (withRelations) {
            relationDao.getForSourceOnce(entity.id).map { it.toDomain() }
        } else {
            emptyList()
        }
        val isFavorite = favoriteDao.get(entity.id) != null
        val state = progressDao.getProgress(entity.id)?.toLearningState() ?: LearningState.NEW
        return entity.toDomain(relations, isFavorite, state)
    }

    /**
     * Escapes LIKE wildcards in the user query (\, %, _) then wraps it
     * in %...% for partial matching.
     */
    internal fun toLikePattern(query: String): String {
        val escaped = query
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return "%$escaped%"
    }
}

private fun JSONObject.optStringArray(key: String): List<String> {
    val array = optJSONArray(key) ?: return emptyList()
    return List(array.length()) { i -> array.optString(i) }
}

private fun JSONObject.optRelations(
    key: String,
    kind: com.hangulmate.domain.model.RelationKind,
    noteKey: String = "noteNe"
): List<WordRelation> {
    val array = optJSONArray(key) ?: return emptyList()
    val out = mutableListOf<WordRelation>()
    for (i in 0 until array.length()) {
        val rel = array.optJSONObject(i) ?: continue
        val targetKorean = rel.optString("korean").trim()
        if (targetKorean.isEmpty()) continue
        out += WordRelation(
            targetWordId = null,
            targetKorean = targetKorean,
            kind = kind,
            noteNe = rel.optString(noteKey)
        )
    }
    return out
}
