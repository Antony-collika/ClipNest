package com.clipnest.ui.note

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

    val topics by topicDao.observeAllTopics().collectAsStateWithLifecycle(initialValue = emptyList())
    val notesFlow = remember(selectedTopicId) {
        selectedTopicId?.let(noteDao::observeActiveNotesByTopic) ?: noteDao.observeActiveNotes()
    }
    val notes by notesFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val selectedTopic = topics.firstOrNull { it.id == selectedTopicId }
    val origin = selectedTopic?.let {
        EditorNoteOrigin(
            label = it.name,
            returnKey = "topic:" + it.id
        )
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    TextButton(onClick = { topicMenuExpanded = true }) {
                        Text(selectedTopic?.name ?: stringResource(R.string.all_notes))
                    }
                    DropdownMenu(
                        expanded = topicMenuExpanded,
                        onDismissRequest = { topicMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.all_notes)) },
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
                    text = stringResource(R.string.note_tab),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (notes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.no_notes),
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
                            onClick = { onOpenNote(note.id, originForTopic(selectedTopic)) }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { onCreateNote(origin, selectedTopicId) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_note))
        }
    }
}

private fun originForTopic(topic: Topic?): EditorNoteOrigin? =
    topic?.let { EditorNoteOrigin(label = it.name, returnKey = "topic:" + it.id) }

@Composable
private fun NoteListItem(
    note: Note,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = note.title.ifBlank { stringResource(R.string.untitled) },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = note.content.ifBlank { stringResource(R.string.untitled) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
