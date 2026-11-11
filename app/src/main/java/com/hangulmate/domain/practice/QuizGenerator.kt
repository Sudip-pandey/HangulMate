package com.hangulmate.domain.practice

import com.hangulmate.domain.model.RelationKind
import com.hangulmate.domain.model.VocabularyWord
import kotlin.random.Random

/**
 * The quiz modes HangulMate can run.
 *
 * [typeKey] is the value persisted in [com.hangulmate.domain.model.QuizResult.type].
 * IMAGE is a real mode in the generator but is never playable: no word in the
 * seed data carries an image asset path, so [QuizGenerator.generate] always
 * returns an empty list for it and the hub shows it disabled instead of
 * faking picture questions.
 */
enum class QuizType(val typeKey: String) {
    KO_TO_NE("ko_to_ne"),
    NE_TO_KO("ne_to_ko"),
    LISTENING("listening"),
    SPELLING("spelling"),
    FILL_BLANK("fill_blank"),
    RELATION("relation"),
    IMAGE("image"),
    EPS_MOCK("eps_mock"),
}

/**
 * One generated quiz question. Self-contained: the UI renders everything
 * from these fields and never needs to look the word up again.
 *
 * [prompt] is the main display text — the Korean word (KO_TO_NE,
 * RELATION), the Nepali meaning (NE_TO_KO, SPELLING), or the example
 * sentence with the target word blanked (FILL_BLANK). For LISTENING the
 * prompt is empty and the word to hear is in [ttsText].
 *
 * [relationKind] is set only for RELATION questions so the UI can ask for
 * "similar to" vs "opposite of". [explanationNe] is the Nepali explanation
 * shown after answering — the relation's noteNe for RELATION questions,
 * otherwise the word's example sentence in Nepali when it has one.
 */
data class QuizQuestion(
    val type: QuizType,
    val targetWordId: String,
    val prompt: String,
    val ttsText: String? = null,
    val relationKind: RelationKind? = null,
    val options: List<String>,
    val correctIndex: Int,
    val explanationNe: String? = null,
    val explanationEn: String? = null,
)

/**
 * Pure quiz-building logic. No Android dependencies, no I/O: the caller
 * supplies the word pool and this object turns it into questions.
 *
 * Rules enforced here:
 * - Questions come only from the supplied pool (local database words).
 * - No target word repeats within one generated set.
 * - Every question has exactly 4 options: the correct answer plus 3
 *   distractors, preferring distractors from the target's own topic.
 * - A question type is only built when the target word actually supports
 *   it: FILL_BLANK needs an example sentence containing the word,
 *   SPELLING needs at least 2 syllables, RELATION needs a non-empty
 *   similar/opposite relation, LISTENING is skipped when
 *   [listeningAvailable] is false.
 * - IMAGE always yields an empty list: the seed data bundles no images,
 *   and a picture question without a real picture would be fake.
 */
object QuizGenerator {

    /** A quiz needs at least this many words in the pool to be playable. */
    const val MIN_POOL_SIZE = 4

    /** Question count for the untimed quiz types. */
    const val DEFAULT_QUESTION_COUNT = 10

    /** Seconds allowed per question in the timed EPS mock test. */
    const val EPS_SECONDS_PER_QUESTION = 60

    /** The 6 playable types mixed into an EPS mock test (IMAGE excluded). */
    private val EPS_MIX_TYPES = listOf(
        QuizType.KO_TO_NE,
        QuizType.NE_TO_KO,
        QuizType.LISTENING,
        QuizType.SPELLING,
        QuizType.FILL_BLANK,
        QuizType.RELATION,
    )

