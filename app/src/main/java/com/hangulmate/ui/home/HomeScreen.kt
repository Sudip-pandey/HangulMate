package com.hangulmate.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hangulmate.R
import com.hangulmate.brand.BrandConfig
import com.hangulmate.domain.model.VocabularyWord
import com.hangulmate.ui.components.EmptyState
import com.hangulmate.ui.components.LoadingState
import com.hangulmate.ui.components.SectionHeader
import com.hangulmate.ui.components.WordCard
import com.hangulmate.ui.theme.Spacing

/**
 * Home feed (Nepali-first): time-of-day greeting, daily-goal progress,
 * word of the day, today's new words, continue-learning card, recently
 * searched, weak words, and the EPS practice shortcut.
 *
 * The search bar is a tap target: tapping it opens the Dictionary (where
 * the query is typed); the camera icon opens the Camera tab.
 */
@Composable
fun HomeScreen(
    onNavigateToDictionary: () -> Unit,
    onNavigateToPractice: () -> Unit,
    onNavigateToWord: (String) -> Unit,
    onNavigateToCamera: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(
            text = BrandConfig.appName,
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(state.greetingRes),
            style = MaterialTheme.typography.bodyLarge,
        )

        // Quick search bar — a real tap target: tapping anywhere except
        // the camera icon opens the Dictionary. A readOnly TextField can
        // swallow the tap, so this is a Card styled as a search bar.
        Card(
            onClick = onNavigateToDictionary,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Icon(Icons.Filled.Search, contentDescription = null)
                Text(
                    text = stringResource(R.string.home_search_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onNavigateToCamera) {
                    Icon(
                        Icons.Filled.CameraAlt,
                        contentDescription = stringResource(R.string.home_camera_content_desc),
                    )
                }
            }
        }

        when {
            state.isPreparing -> {
                LoadingState(message = stringResource(R.string.home_preparing))
            }
            state.seedFailureMessage != null -> {
                EmptyState(
                    message = stringResource(R.string.home_seed_failed),
                    actionLabel = stringResource(R.string.home_open_dictionary),
                    onActionClick = onNavigateToDictionary,
                )
            }
            state.totalCount == 0 -> {
                EmptyState(
                    message = stringResource(R.string.home_empty_state),
                    actionLabel = stringResource(R.string.home_open_dictionary),
                    onActionClick = onNavigateToDictionary,
                )
            }
            else -> {
                // Daily goal progress
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text(
                            text = stringResource(R.string.home_daily_goal_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        LinearProgressIndicator(
                            progress = {
                                (state.wordsLearnedToday.toFloat() / state.dailyGoal)
                                    .coerceIn(0f, 1f)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = stringResource(
                                R.string.home_goal_progress,
                                state.wordsLearnedToday,
                                state.dailyGoal,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (state.wordsReviewedToday > 0) {
                            Text(
                                text = stringResource(
                                    R.string.home_reviewed_today,
                                    state.wordsReviewedToday,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Streak / learned / mastered / remaining
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    StatItem(
                        value = state.streakDays.toString(),
                        label = stringResource(R.string.home_streak),
                        modifier = Modifier.weight(1f),
                    )
                    StatItem(
                        value = state.learnedCount.toString(),
                        label = stringResource(R.string.home_learned),
                        modifier = Modifier.weight(1f),
                    )
                    StatItem(
                        value = state.masteredCount.toString(),
                        label = stringResource(R.string.home_mastered),
                        modifier = Modifier.weight(1f),
                    )
                    StatItem(
                        value = (state.totalCount - state.learnedCount)
                            .coerceAtLeast(0).toString(),
                        label = stringResource(R.string.home_remaining),
                        modifier = Modifier.weight(1f),
                    )
                }

                // Word of the day
                state.wordOfTheDay?.let { word ->
                    SectionHeader(title = stringResource(R.string.home_word_of_the_day))
                    WordCard(
                        word = word,
                        onClick = { onNavigateToWord(word.id) },
                    )
                }

                // Today's new words
                SectionHeader(title = stringResource(R.string.home_today_new_words))
                if (state.todayNewWords.isEmpty()) {
                    Text(
                        text = stringResource(R.string.home_no_new_words),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        contentPadding = PaddingValues(vertical = Spacing.xs),
                    ) {
                        items(state.todayNewWords, key = { it.id }) { word ->
                            CompactWordCard(
                                word = word,
                                onClick = { onNavigateToWord(word.id) },
                            )
                        }
                    }
                }

                // Continue learning — first item of the revision queue
                state.revisionQueue.firstOrNull()?.let { word ->
                    SectionHeader(title = stringResource(R.string.home_continue_learning))
                    Card(
                        onClick = { onNavigateToWord(word.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(Spacing.lg),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            Text(
                                text = word.korean,
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = word.nepali,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = stringResource(
                                    R.string.home_words_due,
                                    state.revisionQueue.size,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(onClick = { onNavigateToWord(word.id) }) {
                                Text(stringResource(R.string.home_review_now))
                            }
                        }
                    }
                }

                // EPS practice shortcut
                Card(
                    onClick = onNavigateToPractice,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text(
                            text = stringResource(R.string.home_eps_practice),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.home_eps_practice_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = onNavigateToPractice) {
                            Text(stringResource(R.string.home_start_practice))
                        }
                    }
                }

                // Recently searched
                if (state.recentlySearched.isNotEmpty()) {
                    SectionHeader(title = stringResource(R.string.home_recently_searched))
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        state.recentlySearched.forEach { word ->
                            WordCard(
                                word = word,
                                onClick = { onNavigateToWord(word.id) },
                            )
                        }
                    }
                }

                // Weak words revision strip
                if (state.difficultWords.isNotEmpty()) {
                    SectionHeader(title = stringResource(R.string.home_weak_words))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        contentPadding = PaddingValues(vertical = Spacing.xs),
                    ) {
                        items(state.difficultWords, key = { it.id }) { word ->
                            CompactWordCard(
                                word = word,
                                onClick = { onNavigateToWord(word.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(text = value, style = MaterialTheme.typography.titleLarge)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CompactWordCard(
    word: VocabularyWord,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.width(160.dp),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = word.korean,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = word.nepali,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (word.romanization.isNotBlank()) {
                Text(
                    text = word.romanization,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
