package com.example.ui.vault

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.ExportFormat
import com.example.data.local.FileManager
import com.example.data.local.SettingsDataStore
import com.example.data.local.UserSettings
import com.example.data.model.ClipboardCard
import com.example.data.model.ClipboardCardProjection
import com.example.data.repository.CapturePayload
import com.example.data.repository.CaptureSource
import com.example.data.repository.localizedCaptureSourceLabel
import com.example.data.repository.ClipboardRepository
import com.example.domain.ExportFormatter
import com.example.domain.ExportLabels
import com.example.domain.OrderHelper
import com.example.domain.TextNormalizer
import com.example.ui.localization.withAppLanguage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class VaultUiState(
    val cards: List<ClipboardCardProjection> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val searchQuery: String = "",
    val isSearchOpen: Boolean = false,
    val userSettings: UserSettings = UserSettings(),
    val revealedSensitiveCardIds: Set<Long> = emptySet(),
    val showInAppCaptureSheet: Boolean = false,
    val showShareDialog: Boolean = false,
    val shareContent: String = "",
    val showDeleteConfirmDialog: Boolean = false,
    val showExportDialog: Boolean = false,
    val previewCard: ClipboardCard? = null,
    val previewAnchorY: Float = 0f
)

sealed class VaultEvent {
    data class ShowToast(val message: String) : VaultEvent()
    data object NavigateToEditor : VaultEvent()
    data object RequestExportFolder : VaultEvent()
    data object NavigateToSettings : VaultEvent()
}