    /**
     * Builds up to [count] questions of [type].
     *
     * [targets] are the words questions may be built about; [distractorPool]
     * is the wider pool distractors are drawn from (callers pass the same
     * list for both, except for RELATION/EPS_MOCK where [targets] is the
     * small relation-enriched subset and [distractorPool] is the full pool).
     * Returns fewer than [count] questions when the pool cannot support
     * more — never invented ones. Returns an empty list when the pool is
     * too small or the type cannot be built at all (e.g. IMAGE).
     */
    fun generate(
        type: QuizType,
        targets: List<VocabularyWord>,
        distractorPool: List<VocabularyWord>,
        count: Int,
        listeningAvailable: Boolean = true,
        random: Random = Random.Default,
    ): List<QuizQuestion> {
        if (type == QuizType.IMAGE) return emptyList()
        // Nepali-dependent types (KO_TO_NE, NE_TO_KO, LISTENING, SPELLING, and
        // the EPS_MOCK mix that includes them) must only quiz words that have
        // a real Nepali translation. For "missing" entries the nepali column
        // holds the English gloss as a display fallback — quizzing it would
        // present English as Nepali. When enough verified words exist, use
        // only them; otherwise fall back to the full pool so small datasets
        // (e.g. a fresh install) still produce quizzes.
        var effectiveTargets = targets
        var effectivePool = distractorPool
        val nepaliDependent = type == QuizType.KO_TO_NE || type == QuizType.NE_TO_KO ||
            type == QuizType.LISTENING || type == QuizType.SPELLING ||
            type == QuizType.EPS_MOCK
        if (nepaliDependent) {
            val verifiedTargets = targets.filter { it.nepaliStatus == "verified" }
            val verifiedPool = distractorPool.filter { it.nepaliStatus == "verified" }
            if (verifiedTargets.size >= MIN_POOL_SIZE && verifiedPool.size >= MIN_POOL_SIZE) {
                effectiveTargets = verifiedTargets
                effectivePool = verifiedPool
            }
        }
        if (effectiveTargets.size < MIN_POOL_SIZE) return emptyList()
        return when (type) {
            QuizType.EPS_MOCK -> generateMixed(effectiveTargets, effectivePool, count, listeningAvailable, random)
            else -> {
                val questions = mutableListOf<QuizQuestion>()
                for (target in effectiveTargets.shuffled(random)) {
                    if (questions.size >= count) break
                    buildQuestion(type, target, effectivePool, listeningAvailable, random)
                        ?.let { questions += it }
                }
                questions
            }
        }
    }

    /** Mixed EPS-style test: cycles through the playable types, one target word each. */
    private fun generateMixed(
        targets: List<VocabularyWord>,
        distractorPool: List<VocabularyWord>,
        count: Int,
        listeningAvailable: Boolean,
        random: Random,
    ): List<QuizQuestion> {
        val types = EPS_MIX_TYPES
            .filter { it != QuizType.LISTENING || listeningAvailable }
            .shuffled(random)
        if (types.isEmpty()) return emptyList()
        val questions = mutableListOf<QuizQuestion>()
        val usedIds = mutableSetOf<String>()
        var typeIndex = 0
        for (target in targets.shuffled(random)) {
            if (questions.size >= count) break
            if (target.id in usedIds) continue
            for (offset in types.indices) {
                val type = types[(typeIndex + offset) % types.size]
                val question = buildQuestion(type, target, distractorPool, listeningAvailable, random)
                if (question != null) {
                    questions += question
                    usedIds += target.id
                    typeIndex = (typeIndex + offset + 1) % types.size
                    break
                }
            }
        }
        return questions.shuffled(random)
    }

