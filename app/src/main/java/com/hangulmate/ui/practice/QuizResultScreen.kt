package com.hangulmate.ui.practice

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hangulmate.R
import com.hangulmate.ui.theme.Spacing

private val CorrectGreen = Color(0xFF2E7D32)
private val WrongRed = Color(0xFFC62828)

/**
 * Quiz result: score, per-question review with correct answers and
 * explanations, and the mistakes section — each wrong word can be added to
 * difficult (recordReview(false) + setLearningState DIFFICULT) so it is
 * prioritised in tomorrow's revision queue.
 */
@Composable
fun QuizResultScreen(
    onBackToHub: () -> Unit,
    onRetry: () -> Unit,
    viewModel: PracticeViewModel = hiltViewModel(),
) {
    val state by viewModel.quizState.collectAsStateWithLifecycle()

    BackHandler { onBackToHub() }

    val finished = state as? QuizUiState.Finished
    if (finished == null) {
        // No finished quiz (e.g. recreated after process death).
        Column(
            modifier = Modifier.fillMaxSize().padding(Spacing.lg),
            verticalArrangement = Arrangement.Center,
        ) {
            OutlinedButton(onClick = onBackToHub, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.quiz_result_back_practice))
            }
        }
        return
    }
    val result = finished.result

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(
            text = stringResource(R.string.quiz_result_title),
            style = MaterialTheme.typography.headlineMedium,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = stringResource(
                        R.string.quiz_result_score,
                        result.score,
                        result.total,
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "${result.percentage}%",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // Mistakes -----------------------------------------------------
        Text(
            text = stringResource(R.string.quiz_result_mistakes),
            style = MaterialTheme.typography.titleLarge,
        )
        if (finished.wrongQuestions.isEmpty()) {
            Text(
                text = stringResource(R.string.quiz_result_no_mistakes),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            finished.wrongQuestions.forEach { question ->
                val wordId = question.targetWordId
                val alreadyAdded = wordId in finished.markedDifficult
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text(
                            text = question.prompt.ifBlank { question.ttsText.orEmpty() },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(
                                R.string.quiz_result_correct_answer,
                                question.options[question.correctIndex],
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = CorrectGreen,
                        )
                        question.explanationNe?.let { explanation ->
                            Text(
                                text = explanation,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(
                            onClick = { viewModel.addWrongWordToDifficult(wordId) },
                            enabled = !alreadyAdded,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (alreadyAdded) {
                                    stringResource(R.string.quiz_result_added_difficult)
                                } else {
                                    stringResource(R.string.quiz_result_add_difficult)
                                },
                            )
                        }
                    }
                }
            }
        }

        // Full review --------------------------------------------------
        Text(
            text = stringResource(R.string.quiz_result_review),
            style = MaterialTheme.typography.titleLarge,
        )
        finished.questions.forEachIndexed { index, question ->
            val selected = finished.answers[index]
            val isCorrect = selected == question.correctIndex
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(
                        text = question.prompt.ifBlank { question.ttsText.orEmpty() },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (selected != null) {
                        Text(
                            text = stringResource(
                                R.string.quiz_result_your_answer,
                                question.options[selected],
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isCorrect) CorrectGreen else WrongRed,
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.quiz_result_correct_answer,
                            question.options[question.correctIndex],
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CorrectGreen,
                    )
                    question.explanationNe?.let { explanation ->
                        Text(
                            text = explanation,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(
                onClick = onRetry,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.quiz_result_retry))
            }
            OutlinedButton(
                onClick = onBackToHub,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.quiz_result_back_practice))
            }
        }
    }
}
