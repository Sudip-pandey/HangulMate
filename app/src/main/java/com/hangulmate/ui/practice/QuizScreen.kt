package com.hangulmate.ui.practice

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hangulmate.R
import com.hangulmate.domain.model.RelationKind
import com.hangulmate.domain.practice.QuizGenerator
import com.hangulmate.domain.practice.QuizQuestion
import com.hangulmate.domain.practice.QuizType
import com.hangulmate.speech.TtsState
import com.hangulmate.ui.theme.Spacing

private val CorrectGreen = Color(0xFF2E7D32)
private val WrongRed = Color(0xFFC62828)

/**
 * Runs one quiz: question card, 4 options, immediate feedback, then Next.
 * The timer (timed quizzes) lives in the ViewModel, so rotation does not
 * reset it. Finishing — last question or time-up — moves the shared state
 * to Finished and [PracticeScreen] switches to the result screen.
 */
@Composable
fun QuizScreen(
    onBack: () -> Unit,
    viewModel: PracticeViewModel = hiltViewModel(),
) {
    val state by viewModel.quizState.collectAsStateWithLifecycle()
    val ttsState by viewModel.ttsState.collectAsStateWithLifecycle()

    BackHandler { onBack() }

    when (val s = state) {
        QuizUiState.Idle -> {
            // Reached without starting a quiz; nothing to show.
            Column(
                modifier = Modifier.fillMaxSize().padding(Spacing.lg),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                OutlinedButton(onClick = onBack) {
                    Text(stringResource(R.string.quiz_back))
                }
            }
        }

        QuizUiState.Loading -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(Spacing.lg),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
        }

        is QuizUiState.NotEnoughWords -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.quiz_not_enough_words),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = stringResource(R.string.quiz_not_enough_words_detail, s.available),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onBack) {
                    Text(stringResource(R.string.quiz_back))
                }
            }
        }

        is QuizUiState.Active -> {
            QuizActiveContent(
                state = s,
                ttsState = ttsState,
                onBack = onBack,
                onSelectAnswer = { viewModel.selectAnswer(it) },
                onNext = { viewModel.nextQuestion() },
                onListenAgain = { viewModel.speakCurrentQuestionIfListening() },
            )
        }

        is QuizUiState.Finished -> {
            // PracticeScreen switches to the result screen; brief spinner meanwhile.
            Column(
                modifier = Modifier.fillMaxSize().padding(Spacing.lg),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun QuizActiveContent(
    state: QuizUiState.Active,
    ttsState: TtsState,
    onBack: () -> Unit,
    onSelectAnswer: (Int) -> Unit,
    onNext: () -> Unit,
    onListenAgain: () -> Unit,
) {
    val question = state.currentQuestion ?: return
    val selected = state.answers[state.currentIndex]
    val answered = selected != null

    // Auto-play the word when a listening question appears.
    LaunchedEffect(state.currentIndex, question.type) {
        if (question.type == QuizType.LISTENING) onListenAgain()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.quiz_back),
                )
            }
            Text(
                text = stringResource(
                    R.string.quiz_question_number,
                    state.currentIndex + 1,
                    state.questions.size,
                ),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            state.remainingSeconds?.let { remaining ->
                Text(
                    text = stringResource(
                        R.string.quiz_time_left,
                        QuizGenerator.formatDuration(remaining),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (remaining < 60) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        LinearProgressIndicator(
            progress = { state.currentIndex.toFloat() / state.questions.size },
            modifier = Modifier.fillMaxWidth(),
        )

        QuestionCard(
            question = question,
            ttsState = ttsState,
            onListenAgain = onListenAgain,
        )

        // Options ------------------------------------------------------
        question.options.forEachIndexed { index, option ->
            val containerColor = when {
                !answered -> MaterialTheme.colorScheme.surfaceVariant
                index == question.correctIndex -> CorrectGreen
                index == selected -> WrongRed
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            val contentColor = when {
                !answered -> MaterialTheme.colorScheme.onSurfaceVariant
                index == question.correctIndex || index == selected -> Color.White
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Button(
                onClick = { onSelectAnswer(index) },
                enabled = !answered,
                colors = ButtonDefaults.buttonColors(
                    containerColor = containerColor,
                    contentColor = contentColor,
                    disabledContainerColor = containerColor,
                    disabledContentColor = contentColor,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = option, style = MaterialTheme.typography.bodyLarge)
            }
        }

        if (answered) {
            Text(
                text = if (selected == question.correctIndex) {
                    stringResource(R.string.quiz_correct)
                } else {
                    stringResource(R.string.quiz_wrong)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected == question.correctIndex) CorrectGreen else WrongRed,
            )
            question.explanationNe?.let { explanation ->
                Text(
                    text = explanation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(Spacing.sm))
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (state.isLastQuestion) {
                        stringResource(R.string.quiz_see_result)
                    } else {
                        stringResource(R.string.quiz_next)
                    },
                )
            }
        }
    }
}

@Composable
private fun QuestionCard(
    question: QuizQuestion,
    ttsState: TtsState,
    onListenAgain: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            when (question.type) {
                QuizType.KO_TO_NE, QuizType.NE_TO_KO -> {
                    Text(
                        text = question.prompt,
                        style = MaterialTheme.typography.headlineLarge,
                    )
                }

                QuizType.LISTENING -> {
                    Text(
                        text = stringResource(R.string.quiz_listen_prompt),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    IconButton(
                        onClick = onListenAgain,
                        enabled = ttsState == TtsState.Ready,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VolumeUp,
                            contentDescription = stringResource(R.string.quiz_listen_again),
                        )
                    }
                    if (ttsState == TtsState.MissingVoice) {
                        Text(
                            text = stringResource(R.string.tts_missing_voice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                QuizType.SPELLING -> {
                    Text(
                        text = stringResource(R.string.quiz_spelling_prompt),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = question.prompt,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }

                QuizType.FILL_BLANK -> {
                    Text(
                        text = question.prompt,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }

                QuizType.RELATION -> {
                    Text(
                        text = if (question.relationKind == RelationKind.OPPOSITE) {
                            stringResource(R.string.quiz_relation_opposite_prompt)
                        } else {
                            stringResource(R.string.quiz_relation_similar_prompt)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = question.prompt,
                        style = MaterialTheme.typography.headlineLarge,
                    )
                }

                QuizType.IMAGE, QuizType.EPS_MOCK -> {
                    Text(
                        text = question.prompt,
                        style = MaterialTheme.typography.headlineLarge,
                    )
                }
            }
        }
    }
}
