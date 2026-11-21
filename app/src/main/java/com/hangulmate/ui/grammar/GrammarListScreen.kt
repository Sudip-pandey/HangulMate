package com.hangulmate.ui.grammar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hangulmate.R
import com.hangulmate.domain.model.GrammarPoint
import com.hangulmate.domain.repository.GrammarLoadState
import com.hangulmate.ui.components.EmptyState
import com.hangulmate.ui.components.LoadingState
import com.hangulmate.ui.components.SectionHeader
import com.hangulmate.ui.theme.Spacing

/**
 * All 120 EPS-TOPIK grammar points, grouped by book then unit, with
 * Korean / Nepali / English search and a Book 1 / Book 2 filter.
 *
 * Layout follows EpsTopicsScreen: one LazyColumn, header row, search
 * field and filter chips as items. While searching the grouping is
 * dropped and matching points are shown flat, each with its unit label.
 */
@Composable
fun GrammarListScreen(
    onBack: () -> Unit,
    onPointClick: (String) -> Unit,
    viewModel: GrammarViewModel = hiltViewModel(),
) {
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val bookFilter by viewModel.bookFilter.collectAsStateWithLifecycle()
    val filtered by viewModel.filteredPoints.collectAsStateWithLifecycle()

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
                    text = stringResource(R.string.grammar_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.setQuery(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.grammar_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { viewModel.clearQuery() }) {
                            Icon(
                                Icons.Filled.Clear,
                                contentDescription = stringResource(
                                    R.string.grammar_clear_search,
                                ),
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { /* results update live */ }),
            )
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                FilterChip(
                    selected = bookFilter == null,
                    onClick = { viewModel.setBookFilter(null) },
                    label = { Text(stringResource(R.string.grammar_filter_all)) },
                )
                FilterChip(
                    selected = bookFilter == 1,
                    onClick = { viewModel.setBookFilter(1) },
                    label = { Text(stringResource(R.string.grammar_filter_book_1)) },
                )
                FilterChip(
                    selected = bookFilter == 2,
                    onClick = { viewModel.setBookFilter(2) },
                    label = { Text(stringResource(R.string.grammar_filter_book_2)) },
                )
            }
        }

        when (val state = loadState) {
            GrammarLoadState.Loading -> {
                item {
                    LoadingState(message = stringResource(R.string.grammar_loading))
                }
            }
            is GrammarLoadState.Failed -> {
                item {
                    EmptyState(message = stringResource(R.string.grammar_failed))
                }
            }
            is GrammarLoadState.Ready -> {
                item {
                    Text(
                        text = stringResource(
                            R.string.grammar_points_count,
                            state.points.size,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (filtered.isEmpty()) {
                    item {
                        EmptyState(message = stringResource(R.string.grammar_no_results))
                    }
                } else if (query.isBlank()) {
                    groupedItems(filtered, onPointClick)
                } else {
                    items(filtered, key = { it.id }) { point ->
                        GrammarRow(
                            point = point,
                            showUnit = true,
                            onClick = { onPointClick(point.id) },
                        )
                    }
                }
            }
        }
    }
}

/** Book → unit sections, each unit headed by its Korean + English title. */
private fun androidx.compose.foundation.lazy.LazyListScope.groupedItems(
    points: List<GrammarPoint>,
    onPointClick: (String) -> Unit,
) {
    val byBook = points.groupBy { it.book }.toSortedMap()
    for ((book, bookPoints) in byBook) {
        item(key = "book-$book") {
            SectionHeader(
                title = stringResource(
                    if (book == 1) {
                        R.string.grammar_book_1_title
                    } else {
                        R.string.grammar_book_2_title
                    },
                ),
            )
        }
        val byUnit = bookPoints.groupBy { it.unit }.toSortedMap()
        for ((unit, unitPoints) in byUnit) {
            val first = unitPoints.first()
            item(key = "unit-$book-$unit") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(
                        text = stringResource(
                            R.string.grammar_unit_label,
                            unit,
                            first.unitTitle,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (first.unitTitleEn.isNotBlank()) {
                        Text(
                            text = first.unitTitleEn,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(unitPoints, key = { it.id }) { point ->
                GrammarRow(
                    point = point,
                    showUnit = false,
                    onClick = { onPointClick(point.id) },
                )
            }
        }
    }
}

@Composable
private fun GrammarRow(
    point: GrammarPoint,
    showUnit: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = point.form,
                style = MaterialTheme.typography.titleMedium,
            )
            if (point.meaningNe.isNotBlank()) {
                Text(
                    text = point.meaningNe,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            if (showUnit) {
                Text(
                    text = stringResource(
                        R.string.grammar_unit_label_short,
                        point.book,
                        point.unit,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
