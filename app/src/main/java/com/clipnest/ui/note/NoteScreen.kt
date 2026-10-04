package com.clipnest.ui.note

import androidx.compose.material3.VerticalDivider
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.viewinterop.AndroidView
import com.clipnest.R
import com.clipnest.data.local.NoteDao
import com.clipnest.data.local.TopicDao
import com.clipnest.data.model.NoteCardProjection
import com.clipnest.data.model.NoteTopicRole
import com.clipnest.data.model.Topic
import com.clipnest.domain.RelativeDateLabels
import com.clipnest.domain.RelativeTimeFormatter
import com.clipnest.domain.FtsSearchQuery
import com.clipnest.ui.editor.EditorNoteOrigin
import com.clipnest.ui.theme.LocalThemePalette
import kotlinx.coroutines.flow.flowOf

@Composable
fun NoteScreen(
    noteDao: NoteDao,
    topicDao: TopicDao,
    onCreateNote: (EditorNoteOrigin?, Long?) -> Unit,
    onOpenNote: (Long, EditorNoteOrigin?) -> Unit,
    selectedNoteIds: Set<Long>,
    onSelectionChanged: (Set<Long>) -> Unit,
    onVisibleNoteIdsChanged: (Set<Long>) -> Unit,
    onSelectedTopicIdChanged: (Long?) -> Unit,
    isSearchOpen: Boolean,
    searchQuery: String,
    onOpenSearch: () -> Unit = {},
    onCloseSearch: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onOpenMenu: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTopicId by rememberSaveable { mutableStateOf<Long?>(null) }
    var viewMode by rememberSaveable { mutableStateOf(NoteViewMode.LIST) }
    var pinnedExpanded by rememberSaveable { mutableStateOf(true) }
    var previewNoteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var previewAnchorY by remember { mutableStateOf(0f) }
    var previewContent by remember { mutableStateOf<String?>(null) }
    var overflowExpanded by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    val topics by topicDao.observeAllTopics().collectAsState(initial = emptyList())
    val normalizedSearchQuery = searchQuery.trim()
    val ftsSearchQuery = remember(normalizedSearchQuery) { FtsSearchQuery.fromUserQuery(normalizedSearchQuery) }
    val noteCardsFlow = remember(selectedTopicId, normalizedSearchQuery, ftsSearchQuery) {
        val topic = topics.firstOrNull { it.id == selectedTopicId }
        when {
            topic == null && normalizedSearchQuery.isBlank() -> noteDao.observeActiveNoteCards()
            topic == null -> if (ftsSearchQuery.isBlank()) flowOf(emptyList()) else noteDao.searchActiveNoteCards(ftsSearchQuery)
            normalizedSearchQuery.isBlank() -> noteDao.observeActiveNoteCardsByTopicTree(topic.id, topic.origin)
            else -> if (ftsSearchQuery.isBlank()) flowOf(emptyList()) else noteDao.searchActiveNoteCardsByTopicTree(topic.id, topic.origin, ftsSearchQuery)
        }
    }
    val noteCards by noteCardsFlow.collectAsState(initial = emptyList())

    val selectedTopic = topics.firstOrNull { it.id == selectedTopicId }
    val currentTopicLabel = selectedTopic?.name
    ?: androidx.compose.ui.res.stringResource(R.string.all_notes)
    val pinnedNoteIds by remember(selectedTopicId) {
        selectedTopicId?.let {
            noteDao.observePinnedNoteIdsForTopic(it, NoteTopicRole.USER_TAG)
        } ?: noteDao.observePinnedNoteIds()
    }.collectAsState(initial = emptyList())

    LaunchedEffect(selectedTopicId) {
        onSelectedTopicIdChanged(selectedTopicId)
    }

    LaunchedEffect(previewNoteId) {
        previewContent = previewNoteId?.let { noteId ->
            val chunkSize = 262_144
            var start = 1
            val builder = StringBuilder()
            while (true) {
                val chunk = noteDao.getNoteContentChunk(noteId, start, chunkSize).orEmpty()
                if (chunk.isEmpty()) break
                builder.append(chunk)
                if (chunk.length < chunkSize) break
                start += chunkSize
            }
            builder.toString()
        }
    }

    LaunchedEffect(noteCards.map(NoteCardProjection::id)) {
        val visibleIds = noteCards.map(NoteCardProjection::id).toSet()
        onVisibleNoteIdsChanged(visibleIds)
        val pruned = selectedNoteIds.intersect(visibleIds)
        if (pruned != selectedNoteIds) {
            onSelectionChanged(pruned)
        }
        if (previewNoteId != null && noteCards.none { it.id == previewNoteId }) {
            previewNoteId = null
        }
    }

    val origin = originForTopic(selectedTopic)
    val previewNote = noteCards.firstOrNull { it.id == previewNoteId }

    val palette = LocalThemePalette.current

    Box(modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .align(Alignment.BottomCenter)
        ) {
            val backWave = Path().apply {
                moveTo(0f, size.height * 0.24f)
                cubicTo(size.width * 0.16f, size.height * 0.02f, size.width * 0.30f, size.height * 0.40f, size.width * 0.48f, size.height * 0.18f)
                cubicTo(size.width * 0.66f, -size.height * 0.02f, size.width * 0.82f, size.height * 0.34f, size.width, size.height * 0.12f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            val frontWave = Path().apply {
                moveTo(0f, size.height * 0.42f)
                cubicTo(size.width * 0.15f, size.height * 0.12f, size.width * 0.31f, size.height * 0.58f, size.width * 0.49f, size.height * 0.34f)
                cubicTo(size.width * 0.67f, size.height * 0.10f, size.width * 0.83f, size.height * 0.50f, size.width, size.height * 0.28f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            val wavePrimary = palette.noteWavePrimary
            val waveSecondary = palette.noteWaveSecondary
            drawPath(backWave, waveSecondary)
            drawPath(frontWave, wavePrimary)
        }

        Column(Modifier.fillMaxSize()) {
                val headerState = NoteHeaderState(
                    avatarIcon = androidx.compose.material.icons.Icons.Default.Person,
myTopicsLabel = androidx.compose.ui.res.stringResource(R.string.drawer_my_topics),
currentTopicLabel = currentTopicLabel,
allNotesLabel = androidx.compose.ui.res.stringResource(R.string.all_notes),
                    topics = topics,
                    typography = MaterialTheme.typography,
                    viewMode = viewMode,
                    pinnedLabel = androidx.compose.ui.res.stringResource(R.string.pinned),
                    pinnedExpanded = pinnedExpanded,
                    onPinnedExpandedChanged = { pinnedExpanded = it },
                    todayLabel = androidx.compose.ui.res.stringResource(R.string.today),
                    yesterdayLabel = androidx.compose.ui.res.stringResource(R.string.yesterday),
                    previous7DaysLabel = androidx.compose.ui.res.stringResource(R.string.previous_7_days),
                    previous30DaysLabel = androidx.compose.ui.res.stringResource(R.string.previous_30_days),
                    olderLabel = androidx.compose.ui.res.stringResource(R.string.older),
                    relativeDateLabels = RelativeDateLabels(
                        today = androidx.compose.ui.res.stringResource(R.string.today),
                        yesterday = androidx.compose.ui.res.stringResource(R.string.yesterday),
                        daysAgo = { count -> context.getString(R.string.days_ago, count) },
                        lastWeek = androidx.compose.ui.res.stringResource(R.string.last_week),
                        weeksAgo = { count -> context.getString(R.string.weeks_ago, count) },
                        lastMonth = androidx.compose.ui.res.stringResource(R.string.last_month),
                        monthYear = { month, year -> context.getString(R.string.month_year, month, year) }
                    ),
                    onTopicSelected = { topicId -> selectedTopicId = topicId },
                    onViewModeChanged = { viewMode = it }
                )
            if (isSearchOpen) {
                SearchOverlay(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    onClose = onCloseSearch,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            if (noteCards.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (searchQuery.isBlank()) {
                            androidx.compose.ui.res.stringResource(R.string.no_notes)
                        } else {
                            androidx.compose.ui.res.stringResource(R.string.no_search_results)
                        },
                        color = palette.noteEmptyStateText
                    )
                }
            } else {
                val colors = NoteRecyclerColors(
                    noteCardBackground = palette.noteCardBackground.toArgb(),
                    noteCardSelectedBackground = palette.noteCardSelectedBackground.toArgb(),
                    noteTagBackground = palette.noteTagBackground.toArgb(),
                    noteTagContent = palette.noteTagText.toArgb(),
                    noteSectionTitle = palette.noteSectionTitle.toArgb(),
                    noteSectionChevron = palette.noteSectionChevron.toArgb(),
                    noteCardSelectionChecked = palette.noteActionIcon.toArgb(),
                    noteCardSelectionUnchecked = palette.notePreviewText.toArgb(),
                    noteListDivider = palette.noteListDivider.toArgb(),
                    pinnedIndicator = palette.pinnedIndicator.toArgb(),
                    noteTitle = palette.noteTitle.toArgb(),
                    notePreviewText = palette.notePreviewText.toArgb(),
                    noteActionIcon = palette.noteActionIcon.toArgb()
                )
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    AndroidView(
                        factory = { context -> NoteRecyclerView(context) },
                        update = { recyclerView ->
                            recyclerView.render(
                                header = headerState,
                                notes = noteCards,
                                selectedIds = selectedNoteIds,
                                pinnedIds = pinnedNoteIds.toSet(),
                                colors = colors,
                                callbacks = NoteRecyclerCallbacks(
                                    onToggleSelect = { id ->
                                        onSelectionChanged(
                                            selectedNoteIds.toMutableSet().also {
                                                if (!it.add(id)) it.remove(id)
                                            }
                                        )
                                    },
                                    onLongPress = { id, anchorY ->
                                        previewAnchorY = anchorY
                                        previewNoteId = id
                                    },
                                    onEdit = { id ->
                                        onOpenNote(id, origin)
                                    }
                                )
                            )
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 12.dp, end = 12.dp, top = 56.dp, bottom = 0.dp)
                    )
                    NoteHeaderContent(headerState)
                }
            }
        }

        Surface(
    modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(horizontal = 18.dp, vertical = 18.dp),
    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    color = palette.dockBackground,
    tonalElevation = 2.dp,
    shadowElevation = 3.dp
) {
            Row(
                modifier = Modifier
                    .height(48.dp)
                    .padding(horizontal = 4.dp, vertical = 0.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onOpenMenu,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Menu,
                        contentDescription = androidx.compose.ui.res.stringResource(R.string.menu),
                        tint = palette.dockIcon
                    )
                }
                IconButton(
                    onClick = onOpenSearch,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = androidx.compose.ui.res.stringResource(R.string.search_notes),
                        tint = palette.dockIcon
                    )
                }
                VerticalDivider(
    modifier = Modifier.height(20.dp),
    color = palette.dockDivider
)
IconButton(
    onClick = { onCreateNote(origin, selectedTopicId) },
    modifier = Modifier.size(48.dp)
) {
    Icon(
        Icons.Default.Edit,
        contentDescription = androidx.compose.ui.res.stringResource(R.string.new_note),
        tint = palette.dockIcon
    )
}
VerticalDivider(
    modifier = Modifier.height(20.dp),
    color = palette.dockDivider
)
                Box {
                    IconButton(
                        onClick = { overflowExpanded = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = androidx.compose.ui.res.stringResource(R.string.more_options),
                            tint = palette.dockIcon
                        )
                    }
                    DropdownMenu(
                        expanded = overflowExpanded,
                        onDismissRequest = { overflowExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(androidx.compose.ui.res.stringResource(R.string.note_list_view)) },
                            onClick = {
                                overflowExpanded = false
                                viewMode = NoteViewMode.LIST
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(androidx.compose.ui.res.stringResource(R.string.note_grid_view)) },
                            onClick = {
                                overflowExpanded = false
                                viewMode = NoteViewMode.GRID
                            }
                        )
                        if (pinnedNoteIds.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text(androidx.compose.ui.res.stringResource(if (pinnedExpanded) R.string.collapse_pinned else R.string.expand_pinned)) },
                                onClick = {
                                    overflowExpanded = false
                                    pinnedExpanded = !pinnedExpanded
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(androidx.compose.ui.res.stringResource(R.string.settings)) },
                            onClick = {
                                overflowExpanded = false
                                onOpenSettings()
                            }
                        )
                    }
                }
            }
        }
    }

    if (previewNote != null) {
        NotePreviewPopup(
            note = previewNote,
            content = previewContent ?: previewNote.preview,
            anchorY = previewAnchorY,
            onDismiss = { previewNoteId = null },
            onEdit = {
                previewNoteId = null
                onOpenNote(previewNote.id, origin)
            }
        )
    }
}

