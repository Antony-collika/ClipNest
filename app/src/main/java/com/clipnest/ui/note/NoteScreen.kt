package com.clipnest.ui.note

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.clipnest.R
import com.clipnest.data.local.NoteDao
import com.clipnest.data.local.TopicDao
import com.clipnest.data.model.Note
import com.clipnest.domain.RelativeTimeFormatter
import com.clipnest.data.model.Topic
import com.clipnest.ui.editor.EditorNoteOrigin

@Composable
fun NoteScreen(
    noteDao: NoteDao,
    topicDao: TopicDao,
    onCreateNote: (EditorNoteOrigin?, Long?) -> Unit,
    onOpenNote: (Long, EditorNoteOrigin?) -> Unit,
    selectedNoteIds: Set<Long>,
    onSelectionChanged: (Set<Long>) -> Unit,
    onVisibleNoteIdsChanged: (Set<Long>) -> Unit,
    isSearchOpen: Boolean,
    searchQuery: String,
    modifier: Modifier = Modifier
) {
    var selectedTopicId by rememberSaveable { mutableStateOf<Long?>(null) }
    var topicMenuExpanded by remember { mutableStateOf(false) }
    var previewNoteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var previewAnchorY by remember { mutableStateOf(0f) }

    val topics by topicDao.observeAllTopics().collectAsState(initial = emptyList())
    val notesFlow = remember(selectedTopicId) {
        selectedTopicId?.let(noteDao::observeActiveNotesByTopic) ?: noteDao.observeActiveNotes()
    }
    val notes by notesFlow.collectAsState(initial = emptyList())
    val filteredNotes = remember(notes, searchQuery) {
        if (searchQuery.isBlank()) {
            notes
        } else {
            val query = searchQuery.trim()
            notes.filter { note ->
                note.title.contains(query, ignoreCase = true) ||
                    note.content.contains(query, ignoreCase = true)
            }
        }
    }

    val selectedTopic = topics.firstOrNull { it.id == selectedTopicId }
    val origin = selectedTopic?.let {
        EditorNoteOrigin(label = it.name, returnKey = "topic:" + it.id)
    }

    LaunchedEffect(filteredNotes.map(Note::id)) {
        onVisibleNoteIdsChanged(filteredNotes.map(Note::id).toSet())
        val visibleIds = filteredNotes.map(Note::id).toSet()
        val pruned = selectedNoteIds.intersect(visibleIds)
        if (pruned != selectedNoteIds) onSelectionChanged(pruned)
        if (previewNoteId != null && filteredNotes.none { it.id == previewNoteId }) {
            previewNoteId = null
        }
    }

    val previewNote = filteredNotes.firstOrNull { it.id == previewNoteId }

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

            if (filteredNotes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (searchQuery.isBlank()) stringResource(R.string.no_notes)
                        else stringResource(R.string.no_search_results),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp)
                ) {
                    items(filteredNotes, key = Note::id) { note ->
                        NoteListItem(
                            note = note,
                            topicDao = topicDao,
                            isSelected = selectedNoteIds.contains(note.id),
                            onToggleSelect = {
                                onSelectionChanged(
                                    selectedNoteIds.toMutableSet().also {
                                        if (!it.add(note.id)) it.remove(note.id)
                                    }
                                )
                            },
                            onLongPress = { anchorY ->
                                previewAnchorY = anchorY
                                previewNoteId = note.id
                            },
                            onEdit = { onOpenNote(note.id, originForTopic(selectedTopic)) }
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

    if (previewNote != null) {
        NotePreviewPopup(
            note = previewNote,
            topicDao = topicDao,
            anchorY = previewAnchorY,
            onDismiss = { previewNoteId = null },
            onEdit = {
                previewNoteId = null
                onOpenNote(previewNote.id, originForTopic(selectedTopic))
            }
        )
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
    onLongPress: (Float) -> Unit,
    onEdit: () -> Unit
) {
    val topics by topicDao.observeTopicsForNote(note.id).collectAsState(initial = emptyList())
    val labels = topics.joinToString(", ") { it.name }
    var anchorY by remember { mutableStateOf(0f) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { anchorY = it.boundsInWindow().top }
            .combinedClickable(onClick = onToggleSelect, onLongClick = { onLongPress(anchorY) })
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
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
            Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                Text(
                    text = note.title.ifBlank { stringResource(R.string.untitled) },
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
                    text = note.content.ifBlank { stringResource(R.string.untitled) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.edit_note)
                )
            }
        }
    }
}

@Composable
private fun NotePreviewPopup(
    note: Note,
    topicDao: TopicDao,
    anchorY: Float,
    onDismiss: () -> Unit,
    onEdit: () -> Unit
) {
    val topics by topicDao.observeTopicsForNote(note.id).collectAsState(initial = emptyList())
    val labels = topics.joinToString(", ") { it.name }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    var visible by remember { mutableStateOf(false) }
    var dismissing by remember { mutableStateOf(false) }
    var dragOffsetY by remember(note.id, anchorY) { mutableStateOf(0f) }

    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val popupHeight = 360.dp
    val popupHeightPx = with(density) { popupHeight.toPx() }
    val marginPx = with(density) { 12.dp.toPx() }
    val spaceBelow = screenHeightPx - anchorY
    val canOpenBelow = spaceBelow >= popupHeightPx + marginPx
    val canOpenAbove = anchorY >= popupHeightPx + marginPx
    val opensBelow = if (anchorY <= 0f) true else if (canOpenBelow) true else if (canOpenAbove) false else spaceBelow >= anchorY
    val rawVerticalOffset = if (opensBelow) anchorY + with(density) { 20.dp.toPx() } else anchorY - popupHeightPx - with(density) { 20.dp.toPx() }
    val minPopupOffset = marginPx
    val maxPopupOffset = (screenHeightPx - popupHeightPx - marginPx).coerceAtLeast(marginPx)
    val verticalOffset = (rawVerticalOffset + dragOffsetY).coerceIn(minPopupOffset, maxPopupOffset).toInt()
    val popupWidth = minOf(360.dp, (configuration.screenWidthDp - 24).dp)

    LaunchedEffect(Unit) { visible = true }

    fun dismissAnimated() {
        if (dismissing) return
        dismissing = true
        visible = false
        onDismiss()
    }

    Popup(
        alignment = Alignment.TopCenter,
        offset = IntOffset(0, verticalOffset),
        onDismissRequest = ::dismissAnimated,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(160)) + scaleIn(initialScale = 0.96f, animationSpec = tween(160)),
            exit = fadeOut(animationSpec = tween(140)) + scaleOut(targetScale = 0.96f, animationSpec = tween(140))
        ) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.width(popupWidth)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(note.id) {
                                detectDragGestures { _, dragAmount ->
                                    dragOffsetY = (dragOffsetY + dragAmount.y)
                                        .coerceIn(minPopupOffset - rawVerticalOffset, maxPopupOffset - rawVerticalOffset)
                                }
                            }
                            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.note_tab),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.close_preview))
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 360.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = note.title.ifBlank { stringResource(R.string.untitled) },
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
                            text = note.content.ifBlank { stringResource(R.string.untitled) },
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 19.sp),
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = RelativeTimeFormatter.format(note.updatedAtMillis, stringResource(R.string.today), stringResource(R.string.yesterday)),
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onEdit) {
                            Text(stringResource(R.string.edit_note))
                        }
                    }
                }
            }
        }
    }
}
