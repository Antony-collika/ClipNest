package com.clipnest.ui.trash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clipnest.R
import com.clipnest.data.local.NoteDao
import com.clipnest.data.model.Note
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun TrashScreen(noteDao: NoteDao, modifier: Modifier = Modifier) {
    val notes by noteDao.observeDeletedNotes().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = CoroutineScope(Dispatchers.IO)

    if (notes.isEmpty()) {
        Column(modifier = modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.trash_empty), style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(notes, key = { it.id }) { note ->
            TrashNoteRow(
                note = note,
                onRestore = { scope.launch { noteDao.setDeleted(note.id, false, null, System.currentTimeMillis()) } },
                onDeleteForever = { scope.launch { noteDao.deleteNotePermanently(note.id) } }
            )
        }
    }
}

@Composable
private fun TrashNoteRow(note: Note, onRestore: () -> Unit, onDeleteForever: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(note.title.ifBlank { stringResource(R.string.untitled) }, style = MaterialTheme.typography.titleMedium)
        Text(note.preview, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onRestore) {
                Icon(Icons.Default.Restore, contentDescription = null)
                Text(stringResource(R.string.trash_restore), modifier = Modifier.padding(start = 6.dp))
            }
            IconButton(onClick = onDeleteForever) {
                Icon(Icons.Default.DeleteForever, contentDescription = stringResource(R.string.trash_delete_forever))
            }
        }
    }
}
