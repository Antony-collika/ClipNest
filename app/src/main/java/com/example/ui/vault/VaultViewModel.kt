package com.example.ui.vault

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.ExportFormat
import com.example.data.local.FileManager
import com.example.data.local.NoteDocType
import com.example.data.local.SettingsDataStore
import com.example.data.local.UserSettings
import com.example.data.model.ClipboardCard
import com.example.data.model.ClipboardCardProjection
import com.example.data.model.ContentType
import com.example.data.repository.ClipboardRepository
import com.example.domain.ExportFormatter
import com.example.domain.OrderHelper
import com.example.domain.TextNormalizer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    val showExportDialog: Boolean = false
)

sealed class VaultEvent {
    data class ShowToast(val message: String) : VaultEvent()
    data class NavigateToEditor(val docType: NoteDocType) : VaultEvent()
    data object NavigateToSettings : VaultEvent()
}

class VaultViewModel(
    private val repository: ClipboardRepository,
    private val settingsDataStore: SettingsDataStore,
    private val fileManager: FileManager
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

    private val _eventFlow = MutableSharedFlow<VaultEvent>()
    val eventFlow: SharedFlow<VaultEvent> = _eventFlow.asSharedFlow()

    val userSettings: StateFlow<UserSettings> = settingsDataStore.userSettingsFlow.stateIn(
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
        _showExportDialog
    ) { args: Array<Any> ->
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
            showExportDialog = exportDialog
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
            _eventFlow.emit(VaultEvent.ShowToast(if (newPinned) "Pinned ${selected.size} items" else "Unpinned ${selected.size} items"))
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
            _eventFlow.emit(VaultEvent.ShowToast("Deleted ${selected.size} items"))
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
                val combinedText = fullCards.joinToString("\n\n---\n\n") { it.content }
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Vault Cards", combinedText)
                clipboard.setPrimaryClip(clip)
                _eventFlow.emit(VaultEvent.ShowToast("Copied ${fullCards.size} items to clipboard"))
            }
        }
    }

    fun copySingleCard(context: Context, id: Long) {
        viewModelScope.launch {
            val card = repository.getCardById(id) ?: return@launch
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Clipboard Card", card.content)
            clipboard.setPrimaryClip(clip)
            _eventFlow.emit(VaultEvent.ShowToast("Copied to clipboard"))
        }
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
                sourceApp = "Manual Entry",
                isSensitive = isSensitive
            )
            _showInAppCaptureSheet.value = false
            if (card != null) {
                _eventFlow.emit(VaultEvent.ShowToast("Clipboard saved"))
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

    fun saveShareOption(choice: ShareChoice, context: Context) {
        val shared = _shareContent.value
        val clipManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipText = clipManager.primaryClip?.getItemAt(0)?.text?.toString() ?: ""

        viewModelScope.launch {
            when (choice) {
                ShareChoice.USE_SHARED -> {
                    if (shared.isNotBlank()) {
                        repository.saveCard(
                            content = shared,
                            sourceApp = "Android Share",
                            contentType = TextNormalizer.detectContentType(shared)
                        )
                    }
                }
                ShareChoice.USE_CLIPBOARD -> {
                    if (clipText.isNotBlank()) {
                        repository.saveCard(
                            content = clipText,
                            sourceApp = "System Clipboard",
                            contentType = TextNormalizer.detectContentType(clipText)
                        )
                    }
                }
                ShareChoice.USE_BOTH -> {
                    val combined = TextNormalizer.combine(shared, clipText)
                    if (combined.isNotBlank()) {
                        repository.saveCard(
                            content = combined,
                            sourceApp = "Share & Clipboard",
                            contentType = ContentType.COMBINED
                        )
                    }
                }
            }
            dismissShareDialog()
            _eventFlow.emit(VaultEvent.ShowToast("Clipboard saved"))
        }
    }

    fun reorderItems(fromIndex: Int, toIndex: Int) {
        val currentVisible = uiState.value.cards
        val updates = OrderHelper.calculateNewSortOrders(currentVisible, fromIndex, toIndex)
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

    fun exportFiles(fileName: String, format: ExportFormat) {
        val selected = _selectedIds.value
        val targetIds = if (selected.isNotEmpty()) {
            uiState.value.cards.filter { selected.contains(it.id) }.map { it.id }
        } else {
            uiState.value.cards.map { it.id }
        }

        viewModelScope.launch {
            val cards = repository.getCardsByIds(targetIds)
            val formatted = when (format) {
                ExportFormat.MARKDOWN -> ExportFormatter.formatMarkdown(cards)
                ExportFormat.PLAIN_TEXT -> ExportFormatter.formatPlainText(cards)
            }
            val file = fileManager.saveNewFile(fileName, format, formatted)
            _showExportDialog.value = false
            _eventFlow.emit(VaultEvent.ShowToast("Saved to ${file.name}"))
        }
    }

    fun openEditorAction() {
        viewModelScope.launch {
            settingsDataStore.setLastActiveNoteFile(NoteDocType.DRAFT)
            _eventFlow.emit(VaultEvent.NavigateToEditor(NoteDocType.DRAFT))
        }
    }

    fun openSettingsAction() {
        viewModelScope.launch {
            _eventFlow.emit(VaultEvent.NavigateToSettings)
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
    private val fileManager: FileManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VaultViewModel::class.java)) {
            return VaultViewModel(repository, settingsDataStore, fileManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
