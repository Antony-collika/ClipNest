package com.clipnest.ui.editor

import android.content.ClipboardManager
import android.content.Intent
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.math.max
import kotlin.math.min
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import com.clipnest.ExternalDocumentOpenContext
import com.clipnest.ExternalDocumentReadFailure
import com.clipnest.IncomingDocumentUri
import com.clipnest.buildOpenWithDiagnostic
import com.clipnest.data.local.ExportFormat
import com.clipnest.ui.localization.withAppLanguage
import com.clipnest.data.local.FileManager
import com.clipnest.data.local.SettingsDataStore
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

private sealed class EditOperation {
    data class Insert(val position: Int, val text: String) : EditOperation()
    data class Delete(val position: Int, val length: Int, val deletedText: String) : EditOperation()
    data class Replace(val start: Int, val end: Int, val newText: String, val oldText: String) : EditOperation()
}

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
    val externalDocumentUri: String? = null,
    val externalDocumentSaveAsOnly: Boolean = false,
    val externalDocumentFileCount: Int = 0
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
    private val _openWithDiagnostic = MutableStateFlow<String?>(null)
    val openWithDiagnostic: StateFlow<String?> = _openWithDiagnostic.asStateFlow()
    private val _isSearchOpen = MutableStateFlow(false)
    val isSearchOpen: StateFlow<Boolean> = _isSearchOpen.asStateFlow()
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    private val _searchMatchCount = MutableStateFlow(0)
    val searchMatchCount: StateFlow<Int> = _searchMatchCount.asStateFlow()
    private val _activeSearchMatch = MutableStateFlow(0)
    val activeSearchMatch: StateFlow<Int> = _activeSearchMatch.asStateFlow()

    private var searchMatchStarts: List<Int> = emptyList()
    private val undoStack = ArrayDeque<EditOperation>()
    private val redoStack = ArrayDeque<EditOperation>()
    private var applyingHistory = false
    private var editorLoaded = false
    private var pendingSave: PendingSave? = null
    private var autoSaveJob: Job? = null
    private var editorSnapshotJob: Job? = null
    private var editorInstance: HighlightingEditText? = null

    val settings: StateFlow<com.clipnest.data.local.UserSettings> = settingsDataStore.userSettingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = com.clipnest.data.local.UserSettings()
    )

    init {
        loadEditor()
        viewModelScope.launch {
            settings.collect { userSettings ->
                _uiState.value = _uiState.value.copy(defaultSaveFolderUri = userSettings.defaultSaveFolderUri)
            }
        }
    }

    fun setEditorInstance(editor: HighlightingEditText) {
        editorInstance = editor
    }

    fun onTextChange(change: TextChange) {
        if (applyingHistory) return
        when {
            change.removedLength > 0 && change.addedLength == 0 -> {
                undoStack.addLast(EditOperation.Delete(change.start.coerceAtLeast(0), change.removedLength, change.removedText.orEmpty()))
                redoStack.clear()
            }
            change.removedLength == 0 && change.addedLength > 0 -> {
                undoStack.addLast(EditOperation.Insert(change.start.coerceAtLeast(0), change.addedText.orEmpty()))
                redoStack.clear()
            }
            change.removedLength > 0 && change.addedLength > 0 -> {
                undoStack.addLast(
                    EditOperation.Replace(
                        change.start.coerceAtLeast(0),
                        change.start.coerceAtLeast(0) + change.removedLength,
                        change.addedText.orEmpty(),
                        change.removedText.orEmpty()
                    )
                )
                redoStack.clear()
            }
        }
        while (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        _uiState.value = _uiState.value.copy(isDirty = true)
        scheduleEditorSnapshot()
        scheduleDebouncedAutoSave()
    }

    private fun scheduleEditorSnapshot() {
        editorSnapshotJob?.cancel()
        editorSnapshotJob = viewModelScope.launch {
            delay(80)
            val editor = editorInstance ?: return@launch
            val text = editor.getFullText()
            val selectionStart = editor.selectionStart.coerceIn(0, text.length)
            val selectionEnd = editor.selectionEnd.coerceIn(selectionStart, text.length)
            val current = _uiState.value.content
            if (current.text != text || current.selection.start != selectionStart || current.selection.end != selectionEnd) {
                _uiState.value = _uiState.value.copy(
                    content = TextFieldValue(text, TextRange(selectionStart, selectionEnd))
                )
            }
            if (_searchQuery.value.isNotBlank()) updateSearchResults(_searchQuery.value, text)
        }
    }

    private fun syncEditorState() {
        val editor = editorInstance ?: return
        val text = editor.getFullText()
        val selectionStart = editor.selectionStart.coerceIn(0, text.length)
        val selectionEnd = editor.selectionEnd.coerceIn(selectionStart, text.length)
        _uiState.value = _uiState.value.copy(
            content = TextFieldValue(text, TextRange(selectionStart, selectionEnd))
        )
        if (_searchQuery.value.isNotBlank()) updateSearchResults(_searchQuery.value, text)
    }

    private fun currentEditorText(): String = editorInstance?.getFullText() ?: _uiState.value.content.text

    fun onSelectionChange(start: Int, end: Int) = Unit

    private fun loadEditor() {
        viewModelScope.launch(Dispatchers.IO) {
            val text = fileManager.readEditor()
            withContext(Dispatchers.Main.immediate) {
                if (!editorLoaded) {
                    undoStack.clear()
                    redoStack.clear()
                    _uiState.value = _uiState.value.copy(
                        content = TextFieldValue(text, TextRange(text.length)),
                        isDirty = false,
                        lastSavedTimestamp = System.currentTimeMillis()
                    )
                    editorInstance?.setFullText(text, text.length, text.length)
                }
                editorLoaded = true
            }
        }
    }

    fun openExternalDocument(
        uri: Uri,
        contentResolver: ContentResolver,
        openContext: ExternalDocumentOpenContext? = null,
        candidates: List<IncomingDocumentUri> = listOf(
            IncomingDocumentUri(uri, openContext?.source ?: com.clipnest.IncomingUriSource.FILE_PICKER)
        )
    ) {
        val previousState = _uiState.value
        editorLoaded = true
        autoSaveJob?.cancel()
        editorSnapshotJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            val failedCandidates = mutableListOf<ExternalDocumentReadFailure>()
            val result = runCatching {
                val candidateUris = candidates.ifEmpty {
                    listOf(IncomingDocumentUri(uri, openContext?.source ?: com.clipnest.IncomingUriSource.FILE_PICKER))
                }
                val loaded = candidateUris.mapNotNull { candidate ->
                    runCatching { candidate to readExternalDocument(candidate.uri, contentResolver) }
                        .onFailure { error -> failedCandidates += ExternalDocumentReadFailure(candidate, error) }
                        .getOrNull()
                }
                if (loaded.isEmpty()) throw IllegalStateException(
                    "Unable to read any incoming document URI",
                    failedCandidates.firstOrNull()?.error
                )
                val merged = if (loaded.size > 1) mergeExternalDocuments(loaded, contentResolver) else loaded.first().second
                val name = queryDisplayName(loaded.first().first.uri, contentResolver)
                Triple(loaded, name, merged)
            }
            withContext(Dispatchers.Main.immediate) {
                result.onSuccess { (loadedDocuments, name, text) ->
                    val candidate = loadedDocuments.first().first
                    val isMergedDocument = candidates.size > 1
                    val firstCandidate = loadedDocuments.firstOrNull()?.first ?: candidate
                    undoStack.clear()
                    redoStack.clear()
                    resetSearchState()
                    _uiState.value = _uiState.value.copy(
                        content = TextFieldValue(text, TextRange(text.length)),
                        isDirty = false,
                        documentName = if (isMergedDocument) {
                            appContext.withAppLanguage(settings.value.language).getString(
                                com.clipnest.R.string.merged_document_name,
                                loadedDocuments.size
                            )
                        } else name,
                        externalDocumentUri = firstCandidate.uri.toString(),
                        externalDocumentSaveAsOnly = isMergedDocument,
                        externalDocumentFileCount = loadedDocuments.size,
                        showSaveNewFileDialog = false,
                        lastSavedTimestamp = System.currentTimeMillis()
                    )
                    editorInstance?.setFullText(text, text.length, text.length)
                    _openWithDiagnostic.value = null
                    if (failedCandidates.isNotEmpty()) {
                        emitToast(com.clipnest.R.string.opened_files_with_failures, loadedDocuments.size, candidates.size)
                    }
                    viewModelScope.launch(Dispatchers.IO) {
                        runCatching { writeDocumentSnapshot(previousState, contentResolver, previousState.content.text) }
                            .onFailure { error -> Log.e(TAG, "Could not snapshot previous document before opening: $uri", error) }
                    }
                }.onFailure { error ->
                    val rootCause = generateSequence(error) { it.cause }.last()
                    Log.e(TAG, "Could not open external document: uri=$uri, error=${error::class.java.simpleName}, root=${rootCause::class.java.simpleName}: ${rootCause.message}", error)
                    emitToast(com.clipnest.R.string.could_not_open_file)
                    _openWithDiagnostic.value = if (failedCandidates.isEmpty()) {
                        buildOpenWithDiagnostic(uri, openContext, error)
                    } else buildOpenWithDiagnostic(failedCandidates, openContext)
                }
            }
        }
    }

    fun dismissOpenWithDiagnostic() { _openWithDiagnostic.value = null }

    fun returnToInternalEditor(contentResolver: ContentResolver, onComplete: () -> Unit = {}) {
        if (_uiState.value.externalDocumentUri == null) return
        val previousState = _uiState.value
        val previousText = currentEditorText()
        autoSaveJob?.cancel()
        editorSnapshotJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                writeDocumentSnapshot(previousState, contentResolver, previousText)
                fileManager.readEditor()
            }
            withContext(Dispatchers.Main.immediate) {
                result.onSuccess { text ->
                    undoStack.clear()
                    redoStack.clear()
                    resetSearchState()
                    _uiState.value = _uiState.value.copy(
                        content = TextFieldValue(text, TextRange(text.length)),
                        isDirty = false,
                        documentName = internalDocumentName(),
                        externalDocumentUri = null,
                        externalDocumentSaveAsOnly = false,
                        externalDocumentFileCount = 0,
                        showSaveNewFileDialog = false,
                        lastSavedTimestamp = System.currentTimeMillis()
                    )
                    editorInstance?.setFullText(text, text.length, text.length)
                    onComplete()
                }.onFailure { emitToast(com.clipnest.R.string.could_not_open_file) }
            }
        }
    }

    private fun mergeExternalDocuments(
        loaded: List<Pair<IncomingDocumentUri, String>>,
        contentResolver: ContentResolver
    ): String {
        val blocks = loaded.mapIndexed { index, (candidate, content) ->
            val queriedName = queryDisplayName(candidate.uri, contentResolver)
            val openFileLabel = appContext.withAppLanguage(settings.value.language).getString(com.clipnest.R.string.open_file)
            val displayName = queriedName.takeUnless { it == openFileLabel }
                ?: appContext.withAppLanguage(settings.value.language).getString(com.clipnest.R.string.external_file_number, index + 1)
            ExternalDocumentBlock(displayName = displayName, content = content)
        }
        return mergeExternalDocumentBlocks(blocks)
    }

    private fun readExternalDocument(uri: Uri, contentResolver: ContentResolver): String {
        val bytes = when (uri.scheme?.lowercase()) {
            ContentResolver.SCHEME_CONTENT -> readContentUri(uri, contentResolver)
            ContentResolver.SCHEME_FILE -> readFileUri(uri, contentResolver)
            else -> throw IllegalArgumentException("Unsupported URI scheme for document: $uri")
        }
        return decodeUtf8(bytes)
    }

    private fun readFileUri(uri: Uri, contentResolver: ContentResolver): ByteArray {
        var lastFailure: Throwable? = null
        fun attempt(method: String, reader: () -> ByteArray?): ByteArray? = try {
            reader()
        } catch (error: Exception) {
            lastFailure = error
            Log.w(TAG, "Open With file URI read failed: method=$method, uri=$uri", error)
            null
        }
        val bytes = attempt("ContentResolver.openInputStream") {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } ?: attempt("java.io.FileInputStream") {
            val path = uri.path?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("File URI has no path")
            File(path).inputStream().use { it.readBytes() }
        }
        return bytes ?: throw IllegalStateException("Unable to read file URI", lastFailure)
    }

    private fun readContentUri(uri: Uri, contentResolver: ContentResolver): ByteArray {
        var lastFailure: Throwable? = null
        fun attempt(method: String, reader: () -> ByteArray?): ByteArray? = try {
            reader()
        } catch (error: Exception) {
            lastFailure = error
            Log.w(TAG, "Open With read failed: method=$method, uri=$uri", error)
            null
        }
        val bytes = attempt("openInputStream") {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } ?: attempt("openFileDescriptor") {
            contentResolver.openFileDescriptor(uri, "r")?.let { descriptor ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
            }
        } ?: attempt("openAssetFileDescriptor") {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor -> descriptor.createInputStream().use { it.readBytes() } }
        } ?: listOf("*/*", "text/markdown", "text/plain", "text/*").firstNotNullOfOrNull { mimeType ->
            attempt("openTypedAssetFileDescriptor($mimeType)") {
                contentResolver.openTypedAssetFileDescriptor(uri, mimeType, null)?.use { descriptor -> descriptor.createInputStream().use { it.readBytes() } }
            }
        }
        return bytes ?: throw IllegalStateException("Unable to read content URI: $uri", lastFailure)
    }

    private fun decodeUtf8(bytes: ByteArray): String {
        val offset = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) 3 else 0
        return bytes.copyOfRange(offset, bytes.size).toString(Charsets.UTF_8)
    }

    private fun writeExternalDocument(uri: Uri, contentResolver: ContentResolver, text: String) {
        when (uri.scheme?.lowercase()) {
            ContentResolver.SCHEME_FILE -> {
                val path = uri.path?.takeIf { it.isNotBlank() } ?: error("File URI has no path")
                File(path).outputStream().use { output -> output.write(text.toByteArray(Charsets.UTF_8)); output.flush() }
            }
            else -> {
                val output = runCatching { contentResolver.openOutputStream(uri, "wt") }.getOrNull()
                    ?: runCatching { contentResolver.openOutputStream(uri) }.getOrNull()
                    ?: error("Unable to save file")
                output.use { it.write(text.toByteArray(Charsets.UTF_8)); it.flush() }
            }
        }
    }

    private fun queryDisplayName(uri: Uri, contentResolver: ContentResolver): String {
        val queriedName = runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        return queriedName?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: appContext.withAppLanguage(settings.value.language).getString(com.clipnest.R.string.open_file)
    }

    private fun internalDocumentName(): String = appContext.withAppLanguage(settings.value.language).getString(com.clipnest.R.string.editor)

    private fun writeDocumentSnapshot(state: EditorUiState, contentResolver: ContentResolver, content: String) {
        if (state.externalDocumentSaveAsOnly) return
        val uri = state.externalDocumentUri?.let(Uri::parse)
        if (uri == null) {
            if (state.isDirty) fileManager.writeEditor(content)
            return
        }
        if (!state.isDirty) return
        writeExternalDocument(uri, contentResolver, content)
    }

    private fun resetSearchState() {
        _isSearchOpen.value = false
        _searchQuery.value = ""
        searchMatchStarts = emptyList()
        _searchMatchCount.value = 0
        _activeSearchMatch.value = 0
    }

    fun openSearch() { _isSearchOpen.value = true }

    fun closeSearch() {
        _isSearchOpen.value = false
        _searchQuery.value = ""
        searchMatchStarts = emptyList()
        _searchMatchCount.value = 0
        _activeSearchMatch.value = 0
        val position = editorInstance?.selectionEnd?.coerceAtLeast(0) ?: _uiState.value.content.selection.end
        _uiState.value = _uiState.value.copy(content = _uiState.value.content.copy(selection = TextRange(position)))
        editorInstance?.setEditorSelectionIfNeeded(position, position)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        updateSearchResults(query, currentEditorText())
    }

    fun nextSearchMatch() {
        if (searchMatchStarts.isEmpty()) return
        selectSearchMatch((_activeSearchMatch.value + 1) % searchMatchStarts.size)
    }

    fun previousSearchMatch() {
        if (searchMatchStarts.isEmpty()) return
        selectSearchMatch((_activeSearchMatch.value - 1 + searchMatchStarts.size) % searchMatchStarts.size)
    }

    private fun updateSearchResults(query: String, text: String) {
        if (query.isBlank()) {
            searchMatchStarts = emptyList()
            _searchMatchCount.value = 0
            _activeSearchMatch.value = 0
            return
        }
        val matches = mutableListOf<Int>()
        var searchFrom = 0
        while (searchFrom <= text.length - query.length) {
            val match = text.indexOf(query, searchFrom, ignoreCase = true)
            if (match < 0) break
            matches += match
            searchFrom = match + query.length.coerceAtLeast(1)
        }
        searchMatchStarts = matches
        _searchMatchCount.value = matches.size
        if (matches.isEmpty()) {
            _activeSearchMatch.value = 0
            emitToast(com.clipnest.R.string.no_matches)
            return
        }
        val caret = editorInstance?.selectionStart?.coerceIn(0, text.length) ?: _uiState.value.content.selection.start
        val firstAtOrAfterCaret = matches.indexOfFirst { it >= caret }
        selectSearchMatch(if (firstAtOrAfterCaret >= 0) firstAtOrAfterCaret else 0)
    }

    private fun selectSearchMatch(index: Int) {
        val start = searchMatchStarts.getOrNull(index) ?: return
        _activeSearchMatch.value = index
        val end = start + _searchQuery.value.length
        _uiState.value = _uiState.value.copy(content = _uiState.value.content.copy(selection = TextRange(start, end)))
        editorInstance?.setEditorSelectionIfNeeded(start, end)
    }

    fun pasteFromClipboard(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = runCatching { clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() }.getOrNull()
        if (clip.isNullOrEmpty()) {
            emitToast(com.clipnest.R.string.clipboard_empty)
            return
        }
        val editor = editorInstance ?: run {
            emitToast(com.clipnest.R.string.editor_not_ready)
            return
        }
        val start = editor.selectionStart.coerceAtLeast(0)
        val end = editor.selectionEnd.coerceAtLeast(start)
        recordAndApplyDirectEdit(editor, start, end, clip, start + clip.length, start + clip.length)
        emitToast(com.clipnest.R.string.pasted)
    }

    fun copySelectedText(context: Context) {
        val editor = editorInstance
        val text = editor?.getFullText() ?: _uiState.value.content.text
        val selectionStart = editor?.selectionStart ?: _uiState.value.content.selection.min
        val selectionEnd = editor?.selectionEnd ?: _uiState.value.content.selection.max
        if (selectionStart == selectionEnd) {
            emitToast(com.clipnest.R.string.select_text_to_copy)
            return
        }
        val start = selectionStart.coerceIn(0, text.length)
        val end = selectionEnd.coerceIn(0, text.length)
        val selected = text.substring(min(start, end), max(start, end))
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Editor selection", selected))
        emitToast(com.clipnest.R.string.copied)
    }

    fun moveCursorLeft() {
        val editor = editorInstance ?: return
        val start = editor.selectionStart
        val end = editor.selectionEnd
        val position = if (start == end) max(0, start - 1) else min(start, end)
        editor.setEditorSelectionIfNeeded(position, position)
        syncEditorState()
    }

    fun moveCursorRight() {
        val editor = editorInstance ?: return
        val start = editor.selectionStart
        val end = editor.selectionEnd
        val position = if (start == end) min(editor.getFullText().length, end + 1) else max(start, end)
        editor.setEditorSelectionIfNeeded(position, position)
        syncEditorState()
    }

    fun selectAll() {
        val text = currentEditorText()
        _uiState.value = _uiState.value.copy(content = _uiState.value.content.copy(selection = TextRange(0, text.length)))
        editorInstance?.setEditorSelectionIfNeeded(0, text.length)
    }

    fun toggleMarkdownTools() {
        _uiState.value = _uiState.value.copy(isMarkdownToolsExpanded = !_uiState.value.isMarkdownToolsExpanded)
    }

    fun toggleMarkdownPreview() {
        val current = _uiState.value
        val opening = !current.showMarkdownPreview
        _uiState.value = current.copy(
            showMarkdownPreview = opening,
            previewSplitFraction = if (opening && current.previewSplitFraction <= 0f) DEFAULT_PREVIEW_FRACTION else current.previewSplitFraction
        )
    }

    fun setPreviewSplitFraction(fraction: Float) {
        _uiState.value = _uiState.value.copy(previewSplitFraction = fraction.coerceIn(MIN_PREVIEW_FRACTION, MAX_PREVIEW_FRACTION))
    }

    private fun recordAndApplyDirectEdit(
        editor: HighlightingEditText,
        start: Int,
        end: Int,
        replacement: String,
        selectionStart: Int,
        selectionEnd: Int
    ) {
        val editable = editor.getEditable() ?: return
        val safeStart = start.coerceIn(0, editable.length)
        val safeEnd = end.coerceIn(safeStart, editable.length)
        val oldText = editable.substring(safeStart, safeEnd)
        editor.applyDirectEdit(
            operation = { target -> target.replace(safeStart, safeEnd, replacement) },
            affectedStart = safeStart,
            affectedEnd = safeStart + replacement.length
        )
        recordEditOperation(safeStart, oldText, replacement, safeEnd)
        editor.setEditorSelectionIfNeeded(selectionStart, selectionEnd)
        _uiState.value = _uiState.value.copy(isDirty = true)
        syncEditorState()
        scheduleDebouncedAutoSave()
    }

    private fun recordEditOperation(start: Int, oldText: String, newText: String, oldEnd: Int = start + oldText.length) {
        when {
            oldText.isEmpty() && newText.isNotEmpty() -> undoStack.addLast(EditOperation.Insert(start, newText))
            oldText.isNotEmpty() && newText.isEmpty() -> undoStack.addLast(EditOperation.Delete(start, oldText.length, oldText))
            oldText != newText -> undoStack.addLast(EditOperation.Replace(start, oldEnd, newText, oldText))
        }
        if (oldText != newText) {
            redoStack.clear()
            while (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        }
    }

    fun insertMarkdownHeading(level: Int) { applyLinePrefix("#".repeat(level) + " ") }
    fun toggleMarkdownStrong() { applyInlineDelimiter("**") }
    fun toggleMarkdownEmphasis() { applyInlineDelimiter("*") }
    fun insertMarkdownQuote() { applyLinePrefix("> ") }

    fun insertMarkdownCodeBlock() {
        val text = currentEditorText()
        val editor = editorInstance ?: return
        val selectionStart = editor.selectionStart.coerceAtLeast(0)
        val selectionEnd = editor.selectionEnd.coerceAtLeast(selectionStart)
        if (selectionStart == selectionEnd) {
            val replacement = "```\n\n```"
            recordAndApplyDirectEdit(editor, selectionStart, selectionEnd, replacement, selectionStart + 4, selectionStart + 4)
        } else {
            val selected = text.substring(selectionStart, selectionEnd)
            val replacement = "```\n$selected\n```"
            recordAndApplyDirectEdit(editor, selectionStart, selectionEnd, replacement, selectionStart + 4, selectionStart + 4 + selected.length)
        }
    }

    fun insertMarkdownBullets() { applyLinePrefix("- ") }
    fun insertMarkdownNumbers() { applyLinePrefix("1. ") }

    fun insertMarkdownHorizontalRule() {
        val editor = editorInstance ?: return
        val text = editor.getFullText()
        val position = editor.selectionEnd.coerceAtLeast(0)
        val before = if (position > 0 && text[position - 1] != '\n') "\n" else ""
        val after = if (position < text.length && text[position] != '\n') "\n" else ""
        val replacement = "$before---$after"
        val newPosition = position + replacement.length
        recordAndApplyDirectEdit(editor, position, position, replacement, newPosition, newPosition)
    }

    private fun applyInlineDelimiter(delimiter: String) {
        val editor = editorInstance ?: return
        val text = editor.getFullText()
        val selectionStart = editor.selectionStart.coerceAtLeast(0)
        val selectionEnd = editor.selectionEnd.coerceAtLeast(selectionStart)
        if (selectionStart == selectionEnd) {
            val replacement = delimiter + delimiter
            recordAndApplyDirectEdit(editor, selectionStart, selectionEnd, replacement, selectionStart + delimiter.length, selectionStart + delimiter.length)
        } else {
            val selected = text.substring(selectionStart, selectionEnd)
            val replacement = delimiter + selected + delimiter
            val newStart = selectionStart + delimiter.length
            val newEnd = newStart + selected.length
            recordAndApplyDirectEdit(editor, selectionStart, selectionEnd, replacement, newStart, newEnd)
        }
    }

    private fun applyLinePrefix(prefix: String) {
        val editor = editorInstance ?: return
        val text = editor.getFullText()
        val selectionStart = editor.selectionStart.coerceAtLeast(0)
        val selectionEnd = editor.selectionEnd.coerceAtLeast(selectionStart)
        val lineStart = text.lastIndexOf('\n', (selectionStart - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', selectionEnd).let { if (it < 0) text.length else it }
        val lineText = text.substring(lineStart, lineEnd)
        val lines = if (lineText.isEmpty()) listOf("") else lineText.split('\n')
        val transformed = lines.joinToString("\n") { prefix + it }
        val addedPrefixLength = transformed.length - lineText.length
        val newStart = selectionStart + prefix.length
        val newEnd = if (selectionStart == selectionEnd) newStart else selectionEnd + addedPrefixLength
        recordAndApplyDirectEdit(editor, lineStart, lineEnd, transformed, newStart, newEnd)
    }

    fun deleteSelectedText() {
        val editor = editorInstance ?: run {
            emitToast(com.clipnest.R.string.editor_not_ready)
            return
        }
        val start = editor.selectionStart.coerceAtLeast(0)
        val end = editor.selectionEnd.coerceAtLeast(start)
        if (start == end) {
            emitToast(com.clipnest.R.string.select_text_to_delete)
            return
        }
        recordAndApplyDirectEdit(editor, start, end, "", start, start)
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val operation = undoStack.removeLast()
        val editor = editorInstance ?: return
        applyingHistory = true
        try {
            when (operation) {
                is EditOperation.Insert -> {
                    editor.applyDirectEdit({ editable -> editable.delete(operation.position, operation.position + operation.text.length) }, operation.position, operation.position)
                    redoStack.addLast(EditOperation.Delete(operation.position, operation.text.length, operation.text))
                    editor.setEditorSelectionIfNeeded(operation.position, operation.position)
                }
                is EditOperation.Delete -> {
                    editor.applyDirectEdit({ editable -> editable.replace(operation.position, operation.position, operation.deletedText) }, operation.position, operation.position + operation.deletedText.length)
                    redoStack.addLast(EditOperation.Insert(operation.position, operation.deletedText))
                    val position = operation.position + operation.deletedText.length
                    editor.setEditorSelectionIfNeeded(position, position)
                }
                is EditOperation.Replace -> {
                    editor.applyDirectEdit({ editable -> editable.replace(operation.start, operation.start + operation.newText.length, operation.oldText) }, operation.start, operation.start + operation.oldText.length)
                    redoStack.addLast(EditOperation.Replace(operation.start, operation.start + operation.oldText.length, operation.newText, operation.oldText))
                    val position = operation.start + operation.oldText.length
                    editor.setEditorSelectionIfNeeded(position, position)
                }
            }
            _uiState.value = _uiState.value.copy(isDirty = true)
            syncEditorState()
            scheduleDebouncedAutoSave()
        } finally {
            applyingHistory = false
        }
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val operation = redoStack.removeLast()
        val editor = editorInstance ?: return
        applyingHistory = true
        try {
            when (operation) {
                is EditOperation.Insert -> {
                    editor.applyDirectEdit({ editable -> editable.replace(operation.position, operation.position, operation.text) }, operation.position, operation.position + operation.text.length)
                    undoStack.addLast(EditOperation.Insert(operation.position, operation.text))
                    val position = operation.position + operation.text.length
                    editor.setEditorSelectionIfNeeded(position, position)
                }
                is EditOperation.Delete -> {
                    editor.applyDirectEdit({ editable -> editable.delete(operation.position, operation.position + operation.length) }, operation.position, operation.position)
                    undoStack.addLast(EditOperation.Delete(operation.position, operation.length, operation.deletedText))
                    editor.setEditorSelectionIfNeeded(operation.position, operation.position)
                }
                is EditOperation.Replace -> {
                    editor.applyDirectEdit({ editable -> editable.replace(operation.start, operation.start + operation.oldText.length, operation.newText) }, operation.start, operation.start + operation.newText.length)
                    undoStack.addLast(EditOperation.Replace(operation.start, operation.start + operation.newText.length, operation.newText, operation.oldText))
                    val position = operation.start + operation.newText.length
                    editor.setEditorSelectionIfNeeded(position, position)
                }
            }
            _uiState.value = _uiState.value.copy(isDirty = true)
            syncEditorState()
            scheduleDebouncedAutoSave()
        } finally {
            applyingHistory = false
        }
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
        val contentSnapshot = currentEditorText()
        viewModelScope.launch(Dispatchers.IO) {
            val saved = runCatching { writeDocumentSnapshot(stateSnapshot, appContext.contentResolver, contentSnapshot) }.isSuccess
            withContext(Dispatchers.Main.immediate) {
                if (saved && currentEditorText() == contentSnapshot) {
                    _uiState.value = _uiState.value.copy(
                        content = _uiState.value.content.copy(text = contentSnapshot),
                        isDirty = false,
                        lastSavedTimestamp = System.currentTimeMillis()
                    )
                }
            }
        }
    }

    fun onSaveClicked(contentResolver: ContentResolver = appContext.contentResolver) {
        val state = _uiState.value
        val externalUri = state.externalDocumentUri
        if (state.externalDocumentSaveAsOnly || externalUri == null) {
            _uiState.value = _uiState.value.copy(showSaveNewFileDialog = true)
        } else saveExternalDocument(Uri.parse(externalUri), contentResolver)
    }

    private fun saveExternalDocument(uri: Uri, contentResolver: ContentResolver) {
        val contentSnapshot = currentEditorText()
        viewModelScope.launch(Dispatchers.IO) {
            val saveResult = runCatching { writeExternalDocument(uri, contentResolver, contentSnapshot) }
            withContext(Dispatchers.Main.immediate) {
                if (saveResult.isSuccess) {
                    if (currentEditorText() == contentSnapshot) {
                        _uiState.value = _uiState.value.copy(
                            content = _uiState.value.content.copy(text = contentSnapshot),
                            isDirty = false,
                            lastSavedTimestamp = System.currentTimeMillis()
                        )
                    }
                    emitToast(com.clipnest.R.string.saved_current_file)
                } else {
                    Log.e(TAG, "Could not save external document: $uri", saveResult.exceptionOrNull())
                    _uiState.value = _uiState.value.copy(showSaveNewFileDialog = true)
                    emitToast(com.clipnest.R.string.could_not_save_open_file)
                }
            }
        }
    }

    fun dismissSaveNewFileDialog() { _uiState.value = _uiState.value.copy(showSaveNewFileDialog = false) }

    fun confirmSaveToNewFile(fileName: String, format: ExportFormat, contentResolver: ContentResolver) {
        if (fileName.isBlank()) return
        val folderUri = _uiState.value.defaultSaveFolderUri
        if (folderUri.isNullOrBlank()) {
            pendingSave = PendingSave(fileName.trim(), format)
            dismissSaveNewFileDialog()
            viewModelScope.launch { _eventFlow.emit(EditorEvent.RequestSaveFolder) }
            return
        }
        saveToFolder(contentResolver, Uri.parse(folderUri), fileName.trim(), format)
    }

    fun setDefaultSaveFolder(uri: Uri, contentResolver: ContentResolver) {
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        viewModelScope.launch { settingsDataStore.setDefaultSaveFolderUri(uri.toString()) }
        _uiState.value = _uiState.value.copy(defaultSaveFolderUri = uri.toString())
        val request = pendingSave ?: return
        pendingSave = null
        saveToFolder(contentResolver, uri, request.fileName, request.format)
    }

    private fun saveToFolder(contentResolver: ContentResolver, folderUri: Uri, fileName: String, format: ExportFormat) {
        val contentSnapshot = currentEditorText()
        viewModelScope.launch(Dispatchers.IO) {
            val saved = runCatching {
                fileManager.saveNewFileToTree(contentResolver, folderUri, fileName, format, contentSnapshot)
            }.getOrNull()
            withContext(Dispatchers.Main.immediate) {
                if (saved == null) emitToast(com.clipnest.R.string.could_not_save_file)
                else {
                    if (currentEditorText() == contentSnapshot) {
                        _uiState.value = _uiState.value.copy(
                            content = _uiState.value.content.copy(text = contentSnapshot),
                            showSaveNewFileDialog = false,
                            isDirty = false,
                            lastSavedTimestamp = System.currentTimeMillis()
                        )
                    }
                    emitToast(com.clipnest.R.string.saved_file, fileName, format.extension)
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
        private const val TAG = "XBoard.Editor"
        private const val MAX_HISTORY = 100
        private const val MIN_PREVIEW_FRACTION = 0.0f
        private const val MAX_PREVIEW_FRACTION = 1.0f
        private const val DEFAULT_PREVIEW_FRACTION = 0.30f
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
