package com.hangulmate.ui.dictionary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hangulmate.R
import com.hangulmate.domain.model.Topic
import com.hangulmate.ml.GeminiError
import com.hangulmate.ui.components.EmptyState
import com.hangulmate.ui.components.SectionHeader
import com.hangulmate.ui.components.WordCard
import com.hangulmate.ui.theme.Spacing

/**
 * Dictionary: 3-way search (Korean / Nepali / English / romanization)
 * with a 300ms debounce, live top-5 suggestions, search-history chips,
 * a favorites-only toggle and topic filter chips.
 *
 * Matching is SQL LIKE `%pattern%` in the repository; when LIKE returns
 * few results the ViewModel adds a typo-tolerant second pass (choseong
 * matching + edit distance ≤ 2 — see [FuzzySearch]). When nothing
 * matches, the screen says so instead of inventing results.
 */
@Composable
fun DictionaryScreen(
    onWordClick: (String) -> Unit,
    initialQuery: String = "",
    onAddWord: () -> Unit = {},
    viewModel: DictionaryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(initialQuery) {
        viewModel.applyInitialQuery(initialQuery)
    }

    val onOpenWord: (String) -> Unit = { wordId ->
        viewModel.onWordOpened(wordId)
        onWordClick(wordId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = { viewModel.setQuery(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.dictionary_search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotBlank()) {
                    IconButton(onClick = { viewModel.clearQuery() }) {
                        Icon(
                            Icons.Filled.Clear,
                            contentDescription = stringResource(R.string.dictionary_clear_search),
                        )
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { /* results update live */ }),
        )

        // Live suggestions dropdown — top 5 matches as the user types.
        if (state.suggestions.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = Spacing.xs)) {
                    state.suggestions.forEach { word ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenWord(word.id) }
                                .padding(
                                    horizontal = Spacing.lg,
                                    vertical = Spacing.sm,
                                ),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
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
                        }
                    }
                }
            }
        }

        // Favorites-only toggle + topic filter chips.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = state.favoritesOnly,
                onClick = { viewModel.setFavoritesOnly(!state.favoritesOnly) },
                label = { Text(stringResource(R.string.dictionary_favorites_only)) },
                leadingIcon = {
                    Icon(Icons.Filled.Favorite, contentDescription = null)
                },
            )
            OutlinedButton(onClick = onAddWord) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(
                    text = stringResource(R.string.dictionary_add_word),
                    modifier = Modifier.padding(start = Spacing.xs),
                )
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            contentPadding = PaddingValues(vertical = Spacing.xs),
        ) {
            item {
                FilterChip(
                    selected = state.selectedTopic == null,
                    onClick = { viewModel.selectTopic(null) },
                    label = { Text(stringResource(R.string.dictionary_all_topics)) },
                )
            }
            items(Topic.entries, key = { it.key }) { topic ->
                FilterChip(
                    selected = state.selectedTopic == topic,
                    onClick = {
                        viewModel.selectTopic(if (state.selectedTopic == topic) null else topic)
                    },
                    label = { Text(topic.nameNe) },
                )
            }
        }

        // Search-history chips (only before the user starts typing).
        if (state.query.isBlank() && state.recentWords.isNotEmpty()) {
            SectionHeader(
                title = stringResource(R.string.dictionary_recent_searches),
                actionLabel = stringResource(R.string.dictionary_clear_history),
                onActionClick = { viewModel.clearHistory() },
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                contentPadding = PaddingValues(vertical = Spacing.xs),
            ) {
                items(state.recentWords, key = { it.id }) { word ->
                    AssistChip(
                        onClick = { viewModel.searchAgain(word) },
                        label = { Text(word.korean) },
                    )
                }
            }
        }

        // Results / empty states.
        when {
            state.totalCount == 0 -> {
                EmptyState(message = stringResource(R.string.dictionary_empty_database))
            }
            state.query.isBlank() && state.selectedTopic == null && !state.favoritesOnly -> {
                EmptyState(message = stringResource(R.string.dictionary_no_query))
            }
            state.results.isEmpty() && state.query.isNotBlank() -> {
                if (!state.favoritesOnly && state.selectedTopic == null) {
                    // Local search found nothing: show the Gemini
                    // online fallback states instead of a dead end.
                    OnlineSearchSection(
                        state = state.onlineState,
                        onWordClick = onOpenWord,
                        onFavoriteClick = { viewModel.toggleFavorite(it) },
                        onRetry = { viewModel.retryOnlineSearch() },
                    )
                } else {
                    EmptyState(message = stringResource(R.string.dictionary_no_results))
                }
            }
            state.results.isEmpty() && state.favoritesOnly -> {
                EmptyState(message = stringResource(R.string.dictionary_no_favorites))
            }
            state.results.isEmpty() && state.selectedTopic != null -> {
                EmptyState(message = stringResource(R.string.dictionary_no_topic_words))
            }
            else -> {
                Text(
                    text = stringResource(
                        R.string.dictionary_results_count,
                        state.results.size,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    contentPadding = PaddingValues(bottom = Spacing.lg),
                ) {
                    items(state.results, key = { it.id }) { word ->
                        WordCard(
                            word = word,
                            onClick = { onOpenWord(word.id) },
                            onFavoriteClick = { viewModel.toggleFavorite(word.id) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * What the Dictionary screen shows when the local search finds
 * nothing: the Gemini online fallback states. When Gemini finds the
 * word it is already saved locally, so it is shown as a normal word
 * card — tapping it opens the detail page, which carries the AI badge.
 */
@Composable
private fun OnlineSearchSection(
    state: OnlineSearchState,
    onWordClick: (String) -> Unit,
    onFavoriteClick: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        OnlineSearchState.Idle -> {
            EmptyState(message = stringResource(R.string.dictionary_no_results))
        }
        OnlineSearchState.Searching -> {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                CircularProgressIndicator()
                Text(
                    text = stringResource(R.string.gemini_search_searching),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        is OnlineSearchState.Found -> {
            Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = stringResource(R.string.gemini_search_found_saved),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                WordCard(
                    word = state.word,
                    onClick = { onWordClick(state.word.id) },
                    onFavoriteClick = { onFavoriteClick(state.word.id) },
                )
            }
        }
        OnlineSearchState.NotFound -> {
            EmptyState(message = stringResource(R.string.gemini_search_not_found))
        }
        OnlineSearchState.NoKey -> {
            Text(
                text = stringResource(R.string.gemini_search_no_key),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
            )
        }
        is OnlineSearchState.Error -> {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(
                    text = stringResource(geminiSearchErrorRes(state.error)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onRetry) {
                    Text(stringResource(R.string.gemini_search_retry))
                }
            }
        }
    }
}

/** Search-specific error strings (the camera ones mention photos). */
private fun geminiSearchErrorRes(error: GeminiError): Int = when (error) {
    GeminiError.INVALID_KEY -> R.string.gemini_search_error_invalid_key
    GeminiError.QUOTA_EXHAUSTED -> R.string.gemini_search_error_quota
    GeminiError.NO_INTERNET -> R.string.gemini_search_error_no_internet
    GeminiError.TIMEOUT -> R.string.gemini_search_error_timeout
    GeminiError.EMPTY_RESPONSE -> R.string.gemini_search_error_empty
    GeminiError.UNKNOWN -> R.string.gemini_search_error_unknown
}
