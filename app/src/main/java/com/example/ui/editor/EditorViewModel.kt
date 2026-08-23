package com.example.ui.editor

import android.content.ClipboardManager
import android.content.Intent
import android.content.ContentResolver
import android.content.Context
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.ExportFormat
import com.example.data.local.FileManager
import com.example.data.local.SettingsDataStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private data class PendingSave(val fileName: String, val format: ExportFormat)

data class EditorUiState(
    val content: TextFieldValue = TextFieldValue(""),
    val isDirty: Boolean = false,
    val showSaveNewFileDialog: Boolean = false,
    val defaultSaveFolderUri: String? = null,
    val lastSavedTimestamp: Long = 0L
)

sealed class EditorEvent {
    data class ShowToast(val message: String) : EditorEvent()
    data object RequestSaveFolder : EditorEvent()
}

class EditorViewModel(
    private val fileManager: FileManager,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<EditorEvent>()
    val eventFlow: SharedFlow<EditorEvent> = _eventFlow.asSharedFlow()

    private val undoStack = ArrayDeque<TextFieldValue>()
    private val redoStack = ArrayDeque<TextFieldValue>()
    private var applyingHistory = false
    private var pendingSave: PendingSave? = null
    private var autoSaveJob: Job? = null

    val settings: StateFlow<com.example.data.local.UserSettings> = settingsDataStore.userSettingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = com.example.data.local.UserSettings()
    )

    init {
        loadEditor()
        viewModelScope.launch {
            settings.collect { userSettings ->
                _uiState.value = _uiState.value.copy(defaultSaveFolderUri = userSettings.defaultSaveFolderUri)
            }
        }
    }

    private fun loadEditor() {
        val text = fileManager.readEditor()
        val value = TextFieldValue(text = text, selection = TextRange(text.length))
        undoStack.clear()
        redoStack.clear()
        _uiState.value = _uiState.value.copy(
            content = value,
            isDirty = false,
            lastSavedTimestamp = System.currentTimeMillis()
        )
    }

    fun onContentChange(newValue: TextFieldValue) {
        val current = _uiState.value.content
        if (newValue.text != current.text && !applyingHistory) {
            undoStack.addLast(current)
            if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
            redoStack.clear()
        }
        _uiState.value = _uiState.value.copy(
            content = newValue,
            isDirty = _uiState.value.isDirty || newValue.text != current.text
        )
        if (newValue.text != current.text) scheduleDebouncedAutoSave()
    }

    fun pasteFromClipboard(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = runCatching { clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() }.getOrNull()
        if (clip.isNullOrEmpty()) {
            emitToast("Clipboard is empty")
            return
        }
        replaceSelection(clip)
        emitToast("Pasted")
    }

    fun selectAll() {
        val text = _uiState.value.content.text
        _uiState.value = _uiState.value.copy(content = _uiState.value.content.copy(selection = TextRange(0, text.length)))
    }

    fun deleteSelectedText() {
        val current = _uiState.value.content
        if (current.selection.collapsed) {
            emitToast("Select text to delete")
            return
        }
        replaceSelection("")
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val current = _uiState.value.content
        val previous = undoStack.removeLast()
        redoStack.addLast(current)
        applyHistoryValue(previous)
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val current = _uiState.value.content
        val next = redoStack.removeLast()
        undoStack.addLast(current)
        applyHistoryValue(next)
    }

    private fun applyHistoryValue(value: TextFieldValue) {
        applyingHistory = true
        _uiState.value = _uiState.value.copy(content = value, isDirty = true)
        applyingHistory = false
        scheduleDebouncedAutoSave()
    }

    private fun replaceSelection(replacement: String) {
        val current = _uiState.value.content
        val start = current.selection.min
        val end = current.selection.max
        val newText = current.text.substring(0, start) + replacement + current.text.substring(end)
        val newCursor = start + replacement.length
        onContentChange(TextFieldValue(newText, TextRange(newCursor)))
    }

    private fun scheduleDebouncedAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(1500)
            if (_uiState.value.isDirty) saveCurrentDocumentSilently()
        }
    }

    fun saveCurrentDocumentSilently() {
        fileManager.writeEditor(_uiState.value.content.text)
        _uiState.value = _uiState.value.copy(
            isDirty = false,
            lastSavedTimestamp = System.currentTimeMillis()
        )
    }

    fun onSaveClicked() {
        _uiState.value = _uiState.value.copy(showSaveNewFileDialog = true)
    }

    fun dismissSaveNewFileDialog() {
        _uiState.value = _uiState.value.copy(showSaveNewFileDialog = false)
    }

    fun confirmSaveToNewFile(fileName: String, format: ExportFormat, contentResolver: ContentResolver) {
        if (fileName.isBlank()) return
        val folderUri = _uiState.value.defaultSaveFolderUri
        if (folderUri.isNullOrBlank()) {
            pendingSave = PendingSave(fileName.trim(), format)
            dismissSaveNewFileDialog()
            viewModelScope.launch { _eventFlow.emit(EditorEvent.RequestSaveFolder) }
            return
        }
        saveToFolder(contentResolver, android.net.Uri.parse(folderUri), fileName.trim(), format)
    }

    fun setDefaultSaveFolder(uri: android.net.Uri, contentResolver: ContentResolver) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        viewModelScope.launch { settingsDataStore.setDefaultSaveFolderUri(uri.toString()) }
        _uiState.value = _uiState.value.copy(defaultSaveFolderUri = uri.toString())
        val request = pendingSave ?: return
        pendingSave = null
        saveToFolder(contentResolver, uri, request.fileName, request.format)
    }

    private fun saveToFolder(
        contentResolver: ContentResolver,
        folderUri: android.net.Uri,
        fileName: String,
        format: ExportFormat
    ) {
        val saved = runCatching {
            fileManager.saveNewFileToTree(
                contentResolver = contentResolver,
                treeUri = folderUri,
                baseName = fileName,
                format = format,
                content = _uiState.value.content.text
            )
        }.getOrNull()
        if (saved == null) {
            emitToast("Could not save file")
        } else {
            _uiState.value = _uiState.value.copy(showSaveNewFileDialog = false)
            emitToast("Saved ${fileName}${format.extension}")
        }
    }

    fun onPauseOrExit() {
        if (_uiState.value.isDirty) saveCurrentDocumentSilently()
    }

    private fun emitToast(message: String) {
        viewModelScope.launch { _eventFlow.emit(EditorEvent.ShowToast(message)) }
    }

    companion object {
        private const val MAX_HISTORY = 100
    }
}

class EditorViewModelFactory(
    private val fileManager: FileManager,
    private val settingsDataStore: SettingsDataStore
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditorViewModel::class.java)) {
            return EditorViewModel(fileManager, settingsDataStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
