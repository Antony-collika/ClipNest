package com.clipnest.ui.trash

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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.clipnest.R
import com.clipnest.data.local.NoteDao
import com.clipnest.data.model.NoteCardProjection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun TrashScreen(noteDao: NoteDao, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val notes by noteDao.observeDeletedNotes().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    val allSelected = notes.isNotEmpty() && selectedIds.size == notes.size
    val selectedNotes = notes.filter { it.id in selectedIds }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Text(stringResource(R.string.drawer_trash), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (notes.isNotEmpty()) {
                IconButton(onClick = { selectedIds = if (allSelected) emptySet() else notes.map { it.id }.toSet() }) {
                    Icon(Icons.Default.Check, contentDescription = stringResource(R.string.select_all))
                }
            }
        }

        if (selectedNotes.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = {
                    scope.launch(Dispatchers.IO) {
                        selectedNotes.forEach { noteDao.setDeleted(it.id, false, null, System.currentTimeMillis()) }
                        selectedIds = emptySet()
                    }
                }) {
                    Icon(Icons.Default.Restore, contentDescription = null)
                    Text(stringResource(R.string.trash_restore), modifier = Modifier.padding(start = 6.dp))
                }
                Button(onClick = {
                    scope.launch(Dispatchers.IO) {
                        selectedNotes.forEach { noteDao.deleteNotePermanently(it.id) }
                        selectedIds = emptySet()
                    }
                }) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                    Text(stringResource(R.string.trash_delete_forever), modifier = Modifier.padding(start = 6.dp))
                }
            }
        }

        if (notes.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.trash_empty), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(notes, key = { it.id }) { note ->
                    TrashNoteRow(
                        note = note,
                        selected = note.id in selectedIds,
                        onToggle = { selectedIds = if (note.id in selectedIds) selectedIds - note.id else selectedIds + note.id },
                        onRestore = { scope.launch(Dispatchers.IO) { noteDao.setDeleted(note.id, false, null, System.currentTimeMillis()) } },
                        onDeleteForever = { scope.launch(Dispatchers.IO) { noteDao.deleteNotePermanently(note.id) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun TrashNoteRow(note: NoteCardProjection, selected: Boolean, onToggle: () -> Unit, onRestore: () -> Unit, onDeleteForever: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = selected, onCheckedChange = { onToggle() })
        Column(modifier = Modifier.weight(1f)) {
            Text(note.title.ifBlank { stringResource(R.string.untitled) }, style = MaterialTheme.typography.titleMedium)
            Text(note.preview, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        IconButton(onClick = onRestore) { Icon(Icons.Default.Restore, contentDescription = stringResource(R.string.trash_restore)) }
        IconButton(onClick = onDeleteForever) { Icon(Icons.Default.DeleteForever, contentDescription = stringResource(R.string.trash_delete_forever)) }
    }
}
