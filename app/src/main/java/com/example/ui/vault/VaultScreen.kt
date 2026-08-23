package com.example.ui.vault

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.NoteDocType
import com.example.data.model.ClipboardCardProjection
import com.example.ui.common.FirstRunEducationDialog

@Composable
fun VaultScreen(
    viewModel: VaultViewModel,
    onNavigateToEditor: (NoteDocType) -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is VaultEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is VaultEvent.NavigateToEditor -> {
                    onNavigateToEditor(event.docType)
                }
                is VaultEvent.NavigateToSettings -> {
                    onNavigateToSettings()
                }
            }
        }
    }

    val selectedCount = uiState.selectedIds.size
    val hasSelection = selectedCount > 0
    val selectedCards = uiState.cards.filter { uiState.selectedIds.contains(it.id) }
    val allSelectedPinned = selectedCards.isNotEmpty() && selectedCards.all { it.pinned }
    val allSelected = uiState.cards.isNotEmpty() && selectedCount == uiState.cards.size

    Scaffold(
        topBar = {
            Column {
                VaultTopBar(
                    isSearchOpen = uiState.isSearchOpen,
                    searchQuery = uiState.searchQuery,
                    onMenuClick = onNavigateToSettings,
                    onSearchOpen = viewModel::openSearch,
                    onSearchClose = viewModel::closeSearch,
                    onSearchQueryChange = viewModel::setSearchQuery,
                    onSaveFile = viewModel::openExportDialog,
                    onOpenEditor = viewModel::openEditorAction,
                    onOpenSettings = viewModel::openSettingsAction
                )

                // Top Tab Row: "Kho" and "Soạn thảo"
                TabRow(
                    selectedTabIndex = 0,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[0]),
                            color = MaterialTheme.colorScheme.primary,
                            height = 3.dp
                        )
                    },
                    divider = {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    },
                    modifier = Modifier.testTag("vault_top_tab_row")
                ) {
                    Tab(
                        selected = true,
                        onClick = { /* Already in Vault */ },
                        text = {
                            Text(
                                text = "Kho",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            )
                        },
                        modifier = Modifier.testTag("vault_tab_kho")
                    )
                    Tab(
                        selected = false,
                        onClick = { onNavigateToEditor(NoteDocType.NOTE) },
                        text = {
                            Text(
                                text = "Soạn thảo",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 15.sp
                                )
                            )
                        },
                        modifier = Modifier.testTag("vault_tab_soan_thao")
                    )
                }

                // Selection / Subheader action bar
                VaultSelectionBar(
                    selectedCount = selectedCount,
                    allSelected = allSelected,
                    allSelectedPinned = allSelectedPinned,
                    showPinnedFirst = uiState.userSettings.showPinnedFirst,
                    onToggleSelectAll = {
                        if (allSelected) {
                            viewModel.clearSelection()
                        } else {
                            viewModel.selectAll()
                        }
                    },
                    onShareSelected = {
                        val orderedSelected = uiState.cards.filter { uiState.selectedIds.contains(it.id) }
                        if (orderedSelected.isNotEmpty()) {
                            val shareText = orderedSelected.joinToString("\n\n") { it.preview }
                            val sendIntent: Intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, shareText)
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, "Chia sẻ nội dung clipboard")
                            context.startActivity(shareIntent)
                        }
                    },
                    onCopySelected = { viewModel.copySelectedCards(context) },
                    onDeleteSelected = viewModel::requestDeleteSelected,
                    onPinSelected = viewModel::togglePinSelected,
                    onToggleShowPinnedFirst = viewModel::toggleShowPinnedFirst,
                    onSaveFile = viewModel::openExportDialog
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::openInAppCapture,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = androidx.compose.foundation.shape.CircleShape,
                modifier = Modifier.testTag("vault_add_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add new clipboard card",
                    modifier = Modifier.size(28.dp)
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

    // First run education dialog
    if (!uiState.userSettings.firstRunEducationShown) {
        FirstRunEducationDialog(
            onDismiss = viewModel::markFirstRunEducationShown
        )
    }

    // In-App capture bottom sheet
    if (uiState.showInAppCaptureSheet) {
        InAppCaptureSheet(
            onDismiss = viewModel::closeInAppCapture,
            onSave = viewModel::saveInAppCapture
        )
    }

    // Share dialog
    if (uiState.showShareDialog) {
        ShareCaptureDialog(
            sharedText = uiState.shareContent,
            onDismiss = viewModel::dismissShareDialog,
            onConfirm = { saveShared, saveClipboard ->
                viewModel.saveShareSelections(saveShared, saveClipboard, context)
            }
        )
    }

    // Export dialog
    if (uiState.showExportDialog) {
        ExportDialog(
            onDismiss = viewModel::dismissExportDialog,
            onConfirm = viewModel::exportFiles
        )
    }

    // Delete confirmation dialog
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
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit
) {
    if (showPinnedFirst) {
        val (pinnedCards, normalCards) = cards.partition { it.pinned }

        LazyColumn(
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .testTag("vault_card_list")
        ) {
            if (pinnedCards.isNotEmpty()) {
                item(key = "header_pinned") {
                    Text(
                        text = "PINNED",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp)
                    )
                }

                itemsIndexed(pinnedCards, key = { _, card -> "pinned_${card.id}" }) { index, card ->
                    val globalIndex = index
                    ClipboardCardItem(
                        card = card,
                        isSelected = selectedIds.contains(card.id),
                        isSensitiveRevealed = revealedSensitiveIds.contains(card.id),
                        isMaskingEnabled = isMaskingEnabled,
                        canMoveUp = index > 0,
                        canMoveDown = index < pinnedCards.size - 1,
                        onToggleSelect = { onToggleSelect(card.id) },
                        onLongPress = { onLongPress(card.id) },
                        onCopy = { onCopy(card.id) },
                        onToggleRevealSensitive = { onToggleRevealSensitive(card.id) },
                        onMoveUp = { onReorder(globalIndex, globalIndex - 1) },
                        onMoveDown = { onReorder(globalIndex, globalIndex + 1) }
                    )
                }

                if (normalCards.isNotEmpty()) {
                    item(key = "divider_groups") {
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
                }
            }

            itemsIndexed(normalCards, key = { _, card -> "normal_${card.id}" }) { index, card ->
                val globalIndex = pinnedCards.size + index
                ClipboardCardItem(
                    card = card,
                    isSelected = selectedIds.contains(card.id),
                    isSensitiveRevealed = revealedSensitiveIds.contains(card.id),
                    isMaskingEnabled = isMaskingEnabled,
                    canMoveUp = index > 0,
                    canMoveDown = index < normalCards.size - 1,
                    onToggleSelect = { onToggleSelect(card.id) },
                    onLongPress = { onLongPress(card.id) },
                    onCopy = { onCopy(card.id) },
                    onToggleRevealSensitive = { onToggleRevealSensitive(card.id) },
                    onMoveUp = { onReorder(globalIndex, globalIndex - 1) },
                    onMoveDown = { onReorder(globalIndex, globalIndex + 1) }
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .testTag("vault_card_list")
        ) {
            itemsIndexed(cards, key = { _, card -> card.id }) { index, card ->
                ClipboardCardItem(
                    card = card,
                    isSelected = selectedIds.contains(card.id),
                    isSensitiveRevealed = revealedSensitiveIds.contains(card.id),
                    isMaskingEnabled = isMaskingEnabled,
                    canMoveUp = index > 0,
                    canMoveDown = index < cards.size - 1,
                    onToggleSelect = { onToggleSelect(card.id) },
                    onLongPress = { onLongPress(card.id) },
                    onCopy = { onCopy(card.id) },
                    onToggleRevealSensitive = { onToggleRevealSensitive(card.id) },
                    onMoveUp = { onReorder(index, index - 1) },
                    onMoveDown = { onReorder(index, index + 1) }
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
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isSearch) Icons.Default.FolderOpen else Icons.Default.ContentPaste,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (isSearch) "Không tìm thấy nội dung phù hợp" else "Kho Clipboard trống",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isSearch) {
                "Thử tìm kiếm với từ khóa khác hoặc xóa bộ lọc tìm kiếm."
            } else {
                "Dán nhanh nội dung bằng nút + bên dưới, Chia sẻ từ ứng dụng khác, Thông báo hoặc phím Quick Settings."
            },
            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            textAlign = TextAlign.Center
        )
    }
}