@Composable
private fun SearchOverlay(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LocalThemePalette.current.noteSearchBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, contentDescription = androidx.compose.ui.res.stringResource(R.string.search_notes), tint = LocalThemePalette.current.noteSearchContent)
            androidx.compose.material3.TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(androidx.compose.ui.res.stringResource(R.string.search_notes), color = LocalThemePalette.current.noteSearchHint) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    errorContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedTextColor = LocalThemePalette.current.noteSearchContent,
                    unfocusedTextColor = LocalThemePalette.current.noteSearchContent,
                    cursorColor = LocalThemePalette.current.noteBreadcrumbText,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                )
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Clear, contentDescription = androidx.compose.ui.res.stringResource(R.string.close_search), tint = LocalThemePalette.current.noteSearchContent)
            }
        }
    }
}


private fun originForTopic(topic: Topic?): EditorNoteOrigin? =
    topic?.let { EditorNoteOrigin(label = it.name, returnKey = "topic:" + it.id) }

@Composable
private fun NotePreviewPopup(
    note: NoteCardProjection,
    content: String,
    anchorY: Float,
    onDismiss: () -> Unit,
    onEdit: () -> Unit
) {
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
    val rawVerticalOffset = if (opensBelow) {
        anchorY + with(density) { 20.dp.toPx() }
    } else {
        anchorY - popupHeightPx - with(density) { 20.dp.toPx() }
    }
    val minPopupOffset = marginPx
    val maxPopupOffset = (screenHeightPx - popupHeightPx - marginPx).coerceAtLeast(marginPx)
    val verticalOffset = (rawVerticalOffset + dragOffsetY)
        .coerceIn(minPopupOffset, maxPopupOffset)
        .toInt()
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
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(160)) +
                scaleIn(initialScale = 0.96f, animationSpec = tween(160)),
            exit = fadeOut(animationSpec = tween(140)) +
                scaleOut(targetScale = 0.96f, animationSpec = tween(140))
        ) {
            Card(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = LocalThemePalette.current.notePopupBackground),
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
                                        .coerceIn(
                                            minPopupOffset - rawVerticalOffset,
                                            maxPopupOffset - rawVerticalOffset
                                        )
                                }
                            }
                            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(R.string.note_tab),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.width(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = androidx.compose.ui.res.stringResource(R.string.close_preview),
                                tint = LocalThemePalette.current.notePopupContent
                            )
                        }
                    }
                    HorizontalDivider(color = LocalThemePalette.current.dockDivider)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 360.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = note.title.ifBlank {
                                androidx.compose.ui.res.stringResource(R.string.untitled)
                            },
                            style = MaterialTheme.typography.titleLarge
                        )
                        if (note.topicLabels.isNotBlank()) {
                            Text(
                                text = note.topicLabels,
                                style = MaterialTheme.typography.labelMedium,
                                color = LocalThemePalette.current.noteTagText,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        Text(
                            text = content.ifBlank {
                                androidx.compose.ui.res.stringResource(R.string.untitled)
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 19.sp),
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                    HorizontalDivider(color = LocalThemePalette.current.dockDivider)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = RelativeTimeFormatter.format(
                                note.createdAtMillis,
                                androidx.compose.ui.res.stringResource(R.string.today),
                                androidx.compose.ui.res.stringResource(R.string.yesterday)
                            ),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = LocalThemePalette.current.notePopupSecondaryText
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onEdit) {
                            Text(androidx.compose.ui.res.stringResource(R.string.edit_note))
                        }
                    }
                }
            }
        }
    }
}
