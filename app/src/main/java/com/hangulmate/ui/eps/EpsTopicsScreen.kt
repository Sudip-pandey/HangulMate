package com.hangulmate.ui.eps

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hangulmate.R
import com.hangulmate.domain.model.EpsImportance
import com.hangulmate.domain.model.Topic
import com.hangulmate.ui.theme.Spacing

/**
 * The 14 EPS-TOPIK topics with live word counts and Nepali names.
 * Difficulty (1/2/3) and EPS importance (high/medium/low) filters apply to
 * the flashcard study session started by tapping a topic.
 */
@Composable
fun EpsTopicsScreen(
    onBack: () -> Unit,
    viewModel: EpsTopicsViewModel = hiltViewModel(),
) {
    val studyState by viewModel.studyState.collectAsStateWithLifecycle()

    BackHandler {
        if (studyState is StudyUiState.Idle) onBack() else viewModel.exitStudy()
    }

    when (studyState) {
        StudyUiState.Idle -> EpsTopicsList(viewModel = viewModel, onBack = onBack)
        else -> EpsStudyScreen(onBack = { viewModel.exitStudy() }, viewModel = viewModel)
    }
}

@Composable
private fun EpsTopicsList(
    viewModel: EpsTopicsViewModel,
    onBack: () -> Unit,
) {
    val topicCounts by viewModel.topicCounts.collectAsStateWithLifecycle()
    val difficultyFilter by viewModel.difficultyFilter.collectAsStateWithLifecycle()
    val importanceFilter by viewModel.importanceFilter.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
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
                    text = stringResource(R.string.eps_topics_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Text(
                text = stringResource(R.string.eps_filter_difficulty),
                style = MaterialTheme.typography.titleSmall,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                FilterChip(
                    label = stringResource(R.string.eps_filter_all),
                    selected = difficultyFilter == null,
                    onClick = { viewModel.setDifficultyFilter(null) },
                )
                (1..3).forEach { level ->
                    FilterChip(
                        label = level.toString(),
                        selected = difficultyFilter == level,
                        onClick = { viewModel.setDifficultyFilter(level) },
                    )
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.eps_filter_importance),
                style = MaterialTheme.typography.titleSmall,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                FilterChip(
                    label = stringResource(R.string.eps_filter_all),
                    selected = importanceFilter == null,
                    onClick = { viewModel.setImportanceFilter(null) },
                )
                FilterChip(
                    label = stringResource(R.string.eps_importance_high),
                    selected = importanceFilter == EpsImportance.HIGH,
                    onClick = { viewModel.setImportanceFilter(EpsImportance.HIGH) },
                )
                FilterChip(
                    label = stringResource(R.string.eps_importance_medium),
                    selected = importanceFilter == EpsImportance.MEDIUM,
                    onClick = { viewModel.setImportanceFilter(EpsImportance.MEDIUM) },
                )
                FilterChip(
                    label = stringResource(R.string.eps_importance_low),
                    selected = importanceFilter == EpsImportance.LOW,
                    onClick = { viewModel.setImportanceFilter(EpsImportance.LOW) },
                )
            }
        }

        items(Topic.entries) { topic ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.startStudy(topic) },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = topicIcon(topic),
                        contentDescription = null,
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = Spacing.md),
                    ) {
                        Text(
                            text = topic.nameNe,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = topic.nameEn,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.eps_words_count,
                            topicCounts[topic] ?: 0,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        } else {
            null
        },
    )
}

/** Maps the topic's stored icon name to a Material icon. */
private fun topicIcon(topic: Topic): ImageVector = when (topic.iconName) {
    "home" -> Icons.Filled.Home
    "work" -> Icons.Filled.Work
    "agriculture" -> Icons.Filled.Agriculture
    "factory" -> Icons.Filled.Factory
    "construction" -> Icons.Filled.Construction
    "restaurant" -> Icons.Filled.Restaurant
    "directions_bus" -> Icons.Filled.DirectionsBus
    "health_and_safety" -> Icons.Filled.HealthAndSafety
    "chat" -> Icons.Filled.Chat
    "pin" -> Icons.Filled.Pin
    "explore" -> Icons.Filled.Explore
    "build" -> Icons.Filled.Build
    "shield" -> Icons.Filled.Shield
    else -> Icons.Filled.Category
}
