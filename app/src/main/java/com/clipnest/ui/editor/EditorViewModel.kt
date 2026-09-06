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

/**
 * Represents an undo/redo operation.
 * Much lighter than storing full TextFieldValue snapshots.
 */
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

    // Undo/redo using operations instead of full snapshots
    private val undoStack = ArrayDeque<EditOperation>()
    private val redoStack = ArrayDeque<EditOperation>()
    private var applyingHistory = false
    private var editorLoaded = false
    private var pendingSave: PendingSave? = null
    private var autoSaveJob: Job? = null

    // Reference to the editor for direct Editable manipulation
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

    /**
     * Called by EditorScreen when text changes.
     * Instead of receiving full text, we receive what changed.
     */
    fun onTextChange(change: TextChange) {
        if (applyingHistory) return
        
        val currentContent = _uiState.value.content
        val oldText = currentContent.text
        val newText = buildString {
            append(oldText.substring(0, change.start))
            // We need the new text to update state
            // The editor already has it, but we need to track it for state consistency
            // We'll get the full text from the editor when needed
        }
        
        // Update the state with new content
        // We need to get the full text from the editor
        val fullText = editorInstance?.getFullText() ?: oldText
        val newTextFieldValue = TextFieldValue(
            text = fullText,
            selection = TextRange(
                start = currentContent.selection.start,
                end = currentContent.selection.end
            )
        )
        
        // Push operation to undo stack
        if (!change.isFullReplacement) {
            val deletedText = oldText.substring(change.start, change.start + change.removedLength)
            val addedText = fullText.substring(change.start, change.start + change.addedLength)
            
            if (change.removedLength > 0 && change.addedLength == 0) {
                // Deletion
                undoStack.addLast(EditOperation.Delete(change.start, change.removedLength, deletedText))
                redoStack.clear()
            } else if (change.removedLength == 0 && change.addedLength > 0) {
                // Insertion
                undoStack.addLast(EditOperation.Insert(change.start, addedText))
                redoStack.clear()
            } else if (change.removedLength > 0 && change.addedLength > 0) {
                // Replacement
                undoStack.addLast(EditOperation.Replace(change.start, change.start + change.removedLength, addedText, deletedText))
                redoStack.clear()
            }
        }
        
        // Trim undo stack
        while (undoStack.size > MAX_HISTORY) {
            undoStack.removeFirst()
        }
        
        _uiState.value = _uiState.value.copy(
            content = newTextFieldValue,
            isDirty = true
        )
        
        // Update search if query is active
        if (_searchQuery.value.isNotBlank()) {
            updateSearchResults(_searchQuery.value)
        }
        
        scheduleDebouncedAutoSave()
    }

    /**
     * Called by EditorScreen when selection changes.
     */
    fun onSelectionChange(start: Int, end: Int) {
        val current = _uiState.value.content
        if (current.selection.start != start || current.selection.end != end) {
            _uiState.value = _uiState.value.copy(
                content = current.copy(selection = TextRange(start, end))
            )
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

    /**
     * Set the editor instance for direct manipulation.
     * Called from EditorScreen's AndroidView update.
     */
    fun setEditorInstance(editor: HighlightingEditText) {
        editorInstance = editor
    }

    fun openExternalDocument(
        uri: android.net.Uri,
        contentResolver: ContentResolver,
        openContext: ExternalDocumentOpenContext? = null,
        candidates: List<IncomingDocumentUri> = listOf(
            IncomingDocumentUri(uri, openContext?.source ?: com.clipnest.IncomingUriSource.FILE_PICKER)
        )
    ) {
        val previousState = _uiState.value
        editorLoaded = true
        autoSaveJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            val failedCandidates = mutableListOf<ExternalDocumentReadFailure>()
            val result = runCatching {
                val candidateUris = candidates.ifEmpty {
                    listOf(IncomingDocumentUri(uri, openContext?.source ?: com.clipnest.IncomingUriSource.FILE_PICKER))
                }
                val loaded = candidateUris.mapNotNull { candidate ->
                    runCatching {
                        candidate to readExternalDocument(candidate.uri, contentResolver)
                    }.onFailure { error ->
                        failedCandidates += ExternalDocumentReadFailure(candidate, error)
                    }.getOrNull()
                }
                if (loaded.isEmpty()) {
                    throw IllegalStateException(
                        "Unable to read any incoming document URI",
                        failedCandidates.firstOrNull()?.error
                    )
                }
                val merged = if (loaded.size > 1) {
                    mergeExternalDocuments(loaded, contentResolver)
                } else {
                    loaded.first().second
                }
                val name = queryDisplayName(loaded.first().first.uri, contentResolver)
                Triple(loaded, name, merged)
            }
            withContext(Dispatchers.Main.immediate) {
                result.onSuccess { (loadedDocuments, name, text) ->
                    val candidate = loadedDocuments.first().first
                    val value = TextFieldValue(text = text, selection = TextRange(text.length))
                    undoStack.clear()
                    redoStack.clear()
                    resetSearchState()
                    val isMergedDocument = candidates.size > 1
                    val firstCandidate = loadedDocuments.firstOrNull()?.first ?: candidate
                    _uiState.value = _uiState.value.copy(
                        content = value.copy(text = text, selection = TextRange(text.length)),
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
                    _openWithDiagnostic.value = null
                    if (failedCandidates.isNotEmpty()) {
                        emitToast(
                            com.clipnest.R.string.opened_files_with_failures,
                            loadedDocuments.size,
                            candidates.size
                        )
                    }
                    // Load into editor
                    editorInstance?.setFullText(text, 0, text.length)
                    viewModelScope.launch(Dispatchers.IO) {
                        runCatching { writeDocumentSnapshot(previousState, contentResolver) }
                            .onFailure { error ->
                                Log.e(TAG, "Could not snapshot previous document before opening: $uri", error)
                            }
                    }
                }.onFailure { error ->
                    val rootCause = generateSequence(error) { it.cause }.last()
                    Log.e(
                        TAG,
                        "Could not open external document: uri=$uri, " +
                            "error=${error::class.java.simpleName}, " +
                            "root=${rootCause::class.java.simpleName}: ${rootCause.message}",
                        error
                    )
                    emitToast(com.clipnest.R.string.could_not_open_file)
                    _openWithDiagnostic.value = if (failedCandidates.isEmpty()) {
                        buildOpenWithDiagnostic(uri, openContext, error)
                    } else {
                        buildOpenWithDiagnostic(failedCandidates, openContext)
                    }
                }
            }
        }
    }

    fun dismissOpenWithDiagnostic() {
        _openWithDiagnostic.value = null
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
                        externalDocumentSaveAsOnly = false,
                        externalDocumentFileCount = 0,
                        showSaveNewFileDialog = false,
                        lastSavedTimestamp = System.currentTimeMillis()
                    )
                    editorInstance?.setFullText(text, 0, text.length)
                    onComplete()
                }.onFailure {
                    emitToast(com.clipnest.R.string.could_not_open_file)
                }
            }
        }
    }

    private fun mergeExternalDocuments(
        loaded: List<Pair<IncomingDocumentUri, String>>,
        contentResolver: ContentResolver
    ): String {
        val blocks = loaded.mapIndexed { index, (candidate, content) ->
            val queriedName = queryDisplayName(candidate.uri, contentResolver)
            val openFileLabel = appContext.withAppLanguage(settings.value.language)
                .getString(com.clipnest.R.string.open_file)
            val displayName = queriedName.takeUnless { it == openFileLabel }
                ?: appContext.withAppLanguage(settings.value.language).getString(
                    com.clipnest.R.string.external_file_number,
                    index + 1
                )
            ExternalDocumentBlock(
                displayName = displayName,
                content = content
            )
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

        fun attempt(method: String, reader: () -> ByteArray?): ByteArray? {
            return try {
                reader()
            } catch (error: Exception) {
                lastFailure = error
                Log.w(TAG, "Open With file URI read failed: method=$method, uri=$uri", error)
                null
            }
        }

        val bytes = attempt("ContentResolver.openInputStream") {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } ?: attempt("java.io.FileInputStream") {
            val path = uri.path?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("File URI has no path")
            File(path).inputStream().use { it.readBytes() }
        }

        return bytes ?: throw IllegalStateException(
            "Unable to read file URI",
            lastFailure
        )
    }

    private fun readContentUri(uri: Uri, contentResolver: ContentResolver): ByteArray {
        var lastFailure: Throwable? = null

        fun attempt(method: String, reader: () -> ByteArray?): ByteArray? {
            return try {
                reader()
            } catch (error: Exception) {
                lastFailure = error
                Log.w(TAG, "Open With read failed: method=$method, uri=$uri", error)
                null
            }
        }

        val bytes = attempt("openInputStream") {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } ?: attempt("openFileDescriptor") {
            contentResolver.openFileDescriptor(uri, "r")?.let { descriptor ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
            }
        } ?: attempt("openAssetFileDescriptor") {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                descriptor.createInputStream().use { it.readBytes() }
            }
        } ?: listOf("*/*", "text/markdown", "text/plain", "text/*").firstNotNullOfOrNull { mimeType ->
            attempt("openTypedAssetFileDescriptor($mimeType)") {
                contentResolver.openTypedAssetFileDescriptor(uri, mimeType, null)?.use { descriptor ->
                    descriptor.createInputStream().use { it.readBytes() }
                }
            }
        }

        return bytes ?: throw IllegalStateException(
            "Unable to read content URI: $uri",
            lastFailure
        )
    }

    private fun decodeUtf8(bytes: ByteArray): String {
        val offset = if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) 3 else 0
        return bytes.copyOfRange(offset, bytes.size).toString(Charsets.UTF_8)
    }

    private fun writeExternalDocument(uri: Uri, contentResolver: ContentResolver, text: String) {
        when (uri.scheme?.lowercase()) {
            ContentResolver.SCHEME_FILE -> {
                val path = uri.path?.takeIf { it.isNotBlank() }
                    ?: error("File URI has no path")
                File(path).outputStream().use { output ->
                    output.write(text.toByteArray(Charsets.UTF_8))
                    output.flush()
                }
            }
            else -> {
                val output = runCatching { contentResolver.openOutputStream(uri, "wt") }.getOrNull()
                    ?: runCatching { contentResolver.openOutputStream(uri) }.getOrNull()
                    ?: error("Unable to save file")
                output.use {
                    it.write(text.toByteArray(Charsets.UTF_8))
                    it.flush()
                }
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

    private fun internalDocumentName(): String =
        appContext.withAppLanguage(settings.value.language).getString(com.clipnest.R.string.editor)

    private fun writeDocumentSnapshot(state: EditorUiState, contentResolver: ContentResolver) {
        if (state.externalDocumentSaveAsOnly) return
        val uri = state.externalDocumentUri?.let(android.net.Uri::parse)
        if (uri == null) {
            if (state.isDirty) fileManager.writeEditor(state.content.text)
            return
        }
        if (!state.isDirty) return
        writeExternalDocument(uri, contentResolver, state.content.text)
    }

    private fun resetSearchState() {
        _isSearchOpen.value = false
        _searchQuery.value = ""
        searchMatchStarts = emptyList()
        _searchMatchCount.value = 0
        _activeSearchMatch.value = 0
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
            emitToast(com.clipnest.R.string.no_matches)
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
        // Also update editor selection
        editorInstance?.setEditorSelectionIfNeeded(start, start + queryLength)
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
        
        val selection = _uiState.value.content.selection
        val start = selection.min
        val end = selection.max
        
        editor.applyDirectEdit(
            operation = { editable ->
                editable.replace(start, end, clip)
            },
            affectedStart = start,
            affectedEnd = start + clip.length
        )
        
        // Update selection after paste
        val newPosition = start + clip.length
        _uiState.value = _uiState.value.copy(
            content = _uiState.value.content.copy(
                selection = TextRange(newPosition)
            )
        )
        editor.setEditorSelectionIfNeeded(newPosition, newPosition)
        
        emitToast(com.clipnest.R.string.pasted)
    }

    fun copySelectedText(context: Context) {
        val current = _uiState.value.content
        if (current.selection.collapsed) {
            emitToast(com.clipnest.R.string.select_text_to_copy)
            return
        }
        val selected = current.text.substring(current.selection.min, current.selection.max)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Editor selection", selected))
        emitToast(com.clipnest.R.string.copied)
    }

    fun moveCursorLeft() {
        val current = _uiState.value.content
        val position = if (current.selection.collapsed) {
            max(0, current.selection.start - 1)
        } else {
            current.selection.min
        }
        _uiState.value = _uiState.value.copy(content = current.copy(selection = TextRange(position)))
        editorInstance?.setEditorSelectionIfNeeded(position, position)
    }

    fun moveCursorRight() {
        val current = _uiState.value.content
        val position = if (current.selection.collapsed) {
            min(current.text.length, current.selection.end + 1)
        } else {
            current.selection.max
        }
        _uiState.value = _uiState.value.copy(content = current.copy(selection = TextRange(position)))
        editorInstance?.setEditorSelectionIfNeeded(position, position)
    }

    fun selectAll() {
        val text = _uiState.value.content.text
        _uiState.value = _uiState.value.copy(content = _uiState.value.content.copy(selection = TextRange(0, text.length)))
        editorInstance?.setEditorSelectionIfNeeded(0, text.length)
    }

    fun toggleMarkdownTools() {
        _uiState.value = _uiState.value.copy(isMarkdownToolsExpanded = !_uiState.value.isMarkdownToolsExpanded)
    }

    fun toggleMarkdownPreview() {
        val currentState = _uiState.value
        val openingPreview = !currentState.showMarkdownPreview
        _uiState.value = currentState.copy(
            showMarkdownPreview = openingPreview,
            previewSplitFraction = if (openingPreview && currentState.previewSplitFraction <= 0f) {
                DEFAULT_PREVIEW_FRACTION
            } else {
                currentState.previewSplitFraction
            }
        )
    }

    fun setPreviewSplitFraction(fraction: Float) {
        _uiState.value = _uiState.value.copy(
            previewSplitFraction = fraction.coerceIn(MIN_PREVIEW_FRACTION, MAX_PREVIEW_FRACTION)
        )
    }

    fun insertMarkdownHeading(level: Int) {
        val editor = editorInstance ?: return
        val selection = _uiState.value.content.selection
        val start = selection.min
        val end = selection.max
        
        // Find line start and end
        val text = _uiState.value.content.text
        val lineStart = text.lastIndexOf('\n', start.coerceAtLeast(0) - 1).let { if (it < 0) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', end).let { if (it < 0) text.length else it }
        
        val prefix = "#".repeat(level) + " "
        
        // Delete existing heading prefix if any
        val lineText = text.substring(lineStart, lineEnd)
        val existingPrefix = lineText.takeWhile { it == '#' }.takeIf { it.isNotEmpty() }
        val prefixLength = existingPrefix?.length ?: 0
        val hasSpaceAfter = prefixLength > 0 && lineText.getOrNull(prefixLength) == ' '
        val cleanStart = if (hasSpaceAfter) lineStart + prefixLength + 1 else if (prefixLength > 0) lineStart + prefixLength else lineStart
        
        editor.applyDirectEdit(
            operation = { editable ->
                editable.replace(cleanStart, lineEnd, prefix + lineText.substring(prefixLength))
            },
            affectedStart = cleanStart,
            affectedEnd = cleanStart + prefix.length + (lineEnd - cleanStart)
        )
        
        // Update selection
        val newPosition = cleanStart + prefix.length
        _uiState.value = _uiState.value.copy(
            content = _uiState.value.content.copy(
                selection = TextRange(newPosition)
            )
        )
        editor.setEditorSelectionIfNeeded(newPosition, newPosition)
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
        val editor = editorInstance ?: return
        val selection = _uiState.value.content.selection
        val start = selection.min
        val end = selection.max
        
        if (start == end) {
            // Insert empty code block
            val codeBlock = "\n```\n\n```\n"
            editor.applyDirectEdit(
                operation = { editable ->
                    editable.replace(start, end, codeBlock)
                },
                affectedStart = start,
                affectedEnd = start + codeBlock.length
            )
            val newPosition = start + 4 // Position inside the code block
            _uiState.value = _uiState.value.copy(
                content = _uiState.value.content.copy(
                    selection = TextRange(newPosition)
                )
            )
            editor.setEditorSelectionIfNeeded(newPosition, newPosition)
        } else {
            // Wrap selected text in code block
            val selected = _uiState.value.content.text.substring(start, end)
            val codeBlock = "```\n$selected\n```"
            editor.applyDirectEdit(
                operation = { editable ->
                    editable.replace(start, end, codeBlock)
                },
                affectedStart = start,
                affectedEnd = start + codeBlock.length
            )
            val newPosition = start + 4 + selected.length
            _uiState.value = _uiState.value.copy(
                content = _uiState.value.content.copy(
                    selection = TextRange(start + 4, newPosition)
                )
            )
            editor.setEditorSelectionIfNeeded(start + 4, newPosition)
        }
    }

    fun insertMarkdownBullets() {
        applyLinePrefix("- ")
    }

    fun insertMarkdownNumbers() {
        applyLinePrefix("1. ")
    }

    fun insertMarkdownHorizontalRule() {
        val editor = editorInstance ?: return
        val selection = _uiState.value.content.selection
        val position = selection.max
        val text = _uiState.value.content.text
        
        val before = if (position > 0 && text[position - 1] != '\n') "\n" else ""
        val after = if (position < text.length && text[position] != '\n') "\n" else ""
        val replacement = "${before}---${after}"
        
        editor.applyDirectEdit(
            operation = { editable ->
                editable.replace(position, position, replacement)
            },
            affectedStart = position,
            affectedEnd = position + replacement.length
        )
        
        val newPosition = position + replacement.length
        _uiState.value = _uiState.value.copy(
            content = _uiState.value.content.copy(
                selection = TextRange(newPosition)
            )
        )
        editor.setEditorSelectionIfNeeded(newPosition, newPosition)
    }

    private fun applyInlineDelimiter(delimiter: String) {
        val editor = editorInstance ?: return
        val selection = _uiState.value.content.selection
        val start = selection.min
        val end = selection.max
        
        if (start == end) {
            // Insert empty delimiters
            editor.applyDirectEdit(
                operation = { editable ->
                    editable.replace(start, end, delimiter + delimiter)
                },
                affectedStart = start,
                affectedEnd = start + delimiter.length * 2
            )
            val newPosition = start + delimiter.length
            _uiState.value = _uiState.value.copy(
                content = _uiState.value.content.copy(
                    selection = TextRange(newPosition)
                )
            )
            editor.setEditorSelectionIfNeeded(newPosition, newPosition)
        } else {
            // Wrap selected text
            val selected = _uiState.value.content.text.substring(start, end)
            val replacement = delimiter + selected + delimiter
            editor.applyDirectEdit(
                operation = { editable ->
                    editable.replace(start, end, replacement)
                },
                affectedStart = start,
                affectedEnd = start + replacement.length
            )
            val newStart = start + delimiter.length
            val newEnd = start + delimiter.length + selected.length
            _uiState.value = _uiState.value.copy(
                content = _uiState.value.content.copy(
                    selection = TextRange(newStart, newEnd)
                )
            )
            editor.setEditorSelectionIfNeeded(newStart, newEnd)
        }
    }

    private fun applyLinePrefix(prefix: String) {
        val editor = editorInstance ?: return
        val selection = _uiState.value.content.selection
        val text = _uiState.value.content.text
        val start = selection.min
        val end = selection.max
        
        // Find line boundaries
        val lineStart = text.lastIndexOf('\n', start.coerceAtLeast(0) - 1).let { if (it < 0) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', end).let { if (it < 0) text.length else it }
        
        val lineText = text.substring(lineStart, lineEnd)
        val lines = if (lineText.isEmpty()) listOf("") else lineText.split('\n')
        val transformed = lines.joinToString("\n") { line -> prefix + line }
        
        editor.applyDirectEdit(
            operation = { editable ->
                editable.replace(lineStart, lineEnd, transformed)
            },
            affectedStart = lineStart,
            affectedEnd = lineStart + transformed.length
        )
        
        val newSelectionStart = start + prefix.length
        val newSelectionEnd = if (selection.collapsed) newSelectionStart else end + (transformed.length - lineText.length)
        _uiState.value = _uiState.value.copy(
            content = _uiState.value.content.copy(
                selection = TextRange(newSelectionStart, newSelectionEnd)
            )
        )
        editor.setEditorSelectionIfNeeded(newSelectionStart, newSelectionEnd)
    }

    fun deleteSelectedText() {
        val editor = editorInstance ?: run {
            emitToast(com.clipnest.R.string.editor_not_ready)
            return
        }
        val selection = _uiState.value.content.selection
        if (selection.collapsed) {
            emitToast(com.clipnest.R.string.select_text_to_delete)
            return
        }
        
        val start = selection.min
        val end = selection.max
        val deletedText = _uiState.value.content.text.substring(start, end)
        
        editor.applyDirectEdit(
            operation = { editable ->
                editable.delete(start, end)
            },
            affectedStart = start,
            affectedEnd = start
        )
        
        // Push to undo stack
        undoStack.addLast(EditOperation.Delete(start, end - start, deletedText))
        redoStack.clear()
        while (undoStack.size > MAX_HISTORY) {
            undoStack.removeFirst()
        }
        
        _uiState.value = _uiState.value.copy(
            content = _uiState.value.content.copy(
                selection = TextRange(start)
            )
        )
        editor.setEditorSelectionIfNeeded(start, start)
        
        scheduleDebouncedAutoSave()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val operation = undoStack.removeLast()
        val editor = editorInstance ?: return
        
        applyingHistory = true
        try {
            when (operation) {
                is EditOperation.Insert -> {
                    // Delete the inserted text
                    val text = _uiState.value.content.text
                    val end = operation.position + operation.text.length
                    editor.applyDirectEdit(
                        operation = { editable ->
                            editable.delete(operation.position, end)
                        },
                        affectedStart = operation.position,
                        affectedEnd = operation.position
                    )
                    redoStack.addLast(EditOperation.Delete(operation.position, operation.text.length, operation.text))
                    _uiState.value = _uiState.value.copy(
                        content = _uiState.value.content.copy(
                            selection = TextRange(operation.position)
                        )
                    )
                    editor.setEditorSelectionIfNeeded(operation.position, operation.position)
                }
                is EditOperation.Delete -> {
                    // Re-insert the deleted text
                    editor.applyDirectEdit(
                        operation = { editable ->
                            editable.replace(operation.position, operation.position, operation.deletedText)
                        },
                        affectedStart = operation.position,
                        affectedEnd = operation.position + operation.deletedText.length
                    )
                    redoStack.addLast(EditOperation.Insert(operation.position, operation.deletedText))
                    _uiState.value = _uiState.value.copy(
                        content = _uiState.value.content.copy(
                            selection = TextRange(operation.position + operation.deletedText.length)
                        )
                    )
                    editor.setEditorSelectionIfNeeded(operation.position + operation.deletedText.length, operation.position + operation.deletedText.length)
                }
                is EditOperation.Replace -> {
                    // Revert to old text
                    editor.applyDirectEdit(
                        operation = { editable ->
                            editable.replace(operation.start, operation.start + operation.newText.length, operation.oldText)
                        },
                        affectedStart = operation.start,
                        affectedEnd = operation.start + operation.oldText.length
                    )
                    redoStack.addLast(EditOperation.Replace(operation.start, operation.start + operation.oldText.length, operation.newText, operation.oldText))
                    _uiState.value = _uiState.value.copy(
                        content = _uiState.value.content.copy(
                            selection = TextRange(operation.start + operation.oldText.length)
                        )
                    )
                    editor.setEditorSelectionIfNeeded(operation.start + operation.oldText.length, operation.start + operation.oldText.length)
                }
            }
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
                    // Re-insert the text
                    editor.applyDirectEdit(
                        operation = { editable ->
                            editable.replace(operation.position, operation.position, operation.text)
                        },
                        affectedStart = operation.position,
                        affectedEnd = operation.position + operation.text.length
                    )
                    undoStack.addLast(EditOperation.Insert(operation.position, operation.text))
                    _uiState.value = _uiState.value.copy(
                        content = _uiState.value.content.copy(
                            selection = TextRange(operation.position + operation.text.length)
                        )
                    )
                    editor.setEditorSelectionIfNeeded(operation.position + operation.text.length, operation.position + operation.text.length)
                }
                is EditOperation.Delete -> {
                    // Delete again
                    val end = operation.position + operation.length
                    editor.applyDirectEdit(
                        operation = { editable ->
                            editable.delete(operation.position, end)
                        },
                        affectedStart = operation.position,
                        affectedEnd = operation.position
                    )
                    undoStack.addLast(EditOperation.Delete(operation.position, operation.length, operation.deletedText))
                    _uiState.value = _uiState.value.copy(
                        content = _uiState.value.content.copy(
                            selection = TextRange(operation.position)
                        )
                    )
                    editor.setEditorSelectionIfNeeded(operation.position, operation.position)
                }
                is EditOperation.Replace -> {
                    // Apply the new text again
                    editor.applyDirectEdit(
                        operation = { editable ->
                            editable.replace(operation.start, operation.start + operation.oldText.length, operation.newText)
                        },
                        affectedStart = operation.start,
                        affectedEnd = operation.start + operation.newText.length
                    )
                    undoStack.addLast(EditOperation.Replace(operation.start, operation.start + operation.newText.length, operation.newText, operation.oldText))
                    _uiState.value = _uiState.value.copy(
                        content = _uiState.value.content.copy(
                            selection = TextRange(operation.start + operation.newText.length)
                        )
                    )
                    editor.setEditorSelectionIfNeeded(operation.start + operation.newText.length, operation.start + operation.newText.length)
                }
            }
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
        val state = _uiState.value
        val externalUri = state.externalDocumentUri
        if (state.externalDocumentSaveAsOnly || externalUri == null) {
            _uiState.value = _uiState.value.copy(showSaveNewFileDialog = true)
        } else {
            saveExternalDocument(android.net.Uri.parse(externalUri), contentResolver)
        }
    }

    private fun saveExternalDocument(uri: android.net.Uri, contentResolver: ContentResolver) {
        val contentSnapshot = _uiState.value.content.text
        viewModelScope.launch(Dispatchers.IO) {
            val saveResult = runCatching {
                writeExternalDocument(uri, contentResolver, contentSnapshot)
            }
            val saved = saveResult.isSuccess
            withContext(Dispatchers.Main.immediate) {
                if (saved) {
                    if (_uiState.value.content.text == contentSnapshot) {
                        _uiState.value = _uiState.value.copy(
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
                    emitToast(com.clipnest.R.string.could_not_save_file)
                } else {
                    _uiState.value = _uiState.value.copy(
                        showSaveNewFileDialog = false,
                        isDirty = false,
                        lastSavedTimestamp = System.currentTimeMillis()
                    )
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