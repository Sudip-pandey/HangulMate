package com.hangulmate.data.repository

import com.hangulmate.data.local.LearningProgressEntity
import com.hangulmate.data.local.QuizResultEntity
import com.hangulmate.data.local.VocabularyEntity
import com.hangulmate.data.local.VocabularyRelationEntity
import com.hangulmate.domain.model.EpsImportance
import com.hangulmate.domain.model.LearningState
import com.hangulmate.domain.model.PartOfSpeech
import com.hangulmate.domain.model.QuizResult
import com.hangulmate.domain.model.RelationKind
import com.hangulmate.domain.model.Topic
import com.hangulmate.domain.model.VocabularyWord
import com.hangulmate.domain.model.WordRelation
import org.json.JSONArray

// ---------------------------------------------------------------------------
// JSON list helpers (org.json — Android framework, no extra dependency)
// ---------------------------------------------------------------------------

internal fun List<String>.toJsonArray(): String {
    val array = JSONArray()
    forEach { array.put(it) }
    return array.toString()
}

internal fun String.parseStringList(): List<String> {
    if (isBlank()) return emptyList()
    return try {
        val array = JSONArray(this)
        List(array.length()) { i -> array.optString(i) }
    } catch (e: Exception) {
        emptyList()
    }
}

// ---------------------------------------------------------------------------
// Vocabulary
// ---------------------------------------------------------------------------

internal fun VocabularyEntity.toDomain(
    relations: List<WordRelation> = emptyList(),
    isFavorite: Boolean = false,
    learningState: LearningState = LearningState.NEW
): VocabularyWord = VocabularyWord(
    id = id,
    korean = korean,
    nepali = nepali,
    english = english,
    romanization = romanization,
    syllables = syllablesJson.parseStringList(),
    partOfSpeech = PartOfSpeech.fromKey(partOfSpeech),
    topic = Topic.fromKey(topic),
    difficulty = difficulty,
    epsImportance = EpsImportance.fromKey(epsImportance),
    exampleKo = exampleKo,
    exampleNe = exampleNe,
    exampleEn = exampleEn,
    tags = tagsJson.parseStringList(),
    similar = relations.filter { it.kind == RelationKind.SIMILAR },
    opposite = relations.filter { it.kind == RelationKind.OPPOSITE },
    confusing = relations.filter { it.kind == RelationKind.CONFUSING },
    isFavorite = isFavorite,
    learningState = learningState,
    isUserCreated = isUserCreated
)

internal fun VocabularyWord.toEntity(): VocabularyEntity = VocabularyEntity(
    id = id,
    korean = korean,
    nepali = nepali,
    english = english,
    romanization = romanization,
    syllablesJson = syllables.toJsonArray(),
    partOfSpeech = partOfSpeech.key,
    topic = topic.key,
    difficulty = difficulty.coerceIn(1, 3),
    epsImportance = epsImportance.key,
    exampleKo = exampleKo,
    exampleNe = exampleNe,
    exampleEn = exampleEn,
    tagsJson = tags.toJsonArray(),
    isUserCreated = isUserCreated
)

internal fun VocabularyRelationEntity.toDomain(): WordRelation = WordRelation(
    targetWordId = targetId,
    targetKorean = targetKorean,
    kind = runCatching { RelationKind.valueOf(kind) }.getOrDefault(RelationKind.SIMILAR),
    noteNe = noteNe
)

internal fun WordRelation.toEntity(sourceId: String): VocabularyRelationEntity =
    VocabularyRelationEntity(
        targetId = targetWordId,
        targetKorean = targetKorean,
        sourceId = sourceId,
        kind = kind.name,
        noteNe = noteNe
    )

// ---------------------------------------------------------------------------
// Progress / quiz
// ---------------------------------------------------------------------------

internal fun LearningProgressEntity.toLearningState(): LearningState =
    runCatching { LearningState.valueOf(state) }.getOrDefault(LearningState.NEW)

internal fun QuizResultEntity.toDomain(): QuizResult = QuizResult(
    id = id,
    type = type,
    score = score,
    total = total,
    dateEpoch = epochMillis,
    wrongWordIds = wrongWordIdsJson.parseStringList()
)

internal fun QuizResult.toEntity(): QuizResultEntity = QuizResultEntity(
    id = if (id == 0L) 0L else id,
    type = type,
    score = score,
    total = total,
    epochMillis = dateEpoch,
    wrongWordIdsJson = wrongWordIds.toJsonArray()
)
