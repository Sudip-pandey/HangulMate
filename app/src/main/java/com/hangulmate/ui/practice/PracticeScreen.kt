package com.hangulmate.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hangulmate.R
import com.hangulmate.domain.practice.QuizGenerator
import com.hangulmate.domain.practice.QuizType
import com.hangulmate.speech.TtsState
import com.hangulmate.ui.eps.EpsTopicsScreen
import com.hangulmate.ui.theme.Spacing

/** Sub-screens of the Practice tab; the top-level NavHost is untouched. */
private enum class PracticeDestination {
    HUB,
    QUIZ,
    RESULT,
    REVISION,
    EPS_TOPICS,
    HISTORY,
}

/**
 * Practice hub: today's revision entry point, the quiz menu (including the
 * timed EPS mock test), and the EPS topics entry point. Quiz, result,
 * revision and EPS screens are shown through this internal state machine;
 * the ViewModel is shared across them (same NavBackStackEntry scope).
 */
@Composable
fun PracticeScreen() {
    val viewModel: PracticeViewModel = hiltViewModel()
    val quizState by viewModel.quizState.collectAsStateWithLifecycle()
    var destination by rememberSaveable { mutableStateOf(PracticeDestination.HUB) }

    // When a running quiz finishes (timer or last question), show the result.
    LaunchedEffect(quizState) {
        if (quizState is QuizUiState.Finished && destination == PracticeDestination.QUIZ) {
            destination = PracticeDestination.RESULT
        }
    }

    when (destination) {
        PracticeDestination.HUB -> PracticeHub(
            viewModel = viewModel,
            onStartQuiz = { type, count, timed ->
                viewModel.startQuiz(type = type, count = count, timed = timed)
                destination = PracticeDestination.QUIZ
            },
            onOpenRevision = {
                viewModel.refreshRevisionQueue()
                destination = PracticeDestination.REVISION
            },
            onOpenEpsTopics = { destination = PracticeDestination.EPS_TOPICS },
            onOpenQuizHistory = { destination = PracticeDestination.HISTORY },
        )

        PracticeDestination.QUIZ -> QuizScreen(
            onBack = {
                viewModel.resetQuiz()
                destination = PracticeDestination.HUB
            },
        )

        PracticeDestination.RESULT -> QuizResultScreen(
            onBackToHub = {
                viewModel.resetQuiz()
                destination = PracticeDestination.HUB
            },
            onRetry = {
                viewModel.retryQuiz()
                destination = PracticeDestination.QUIZ
            },
        )

        PracticeDestination.REVISION -> RevisionScreen(
            onBack = {
                viewModel.refreshRevisionQueue()
                destination = PracticeDestination.HUB
            },
        )

        PracticeDestination.EPS_TOPICS -> EpsTopicsScreen(
            onBack = { destination = PracticeDestination.HUB },
        )

        PracticeDestination.HISTORY -> QuizHistoryScreen(
            onBack = { destination = PracticeDestination.HUB },
        )
    }
}

