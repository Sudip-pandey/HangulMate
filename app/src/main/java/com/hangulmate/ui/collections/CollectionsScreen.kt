package com.hangulmate.ui.collections

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hangulmate.R
import com.hangulmate.domain.model.UserCollection
import com.hangulmate.domain.model.VocabularyWord
import com.hangulmate.ui.theme.Spacing

/**
 * User-created word collections.
 *
 * A collection groups words the user picks. Tapping a collection expands
 * it inline to show its words; tapping a word opens its detail screen
 * ([onWordClick]). Creating, renaming and deleting are done here; adding
 * words happens from the word detail screen. Removing a word from a
 * collection never deletes the word itself.
 */
@Composable
fun CollectionsScreen(
    onWordClick: (String) -> Unit,
    viewModel: CollectionsViewModel = hiltViewModel(),
) {
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val expandedId by viewModel.expandedId.collectAsStateWithLifecycle()
    val expandedWords by viewModel.expandedWords.collectAsStateWithLifecycle()

    var createDialogOpen by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<UserCollection?>(null) }
    var deleteTarget by remember { mutableStateOf<UserCollection?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(
            text = stringResource(R.string.collections_title),
            style = MaterialTheme.typography.headlineMedium,
        )

        Button(
            onClick = { createDialogOpen = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(stringResource(R.string.collections_new))
        }

        if (collections.isEmpty()) {
            Text(
                text = stringResource(R.string.collections_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(collections, key = { it.id }) { collection ->
                    val expanded = expandedId == collection.id
                    CollectionCard(
                        collection = collection,
                        expanded = expanded,
                        words = if (expanded) expandedWords else emptyList(),
                        onToggle = { viewModel.toggleExpanded(collection.id) },
                        onRename = { renameTarget = collection },
                        onDelete = { deleteTarget = collection },
                        onWordClick = onWordClick,
                        onRemoveWord = { wordId -> viewModel.removeWord(collection.id, wordId) },
                    )
                }
            }
        }
    }

    if (createDialogOpen) {
        CollectionNameDialog(
            title = stringResource(R.string.collections_create_title),
            initialName = "",
            confirmLabel = stringResource(R.string.collections_create),
            onConfirm = { name ->
                viewModel.createCollection(name)
                createDialogOpen = false
            },
            onDismiss = { createDialogOpen = false },
        )
    }

    renameTarget?.let { collection ->
        CollectionNameDialog(
            title = stringResource(R.string.collections_rename_title),
            initialName = collection.name,
            confirmLabel = stringResource(R.string.collections_rename),
            onConfirm = { name ->
                viewModel.renameCollection(collection.id, name)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    deleteTarget?.let { collection ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.collections_delete_title)) },
            text = {
                Text(stringResource(R.string.collections_delete_message, collection.name))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCollection(collection.id)
                        deleteTarget = null
                    },
                ) {
                    Text(
                        stringResource(R.string.collections_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun CollectionCard(
    collection: UserCollection,
    expanded: Boolean,
    words: List<VocabularyWord>,
    onToggle: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onWordClick: (String) -> Unit,
    onRemoveWord: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Folder, contentDescription = null)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = Spacing.sm),
                ) {
                    Text(text = collection.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = pluralStringResource(
                            R.plurals.collections_words_count,
                            collection.wordCount,
                            collection.wordCount
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(onClick = onRename) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.collections_rename_action),
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.collections_delete_action),
                    )
                }
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(
                        if (expanded) R.string.collections_hide_words else R.string.collections_show_words
                    ),
                )
            }

            if (expanded) {
                if (words.isEmpty()) {
                    Text(
                        text = stringResource(R.string.collections_no_words),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                } else {
                    Column(modifier = Modifier.padding(top = Spacing.sm)) {
                        words.forEach { word ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onWordClick(word.id) }
                                    .padding(vertical = Spacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = word.korean,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        text = word.nepali,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                                IconButton(onClick = { onRemoveWord(word.id) }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = stringResource(
                                            R.string.collections_remove_word
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionNameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.collections_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.trim().isNotEmpty(),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
