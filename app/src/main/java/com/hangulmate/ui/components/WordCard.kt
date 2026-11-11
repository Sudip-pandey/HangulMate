package com.hangulmate.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.hangulmate.R
import com.hangulmate.domain.model.PartOfSpeech
import com.hangulmate.domain.model.VocabularyWord
import com.hangulmate.ui.theme.Spacing

/**
 * One vocabulary word in a list: Korean prominent, Nepali + English
 * meanings, romanization, part-of-speech chip and EPS importance badge.
 * The favorite star is shown only when [onFavoriteClick] is supplied.
 */
@Composable
fun WordCard(
    word: VocabularyWord,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFavoriteClick: (() -> Unit)? = null,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = word.korean,
                    style = MaterialTheme.typography.titleLarge,
                )
                if (onFavoriteClick != null) {
                    IconButton(onClick = onFavoriteClick) {
                        Icon(
                            imageVector = if (word.isFavorite) {
                                Icons.Filled.Favorite
                            } else {
                                Icons.Filled.FavoriteBorder
                            },
                            contentDescription = stringResource(R.string.favorite),
                            tint = if (word.isFavorite) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
            Text(
                text = word.nepali,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = word.english,
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(stringResource(partOfSpeechLabelRes(word.partOfSpeech))) },
                )
                EpsImportanceBadge(importance = word.epsImportance)
            }
        }
    }
}

/** Localised label for a part of speech (strings live in strings_dictionary.xml). */
fun partOfSpeechLabelRes(partOfSpeech: PartOfSpeech): Int = when (partOfSpeech) {
    PartOfSpeech.NOUN -> R.string.pos_noun
    PartOfSpeech.VERB -> R.string.pos_verb
    PartOfSpeech.ADJECTIVE -> R.string.pos_adjective
    PartOfSpeech.ADVERB -> R.string.pos_adverb
    PartOfSpeech.EXPRESSION -> R.string.pos_expression
    PartOfSpeech.NUMBER -> R.string.pos_number
    PartOfSpeech.PRONOUN -> R.string.pos_pronoun
    PartOfSpeech.PARTICLE -> R.string.pos_particle
    PartOfSpeech.INTERJECTION -> R.string.pos_interjection
}
