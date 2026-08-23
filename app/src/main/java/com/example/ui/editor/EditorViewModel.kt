package com.example.ui.editor

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.ExportFormat
import com.example.data.local.FileManager
import com.example.data.local.NoteDocType
import com.example.data.local.SettingsDataStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EditorUiState(
    val activeDocType: NoteDocType = NoteDocType.DRAFT,
    val content: TextFieldValue = TextFieldValue(""),
    val isDirty: Boolean = false,
    val showSaveMenu: Boolean = false,
    val showSaveNewFileDialog: Boolean = false,
    val isDropdownExpanded: Boolean = false,
    val lastSavedTimestamp: Long = 0L
)

sealed class EditorEvent {
    data class ShowToast(val message: String) : EditorEvent()
}

class EditorViewModel(
    private val fileManager: FileManager,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<EditorEvent>()
    val eventFlow: SharedFlow<EditorEvent> = _eventFlow.asSharedFlow()

    private var autoSaveJob: Job? = null

    init {
        viewModelScope.launch {
            settingsDataStore.userSettingsFlow.collect { settings ->
                val initialDoc = settings.lastActiveNoteFile
                if (_uiState.value.content.text.isEmpty() && !_uiState.value.isDirty) {
                    loadDocument(initialDoc)
                }
            }
        }
    }

    fun loadDocument(docType: NoteDocType) {
        val text = fileManager.readNote(docType)
        _uiState.value = _uiState.value.copy(
            activeDocType = docType,
            content = TextFieldValue(text = text, selection = TextRange(text.length)),
            isDirty = false,
            isDropdownExpanded = false,
            lastSavedTimestamp = System.currentTimeMillis()
        )
    }

    fun switchDocument(newDocType: NoteDocType) {
        if (newDocType == _uiState.value.activeDocType) {
            _uiState.value = _uiState.value.copy(isDropdownExpanded = false)
            return
        }

        // Autosave current document before switching
        saveCurrentDocumentSilently()

        // Persist last active note setting
        viewModelScope.launch {
            settingsDataStore.setLastActiveNoteFile(newDocType)
        }

        // Load target document
        loadDocument(newDocType)
    }

    fun onContentChange(newValue: TextFieldValue) {
        val isChanged = newValue.text != _uiState.value.content.text
        _uiState.value = _uiState.value.copy(
            content = newValue,
            isDirty = _uiState.value.isDirty || isChanged
        )

        if (isChanged) {
            scheduleDebouncedAutoSave()
        }
    }

    private fun scheduleDebouncedAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(1500) // Debounce 1.5s
            if (_uiState.value.isDirty) {
                saveCurrentDocumentSilently()
            }
        }
    }

    fun saveCurrentDocumentSilently() {
        val docType = _uiState.value.activeDocType
        val text = _uiState.value.content.text
        fileManager.writeNote(docType, text)
        _uiState.value = _uiState.value.copy(
            isDirty = false,
            lastSavedTimestamp = System.currentTimeMillis()
        )
    }

    fun onSaveClicked() {
        val activeDoc = _uiState.value.activeDocType
        if (activeDoc == NoteDocType.DRAFT) {
            // Open Save Menu for Draft: "Save Draft" vs "Save to new file"
            _uiState.value = _uiState.value.copy(showSaveMenu = true)
        } else {
            // For Note, standard save
            saveCurrentDocumentSilently()
            viewModelScope.launch {
                _eventFlow.emit(EditorEvent.ShowToast("Note saved"))
            }
        }
    }

    fun saveDraftDirectly() {
        saveCurrentDocumentSilently()
        _uiState.value = _uiState.value.copy(showSaveMenu = false)
        viewModelScope.launch {
            _eventFlow.emit(EditorEvent.ShowToast("Draft saved"))
        }
    }

    fun openSaveNewFileDialog() {
        _uiState.value = _uiState.value.copy(
            showSaveMenu = false,
            showSaveNewFileDialog = true
        )
    }

    fun dismissSaveMenu() {
        _uiState.value = _uiState.value.copy(showSaveMenu = false)
    }

    fun dismissSaveNewFileDialog() {
        _uiState.value = _uiState.value.copy(showSaveNewFileDialog = false)
    }

    fun saveToNewFile(fileName: String, format: ExportFormat) {
        val content = _uiState.value.content.text
        val file = fileManager.saveNewFile(fileName, format, content)
        _uiState.value = _uiState.value.copy(showSaveNewFileDialog = false)
        viewModelScope.launch {
            _eventFlow.emit(EditorEvent.ShowToast("Saved to ${file.name}"))
        }
    }

    fun pasteFromClipboard(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString()

        if (clip.isNullOrEmpty()) {
            viewModelScope.launch {
                _eventFlow.emit(EditorEvent.ShowToast("Clipboard is empty"))
            }
            return
        }

        val currentFieldValue = _uiState.value.content
        val currentText = currentFieldValue.text
        val selection = currentFieldValue.selection

        val start = selection.min
        val end = selection.max

        val newText = currentText.substring(0, start) + clip + currentText.substring(end)
        val newCursor = start + clip.length

        onContentChange(
            TextFieldValue(
                text = newText,
                selection = TextRange(newCursor)
            )
        )

        viewModelScope.launch {
            _eventFlow.emit(EditorEvent.ShowToast("Pasted"))
        }
    }

    fun setDropdownExpanded(expanded: Boolean) {
        _uiState.value = _uiState.value.copy(isDropdownExpanded = expanded)
    }

    fun insertMarkdownHelper(prefix: String, suffix: String = "") {
        val currentFieldValue = _uiState.value.content
        val currentText = currentFieldValue.text
        val selection = currentFieldValue.selection

        val start = selection.min
        val end = selection.max
        val selectedText = currentText.substring(start, end)

        val replacement = "$prefix$selectedText$suffix"
        val newText = currentText.substring(0, start) + replacement + currentText.substring(end)
        val newCursor = if (selectedText.isEmpty()) start + prefix.length else start + replacement.length

        onContentChange(
            TextFieldValue(
                text = newText,
                selection = TextRange(newCursor)
            )
        )
    }

    fun onPauseOrExit() {
        if (_uiState.value.isDirty) {
            saveCurrentDocumentSilently()
        }
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
