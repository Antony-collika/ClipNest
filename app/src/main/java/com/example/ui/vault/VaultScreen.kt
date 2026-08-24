package com.example.ui.vault

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ClipboardCardProjection

@Composable
fun VaultScreen(
    viewModel: VaultViewModel,
    onOpenEditor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exportFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.setExportFolder(uri, context.contentResolver)
        }
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is VaultEvent.ShowToast -> android.widget.Toast.makeText(context, event.message, android.widget.Toast.LENGTH_SHORT).show()
                VaultEvent.NavigateToEditor -> onOpenEditor()
                VaultEvent.RequestExportFolder -> exportFolderLauncher.launch(null)
                VaultEvent.NavigateToSettings -> Unit
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.captureCurrentClipboard(context) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("vault_add_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Save current clipboard"
                )
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.cards.isEmpty()) {
                VaultEmptyState(
                    isSearch = uiState.searchQuery.isNotBlank(),
                    onOpenCapture = { viewModel.captureCurrentClipboard(context) }
                )
            } else {
                VaultCardList(
                    cards = uiState.cards,
                    selectedIds = uiState.selectedIds,
                    revealedSensitiveIds = uiState.revealedSensitiveCardIds,
                    isMaskingEnabled = uiState.userSettings.isSensitivePreviewMasked,
                    showPinnedFirst = uiState.userSettings.showPinnedFirst,
                    onToggleSelect = viewModel::toggleCardSelection,
                    onLongPress = { id, anchorY -> viewModel.openPreview(id, anchorY) },
                    onCopy = { id -> viewModel.copySingleCard(context, id) },
                    onToggleRevealSensitive = viewModel::toggleRevealSensitive,
                    onReorder = viewModel::reorderItems
                )
            }
        }
    }

    if (uiState.showInAppCaptureSheet) {
        InAppCaptureSheet(
            onDismiss = viewModel::closeInAppCapture,
            onSave = viewModel::saveInAppCapture
        )
    }
    if (uiState.showShareDialog) {
        ShareCaptureDialog(
            sharedText = uiState.shareContent,
            onDismiss = viewModel::dismissShareDialog,
            onConfirm = { saveShared, saveClipboard, clipboardSnapshot ->
                viewModel.saveShareSelections(saveShared, saveClipboard, clipboardSnapshot)
            }
        )
    }
    if (uiState.showExportDialog) {
        ExportDialog(
            onDismiss = viewModel::dismissExportDialog,
            onConfirm = { fileName, format ->
                viewModel.exportFiles(fileName, format, context.contentResolver)
            }
        )
    }
    if (uiState.showDeleteConfirmDialog) {
        DeleteConfirmDialog(
            count = uiState.selectedIds.size,
            onDismiss = viewModel::dismissDeleteDialog,
            onConfirm = viewModel::confirmDeleteSelected
        )
    }
    uiState.previewCard?.let { card ->
        ClipboardPreviewPopup(
            card = card,
            anchorY = uiState.previewAnchorY,
            isMaskingEnabled = uiState.userSettings.isSensitivePreviewMasked,
            isSensitiveRevealed = uiState.revealedSensitiveCardIds.contains(card.id),
            onDismiss = viewModel::closePreview,
            onCopy = { viewModel.copySingleCard(context, card.id) },
            onToggleRevealSensitive = { viewModel.toggleRevealSensitive(card.id) }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VaultCardList(
    cards: List<ClipboardCardProjection>,
    selectedIds: Set<Long>,
    revealedSensitiveIds: Set<Long>,
    isMaskingEnabled: Boolean,
    showPinnedFirst: Boolean,
    onToggleSelect: (Long) -> Unit,
    onLongPress: (Long, Float) -> Unit,
    onCopy: (Long) -> Unit,
    onToggleRevealSensitive: (Long) -> Unit,
    onReorder: (List<Long>) -> Unit
) {
    val (pinnedCards, normalCards) = cards.partition { it.pinned }
    val displayedFromState = if (showPinnedFirst) pinnedCards + normalCards else cards
    var displayedCards by remember { mutableStateOf(displayedFromState) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragPointerViewportY by remember { mutableFloatStateOf(0f) }
    var pointerOffsetInItem by remember { mutableFloatStateOf(0f) }
    var dragTargetIndex by remember { mutableIntStateOf(-1) }
    var dragTargetId by remember { mutableStateOf<Long?>(null) }
    var dragStartOrder by remember { mutableStateOf<List<Long>>(emptyList()) }
    var dragStartCards by remember { mutableStateOf<List<ClipboardCardProjection>>(emptyList()) }
    val handleBounds = remember { mutableStateMapOf<Long, Rect>() }
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    var listBounds by remember { mutableStateOf<Rect?>(null) }

    LaunchedEffect(cards, showPinnedFirst) {
        if (draggingId == null) {
            displayedCards = if (showPinnedFirst) {
                cards.filter { it.pinned } + cards.filterNot { it.pinned }
            } else {
                cards
            }
        }
    }

    LaunchedEffect(draggingId) {
        while (draggingId != null) {
            val layoutInfo = listState.layoutInfo
            val edge = with(density) { 56.dp.toPx() }
            val topEdge = layoutInfo.viewportStartOffset + edge
            val bottomEdge = layoutInfo.viewportEndOffset - edge
            val scrollDelta = when {
                dragPointerViewportY < topEdge ->
                    -(((topEdge - dragPointerViewportY) / edge) * 18f).coerceIn(4f, 18f)
                dragPointerViewportY > bottomEdge ->
                    (((dragPointerViewportY - bottomEdge) / edge) * 18f).coerceIn(4f, 18f)
                else -> 0f
            }
            if (scrollDelta != 0f) listState.scrollBy(scrollDelta)
            kotlinx.coroutines.delay(16L)
        }
    }

    fun resetDrag(restoreCards: Boolean) {
        if (restoreCards && dragStartCards.isNotEmpty()) displayedCards = dragStartCards
        draggingId = null
        dragPointerViewportY = 0f
        pointerOffsetInItem = 0f
        dragTargetIndex = -1
        dragTargetId = null
        dragStartOrder = emptyList()
        dragStartCards = emptyList()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                listBounds = coordinates.boundsInWindow()
            }
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .testTag("vault_card_list")
        ) {
            if (showPinnedFirst && pinnedCards.isNotEmpty()) {
                item(key = "pinned_header") {
                    Text(
                        text = "PINNED",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp)
                    )
                }
            }

            items(displayedCards, key = { it.id }) { card ->
                if (showPinnedFirst && normalCards.isNotEmpty() && card.id == normalCards.first().id) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            modifier = Modifier.testTag("pinned_first_divider")
                        )
                        Text(
                            text = "OTHER",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)
                        )
                    }
                }

                val isDragging = draggingId == card.id
                ClipboardCardItem(
                    card = card,
                    isSelected = selectedIds.contains(card.id),
                    isSensitiveRevealed = revealedSensitiveIds.contains(card.id),
                    isMaskingEnabled = isMaskingEnabled,
                    isDragging = isDragging,
                    isDropTarget = draggingId != null && dragTargetId == card.id,
                    onToggleSelect = { onToggleSelect(card.id) },
                    onLongPress = {
                        val anchorY = listBounds?.top?.plus(
                            listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == card.id }?.let { info ->
                                info.offset + info.size / 2f
                            } ?: 0f
                        ) ?: 0f
                        onLongPress(card.id, anchorY)
                    },
                    onCopy = { onCopy(card.id) },
                    onToggleRevealSensitive = { onToggleRevealSensitive(card.id) },
                    onDragStart = { pointerPosition ->
                        val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == card.id }
                            ?: return@ClipboardCardItem
                        val listTop = listBounds?.top ?: return@ClipboardCardItem
                        val handle = handleBounds[card.id] ?: return@ClipboardCardItem
                        val pointerViewportY = handle.top + pointerPosition.y - listTop
                        draggingId = card.id
                        dragPointerViewportY = pointerViewportY
                        pointerOffsetInItem = pointerViewportY - itemInfo.offset
                        dragTargetIndex = displayedCards.indexOfFirst { it.id == card.id }.coerceAtLeast(0)
                        dragTargetId = null
                        dragStartOrder = displayedCards.map { it.id }
                        dragStartCards = displayedCards
                    },
                    onDragHandlePositioned = { bounds ->
                        handleBounds[card.id] = bounds
                    },
                    onDrag = { change, dragAmount ->
                        if (draggingId != card.id) return@ClipboardCardItem
                        change.consume()
                        dragPointerViewportY += dragAmount.y

                        val activeIndex = displayedCards.indexOfFirst { it.id == card.id }
                        if (activeIndex < 0) return@ClipboardCardItem
                        val groupStart = if (showPinnedFirst) {
                            displayedCards.indexOfFirst { it.pinned == card.pinned }.coerceAtLeast(0)
                        } else 0
                        val groupEnd = if (showPinnedFirst) {
                            displayedCards.indexOfLast { it.pinned == card.pinned }.coerceAtLeast(groupStart)
                        } else {
                            (displayedCards.size - 1).coerceAtLeast(0)
                        }

                        val layoutInfo = listState.layoutInfo
                        var targetIndex = dragTargetIndex.coerceIn(groupStart, groupEnd)
                        val hysteresis = with(density) { 4.dp.toPx() }
                        var changed = true
                        while (changed) {
                            changed = false
                            if (targetIndex < groupEnd) {
                                val nextInfo = layoutInfo.visibleItemsInfo.firstOrNull {
                                    it.key == displayedCards[targetIndex + 1].id
                                }
                                if (nextInfo != null && dragPointerViewportY > nextInfo.offset + nextInfo.size / 2f + hysteresis) {
                                    targetIndex++
                                    changed = true
                                }
                            }
                            if (targetIndex > groupStart) {
                                val previousInfo = layoutInfo.visibleItemsInfo.firstOrNull {
                                    it.key == displayedCards[targetIndex - 1].id
                                }
                                if (previousInfo != null && dragPointerViewportY < previousInfo.offset + previousInfo.size / 2f - hysteresis) {
                                    targetIndex--
                                    changed = true
                                }
                            }
                        }
                        if (targetIndex != dragTargetIndex) {
                            val movingDown = targetIndex > activeIndex
                            val reordered = displayedCards.toMutableList()
                            val moving = reordered.removeAt(activeIndex)
                            val insertAt = targetIndex.coerceIn(0, reordered.size)
                            reordered.add(insertAt, moving)
                            displayedCards = reordered
                            dragTargetIndex = insertAt
                            dragTargetId = if (movingDown) {
                                reordered.getOrNull(insertAt - 1)?.id
                            } else {
                                reordered.getOrNull(insertAt + 1)?.id
                            }
                        }
                    },
                    onDragEnd = {
                        if (draggingId == card.id) {
                            val finalOrder = displayedCards.map { it.id }
                            if (finalOrder != dragStartOrder) onReorder(finalOrder)
                        }
                        resetDrag(restoreCards = false)
                    },
                    onDragCancel = {
                        resetDrag(restoreCards = true)
                    },
                    modifier = (if (!isDragging) Modifier.animateItem() else Modifier)
                        .zIndex(if (isDragging) 2f else if (dragTargetId == card.id) 1f else 0f)
                        .graphicsLayer {
                            val currentOffset = listState.layoutInfo.visibleItemsInfo
                                .firstOrNull { it.key == card.id }?.offset ?: 0
                            translationY = if (isDragging) {
                                dragPointerViewportY - pointerOffsetInItem - currentOffset
                            } else 0f
                            if (isDragging) {
                                scaleX = 1.015f
                                scaleY = 1.015f
                                shadowElevation = 6.dp.toPx()
                            } else {
                                scaleX = 1f
                                scaleY = 1f
                                shadowElevation = 0f
                            }
                        }
                )
            }
        }
    }
}

@Composable
private fun VaultEmptyState(
    isSearch: Boolean,
    onOpenCapture: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .testTag("vault_empty_state"),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isSearch) Icons.Default.FolderOpen else Icons.Default.ContentPaste,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            text = if (isSearch) "Không tìm thấy nội dung phù hợp" else "Kho Clipboard trống",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
        Text(
            text = if (isSearch) {
                "Thử tìm kiếm với từ khóa khác hoặc xóa bộ lọc tìm kiếm."
            } else {
                "Dùng nút + bên dưới, Chia sẻ từ ứng dụng khác, Thông báo hoặc Quick Settings để lưu clipboard."
            },
            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
