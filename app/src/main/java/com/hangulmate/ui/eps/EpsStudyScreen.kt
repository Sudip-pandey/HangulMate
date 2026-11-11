package com.hangulmate.ui.eps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hangulmate.R
import com.hangulmate.speech.TtsState
import com.hangulmate.ui.theme.Spacing

/**
 * Flashcard study run for one EPS topic, sharing [EpsTopicsViewModel]
 * with [EpsTopicsScreen] (same NavBackStackEntry scope, so the same
 * ViewModel instance). Front: Korean word + TTS button. Back (after tap):
 * Nepali + English meanings and the example sentence. "I know" /
 * "I don't know" feed recordReview and advance to the next card.
 */
@Composable
fun EpsStudyScreen(
    onBack: () -> Unit,
    viewModel: EpsTopicsViewModel = hiltViewModel(),
) {
    val studyState by viewModel.studyState.collectAsStateWithLifecycle()
    val ttsState by viewModel.ttsState.collectAsStateWithLifecycle()

    when (val s = studyState) {
        StudyUiState.Idle -> {
            // Study was exited; show nothing and let the topics screen take over.
        }

        StudyUiState.Loading -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(Spacing.lg),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
        }

        is StudyUiState.Empty -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(
                        R.string.eps_study_title,
                        s.topic.nameNe,
                    ),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = stringResource(R.string.eps_study_empty),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onBack) {
                    Text(stringResource(R.string.eps_study_back_topics))
                }
            }
        }

        is StudyUiState.Active -> {
            if (s.isFinished) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.eps_study_done),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = stringResource(
                            R.string.eps_study_done_detail,
                            s.words.size,
                            s.topic.nameNe,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = onBack) {
                        Text(stringResource(R.string.eps_study_back_topics))
                    }
                }
            } else {
                val word = s.current ?: return
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
                                contentDescription = stringResource(R.string.eps_study_back_topics),
                            )
                        }
                        Text(
                            text = stringResource(
                                R.string.eps_study_title,
                                s.topic.nameNe,
                            ),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        text = "${s.index + 1} / ${s.words.size}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LinearProgressIndicator(
                        progress = { s.index.toFloat() / s.words.size },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleReveal() },
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.xl),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            Text(
                                text = word.korean,
                                style = MaterialTheme.typography.headlineLarge,
                            )
                            Text(
                                text = word.romanization,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            IconButton(
                                onClick = { viewModel.speak(word.korean) },
                                enabled = ttsState == TtsState.Ready,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.VolumeUp,
                                    contentDescription = word.korean,
                                )
                            }
                            if (s.revealed) {
                                Text(
                                    text = word.nepali,
                                    style = MaterialTheme.typography.headlineSmall,
                                )
                                Text(
                                    text = word.english,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                if (word.exampleKo.isNotBlank()) {
                                    Text(
                                        text = word.exampleKo,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = word.exampleNe,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else {
                                Text(
                                    text = stringResource(R.string.eps_study_tap_reveal),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { viewModel.markKnown() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.eps_study_know))
                    }
                    OutlinedButton(
                        onClick = { viewModel.markUnknown() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.eps_study_dont_know))
                    }
                }
            }
        }
    }
}
