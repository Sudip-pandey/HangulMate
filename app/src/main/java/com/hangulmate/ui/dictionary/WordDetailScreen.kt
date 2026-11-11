package com.hangulmate.ui.dictionary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hangulmate.R
import com.hangulmate.domain.model.LearningState
import com.hangulmate.domain.model.UserCollection
import com.hangulmate.domain.model.VocabularyWord
import com.hangulmate.domain.model.WordRelation
import com.hangulmate.speech.TtsState
import com.hangulmate.ui.components.EmptyState
import com.hangulmate.ui.components.EpsImportanceBadge
import com.hangulmate.ui.components.LoadingState
import com.hangulmate.ui.components.SectionHeader
import com.hangulmate.ui.components.partOfSpeechLabelRes
import com.hangulmate.ui.theme.Spacing

/**
 * Full word detail: Korean word + romanization + syllable chips, Nepali and
 * English meanings, TTS speaker, part of speech / topic / difficulty /
 * EPS importance, example sentences with speak buttons, Similar / Opposite /
 * Confusing relations (only what the data contains), learning actions
 * (including "Add to collection", which is the only place words enter a
 * collection), and previous / next navigation within the same topic.
 *
 * Related words are tappable only when the relation resolves to a word in
 * the database; an unresolvable target is shown as plain text, never a
 * dead link.
 */
