package com.hangulmate.ui.grammar

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
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hangulmate.R
import com.hangulmate.domain.model.ExampleSource
import com.hangulmate.domain.model.GrammarExample
import com.hangulmate.domain.model.GrammarPoint
import com.hangulmate.domain.model.PersonRule
import com.hangulmate.domain.model.TenseRule
import com.hangulmate.domain.repository.GrammarLoadState
import com.hangulmate.ui.components.EmptyState
import com.hangulmate.ui.components.LoadingState
import com.hangulmate.ui.components.SectionHeader
import com.hangulmate.ui.theme.Spacing

/**
 * One grammar point: form, structure, meaning in Korean / English /
 * Nepali, the past-tense rule and the person restriction (each with the
 * book's note), trilingual examples labelled by source, and common
 * mistakes. Every string on screen comes from the seed file or the
 * app's string resources — nothing is invented.
 */
@Composable
fun GrammarDetailScreen(
    pointId: String,
    onBack: () -> Unit,
    viewModel: GrammarViewModel = hiltViewModel(),
) {
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()

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
                text = stringResource(R.string.grammar_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
        }

        when (loadState) {
            GrammarLoadState.Loading -> {
                LoadingState(message = stringResource(R.string.grammar_loading))
            }
            is GrammarLoadState.Failed -> {
                EmptyState(message = stringResource(R.string.grammar_failed))
            }
            is GrammarLoadState.Ready -> {
                val point = viewModel.getPoint(pointId)
                if (point == null) {
                    EmptyState(message = stringResource(R.string.grammar_not_found))
                } else {
                    GrammarDetailContent(point = point)
                }
            }
        }
    }
}

@Composable
private fun GrammarDetailContent(point: GrammarPoint) {
    // Form + structure
    Text(
        text = point.form,
        style = MaterialTheme.typography.headlineMedium,
    )
    if (point.structure.isNotBlank()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = stringResource(R.string.grammar_detail_structure),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = point.structure,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
    Text(
        text = stringResource(
            R.string.grammar_source_page,
            point.book,
            point.unit,
            point.sourcePage,
        ),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // Meaning — Korean, English, Nepali (the order in the guide).
    SectionHeader(title = stringResource(R.string.grammar_detail_meaning))
    if (point.meaningKo.isNotBlank()) {
        MeaningBlock(
            label = stringResource(R.string.grammar_meaning_korean),
            text = point.meaningKo,
        )
    }
    if (point.meaningEn.isNotBlank()) {
        MeaningBlock(
            label = stringResource(R.string.grammar_meaning_english),
            text = point.meaningEn,
        )
    }
    if (point.meaningNe.isNotBlank()) {
        MeaningBlock(
            label = stringResource(R.string.grammar_meaning_nepali),
            text = point.meaningNe,
        )
    }

    // Past-tense rule
    SectionHeader(title = stringResource(R.string.grammar_tense_title))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = stringResource(tenseLabelRes(point.tensePast)),
                style = MaterialTheme.typography.titleMedium,
            )
            if (point.tenseNote.isNotBlank()) {
                Text(
                    text = point.tenseNote,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // Person restriction
    SectionHeader(title = stringResource(R.string.grammar_person_title))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = stringResource(personLabelRes(point.person)),
                style = MaterialTheme.typography.titleMedium,
            )
            if (point.personNote.isNotBlank()) {
                Text(
                    text = point.personNote,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // Examples
    if (point.examples.isNotEmpty()) {
        SectionHeader(title = stringResource(R.string.grammar_examples_title))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            point.examples.forEach { example ->
                ExampleCard(example = example)
            }
        }
    }

    // Common mistakes
    if (point.mistakes.isNotBlank()) {
        SectionHeader(title = stringResource(R.string.grammar_mistakes_title))
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = point.mistakes,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(Spacing.lg),
            )
        }
    }
}

@Composable
private fun MeaningBlock(label: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun ExampleCard(example: GrammarExample) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = example.ko,
                style = MaterialTheme.typography.titleMedium,
            )
            if (example.ne.isNotBlank()) {
                Text(
                    text = example.ne,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (example.en.isNotBlank()) {
                Text(
                    text = example.en,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(
                    if (example.source == ExampleSource.BOOK) {
                        R.string.grammar_example_book
                    } else {
                        R.string.grammar_example_authored
                    },
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun tenseLabelRes(tense: TenseRule): Int = when (tense) {
    TenseRule.YES -> R.string.grammar_tense_yes
    TenseRule.NO -> R.string.grammar_tense_no
    TenseRule.NA -> R.string.grammar_tense_na
    TenseRule.NOT_STATED -> R.string.grammar_tense_not_stated
}

private fun personLabelRes(person: PersonRule): Int = when (person) {
    PersonRule.ALL -> R.string.grammar_person_all
    PersonRule.FIRST_ONLY -> R.string.grammar_person_1st_only
    PersonRule.SECOND_ONLY -> R.string.grammar_person_2nd_only
    PersonRule.FIRST_SECOND -> R.string.grammar_person_1st_2nd
    PersonRule.SECOND_THIRD -> R.string.grammar_person_2nd_3rd
    PersonRule.NOT_STATED -> R.string.grammar_person_not_stated
}
