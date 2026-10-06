package com.clipnest.ui.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clipnest.ui.theme.LocalThemePalette

@Composable
fun VaultScreen(
    viewModel: VaultViewModel,
    onOpenEditor: () -> Unit,
    onShareText: (String, String) -> Unit,
    onRequestExportFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is VaultEvent.ShowToast -> android.widget.Toast.makeText(
                    context,
                    event.message,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                is VaultEvent.ShareText -> onShareText(event.text, event.chooserTitle)
                VaultEvent.NavigateToEditor -> onOpenEditor()
                VaultEvent.RequestExportFolder -> onRequestExportFolder()
                VaultEvent.NavigateToSettings -> Unit
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = androidx.compose.ui.graphics.Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        LocalThemePalette.current.screenOverlayBackground.copy(
                            alpha = LocalThemePalette.current.screenOverlayBackgroundAlpha
                        )
                    )
            )
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
                    searchQuery = uiState.searchQuery,
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

@Composable
private fun VaultCardList(
    cards: List<com.clipnest.data.model.ClipboardCardProjection>,
    selectedIds: Set<Long>,
    revealedSensitiveIds: Set<Long>,
    isMaskingEnabled: Boolean,
    showPinnedFirst: Boolean,
    searchQuery: String,
    onToggleSelect: (Long) -> Unit,
    onLongPress: (Long, Float) -> Unit,
    onCopy: (Long) -> Unit,
    onToggleRevealSensitive: (Long) -> Unit,
    onReorder: (List<Long>) -> Unit
) {
    val palette = LocalThemePalette.current
    val colors = VaultRecyclerColors(
        vaultClipboardCardBackground = LocalThemePalette.current.vaultClipboardCardBackground.copy(alpha = LocalThemePalette.current.cardBackgroundAlpha).toArgb(),
        vaultClipboardCardSelectedBackground = LocalThemePalette.current.vaultClipboardCardSelectedBackground.toArgb(),
        vaultClipboardCardPressedBackground = LocalThemePalette.current.vaultClipboardCardPressedBackground.toArgb(),
        vaultClipboardCardDraggingBackground = LocalThemePalette.current.vaultClipboardCardDraggingBackground.toArgb(),
        vaultClipboardPreviewText = LocalThemePalette.current.vaultClipboardPreviewText.toArgb(),
        vaultClipboardMetaText = LocalThemePalette.current.vaultClipboardMetaText.toArgb(),
        vaultActionIcon = LocalThemePalette.current.vaultActionIcon.toArgb(),
        vaultCheckboxChecked = LocalThemePalette.current.vaultCheckboxChecked.toArgb(),
        vaultCheckboxUnchecked = LocalThemePalette.current.vaultCheckboxUnchecked.toArgb(),
        vaultDragHandle = LocalThemePalette.current.vaultDragHandle.toArgb(),
        pinnedIndicator = LocalThemePalette.current.pinnedIndicator.toArgb(),
        sensitiveIndicator = LocalThemePalette.current.sensitiveIndicator.toArgb(),
        vaultGroupDivider = LocalThemePalette.current.vaultGroupDivider.toArgb()
    )

    AndroidView(
        factory = { context -> VaultRecyclerView(context).apply { setBackgroundColor(android.graphics.Color.TRANSPARENT) } },
        update = { recyclerView ->
            recyclerView.render(
                cards = cards,
                selectedIds = selectedIds,
                revealedSensitiveIds = revealedSensitiveIds,
                isMaskingEnabled = isMaskingEnabled,
                showPinnedFirst = showPinnedFirst,
                searchQuery = searchQuery,
                colors = colors,
                callbacks = VaultRecyclerCallbacks(
                    onToggleSelect = onToggleSelect,
                    onLongPress = onLongPress,
                    onCopy = onCopy,
                    onToggleRevealSensitive = onToggleRevealSensitive,
                    onReorder = onReorder
                )
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .testTag("vault_card_list")
    )
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
            tint = LocalThemePalette.current.vaultEmptyStateIcon,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            text = if (isSearch) stringResource(com.clipnest.R.string.no_matching_content) else stringResource(com.clipnest.R.string.empty_vault),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
        Text(
            text = if (isSearch) {
                stringResource(com.clipnest.R.string.try_another_search)
            } else {
                stringResource(com.clipnest.R.string.empty_vault_hint)
            },
            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
