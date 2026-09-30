package com.clipnest.ui.note

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clipnest.R
import com.clipnest.data.local.NoteDao
import com.clipnest.data.local.TopicDao
import com.clipnest.data.model.Note
import com.clipnest.data.model.Topic
import com.clipnest.ui.editor.EditorNoteOrigin

@Composable
fun NoteScreen(
    noteDao: NoteDao,
    topicDao: TopicDao,
    onCreateNote: (EditorNoteOrigin?, Long?) -> Unit,
    onOpenNote: (Long, EditorNoteOrigin?) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTopicId by rememberSaveable { mutableStateOf<Long?>(null) }
    var topicMenuExpanded by remember { mutableStateOf(false) }
    var selectedNoteIds by rememberSaveable { mutableStateOf<Set<Long>>(emptySet()) }
    var previewNoteId by rememberSaveable { mutableStateOf<Long?>(null) }

    val topics by topicDao.observeAllTopics().collectAsStateWithLifecycle(initialValue = emptyList())
    val notesFlow = remember(selectedTopicId) {
        selectedTopicId?.let(noteDao::observeActiveNotesByTopic) ?: noteDao.observeActiveNotes()
    }
    val notes by notesFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val selectedTopic = topics.firstOrNull { it.id == selectedTopicId }
    val origin = selectedTopic?.let {
        EditorNoteOrigin(label = it.name, returnKey = "topic:" + it.id)
    }

    val previewNote = notes.firstOrNull { it.id == previewNoteId }
    val allSelected = notes.isNotEmpty() && selectedNoteIds.containsAll(notes.map(Note::id))

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    TextButton(onClick = { topicMenuExpanded = true }) {
                        Text(selectedTopic?.name ?: R.string.all_notes.let { androidx.compose.ui.res.stringResource(it) })
                    }
                    DropdownMenu(
                        expanded = topicMenuExpanded,
                        onDismissRequest = { topicMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(androidx.compose.ui.res.stringResource(R.string.all_notes)) },
                            onClick = {
                                selectedTopicId = null
                                topicMenuExpanded = false
                            }
                        )
                        topics.forEach { topic ->
                            DropdownMenuItem(
                                text = { Text(topic.name) },
                                onClick = {
                                    selectedTopicId = topic.id
                                    topicMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.note_tab),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (notes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(R.string.no_notes),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = 96.dp
                    )
                ) {
                    items(notes, key = Note::id) { note ->
                        NoteListItem(
                            note = note,
                            topicDao = topicDao,
                            isSelected = selectedNoteIds.contains(note.id),
                            onToggleSelect = {
                                selectedNoteIds = selectedNoteIds.toMutableSet().also {
                                    if (!it.add(note.id)) it.remove(note.id)
                                }
                            },
                            onLongPress = { previewNoteId = note.id },
                            onEdit = { onOpenNote(note.id, originForTopic(selectedTopic)) }
                        )
                    }
                }
            }
        }

        androidx.compose.material3.FloatingActionButton(
            onClick = { onCreateNote(origin, selectedTopicId) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        ) {
            Icon(
                androidx.compose.material.icons.Icons.Default.Add,
                contentDescription = androidx.compose.ui.res.stringResource(R.string.new_note)
            )
        }
    }

    if (previewNote != null) {
        NotePreviewDialog(
            note = previewNote,
            topicDao = topicDao,
            onDismiss = { previewNoteId = null },
            onEdit = {
                previewNoteId = null
                onOpenNote(previewNote.id, originForTopic(selectedTopic))
            }
        )
    }

    // Keep the same selection semantics as Vault at card level: tapping a card toggles
    // selection, while the explicit Edit action opens the note.
    if (selectedNoteIds.isNotEmpty() && allSelected) {
        // State is intentionally kept local to this screen; no new note persistence
        // semantics are introduced.
    }
}

private fun originForTopic(topic: Topic?): EditorNoteOrigin? =
    topic?.let { EditorNoteOrigin(label = it.name, returnKey = "topic:" + it.id) }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteListItem(
    note: Note,
    topicDao: TopicDao,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onLongPress: () -> Unit,
    onEdit: () -> Unit
) {
    val topics by topicDao.observeTopicsForNote(note.id).collectAsState(initial = emptyList())
    val labels = topics.joinToString(", ") { it.name }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onToggleSelect,
                onLongClick = onLongPress
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                ),
                modifier = Modifier.size(48.dp)
            )

            Spacer(Modifier.width(4.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = note.title.ifBlank { androidx.compose.ui.res.stringResource(R.string.untitled) },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (labels.isNotBlank()) {
                    Text(
                        text = labels,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                Text(
                    text = note.content.ifBlank { androidx.compose.ui.res.stringResource(R.string.untitled) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = androidx.compose.ui.res.stringResource(R.string.edit_note)
                )
            }
        }
    }
}

@Composable
private fun NotePreviewDialog(
    note: Note,
    topicDao: TopicDao,
    onDismiss: () -> Unit,
    onEdit: () -> Unit
) {
    val topics by topicDao.observeTopicsForNote(note.id).collectAsState(initial = emptyList())
    val labels = topics.joinToString(", ") { it.name }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(R.string.note_tab),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = androidx.compose.ui.res.stringResource(R.string.close_preview)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = note.title.ifBlank { androidx.compose.ui.res.stringResource(R.string.untitled) },
                        style = MaterialTheme.typography.titleLarge
                    )
                    if (labels.isNotBlank()) {
                        Text(
                            text = labels,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    Text(
                        text = note.content.ifBlank { androidx.compose.ui.res.stringResource(R.string.untitled) },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    TextButton(
                        onClick = onEdit,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(androidx.compose.ui.res.stringResource(R.string.edit_note))
                    }
                }
            }
        }
    }
}