@Composable
fun WordDetailScreen(
    wordId: String,
    onWordClick: (String) -> Unit,
    viewModel: WordDetailViewModel = hiltViewModel(),
) {
    val word by viewModel.word.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val ttsState by viewModel.ttsState.collectAsStateWithLifecycle()
    val navigation by viewModel.navigation.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val addedToCollection by viewModel.addedToCollection.collectAsStateWithLifecycle()
    // Local copy so the null check below smart-casts.
    val currentWord = word

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        when {
            !loaded -> {
                LoadingState(message = stringResource(R.string.word_detail_loading))
            }
            currentWord == null -> {
                EmptyState(
                    message = stringResource(R.string.word_detail_not_found),
                )
            }
            else -> {
                WordDetailContent(
                    word = currentWord,
                    ttsState = ttsState,
                    navigation = navigation,
                    collections = collections,
                    addedToCollection = addedToCollection,
                    onSpeak = { viewModel.speak(it) },
                    onToggleFavorite = { viewModel.toggleFavorite() },
                    onMarkLearned = { viewModel.markLearned() },
                    onMarkDifficult = { viewModel.markDifficult() },
                    onRecordReview = { viewModel.recordReview(it) },
                    onAddToCollection = { viewModel.addWordToCollection(it) },
                    onCreateCollectionAndAdd = { viewModel.createCollectionAndAddWord(it) },
                    onClearAddedFeedback = { viewModel.clearAddedToCollection() },
                    onWordClick = onWordClick,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordDetailContent(
    word: VocabularyWord,
    ttsState: TtsState,
    navigation: TopicNavigation,
    collections: List<UserCollection>,
    addedToCollection: String?,
    onSpeak: (String) -> Unit,
    onToggleFavorite: () -> Unit,
    onMarkLearned: () -> Unit,
    onMarkDifficult: () -> Unit,
    onRecordReview: (Boolean) -> Unit,
    onAddToCollection: (Long) -> Unit,
    onCreateCollectionAndAdd: (String) -> Unit,
    onClearAddedFeedback: () -> Unit,
    onWordClick: (String) -> Unit,
) {
    var addToCollectionOpen by remember { mutableStateOf(false) }
    // Headword + pronunciation
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = word.korean,
            style = MaterialTheme.typography.displayLarge,
        )
        SpeakButton(
            enabled = ttsState == TtsState.Ready,
            onClick = { onSpeak(word.korean) },
        )
    }
    if (word.romanization.isNotBlank()) {
        Text(
            text = word.romanization,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (word.syllables.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            word.syllables.forEach { syllable ->
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(syllable) },
                )
            }
        }
    }

    // TTS state message (honest: never pretend a voice exists).
    when (ttsState) {
        TtsState.MissingVoice -> {
            Text(
                text = stringResource(R.string.tts_missing_voice),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        is TtsState.Error -> {
            Text(
                text = stringResource(R.string.tts_error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        TtsState.Checking -> {
            Text(
                text = stringResource(R.string.tts_checking),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TtsState.Ready -> Unit
    }

    // Meanings
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = stringResource(R.string.word_detail_nepali_meaning),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = word.nepali, style = MaterialTheme.typography.titleLarge)
            if (word.nepaliStatus == "missing") {
                // The nepali column holds the English gloss as a display
                // fallback for entries without a Nepali translation yet —
                // say so plainly instead of presenting English as Nepali.
                Text(
                    text = stringResource(R.string.word_detail_nepali_pending),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(R.string.word_detail_english_meaning),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = word.english, style = MaterialTheme.typography.bodyLarge)
        }
    }

    // Part of speech / topic / difficulty / EPS importance
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        AssistChip(
            onClick = {},
            enabled = false,
            label = { Text(stringResource(partOfSpeechLabelRes(word.partOfSpeech))) },
        )
        AssistChip(
            onClick = {},
            enabled = false,
            label = { Text(word.topic.nameNe) },
        )
        AssistChip(
            onClick = {},
            enabled = false,
            label = { Text(stringResource(R.string.word_detail_difficulty, word.difficulty)) },
        )
        EpsImportanceBadge(importance = word.epsImportance)
    }

    // Example sentences — each with its own speak button.
    if (word.exampleKo.isNotBlank() ||
        word.exampleNe.isNotBlank() ||
        word.exampleEn.isNotBlank()
    ) {
        SectionHeader(title = stringResource(R.string.word_detail_example))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                if (word.exampleKo.isNotBlank()) {
                    ExampleLine(
                        text = word.exampleKo,
                        ttsEnabled = ttsState == TtsState.Ready,
                        onSpeak = { onSpeak(word.exampleKo) },
                    )
                }
                if (word.exampleNe.isNotBlank()) {
                    ExampleLine(
                        text = word.exampleNe,
                        ttsEnabled = ttsState == TtsState.Ready,
                        onSpeak = { onSpeak(word.exampleNe) },
                    )
                }
                if (word.exampleEn.isNotBlank()) {
                    ExampleLine(
                        text = word.exampleEn,
                        ttsEnabled = ttsState == TtsState.Ready,
                        onSpeak = { onSpeak(word.exampleEn) },
                    )
                }
            }
        }
    }

    // Learning actions
    SectionHeader(title = stringResource(R.string.word_detail_your_progress))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        OutlinedButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = if (word.isFavorite) {
                    Icons.Filled.Favorite
                } else {
                    Icons.Filled.FavoriteBorder
                },
                contentDescription = null,
            )
            Text(
                text = stringResource(R.string.favorite),
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }
        OutlinedButton(
            onClick = onMarkLearned,
            enabled = word.learningState != LearningState.KNOWN &&
                word.learningState != LearningState.MASTERED,
        ) {
            Text(stringResource(R.string.word_detail_mark_learned))
        }
        OutlinedButton(
            onClick = onMarkDifficult,
            enabled = word.learningState != LearningState.DIFFICULT,
        ) {
            Text(stringResource(R.string.word_detail_mark_difficult))
        }
        OutlinedButton(onClick = { onRecordReview(true) }) {
            Text(stringResource(R.string.word_detail_remembered))
        }
        OutlinedButton(onClick = { onRecordReview(false) }) {
            Text(stringResource(R.string.word_detail_forgot))
        }
        OutlinedButton(
            onClick = {
                onClearAddedFeedback()
                addToCollectionOpen = true
            },
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(
                text = stringResource(R.string.word_detail_add_to_collection),
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }
    }
    addedToCollection?.let { collectionName ->
        Text(
            text = stringResource(R.string.word_detail_added_to_collection, collectionName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }

    if (addToCollectionOpen) {
        AddToCollectionDialog(
            collections = collections,
            onAddToCollection = { collectionId ->
                onAddToCollection(collectionId)
                addToCollectionOpen = false
            },
            onCreateCollectionAndAdd = { name ->
                onCreateCollectionAndAdd(name)
                addToCollectionOpen = false
            },
            onDismiss = { addToCollectionOpen = false },
        )
    }

    // Relations — only what the data contains; no invented opposites/synonyms.
    RelationSection(
        title = stringResource(R.string.word_detail_similar),
        relations = word.similar,
        onWordClick = onWordClick,
    )
    RelationSection(
        title = stringResource(R.string.word_detail_opposite),
        relations = word.opposite,
        onWordClick = onWordClick,
    )
    RelationSection(
        title = stringResource(R.string.word_detail_confusing),
        relations = word.confusing,
        onWordClick = onWordClick,
    )

    // Previous / next within the same topic (hidden at the ends).
    // Local copies so the null checks smart-cast.
    val previousWordId = navigation.previousWordId
    val nextWordId = navigation.nextWordId
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (previousWordId != null) {
            OutlinedButton(
                onClick = { onWordClick(previousWordId) },
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = null)
                Text(
                    text = stringResource(R.string.word_detail_previous),
                    modifier = Modifier.padding(start = Spacing.xs),
                )
            }
        }
        if (nextWordId != null) {
            OutlinedButton(
                onClick = { onWordClick(nextWordId) },
            ) {
                Text(
                    text = stringResource(R.string.word_detail_next),
                    modifier = Modifier.padding(end = Spacing.xs),
                )
                Icon(Icons.Filled.ArrowForward, contentDescription = null)
            }
        }
    }
}

@Composable
private fun AddToCollectionDialog(
    collections: List<UserCollection>,
    onAddToCollection: (Long) -> Unit,
    onCreateCollectionAndAdd: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.word_detail_add_to_collection_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (collections.isEmpty()) {
                    Text(
                        text = stringResource(R.string.word_detail_no_collections),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        collections.forEach { collection ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAddToCollection(collection.id) }
                                    .padding(vertical = Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = collection.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = pluralStringResource(
                                        R.plurals.collections_words_count,
                                        collection.wordCount,
                                        collection.wordCount,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.word_detail_new_collection_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { onCreateCollectionAndAdd(newName) },
                    enabled = newName.trim().isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.word_detail_create_collection_and_add))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun SpeakButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
    ) {
        Icon(
            Icons.Filled.PlayArrow,
            contentDescription = stringResource(R.string.word_detail_listen),
        )
    }
}

@Composable
private fun ExampleLine(
    text: String,
    ttsEnabled: Boolean,
    onSpeak: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        SpeakButton(enabled = ttsEnabled, onClick = onSpeak)
    }
}

@Composable
private fun RelationSection(
    title: String,
    relations: List<WordRelation>,
    onWordClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (relations.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SectionHeader(title = title)
        relations.forEach { relation ->
            RelationRow(relation = relation, onWordClick = onWordClick)
        }
    }
}

@Composable
private fun RelationRow(
    relation: WordRelation,
    onWordClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val targetId = relation.targetWordId
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (targetId != null) {
                    Modifier.clickable { onWordClick(targetId) }
                } else {
                    Modifier
                },
            ),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = relation.targetKorean,
                style = MaterialTheme.typography.titleMedium,
            )
            if (relation.noteNe.isNotBlank()) {
                Text(
                    text = relation.noteNe,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!relation.noteEn.isNullOrBlank()) {
                Text(
                    text = relation.noteEn,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
