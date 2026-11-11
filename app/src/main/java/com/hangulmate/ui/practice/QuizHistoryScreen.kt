package com.hangulmate.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hangulmate.R
import com.hangulmate.domain.model.QuizResult
import com.hangulmate.domain.practice.QuizType
import com.hangulmate.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Localised label for a persisted quiz [QuizResult.type] key; null when unknown. */
private fun quizTypeLabelRes(typeKey: String): Int? = when (typeKey) {
    QuizType.KO_TO_NE.typeKey -> R.string.quiz_ko_to_ne
    QuizType.NE_TO_KO.typeKey -> R.string.quiz_ne_to_ko
    QuizType.LISTENING.typeKey -> R.string.quiz_listening
    QuizType.SPELLING.typeKey -> R.string.quiz_spelling
    QuizType.FILL_BLANK.typeKey -> R.string.quiz_fill_blank
    QuizType.RELATION.typeKey -> R.string.quiz_relation
    QuizType.IMAGE.typeKey -> R.string.quiz_image
    QuizType.EPS_MOCK.typeKey -> R.string.quiz_eps_mock
    else -> null
}

/** Quiz date in the device locale and time zone (e.g. 11 Nov 2026). */
private fun formatQuizDate(dateEpoch: Long): String {
    val date = Instant.ofEpochMilli(dateEpoch)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
        .format(date)
}

/**
 * Past quiz results, newest first: quiz type, date and score for each
 * run. Read-only — results are written when a quiz finishes.
 */
@Composable
fun QuizHistoryScreen(
    onBack: () -> Unit,
    viewModel: PracticeViewModel = hiltViewModel(),
) {
    val history by viewModel.quizHistory.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(
            text = stringResource(R.string.quiz_history_title),
            style = MaterialTheme.typography.headlineMedium,
        )

        if (history.isEmpty()) {
            Text(
                text = stringResource(R.string.quiz_history_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                contentPadding = PaddingValues(bottom = Spacing.sm),
            ) {
                items(history, key = { it.id }) { result ->
                    QuizHistoryRow(result = result)
                }
            }
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.quiz_result_back_practice))
        }
    }
}

@Composable
private fun QuizHistoryRow(result: QuizResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = quizTypeLabelRes(result.type)?.let { stringResource(it) }
                    ?: result.type,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = formatQuizDate(result.dateEpoch),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(
                    R.string.quiz_result_score,
                    result.score,
                    result.total,
                ) + " · ${result.percentage}%",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
