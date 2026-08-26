package com.example.ui.editor

import android.content.ClipboardManager
import android.content.Intent
import android.content.ContentResolver
import android.content.Context
import android.provider.OpenableColumns
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.math.max
import kotlin.math.min
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import com.example.data.local.ExportFormat
import com.example.ui.localization.withAppLanguage
import com.example.data.local.FileManager
import com.example.data.local.SettingsDataStore
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext

private data class PendingSave(val fileName: String, val format: ExportFormat)

data class EditorUiState(
    val content: TextFieldValue = TextFieldValue(""),
    val isDirty: Boolean = false,
    val showSaveNewFileDialog: Boolean = false,
    val defaultSaveFolderUri: String? = null,
    val lastSavedTimestamp: Long = 0L,
    val isMarkdownToolsExpanded: Boolean = false,
    val showMarkdownPreview: Boolean = false,
    val previewSplitFraction: Float = 0.30f,
    val documentName: String = "Editor",
    val externalDocumentUri: String? = null
)

sealed class EditorEvent {
    data class ShowToast(val message: String) : EditorEvent()
    data object RequestSaveFolder : EditorEvent()
}

class EditorViewModel(
    private val fileManager: FileManager,
    private val settingsDataStore: SettingsDataStore,
    private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<EditorEvent>()
    val eventFlow: SharedFlow<EditorEvent> = _eventFlow.asSharedFlow()

    private val _isSearchOpen = MutableStateFlow(false)
    val isSearchOpen: StateFlow<Boolean> = _isSearchOpen.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchMatchCount = MutableStateFlow(0)
    val searchMatchCount: StateFlow<Int> = _searchMatchCount.asStateFlow()

    private val _activeSearchMatch = MutableStateFlow(0)
    val activeSearchMatch: StateFlow<Int> = _activeSearchMatch.asStateFlow()

    private var searchMatchStarts: List<Int> = emptyList()

    private val undoStack = ArrayDeque<TextFieldValue>()
    private val redoStack = ArrayDeque<TextFieldValue>()
    private var applyingHistory = false
    private var editorLoaded = false
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
        viewModelScope.launch(Dispatchers.IO) {
            val text = fileManager.readEditor()
            withContext(Dispatchers.Main.immediate) {
                if (!editorLoaded) {
                    val value = TextFieldValue(text = text, selection = TextRange(text.length))
                    undoStack.clear()
                    redoStack.clear()
                    _uiState.value = _uiState.value.copy(
                        content = value,
                        isDirty = false,
                        lastSavedTimestamp = System.currentTimeMillis()
                    )
                    if (_searchQuery.value.isNotBlank()) {
                        updateSearchResults(_searchQuery.value)
                    }
                }
                editorLoaded = true
            }
        }
    }

    fun openExternalDocument(uri: android.net.Uri, contentResolver: ContentResolver) {
        val previousState = _uiState.value
        editorLoaded = true
        autoSaveJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                writeDocumentSnapshot(previousState, contentResolver)
                val text = contentResolver.openInputStream(uri)?.use { input ->
                    input.readBytes().toString(Charsets.UTF_8)
                } ?: error("Unable to open file")
                val name = queryDisplayName(uri, contentResolver)
                name to text
            }
            withContext(Dispatchers.Main.immediate) {
                result.onSuccess { (name, text) ->
                    val value = TextFieldValue(text = text, selection = TextRange(text.length))
                    undoStack.clear()
                    redoStack.clear()
                    resetSearchState()
                    _uiState.value = _uiState.value.copy(
                        content = value,
                        isDirty = false,
                        documentName = name,
                        externalDocumentUri = uri.toString(),
                        showSaveNewFileDialog = false,
                        lastSavedTimestamp = System.currentTimeMillis()
                    )
                }.onFailure {
                    emitToast(com.example.R.string.could_not_open_file)
                }
            }
        }
    }

    fun returnToInternalEditor(contentResolver: ContentResolver, onComplete: () -> Unit = {}) {
        if (_uiState.value.externalDocumentUri == null) return
        val previousState = _uiState.value
        autoSaveJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                writeDocumentSnapshot(previousState, contentResolver)
                fileManager.readEditor()
            }
            withContext(Dispatchers.Main.immediate) {
                result.onSuccess { text ->
                    val value = TextFieldValue(text = text, selection = TextRange(text.length))
                    undoStack.clear()
                    redoStack.clear()
                    resetSearchState()
                    _uiState.value = _uiState.value.copy(
                        content = value,
                        isDirty = false,
                        documentName = internalDocumentName(),
                        externalDocumentUri = null,
                        showSaveNewFileDialog = false,
                        lastSavedTimestamp = System.currentTimeMillis()
                    )
                    onComplete()
                }.onFailure {
                    emitToast(com.example.R.string.could_not_open_file)
                }
            }
        }
    }

    private fun queryDisplayName(uri: android.net.Uri, contentResolver: ContentResolver): String {
        val queriedName = runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        return queriedName?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: appContext.withAppLanguage(settings.value.language).getString(com.example.R.string.open_file)
    }

    private fun internalDocumentName(): String =
        appContext.withAppLanguage(settings.value.language).getString(com.example.R.string.editor)

    private fun writeDocumentSnapshot(state: EditorUiState, contentResolver: ContentResolver) {
        val uri = state.externalDocumentUri?.let(android.net.Uri::parse)
        if (uri == null) {
            if (state.isDirty) fileManager.writeEditor(state.content.text)
            return
        }
        if (!state.isDirty) return
        contentResolver.openOutputStream(uri, "wt")?.use { output ->
            output.write(state.content.text.toByteArray(Charsets.UTF_8))
            output.flush()
        } ?: error("Unable to save file")
    }

    private fun resetSearchState() {
        _isSearchOpen.value = false
        _searchQuery.value = ""
        searchMatchStarts = emptyList()
        _searchMatchCount.value = 0
        _activeSearchMatch.value = 0
    }

    fun onContentChange(newValue: TextFieldValue) {
        editorLoaded = true
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
        if (newValue.text != current.text) {
            if (_searchQuery.value.isNotBlank()) {
                updateSearchResults(_searchQuery.value)
            }
            scheduleDebouncedAutoSave()
        }
    }

    fun openSearch() {
        _isSearchOpen.value = true
    }

    fun closeSearch() {
        _isSearchOpen.value = false
        _searchQuery.value = ""
        searchMatchStarts = emptyList()
        _searchMatchCount.value = 0
        _activeSearchMatch.value = 0
        val current = _uiState.value.content
        _uiState.value = _uiState.value.copy(content = current.copy(selection = TextRange(current.selection.end)))
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        updateSearchResults(query)
    }

    fun nextSearchMatch() {
        if (searchMatchStarts.isEmpty()) return
        val next = (_activeSearchMatch.value + 1) % searchMatchStarts.size
        selectSearchMatch(next)
    }

    fun previousSearchMatch() {
        if (searchMatchStarts.isEmpty()) return
        val previous = (_activeSearchMatch.value - 1 + searchMatchStarts.size) % searchMatchStarts.size
        selectSearchMatch(previous)
    }

    private fun updateSearchResults(query: String) {
        if (query.isBlank()) {
            searchMatchStarts = emptyList()
            _searchMatchCount.value = 0
            _activeSearchMatch.value = 0
            return
        }

        val text = _uiState.value.content.text
        val matches = mutableListOf<Int>()
        var searchFrom = 0
        while (searchFrom <= text.length - query.length) {
            val match = text.indexOf(query, startIndex = searchFrom, ignoreCase = true)
            if (match < 0) break
            matches += match
            searchFrom = match + query.length.coerceAtLeast(1)
        }

        searchMatchStarts = matches
        _searchMatchCount.value = matches.size
        if (matches.isEmpty()) {
            _activeSearchMatch.value = 0
            emitToast(com.example.R.string.no_matches)
            return
        }

        val currentCaret = _uiState.value.content.selection.start
        val firstAtOrAfterCaret = matches.indexOfFirst { it >= currentCaret }
        val active = if (firstAtOrAfterCaret >= 0) firstAtOrAfterCaret else 0
        selectSearchMatch(active)
    }

    private fun selectSearchMatch(index: Int) {
        val start = searchMatchStarts.getOrNull(index) ?: return
        _activeSearchMatch.value = index
        val queryLength = _searchQuery.value.length
        val current = _uiState.value.content
        _uiState.value = _uiState.value.copy(
            content = current.copy(selection = TextRange(start, start + queryLength))
        )
    }

    fun pasteFromClipboard(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = runCatching { clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() }.getOrNull()
        if (clip.isNullOrEmpty()) {
            emitToast(com.example.R.string.clipboard_empty)
            return
        }
        replaceSelection(clip)
        emitToast(com.example.R.string.pasted)
    }

    fun copySelectedText(context: Context) {
        val current = _uiState.value.content
        if (current.selection.collapsed) {
            emitToast(com.example.R.string.select_text_to_copy)
            return
        }
        val selected = current.text.substring(current.selection.min, current.selection.max)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Editor selection", selected))
        emitToast(com.example.R.string.copied)
    }

    fun moveCursorLeft() {
        val current = _uiState.value.content
        val position = if (current.selection.collapsed) {
            max(0, current.selection.start - 1)
        } else {
            current.selection.min
        }
        _uiState.value = _uiState.value.copy(content = current.copy(selection = TextRange(position)))
    }

    fun moveCursorRight() {
        val current = _uiState.value.content
        val position = if (current.selection.collapsed) {
            min(current.text.length, current.selection.end + 1)
        } else {
            current.selection.max
        }
        _uiState.value = _uiState.value.copy(content = current.copy(selection = TextRange(position)))
    }

    fun selectAll() {
        val text = _uiState.value.content.text
        _uiState.value = _uiState.value.copy(content = _uiState.value.content.copy(selection = TextRange(0, text.length)))
    }

    fun toggleMarkdownTools() {
        _uiState.value = _uiState.value.copy(isMarkdownToolsExpanded = !_uiState.value.isMarkdownToolsExpanded)
    }

    fun toggleMarkdownPreview() {
        _uiState.value = _uiState.value.copy(showMarkdownPreview = !_uiState.value.showMarkdownPreview)
    }

    fun setPreviewSplitFraction(fraction: Float) {
        _uiState.value = _uiState.value.copy(
            previewSplitFraction = fraction.coerceIn(MIN_PREVIEW_FRACTION, MAX_PREVIEW_FRACTION)
        )
    }

    fun insertMarkdownHeading(level: Int) {
        applyLinePrefix("${"#".repeat(level)} ")
    }

    fun toggleMarkdownStrong() {
        applyInlineDelimiter("**")
    }

    fun toggleMarkdownEmphasis() {
        applyInlineDelimiter("*")
    }

    fun insertMarkdownQuote() {
        applyLinePrefix("> ")
    }

    fun insertMarkdownCodeBlock() {
        val current = _uiState.value.content
        val selection = current.selection
        if (selection.collapsed) {
            replaceRange(
                start = selection.start,
                end = selection.end,
                replacement = "```\n\n```",
                selectionStart = selection.start + 4,
                selectionEnd = selection.start + 4
            )
            return
        }
        val selected = current.text.substring(selection.min, selection.max)
        val replacement = "```\n$selected\n```"
        replaceRange(
            start = selection.min,
            end = selection.max,
            replacement = replacement,
            selectionStart = selection.min + 4,
            selectionEnd = selection.min + 4 + selected.length
        )
    }

    fun insertMarkdownBullets() {
        applyLinePrefix("- ")
    }

    fun insertMarkdownNumbers() {
        applyLinePrefix("1. ")
    }

    fun insertMarkdownHorizontalRule() {
        val current = _uiState.value.content
        val caret = current.selection.max
        val before = if (caret > 0 && current.text[caret - 1] != '\n') "\n" else ""
        val after = if (caret < current.text.length && current.text[caret] != '\n') "\n" else ""
        val replacement = "$before---$after"
        val newCaret = caret + before.length + 3 + after.length
        replaceRange(caret, caret, replacement, newCaret, newCaret)
    }

    private fun applyInlineDelimiter(delimiter: String) {
        val current = _uiState.value.content
        val selection = current.selection
        if (selection.collapsed) {
            replaceRange(
                start = selection.start,
                end = selection.end,
                replacement = delimiter + delimiter,
                selectionStart = selection.start + delimiter.length,
                selectionEnd = selection.start + delimiter.length
            )
        } else {
            val selected = current.text.substring(selection.min, selection.max)
            val replacement = delimiter + selected + delimiter
            replaceRange(
                start = selection.min,
                end = selection.max,
                replacement = replacement,
                selectionStart = selection.min + delimiter.length,
                selectionEnd = selection.min + delimiter.length + selected.length
            )
        }
    }

    private fun applyLinePrefix(prefix: String) {
        val current = _uiState.value.content
        val selection = current.selection
        val start = current.text.lastIndexOf('\n', (selection.min - 1).coerceAtLeast(0))
            .let { if (it < 0) 0 else it + 1 }
        val end = current.text.indexOf('\n', selection.max).let { if (it < 0) current.text.length else it }
        val block = current.text.substring(start, end)
        val lines = if (block.isEmpty()) listOf("") else block.split('\n')
        val transformed = lines.joinToString("\n") { line -> prefix + line }
        val insertedPrefixLength = transformed.length - block.length
        val newSelectionStart = selection.min + prefix.length
        val newSelectionEnd = if (selection.collapsed) newSelectionStart else selection.max + insertedPrefixLength
        replaceRange(start, end, transformed, newSelectionStart, newSelectionEnd)
    }

    private fun replaceRange(
        start: Int,
        end: Int,
        replacement: String,
        selectionStart: Int,
        selectionEnd: Int
    ) {
        val current = _uiState.value.content
        val newText = current.text.substring(0, start) + replacement + current.text.substring(end)
        onContentChange(
            TextFieldValue(
                text = newText,
                selection = TextRange(selectionStart.coerceIn(0, newText.length), selectionEnd.coerceIn(0, newText.length))
            )
        )
    }

    fun deleteSelectedText() {
        val current = _uiState.value.content
        if (current.selection.collapsed) {
            emitToast(com.example.R.string.select_text_to_delete)
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
        val stateSnapshot = _uiState.value
        val contentSnapshot = stateSnapshot.content.text
        viewModelScope.launch(Dispatchers.IO) {
            val saved = runCatching {
                writeDocumentSnapshot(stateSnapshot, appContext.contentResolver)
            }.isSuccess
            withContext(Dispatchers.Main.immediate) {
                if (saved && _uiState.value.content.text == contentSnapshot) {
                    _uiState.value = _uiState.value.copy(
                        isDirty = false,
                        lastSavedTimestamp = System.currentTimeMillis()
                    )
                }
            }
        }
    }

    fun onSaveClicked(contentResolver: ContentResolver = appContext.contentResolver) {
        val externalUri = _uiState.value.externalDocumentUri
        if (externalUri == null) {
            _uiState.value = _uiState.value.copy(showSaveNewFileDialog = true)
        } else {
            saveExternalDocument(android.net.Uri.parse(externalUri), contentResolver)
        }
    }

    private fun saveExternalDocument(uri: android.net.Uri, contentResolver: ContentResolver) {
        val contentSnapshot = _uiState.value.content.text
        viewModelScope.launch(Dispatchers.IO) {
            val saved = runCatching {
                contentResolver.openOutputStream(uri, "wt")?.use { output ->
                    output.write(contentSnapshot.toByteArray(Charsets.UTF_8))
                    output.flush()
                } ?: error("Unable to save file")
            }.isSuccess
            withContext(Dispatchers.Main.immediate) {
                if (saved) {
                    if (_uiState.value.content.text == contentSnapshot) {
                        _uiState.value = _uiState.value.copy(
                            isDirty = false,
                            lastSavedTimestamp = System.currentTimeMillis()
                        )
                    }
                    emitToast(com.example.R.string.saved_current_file)
                } else {
                    _uiState.value = _uiState.value.copy(showSaveNewFileDialog = true)
                    emitToast(com.example.R.string.could_not_save_open_file)
                }
            }
        }
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
        val contentSnapshot = _uiState.value.content.text
        viewModelScope.launch(Dispatchers.IO) {
            val saved = runCatching {
                fileManager.saveNewFileToTree(
                    contentResolver = contentResolver,
                    treeUri = folderUri,
                    baseName = fileName,
                    format = format,
                    content = contentSnapshot
                )
            }.getOrNull()
            withContext(Dispatchers.Main.immediate) {
                if (saved == null) {
                    emitToast(com.example.R.string.could_not_save_file)
                } else {
                    _uiState.value = _uiState.value.copy(showSaveNewFileDialog = false)
                    emitToast(com.example.R.string.saved_file, fileName, format.extension)
                }
            }
        }
    }

    fun onPauseOrExit() {
        if (_uiState.value.isDirty) saveCurrentDocumentSilently()
    }

    private fun emitToast(message: String) {
        viewModelScope.launch { _eventFlow.emit(EditorEvent.ShowToast(message)) }
    }

    private fun emitToast(@StringRes resourceId: Int, vararg args: Any) {
        val localizedContext = appContext.withAppLanguage(settings.value.language)
        emitToast(localizedContext.getString(resourceId, *args))
    }

    companion object {
        private const val MAX_HISTORY = 100
        private const val MIN_PREVIEW_FRACTION = 0.18f
        private const val MAX_PREVIEW_FRACTION = 1.0f
    }
}

class EditorViewModelFactory(
    private val fileManager: FileManager,
    private val settingsDataStore: SettingsDataStore,
    private val appContext: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditorViewModel::class.java)) {
            return EditorViewModel(fileManager, settingsDataStore, appContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