class VaultViewModel(
    private val repository: ClipboardRepository,
    private val settingsDataStore: SettingsDataStore,
    private val fileManager: FileManager,
    private val appContext: Context
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _isSearchOpen = MutableStateFlow(false)
    val isSearchOpen = _isSearchOpen.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds = _selectedIds.asStateFlow()

    private val _revealedSensitiveIds = MutableStateFlow<Set<Long>>(emptySet())
    val revealedSensitiveIds = _revealedSensitiveIds.asStateFlow()

    private val _showInAppCaptureSheet = MutableStateFlow(false)
    val showInAppCaptureSheet = _showInAppCaptureSheet.asStateFlow()

    private val _showShareDialog = MutableStateFlow(false)
    val showShareDialog = _showShareDialog.asStateFlow()

    private val _shareContent = MutableStateFlow("")
    val shareContent = _shareContent.asStateFlow()

    private val _showDeleteConfirmDialog = MutableStateFlow(false)
    val showDeleteConfirmDialog = _showDeleteConfirmDialog.asStateFlow()

    private val _showExportDialog = MutableStateFlow(false)
    val showExportDialog = _showExportDialog.asStateFlow()

    private val _previewCard = MutableStateFlow<ClipboardCard?>(null)
    val previewCard = _previewCard.asStateFlow()

    private val _previewAnchorY = MutableStateFlow(0f)
    val previewAnchorY = _previewAnchorY.asStateFlow()

    private val _eventFlow = MutableSharedFlow<VaultEvent>()
    val eventFlow: SharedFlow<VaultEvent> = _eventFlow.asSharedFlow()

    private val _settingsLoaded = MutableStateFlow(false)
    val settingsLoaded: StateFlow<Boolean> = _settingsLoaded.asStateFlow()

    val userSettings: StateFlow<UserSettings> = settingsDataStore.userSettingsFlow
        .onEach { _settingsLoaded.value = true }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserSettings()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val rawCardsFlow = _searchQuery.flatMapLatest { query ->
        if (query.isBlank()) {
            repository.getAllCardProjections()
        } else {
            repository.searchCardProjections(query.trim())
        }
    }

    val uiState: StateFlow<VaultUiState> = combine(
        rawCardsFlow,
        _selectedIds,
        _searchQuery,
        _isSearchOpen,
        userSettings,
        _revealedSensitiveIds,
        _showInAppCaptureSheet,
        _showShareDialog,
        _shareContent,
        _showDeleteConfirmDialog,
        _showExportDialog,
        _previewCard,
        _previewAnchorY
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val rawCards = args[0] as List<ClipboardCardProjection>
        @Suppress("UNCHECKED_CAST")
        val selected = args[1] as Set<Long>
        val query = args[2] as String
        val searchOpen = args[3] as Boolean
        val settings = args[4] as UserSettings
        @Suppress("UNCHECKED_CAST")
        val revealed = args[5] as Set<Long>
        val captureSheet = args[6] as Boolean
        val shareDialog = args[7] as Boolean
        val shareText = args[8] as String
        val deleteDialog = args[9] as Boolean
        val exportDialog = args[10] as Boolean
        val previewCard = args[11] as ClipboardCard?
        val previewAnchorY = args[12] as Float

        val presentedCards = OrderHelper.applyPresentationOrder(
            items = rawCards,
            showPinnedFirst = settings.showPinnedFirst
        )

        VaultUiState(
            cards = presentedCards,
            selectedIds = selected,
            searchQuery = query,
            isSearchOpen = searchOpen,
            userSettings = settings,
            revealedSensitiveCardIds = revealed,
            showInAppCaptureSheet = captureSheet,
            showShareDialog = shareDialog,
            shareContent = shareText,
            showDeleteConfirmDialog = deleteDialog,
            showExportDialog = exportDialog,
            previewCard = previewCard,
            previewAnchorY = previewAnchorY
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VaultUiState()
    )

    init {
        viewModelScope.launch {
            userSettings.collect { settings ->
                repository.cleanupOldCards(settings.retentionPolicy)
            }
        }
    }

    fun openSearch() {
        _isSearchOpen.value = true
    }

    fun closeSearch() {
        _isSearchOpen.value = false
        _searchQuery.value = ""
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleCardSelection(id: Long) {
        val current = _selectedIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _selectedIds.value = current
    }

    fun onCardLongPress(id: Long) {
        val current = _selectedIds.value.toMutableSet()
        if (!current.contains(id)) {
            current.add(id)
            _selectedIds.value = current
        }
        // Long press on already selected card is a NO-OP as per specs
    }

    fun selectAll() {
        val allIds = uiState.value.cards.map { it.id }.toSet()
        _selectedIds.value = allIds
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun toggleShowPinnedFirst() {
        viewModelScope.launch {
            val current = userSettings.value.showPinnedFirst
            settingsDataStore.setShowPinnedFirst(!current)
        }
    }

    fun togglePinSelected() {
        val selected = _selectedIds.value.toList()
        if (selected.isEmpty()) return

        val cards = uiState.value.cards.filter { selected.contains(it.id) }
        val allPinned = cards.all { it.pinned }
        val newPinned = !allPinned

        viewModelScope.launch {
            repository.setPinned(selected, newPinned)
            _selectedIds.value = emptySet()
            emitPluralToast(
                if (newPinned) com.example.R.plurals.pinned_items else com.example.R.plurals.unpinned_items,
                selected.size
            )
        }
    }

    fun requestDeleteSelected() {
        val selected = _selectedIds.value
        if (selected.isEmpty()) return
        if (selected.size > 1) {
            _showDeleteConfirmDialog.value = true
        } else {
            confirmDeleteSelected()
        }
    }

    fun dismissDeleteDialog() {
        _showDeleteConfirmDialog.value = false
    }

    fun confirmDeleteSelected() {
        val selected = _selectedIds.value.toList()
        _showDeleteConfirmDialog.value = false
        if (selected.isEmpty()) return

        viewModelScope.launch {
            repository.deleteCards(selected)
            _selectedIds.value = emptySet()
            emitPluralToast(com.example.R.plurals.deleted_items, selected.size)
        }
    }

    fun shareSelected(context: Context) {
        val selected = _selectedIds.value
        if (selected.isEmpty()) return
        val orderedSelectedIds = uiState.value.cards
            .filter { selected.contains(it.id) }
            .map { it.id }

        viewModelScope.launch {
            val fullCards = repository.getCardsByIds(orderedSelectedIds)
            if (fullCards.isNotEmpty()) {
                val shareText = TextNormalizer.formatSelectedCards(fullCards.map { it.content })
                val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                    type = "text/plain"
                }
                context.startActivity(android.content.Intent.createChooser(
                    sendIntent,
                    localizedContext().getString(com.example.R.string.share_selected_cards)
                ))
            }
        }
    }

    fun copySelectedCards(context: Context) {
        val selected = _selectedIds.value
        if (selected.isEmpty()) return

        // Fetch in visible display order
        val orderedSelectedIds = uiState.value.cards
            .filter { selected.contains(it.id) }
            .map { it.id }

        viewModelScope.launch {
            val fullCards = repository.getCardsByIds(orderedSelectedIds)
            if (fullCards.isNotEmpty()) {
                val combinedText = TextNormalizer.formatSelectedCards(
                    fullCards.map { it.content },
                    includeHeaders = true
                )
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Vault Cards", combinedText)
                clipboard.setPrimaryClip(clip)
                emitPluralToast(com.example.R.plurals.copied_items, fullCards.size)
            }
        }
    }

    fun copySingleCard(context: Context, id: Long) {
        viewModelScope.launch {
            val card = repository.getCardById(id) ?: return@launch
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(
                localizedContext().getString(com.example.R.string.clipboard_card_label),
                card.content
            )
            clipboard.setPrimaryClip(clip)
            emitToast(com.example.R.string.copied_to_clipboard)
        }
    }

    fun openPreview(id: Long, anchorY: Float) {
        viewModelScope.launch {
            val card = repository.getCardById(id) ?: return@launch
            _previewAnchorY.value = anchorY
            _previewCard.value = card
        }
    }

    fun closePreview() {
        _previewCard.value = null
        _previewAnchorY.value = 0f
    }

    fun toggleRevealSensitive(id: Long) {
        val current = _revealedSensitiveIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _revealedSensitiveIds.value = current
    }

    fun captureCurrentClipboard(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = runCatching {
            clipboard.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(context)
                ?.toString()
                ?.trim()
        }.getOrNull()

        if (text.isNullOrBlank()) {
            emitToast(com.example.R.string.clipboard_empty)
            return
        }

        viewModelScope.launch {
            val saved = repository.saveCards(
                listOf(CapturePayload(content = text, sourceApp = CaptureSource.MANUAL_CLIPBOARD_BUTTON))
            )
            _eventFlow.emit(
                VaultEvent.ShowToast(
                    localizedContext().getString(
                        if (saved.isNotEmpty()) com.example.R.string.clipboard_saved else com.example.R.string.clipboard_empty
                    )
                )
            )
        }
    }

    fun openInAppCapture() {
        _showInAppCaptureSheet.value = true
    }

    fun closeInAppCapture() {
        _showInAppCaptureSheet.value = false
    }

    fun saveInAppCapture(text: String, isSensitive: Boolean) {
        viewModelScope.launch {
            val card = repository.saveCard(
                content = text,
                sourceApp = CaptureSource.MANUAL_ENTRY,
                isSensitive = isSensitive
            )
            _showInAppCaptureSheet.value = false
            if (card != null) {
                emitToast(com.example.R.string.clipboard_saved)
            }
        }
    }

    fun handleIncomingShare(text: String) {
        _shareContent.value = text
        _showShareDialog.value = true
    }

    fun dismissShareDialog() {
        _showShareDialog.value = false
        _shareContent.value = ""
    }

    fun saveShareSelections(
        saveShared: Boolean,
        saveClipboard: Boolean,
        clipboardSnapshot: String
    ) {
        val shared = _shareContent.value.takeIf { saveShared && it.isNotBlank() }
        val clipText = clipboardSnapshot.takeIf { saveClipboard && it.isNotBlank() }

        val payloads = when {
            clipText != null && shared != null && clipText != shared -> listOf(
                CapturePayload(
                    content = TextNormalizer.combine(clipText, shared),
                    sourceApp = CaptureSource.COMBINED,
                    contentType = com.example.data.model.ContentType.COMBINED
                )
            )
            clipText != null -> listOf(CapturePayload(clipText, CaptureSource.SYSTEM_CLIPBOARD))
            shared != null -> listOf(CapturePayload(shared, CaptureSource.ANDROID_SHARE))
            else -> emptyList()
        }

        viewModelScope.launch {
            val saved = repository.saveCards(payloads)
            dismissShareDialog()
            if (saved.isNotEmpty()) {
                emitToast(com.example.R.string.clipboard_saved)
            }
        }
    }

    fun reorderItems(orderedIds: List<Long>) {
        val currentVisible = uiState.value.cards
        if (orderedIds.isEmpty()) return

        val orderedCards = orderedIds.mapNotNull { id -> currentVisible.firstOrNull { it.id == id } }
        val groups = if (uiState.value.userSettings.showPinnedFirst) {
            listOf(true, false).map { pinned -> currentVisible.filter { it.pinned == pinned } }
        } else {
            listOf(currentVisible)
        }

        val updates = groups.flatMap { currentGroup ->
            if (currentGroup.isEmpty()) return@flatMap emptyList<Pair<Long, Long>>()
            val currentIds = currentGroup.map { it.id }
            val requestedGroup = if (uiState.value.userSettings.showPinnedFirst) {
                orderedCards.filter { it.pinned == currentGroup.first().pinned }
            } else {
                orderedCards
            }
            if (requestedGroup.map { it.id } == currentIds) {
                emptyList<Pair<Long, Long>>()
            } else {
                requestedGroup.mapIndexed { index, card ->
                    card.id to ((requestedGroup.size - index) * OrderHelper.ORDER_STEP)
                }
            }
        }

        if (updates.isNotEmpty()) {
            viewModelScope.launch {
                repository.updateSortOrders(updates)
            }
        }
    }

    fun openExportDialog() {
        _showExportDialog.value = true
    }

    fun dismissExportDialog() {
        _showExportDialog.value = false
    }

    private data class PendingExport(val fileName: String, val format: ExportFormat)
    private var pendingExport: PendingExport? = null

    fun exportFiles(fileName: String, format: ExportFormat, contentResolver: android.content.ContentResolver) {
        val selected = _selectedIds.value
        val targetIds = if (selected.isNotEmpty()) {
            uiState.value.cards.filter { selected.contains(it.id) }.map { it.id }
        } else {
            uiState.value.cards.map { it.id }
        }

        viewModelScope.launch {
            val cards = repository.getCardsByIds(targetIds)
            val formatted = when (format) {
                ExportFormat.MARKDOWN -> ExportFormatter.formatMarkdown(cards, localizedExportLabels())
                ExportFormat.PLAIN_TEXT -> ExportFormatter.formatPlainText(cards, localizedExportLabels())
            }
            val folderUri = userSettings.value.defaultSaveFolderUri
            if (folderUri.isNullOrBlank()) {
                pendingExport = PendingExport(fileName.trim(), format)
                _showExportDialog.value = false
                _eventFlow.emit(VaultEvent.RequestExportFolder)
            } else {
                saveExportToFolder(contentResolver, android.net.Uri.parse(folderUri), fileName.trim(), format, formatted)
            }
        }
    }

    fun setExportFolder(uri: android.net.Uri, contentResolver: android.content.ContentResolver) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        viewModelScope.launch {
            settingsDataStore.setDefaultSaveFolderUri(uri.toString())
            val request = pendingExport ?: return@launch
            pendingExport = null
            val selected = _selectedIds.value
            val targetIds = if (selected.isNotEmpty()) {
                uiState.value.cards.filter { selected.contains(it.id) }.map { it.id }
            } else {
                uiState.value.cards.map { it.id }
            }
            val cards = repository.getCardsByIds(targetIds)
            val formatted = when (request.format) {
                ExportFormat.MARKDOWN -> ExportFormatter.formatMarkdown(cards, localizedExportLabels())
                ExportFormat.PLAIN_TEXT -> ExportFormatter.formatPlainText(cards, localizedExportLabels())
            }
            saveExportToFolder(contentResolver, uri, request.fileName, request.format, formatted)
        }
    }

    private fun saveExportToFolder(
        contentResolver: android.content.ContentResolver,
        folderUri: android.net.Uri,
        fileName: String,
        format: ExportFormat,
        content: String
    ) {
        viewModelScope.launch {
            val saved = runCatching {
                fileManager.saveNewFileToTree(contentResolver, folderUri, fileName, format, content)
            }.getOrNull()
            _eventFlow.emit(
                VaultEvent.ShowToast(
                    localizedContext().getString(
                        if (saved == null) com.example.R.string.could_not_save_file else com.example.R.string.saved_to_vault
                    )
                )
            )
        }
    }

    fun copySelectedCardsThenOpenEditor(context: Context, onComplete: () -> Unit) {
        val selected = _selectedIds.value
        if (selected.isEmpty()) {
            onComplete()
            return
        }
        val orderedSelectedIds = uiState.value.cards
            .filter { selected.contains(it.id) }
            .map { it.id }
        viewModelScope.launch {
            val fullCards = repository.getCardsByIds(orderedSelectedIds)
            if (fullCards.isNotEmpty()) {
                val combinedText = TextNormalizer.formatSelectedCards(
                    fullCards.map { it.content },
                    includeHeaders = true
                )
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Vault Cards", combinedText))
            }
            withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                onComplete()
            }
        }
    }

    fun openEditorAction() {
        viewModelScope.launch {
            _eventFlow.emit(VaultEvent.NavigateToEditor)
        }
    }

    fun openSettingsAction() {
        viewModelScope.launch {
            _eventFlow.emit(VaultEvent.NavigateToSettings)
        }
    }

    private fun localizedContext(): Context = appContext.withAppLanguage(userSettings.value.language)

    private fun localizedExportLabels(): ExportLabels {
        val context = localizedContext()
        return ExportLabels(
            title = context.getString(com.example.R.string.export_title),
            noItems = context.getString(com.example.R.string.export_no_items),
            exportedOn = context.getString(com.example.R.string.exported_on),
            date = context.getString(com.example.R.string.export_date),
            source = context.getString(com.example.R.string.export_source).substringBefore(":"),
            item = context.getString(com.example.R.string.export_item),
            captured = context.getString(com.example.R.string.export_captured),
            pinned = context.getString(com.example.R.string.export_pinned),
            urlType = context.getString(com.example.R.string.export_url_type),
            combinedType = context.getString(com.example.R.string.export_combined_type),
            sourceLabel = { value -> localizedCaptureSourceLabel(context, value) }
        )
    }

    private fun emitToast(@StringRes resourceId: Int, vararg args: Any) {
        viewModelScope.launch {
            _eventFlow.emit(VaultEvent.ShowToast(localizedContext().getString(resourceId, *args)))
        }
    }

    private fun emitPluralToast(@PluralsRes resourceId: Int, quantity: Int) {
        viewModelScope.launch {
            _eventFlow.emit(
                VaultEvent.ShowToast(
                    localizedContext().resources.getQuantityString(resourceId, quantity, quantity)
                )
            )
        }
    }

    fun markFirstRunEducationShown() {
        viewModelScope.launch {
            settingsDataStore.setFirstRunEducationShown(true)
        }
    }
}

enum class ShareChoice {
    USE_SHARED,
    USE_CLIPBOARD,
    USE_BOTH
}

class VaultViewModelFactory(
    private val repository: ClipboardRepository,
    private val settingsDataStore: SettingsDataStore,
    private val fileManager: FileManager,
    private val appContext: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VaultViewModel::class.java)) {
            return VaultViewModel(repository, settingsDataStore, fileManager, appContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
