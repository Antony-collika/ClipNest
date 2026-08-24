package com.example.ui.vault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ClipboardCardProjection
import kotlin.math.roundToInt

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

    val selectedCount = uiState.selectedIds.size
    val selectedCards = uiState.cards.filter { uiState.selectedIds.contains(it.id) }
    val allSelectedPinned = selectedCards.isNotEmpty() && selectedCards.all { it.pinned }
    val allSelected = uiState.cards.isNotEmpty() && selectedCount == uiState.cards.size

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(
                visible = selectedCount > 0,
                enter = fadeIn(animationSpec = tween(160)) + expandVertically(expandFrom = Alignment.Top, animationSpec = tween(160)),
                exit = fadeOut(animationSpec = tween(120)) + shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(120))
            ) {
                VaultSelectionBar(
                    selectedCount = selectedCount,
                    allSelected = allSelected,
                    allSelectedPinned = allSelectedPinned,
                    showPinnedFirst = uiState.userSettings.showPinnedFirst,
                    onToggleSelectAll = {
                        if (allSelected) viewModel.clearSelection() else viewModel.selectAll()
                    },
                    onShareSelected = { viewModel.shareSelected(context) },
                    onCopySelected = { viewModel.copySelectedCards(context) },
                    onDeleteSelected = viewModel::requestDeleteSelected,
                    onPinSelected = viewModel::togglePinSelected
                )
            }

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
    var dragTotalDelta by remember { mutableFloatStateOf(0f) }
    var pointerDownOffsetInCard by remember { mutableFloatStateOf(0f) }
    var dragPointerAbsoluteY by remember { mutableFloatStateOf(0f) }
    var dragDropIndex by remember { mutableIntStateOf(-1) }
    var dragTargetId by remember { mutableStateOf<Long?>(null) }
    var dragStartOrder by remember { mutableStateOf<List<Long>>(emptyList()) }
    var dragStartBounds by remember { mutableStateOf<Rect?>(null) }
    var listBounds by remember { mutableStateOf<Rect?>(null) }
    val cardBounds = remember { mutableStateMapOf<Long, Rect>() }
    val handleBounds = remember { mutableStateMapOf<Long, Rect>() }
    val density = LocalDensity.current
    val listState = rememberLazyListState()

    LaunchedEffect(cards, showPinnedFirst) {
        if (draggingId == null) {
            displayedCards = if (showPinnedFirst) {
                cards.filter { it.pinned } + cards.filterNot { it.pinned }
            } else {
                cards
            }
        }
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
                if (isDragging) {
                    val measuredHeight = cardBounds[card.id]?.height ?: with(density) { 72.dp.toPx() }
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(with(density) { measuredHeight.coerceAtLeast(1f).toDp() })
                            .testTag("drag_placeholder_${card.id}")
                    )
                } else {
                    ClipboardCardItem(
                        card = card,
                        isSelected = selectedIds.contains(card.id),
                        isSensitiveRevealed = revealedSensitiveIds.contains(card.id),
                        isMaskingEnabled = isMaskingEnabled,
                        isDragging = false,
                        isDropTarget = draggingId != null && dragTargetId == card.id,
                        onToggleSelect = { onToggleSelect(card.id) },
                        onLongPress = {
                            onLongPress(card.id, cardBounds[card.id]?.center?.y ?: 0f)
                        },
                        onCopy = { onCopy(card.id) },
                        onToggleRevealSensitive = { onToggleRevealSensitive(card.id) },
                        onDragStart = { pointerPosition ->
                            val itemBounds = cardBounds[card.id] ?: return@ClipboardCardItem
                            val handle = handleBounds[card.id]
                            val pointerAbsoluteY = handle?.top?.plus(pointerPosition.y)
                                ?: itemBounds.center.y
                            draggingId = card.id
                            dragTotalDelta = 0f
                            pointerDownOffsetInCard = pointerAbsoluteY - itemBounds.top
                            dragPointerAbsoluteY = pointerAbsoluteY
                            dragStartBounds = itemBounds
                            dragDropIndex = displayedCards
                                .filter { !showPinnedFirst || it.pinned == card.pinned }
                                .indexOfFirst { it.id == card.id }
                                .coerceAtLeast(0)
                            dragTargetId = null
                            dragStartOrder = displayedCards.map { it.id }
                        },
                        onDragHandlePositioned = { bounds ->
                            handleBounds[card.id] = bounds
                        },
                        onDrag = { change, dragAmount ->
                            if (draggingId != card.id) return@ClipboardCardItem
                            change.consume()
                            dragTotalDelta += dragAmount.y
                            dragPointerAbsoluteY += dragAmount.y

                            val startBounds = dragStartBounds ?: return@ClipboardCardItem
                            val draggedCenter = startBounds.top + dragTotalDelta + startBounds.height / 2f
                            val sameGroupCards = displayedCards.filter {
                                it.id != card.id && (!showPinnedFirst || it.pinned == card.pinned)
                            }
                            val insertionIndex = sameGroupCards.count { target ->
                                cardBounds[target.id]?.center?.y?.let { draggedCenter > it } == true
                            }
                            dragDropIndex = insertionIndex
                            dragTargetId = sameGroupCards
                                .getOrNull(insertionIndex)
                                ?.id
                                ?: sameGroupCards.lastOrNull()?.id
                        },
                        onDragEnd = {
                            if (draggingId == card.id && dragDropIndex >= 0) {
                                val groupCards = displayedCards.filter {
                                    !showPinnedFirst || it.pinned == card.pinned
                                }
                                val remaining = groupCards.filterNot { it.id == card.id }.toMutableList()
                                val insertAt = dragDropIndex.coerceIn(0, remaining.size)
                                remaining.add(insertAt, card)
                                val reorderedIds = displayedCards.map { item ->
                                    if (!showPinnedFirst || item.pinned == card.pinned) {
                                        remaining.removeFirstOrNull()?.id ?: item.id
                                    } else {
                                        item.id
                                    }
                                }
                                if (reorderedIds != dragStartOrder) {
                                    onReorder(reorderedIds)
                                }
                            }
                            draggingId = null
                            dragTotalDelta = 0f
                            pointerDownOffsetInCard = 0f
                            dragPointerAbsoluteY = 0f
                            dragDropIndex = -1
                            dragTargetId = null
                            dragStartOrder = emptyList()
                            dragStartBounds = null
                        },
                        onDragCancel = {
                            draggingId = null
                            dragTotalDelta = 0f
                            pointerDownOffsetInCard = 0f
                            dragPointerAbsoluteY = 0f
                            dragDropIndex = -1
                            dragTargetId = null
                            dragStartOrder = emptyList()
                            dragStartBounds = null
                        },
                        modifier = Modifier
                            .zIndex(if (dragTargetId == card.id) 1f else 0f)
                            .onGloballyPositioned { coordinates ->
                                cardBounds[card.id] = coordinates.boundsInWindow()
                            }
                    )
                }
            }
        }

        val draggedCard = displayedCards.firstOrNull { it.id == draggingId }
        val startBounds = dragStartBounds
        val containerBounds = listBounds
        if (draggedCard != null && startBounds != null && containerBounds != null) {
            val proxyTop = (dragPointerAbsoluteY - pointerDownOffsetInCard - containerBounds.top)
                .coerceIn(0f, (containerBounds.height - startBounds.height).coerceAtLeast(0f))
            ClipboardCardItem(
                card = draggedCard,
                isSelected = selectedIds.contains(draggedCard.id),
                isSensitiveRevealed = revealedSensitiveIds.contains(draggedCard.id),
                isMaskingEnabled = isMaskingEnabled,
                isDragging = true,
                isDropTarget = false,
                onToggleSelect = {},
                onLongPress = {},
                onCopy = {},
                onToggleRevealSensitive = {},
                onDragStart = { _ -> },
                onDragHandlePositioned = { _ -> },
                onDrag = { _, _ -> },
                onDragEnd = {},
                onDragCancel = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .offset { IntOffset(0, proxyTop.roundToInt()) }
                    .zIndex(10f)
                    .graphicsLayer {
                        scaleX = 1.02f
                        scaleY = 1.02f
                        shadowElevation = 8.dp.toPx()
                    }
            )
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
