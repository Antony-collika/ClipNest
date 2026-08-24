package com.example.ui.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ClipboardCardProjection
import com.example.ui.common.FirstRunEducationDialog

@Composable
fun VaultScreen(
    viewModel: VaultViewModel,
    onOpenEditor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is VaultEvent.ShowToast -> android.widget.Toast.makeText(context, event.message, android.widget.Toast.LENGTH_SHORT).show()
                VaultEvent.NavigateToEditor -> Unit
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
                onClick = viewModel::openInAppCapture,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("vault_add_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add new clipboard card"
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
            if (selectedCount > 0) {
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
                    onPinSelected = viewModel::togglePinSelected,
                    onToggleShowPinnedFirst = viewModel::toggleShowPinnedFirst,
                    onSaveFile = viewModel::openExportDialog,
                    onOpenEditor = onOpenEditor
                )
            }

            if (uiState.cards.isEmpty()) {
                VaultEmptyState(
                    isSearch = uiState.searchQuery.isNotBlank(),
                    onOpenCapture = viewModel::openInAppCapture
                )
            } else {
                VaultCardList(
                    cards = uiState.cards,
                    selectedIds = uiState.selectedIds,
                    revealedSensitiveIds = uiState.revealedSensitiveCardIds,
                    isMaskingEnabled = uiState.userSettings.isSensitivePreviewMasked,
                    showPinnedFirst = uiState.userSettings.showPinnedFirst,
                    onToggleSelect = viewModel::toggleCardSelection,
                    onLongPress = viewModel::onCardLongPress,
                    onCopy = { id -> viewModel.copySingleCard(context, id) },
                    onToggleRevealSensitive = viewModel::toggleRevealSensitive,
                    onReorder = viewModel::reorderItems
                )
            }
        }
    }

    if (!uiState.userSettings.firstRunEducationShown) {
        FirstRunEducationDialog(onDismiss = viewModel::markFirstRunEducationShown)
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
            onConfirm = { saveShared, saveClipboard ->
                viewModel.saveShareSelections(saveShared, saveClipboard, context)
            }
        )
    }
    if (uiState.showExportDialog) {
        ExportDialog(
            onDismiss = viewModel::dismissExportDialog,
            onConfirm = viewModel::exportFiles
        )
    }
    if (uiState.showDeleteConfirmDialog) {
        DeleteConfirmDialog(
            count = uiState.selectedIds.size,
            onDismiss = viewModel::dismissDeleteDialog,
            onConfirm = viewModel::confirmDeleteSelected
        )
    }
}

@Composable
private fun VaultCardList(
    cards: List<ClipboardCardProjection>,
    selectedIds: Set<Long>,
    revealedSensitiveIds: Set<Long>,
    isMaskingEnabled: Boolean,
    showPinnedFirst: Boolean,
    onToggleSelect: (Long) -> Unit,
    onLongPress: (Long) -> Unit,
    onCopy: (Long) -> Unit,
    onToggleRevealSensitive: (Long) -> Unit,
    onReorder: (fromId: Long, targetId: Long) -> Unit
) {
    val (pinnedCards, normalCards) = cards.partition { it.pinned }
    val displayedCards = if (showPinnedFirst) pinnedCards + normalCards else cards
    val cardsById = remember(cards) { cards.associateBy { it.id } }
    val listState = rememberLazyListState()
    var draggingId by remember { mutableStateOf<Long?>(null) }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("vault_card_list")
    ) {
        itemsIndexed(displayedCards, key = { _, card -> card.id }) { index, card ->
            val group = if (showPinnedFirst && card.pinned) pinnedCards else if (showPinnedFirst) normalCards else displayedCards
            val groupIndex = group.indexOfFirst { it.id == card.id }
            val canMoveUp = if (showPinnedFirst) groupIndex > 0 else index > 0
            val canMoveDown = if (showPinnedFirst) groupIndex < group.lastIndex else index < displayedCards.lastIndex

            if (showPinnedFirst && index == 0 && pinnedCards.isNotEmpty()) {
                Text(
                    text = "PINNED",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp)
                )
            }
            if (showPinnedFirst && normalCards.isNotEmpty() && index == pinnedCards.size) {
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

            var dragOffset by remember(card.id) { mutableFloatStateOf(0f) }
            var lastTargetId by remember(card.id) { mutableStateOf<Long?>(null) }

            ClipboardCardItem(
                card = card,
                isSelected = selectedIds.contains(card.id),
                isSensitiveRevealed = revealedSensitiveIds.contains(card.id),
                isMaskingEnabled = isMaskingEnabled,
                isDragging = draggingId == card.id,
                onToggleSelect = { onToggleSelect(card.id) },
                onLongPress = { onLongPress(card.id) },
                onCopy = { onCopy(card.id) },
                onToggleRevealSensitive = { onToggleRevealSensitive(card.id) },
                onDragStart = {
                    draggingId = card.id
                    dragOffset = 0f
                    lastTargetId = null
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    dragOffset += dragAmount.y
                    val draggedInfo = listState.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.key == card.id }
                    if (draggedInfo != null) {
                        val draggedCenter = draggedInfo.offset + draggedInfo.size / 2f + dragOffset
                        val targetInfo = listState.layoutInfo.visibleItemsInfo
                            .asSequence()
                            .filter { item ->
                                val targetId = item.key as? Long
                                targetId != null && targetId != card.id &&
                                    (!showPinnedFirst || cardsById[targetId]?.pinned == card.pinned) &&
                                    draggedCenter in item.offset.toFloat()..(item.offset + item.size).toFloat()
                            }
                            .minByOrNull { item ->
                                abs(draggedCenter - (item.offset + item.size / 2f))
                            }
                        val targetId = targetInfo?.key as? Long
                        if (targetId != null && targetId != lastTargetId) {
                            lastTargetId = targetId
                            dragOffset = 0f
                            onReorder(card.id, targetId)
                        }
                    }
                },
                onDragEnd = {
                    draggingId = null
                    dragOffset = 0f
                    lastTargetId = null
                },
                onDragCancel = {
                    draggingId = null
                    dragOffset = 0f
                    lastTargetId = null
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
        horizontalAlignment = Alignment.CenterHorizontally,
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
                "Dán nhanh nội dung bằng nút + bên dưới, Chia sẻ từ ứng dụng khác, Thông báo hoặc phím Quick Settings."
            },
            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
