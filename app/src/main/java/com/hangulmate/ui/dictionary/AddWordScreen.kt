package com.hangulmate.ui.dictionary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hangulmate.R
import com.hangulmate.domain.model.PartOfSpeech
import com.hangulmate.domain.model.Topic
import com.hangulmate.ui.components.SectionHeader
import com.hangulmate.ui.components.partOfSpeechLabelRes
import com.hangulmate.ui.theme.Spacing

/**
 * Add-a-word form: Korean (required) + Nepali + English + part of
 * speech + topic. On save the new word opens in the word detail
 * screen ([onWordSaved]). Nothing is invented: romanization, examples
 * and syllables stay empty until the user adds them elsewhere.
 */
@Composable
fun AddWordScreen(
    onWordSaved: (String) -> Unit,
    viewModel: AddWordViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val savedWordId by viewModel.savedWordId.collectAsStateWithLifecycle()

    LaunchedEffect(savedWordId) {
        savedWordId?.let { onWordSaved(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(
            text = stringResource(R.string.add_word_title),
            style = MaterialTheme.typography.headlineMedium,
        )

        OutlinedTextField(
            value = state.korean,
            onValueChange = { viewModel.setKorean(it) },
            label = { Text(stringResource(R.string.add_word_korean)) },
            isError = state.koreanError,
            supportingText = {
                if (state.koreanError) {
                    Text(stringResource(R.string.add_word_korean_required))
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.nepali,
            onValueChange = { viewModel.setNepali(it) },
            label = { Text(stringResource(R.string.add_word_nepali)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.english,
            onValueChange = { viewModel.setEnglish(it) },
            label = { Text(stringResource(R.string.add_word_english)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        SectionHeader(title = stringResource(R.string.add_word_part_of_speech))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            PartOfSpeech.entries.forEach { partOfSpeech ->
                FilterChip(
                    selected = state.partOfSpeech == partOfSpeech,
                    onClick = { viewModel.setPartOfSpeech(partOfSpeech) },
                    label = { Text(stringResource(partOfSpeechLabelRes(partOfSpeech))) },
                )
            }
        }

        SectionHeader(title = stringResource(R.string.add_word_topic))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Topic.entries.forEach { topic ->
                FilterChip(
                    selected = state.topic == topic,
                    onClick = { viewModel.setTopic(topic) },
                    label = { Text(topic.nameNe) },
                )
            }
        }

        Button(
            onClick = { viewModel.save() },
            enabled = !state.saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.add_word_save))
        }
    }
}