@Composable
private fun PracticeHub(
    viewModel: PracticeViewModel,
    onStartQuiz: (QuizType, Int, Boolean) -> Unit,
    onOpenRevision: () -> Unit,
    onOpenEpsTopics: () -> Unit,
    onOpenQuizHistory: () -> Unit,
) {
    val revisionState by viewModel.revisionState.collectAsStateWithLifecycle()
    val totalCount by viewModel.totalCount.collectAsStateWithLifecycle()
    val ttsState by viewModel.ttsState.collectAsStateWithLifecycle()

    val dueCount = (revisionState as? RevisionUiState.Active)?.queue?.size ?: 0
    val hasVocabulary = totalCount > 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(
            text = stringResource(R.string.practice_title),
            style = MaterialTheme.typography.headlineMedium,
        )

        if (!hasVocabulary) {
            Text(
                text = stringResource(R.string.practice_empty_state),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // Today's revision -------------------------------------------
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                    )
                    Text(
                        text = stringResource(R.string.practice_revision_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = Spacing.sm),
                    )
                }
                Text(
                    text = if (dueCount > 0) {
                        stringResource(R.string.practice_revision_due, dueCount)
                    } else {
                        stringResource(R.string.practice_revision_all_done)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = onOpenRevision,
                    enabled = dueCount > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.practice_revision_start))
                }
            }
        }

        // Quizzes ------------------------------------------------------
        Text(
            text = stringResource(R.string.practice_hub_quizzes),
            style = MaterialTheme.typography.titleLarge,
        )

        QuizMenuCard(
            icon = Icons.Filled.Quiz,
            title = stringResource(R.string.quiz_ko_to_ne),
            description = stringResource(R.string.quiz_ko_to_ne_desc),
            enabled = hasVocabulary,
            onClick = { onStartQuiz(QuizType.KO_TO_NE, QuizGenerator.DEFAULT_QUESTION_COUNT, false) },
        )
        QuizMenuCard(
            icon = Icons.Filled.Quiz,
            title = stringResource(R.string.quiz_ne_to_ko),
            description = stringResource(R.string.quiz_ne_to_ko_desc),
            enabled = hasVocabulary,
            onClick = { onStartQuiz(QuizType.NE_TO_KO, QuizGenerator.DEFAULT_QUESTION_COUNT, false) },
        )
        QuizMenuCard(
            icon = Icons.Filled.VolumeUp,
            title = stringResource(R.string.quiz_listening),
            description = stringResource(R.string.quiz_listening_desc),
            enabled = hasVocabulary && ttsState == TtsState.Ready,
            disabledReason = when (ttsState) {
                TtsState.MissingVoice -> stringResource(R.string.tts_missing_voice)
                TtsState.Checking -> stringResource(R.string.tts_checking)
                is TtsState.Error -> stringResource(R.string.tts_error)
                TtsState.Ready -> null
            },
            onClick = { onStartQuiz(QuizType.LISTENING, QuizGenerator.DEFAULT_QUESTION_COUNT, false) },
        )
        QuizMenuCard(
            icon = Icons.Filled.School,
            title = stringResource(R.string.quiz_spelling),
            description = stringResource(R.string.quiz_spelling_desc),
            enabled = hasVocabulary,
            onClick = { onStartQuiz(QuizType.SPELLING, QuizGenerator.DEFAULT_QUESTION_COUNT, false) },
        )
        QuizMenuCard(
            icon = Icons.Filled.MenuBook,
            title = stringResource(R.string.quiz_fill_blank),
            description = stringResource(R.string.quiz_fill_blank_desc),
            enabled = hasVocabulary,
            onClick = { onStartQuiz(QuizType.FILL_BLANK, QuizGenerator.DEFAULT_QUESTION_COUNT, false) },
        )
        QuizMenuCard(
            icon = Icons.Filled.Quiz,
            title = stringResource(R.string.quiz_relation),
            description = stringResource(R.string.quiz_relation_desc),
            enabled = hasVocabulary,
            onClick = { onStartQuiz(QuizType.RELATION, QuizGenerator.DEFAULT_QUESTION_COUNT, false) },
        )
        // Image quiz exists in the generator but the seed bundles no
        // images — shown disabled with the honest reason, never faked.
        QuizMenuCard(
            icon = Icons.Filled.Image,
            title = stringResource(R.string.quiz_image),
            description = stringResource(R.string.quiz_image_desc),
            enabled = false,
            disabledReason = stringResource(R.string.quiz_image_disabled),
            onClick = {},
        )

        // Timed EPS mock test ------------------------------------------
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Filled.School, contentDescription = null)
                    Text(
                        text = stringResource(R.string.quiz_eps_mock),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = Spacing.sm),
                    )
                }
                Text(
                    text = stringResource(R.string.quiz_eps_mock_desc),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(
                        onClick = { onStartQuiz(QuizType.EPS_MOCK, 10, true) },
                        enabled = hasVocabulary,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.quiz_questions_10))
                    }
                    Button(
                        onClick = { onStartQuiz(QuizType.EPS_MOCK, 20, true) },
                        enabled = hasVocabulary,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.quiz_questions_20))
                    }
                }
            }
        }

        // Quiz history -------------------------------------------------
        QuizMenuCard(
            icon = Icons.Filled.History,
            title = stringResource(R.string.quiz_history_title),
            description = stringResource(R.string.quiz_history_desc),
            enabled = true,
            onClick = onOpenQuizHistory,
        )

        // EPS topics ---------------------------------------------------
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Filled.MenuBook, contentDescription = null)
                    Text(
                        text = stringResource(R.string.practice_eps_topics_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = Spacing.sm),
                    )
                }
                Text(
                    text = stringResource(R.string.practice_eps_topics_desc),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = onOpenEpsTopics,
                    enabled = hasVocabulary,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.practice_eps_topics_open))
                }
            }
        }
    }
}

@Composable
private fun QuizMenuCard(
    icon: ImageVector,
    title: String,
    description: String,
    enabled: Boolean,
    disabledReason: String? = null,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = null)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(text = description, style = MaterialTheme.typography.bodyMedium)
                if (!enabled && disabledReason != null) {
                    Text(
                        text = disabledReason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            IconButton(onClick = onClick, enabled = enabled) {
                Icon(
                    imageVector = Icons.Filled.ArrowForward,
                    contentDescription = title,
                )
            }
        }
    }
}