    /** Builds one question of [type] about [target], or null when the word cannot support it. */
    private fun buildQuestion(
        type: QuizType,
        target: VocabularyWord,
        pool: List<VocabularyWord>,
        listeningAvailable: Boolean,
        random: Random,
    ): QuizQuestion? = when (type) {
        QuizType.KO_TO_NE -> {
            val (options, correctIndex) = optionsWithCorrect(
                correct = target.nepali,
                pool = pool,
                labelOf = { it.nepali },
                preferTopic = target.topic,
                random = random,
            ) ?: return null
            QuizQuestion(
                type = type,
                targetWordId = target.id,
                prompt = target.korean,
                options = options,
                correctIndex = correctIndex,
                explanationNe = target.exampleNe.ifBlank { null },
                explanationEn = target.exampleEn.ifBlank { null },
            )
        }

        QuizType.NE_TO_KO -> {
            val (options, correctIndex) = optionsWithCorrect(
                correct = target.korean,
                pool = pool,
                labelOf = { it.korean },
                preferTopic = target.topic,
                random = random,
            ) ?: return null
            QuizQuestion(
                type = type,
                targetWordId = target.id,
                prompt = target.nepali,
                options = options,
                correctIndex = correctIndex,
                explanationNe = target.exampleNe.ifBlank { null },
                explanationEn = target.exampleEn.ifBlank { null },
            )
        }

        QuizType.LISTENING -> {
            if (!listeningAvailable) return null
            val (options, correctIndex) = optionsWithCorrect(
                correct = target.nepali,
                pool = pool,
                labelOf = { it.nepali },
                preferTopic = target.topic,
                random = random,
            ) ?: return null
            QuizQuestion(
                type = type,
                targetWordId = target.id,
                prompt = "",
                ttsText = target.korean,
                options = options,
                correctIndex = correctIndex,
                explanationNe = target.exampleNe.ifBlank { null },
                explanationEn = target.exampleEn.ifBlank { null },
            )
        }

        QuizType.SPELLING -> {
            if (target.syllables.size < 2) return null
            val syllablePool = pool.flatMap { it.syllables }.distinct()
            val mutations = generateSpellingMutations(target, syllablePool, random).take(3)
            if (mutations.size < 3) return null
            val options = (mutations + target.korean).shuffled(random)
            QuizQuestion(
                type = type,
                targetWordId = target.id,
                prompt = target.nepali,
                options = options,
                correctIndex = options.indexOf(target.korean),
                explanationNe = target.exampleNe.ifBlank { null },
                explanationEn = target.exampleEn.ifBlank { null },
            )
        }

        QuizType.FILL_BLANK -> {
            if (target.exampleKo.isBlank() || !target.exampleKo.contains(target.korean)) return null
            val sentence = target.exampleKo.replaceFirst(target.korean, "_____")
            val (options, correctIndex) = optionsWithCorrect(
                correct = target.korean,
                pool = pool,
                labelOf = { it.korean },
                preferTopic = target.topic,
                random = random,
            ) ?: return null
            QuizQuestion(
                type = type,
                targetWordId = target.id,
                prompt = sentence,
                options = options,
                correctIndex = correctIndex,
                explanationNe = target.exampleNe.ifBlank { null },
                explanationEn = target.exampleEn.ifBlank { null },
            )
        }

        QuizType.RELATION -> {
            val availableKinds = listOfNotNull(
                RelationKind.SIMILAR.takeIf { target.similar.isNotEmpty() },
                RelationKind.OPPOSITE.takeIf { target.opposite.isNotEmpty() },
            )
            if (availableKinds.isEmpty()) return null
            val kind = availableKinds.random(random)
            val relation = (if (kind == RelationKind.SIMILAR) target.similar else target.opposite)
                .random(random)
            val (options, correctIndex) = optionsWithCorrect(
                correct = relation.targetKorean,
                pool = pool,
                labelOf = { it.korean },
                preferTopic = target.topic,
                random = random,
            ) ?: return null
            QuizQuestion(
                type = type,
                targetWordId = target.id,
                prompt = target.korean,
                relationKind = kind,
                options = options,
                correctIndex = correctIndex,
                explanationNe = relation.noteNe.ifBlank { null }
                    ?: target.exampleNe.ifBlank { null },
                explanationEn = relation.noteEn ?: target.exampleEn.ifBlank { null },
            )
        }

        QuizType.IMAGE, QuizType.EPS_MOCK -> null
    }

    /**
     * Four options for [correct]: the correct label plus 3 distractors.
     * Distractors are drawn from [preferTopic] first, then the rest of the
     * pool; all labels are distinct and none is blank. Null when 3 usable
     * distractors cannot be found.
     */
    private fun optionsWithCorrect(
        correct: String,
        pool: List<VocabularyWord>,
        labelOf: (VocabularyWord) -> String,
        preferTopic: com.hangulmate.domain.model.Topic,
        random: Random,
    ): Pair<List<String>, Int>? {
        if (correct.isBlank()) return null
        val sameTopic = pool.filter { it.topic == preferTopic }.map(labelOf)
        val others = pool.filter { it.topic != preferTopic }.map(labelOf)
        val distractors = (sameTopic.shuffled(random) + others.shuffled(random))
            .filter { it.isNotBlank() && it != correct }
            .distinct()
            .take(3)
        if (distractors.size < 3) return null
        val options = (distractors + correct).shuffled(random)
        return options to options.indexOf(correct)
    }

    /**
     * Plausible-but-wrong Hangul spellings: the target word with one
     * syllable swapped for a syllable taken from another real word in the
     * pool. These are distractors only — they are never presented as real
     * words. Yields distinct mutations different from the correct spelling.
     */
    private fun generateSpellingMutations(
        target: VocabularyWord,
        syllablePool: List<String>,
        random: Random,
    ): List<String> {
        val mutations = linkedSetOf<String>()
        repeat(24) {
            if (mutations.size >= 3) return@repeat
            val index = random.nextInt(target.syllables.size)
            val replacement = syllablePool
                .filter { it != target.syllables[index] }
                .randomOrNull(random)
                ?: return@repeat
            val mutated = target.syllables.toMutableList()
                .also { it[index] = replacement }
                .joinToString("")
            if (mutated != target.korean) mutations += mutated
        }
        return mutations.toList()
    }

    /** Formats seconds as m:ss for the quiz countdown. */
    fun formatDuration(totalSeconds: Int): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }
}
