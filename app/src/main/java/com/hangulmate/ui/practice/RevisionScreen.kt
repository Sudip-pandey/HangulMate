package com.hangulmate.ui.practice

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hangulmate.R
import com.hangulmate.speech.TtsState
import com.hangulmate.ui.theme.Spacing

/**
 * Today's SRS revision: flashcards from [PracticeViewModel.revisionState]
 * in the repository's own queue order (most-wrong first, then due date —
 * never re-sorted here). Tap a card to reveal the meaning, then answer
 * with one of the three buttons:
 * - "I know this" → recordReview(true)
 * - "I need practice" → recordReview(true) then setLearningState LEARNING
 * - "I forgot this" → recordReview(false) → DIFFICULT, re-queued once
 *   at the end of this session (and due again tomorrow per the SRS rule).
 */
@Composable
fun RevisionScreen(
    onBack: () -> Unit,
    viewModel: PracticeViewModel = hiltViewModel(),
) {
    val revisionState by viewModel.revisionState.collectAsStateWithLifecycle()
    val ttsState by viewModel.ttsState.collectAsStateWithLifecycle()

    BackHandler { onBack() }

    when (val s = revisionState) {
        RevisionUiState.Loading -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(Spacing.lg),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
        }

        RevisionUiState.Empty -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.revision_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = stringResource(R.string.revision_empty),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onBack) {
                    Text(stringResource(R.string.revision_back))
                }
            }
        }

        is RevisionUiState.Active -> {
            if (s.isFinished) {
                RevisionComplete(
                    reviewed = s.reviewed,
                    onBack = onBack,
                )
            } else {
                RevisionCardContent(
                    state = s,
                    ttsState = ttsState,
                    onBack = onBack,
                    onSpeak = { word -> viewModel.speak(word) },
                    onReview = { action -> viewModel.reviewCurrent(action) },
                )
            }
        }
    }
}

@Composable
private fun RevisionCardContent(
    state: RevisionUiState.Active,
    ttsState: TtsState,
    onBack: () -> Unit,
    onSpeak: (String) -> Unit,
    onReview: (RevisionAction) -> Unit,
) {
    val word = state.current ?: return
    // Reset the reveal state for each new card.
    var revealed by rememberSaveable(word.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.revision_back),
                    )
                }
                Text(
                    text = stringResource(R.string.revision_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = stringResource(
                    R.string.revision_progress,
                    state.reviewed,
                    state.queue.size,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            LinearProgressIndicator(
                progress = { state.reviewed.toFloat() / state.queue.size },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { revealed = !revealed },
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
                    onClick = { onSpeak(word.korean) },
                    enabled = ttsState == TtsState.Ready,
                ) {
                    Icon(
                        imageVector = Icons.Filled.VolumeUp,
                        contentDescription = word.korean,
                    )
                }
                if (revealed) {
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
                        text = stringResource(R.string.revision_tap_to_reveal),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Button(
            onClick = { onReview(RevisionAction.KNOW) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.revision_know))
        }
        OutlinedButton(
            onClick = { onReview(RevisionAction.NEED_PRACTICE) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.revision_need_practice))
        }
        OutlinedButton(
            onClick = { onReview(RevisionAction.FORGOT) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.revision_forgot))
        }
    }
}

@Composable
private fun RevisionComplete(
    reviewed: Int,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.revision_done),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.revision_done_detail, reviewed),
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onBack) {
            Text(stringResource(R.string.revision_back))
        }
    }
}
