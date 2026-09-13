package com.clipnest.ui.editor

import android.content.ClipboardManager
import android.content.Intent
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import com.clipnest.ExternalDocumentOpenContext
import com.clipnest.ExternalDocumentReadFailure
import com.clipnest.IncomingDocumentUri
import com.clipnest.buildOpenWithDiagnostic
import com.clipnest.data.local.ExportFormat
import com.clipnest.data.repository.EncryptedDocumentCodec
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

data class PendingEncryptedOpen(
    val uri: Uri,
    val encryptedJson: String,
    val displayName: String,
    val error: Boolean = false
)

data class EditorUiState(
    val content: EditorContent = EditorContent(),
    val documentRevision: Long = 0L,
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
    val externalDocumentFileCount: Int = 0,
    val externalDocumentEncrypted: Boolean = false,
    val mode: EditorMode = EditorMode.PLAIN,
    val title: String = "",
    val noteOrigin: EditorNoteOrigin? = null
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
    private val _pendingEncryptedOpen = MutableStateFlow<PendingEncryptedOpen?>(null)
    val pendingEncryptedOpen: StateFlow<PendingEncryptedOpen?> = _pendingEncryptedOpen.asStateFlow()
    // Held only in memory for the lifetime of the currently open encrypted document,
    // so autosave/save can re-encrypt without prompting again. Cleared whenever the
    // document is closed, replaced, or returned to the internal editor.
    private var currentDocumentPassword: String? = null
    private val _isSearchOpen = MutableStateFlow(false)
    val isSearchOpen: StateFlow<Boolean> = _isSearchOpen.asStateFlow()
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    private val _replaceQuery = MutableStateFlow("")
    val replaceQuery: StateFlow<String> = _replaceQuery.asStateFlow()
    private val _searchMatchCount = MutableStateFlow(0)
    val searchMatchCount: StateFlow<Int> = _searchMatchCount.asStateFlow()
    private val _activeSearchMatch = MutableStateFlow(0)
    val activeSearchMatch: StateFlow<Int> = _activeSearchMatch.asStateFlow()
    private var searchMatchStarts: List<Int> = emptyList()
    private var editorLoaded = false
    private var pendingSave: PendingSave? = null
    private var autoSaveJob: Job? = null
    private var searchJob: Job? = null
    private var nativeEditor: NativeEditorView? = null
    private var editorDocumentGeneration = 0L

    internal val editorDocumentResetKey: Long get() = editorDocumentGeneration
    val settings: StateFlow<com.clipnest.data.local.UserSettings> = settingsDataStore.userSettingsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.clipnest.data.local.UserSettings())

    fun bindNativeEditor(editor: NativeEditorView) {
        if (nativeEditor === editor) return
        nativeEditor?.setTextChangeListener(null)
        nativeEditor = editor
        editor.setTextChangeListener { onDocumentTextChanged() }
        editor.setEditorTextSize(settings.value.editorTextSize)
        if (!editorLoaded || editor.text?.isEmpty() == true) {
            val content = _uiState.value.content
            editor.setEditorText(content.text, content.selection.start, content.selection.end)
        }
    }

    fun currentDocumentText(): String = nativeEditor?.text?.toString() ?: _uiState.value.content.text
    fun currentDocumentSnapshot(): EditorSnapshot = EditorSnapshot(_uiState.value.documentRevision, currentDocumentText())

    init {
        loadEditor()
        viewModelScope.launch { settings.collect { _uiState.value = _uiState.value.copy(defaultSaveFolderUri = it.defaultSaveFolderUri); nativeEditor?.setEditorTextSize(it.editorTextSize) } }
    }

    private fun loadEditor() {
        viewModelScope.launch(Dispatchers.IO) {
            val text = fileManager.readEditor()
            val title = fileManager.readEditorTitle()
            withContext(Dispatchers.Main.immediate) {
                if (!editorLoaded && !_uiState.value.isDirty) {
                    _uiState.value.content.setFallback(text, TextRange(text.length))
                    nativeEditor?.setEditorText(text, text.length)
                    editorDocumentGeneration++
                    _uiState.value = _uiState.value.copy(documentRevision = _uiState.value.documentRevision + 1, isDirty = false, title = title, lastSavedTimestamp = System.currentTimeMillis())
                }
                editorLoaded = true
            }
        }
    }

    fun openExternalDocument(uri: Uri, contentResolver: ContentResolver, openContext: ExternalDocumentOpenContext? = null, candidates: List<IncomingDocumentUri> = listOf(IncomingDocumentUri(uri, openContext?.source ?: com.clipnest.IncomingUriSource.FILE_PICKER))) {
        val previousState = _uiState.value
        val previousText = currentDocumentText()
        val previousPassword = currentDocumentPassword
        editorLoaded = true
        autoSaveJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            val failedCandidates = mutableListOf<ExternalDocumentReadFailure>()
            val result = runCatching {
                val candidateUris = candidates.ifEmpty { listOf(IncomingDocumentUri(uri, openContext?.source ?: com.clipnest.IncomingUriSource.FILE_PICKER)) }
                val loaded = candidateUris.mapNotNull { candidate -> runCatching { candidate to readExternalDocument(candidate.uri, contentResolver) }.onFailure { failedCandidates += ExternalDocumentReadFailure(candidate, it) }.getOrNull() }
                if (loaded.isEmpty()) throw IllegalStateException("Unable to read any incoming document URI", failedCandidates.firstOrNull()?.error)
                val merged = if (loaded.size > 1) mergeExternalDocuments(loaded, contentResolver) else loaded.first().second
                Triple(loaded, queryDisplayName(loaded.first().first.uri, contentResolver), merged)
            }
            withContext(Dispatchers.Main.immediate) {
                result.onSuccess { (loadedDocuments, name, text) ->
                    if (loadedDocuments.size == 1 && EncryptedDocumentCodec.isEncryptedDocument(text)) {
                        // A single ClipNest-encrypted document: hold the ciphertext and ask for
                        // the password instead of loading raw JSON into the editor. Multi-file
                        // merges are not supported for encrypted documents since merging
                        // ciphertext blocks would be meaningless.
                        _pendingEncryptedOpen.value = PendingEncryptedOpen(uri = loadedDocuments.first().first.uri, encryptedJson = text, displayName = name)
                        _openWithDiagnostic.value = null
                        return@onSuccess
                    }
                    resetSearchState()
                    val first = loadedDocuments.first().first
                    nativeEditor?.setEditorText(text, text.length)
                    _uiState.value.content.setFallback(text, TextRange(text.length))
                    editorDocumentGeneration++
                    currentDocumentPassword = null
                    _uiState.value = _uiState.value.copy(documentRevision = _uiState.value.documentRevision + 1, isDirty = false, documentName = if (candidates.size > 1) appContext.withAppLanguage(settings.value.language).getString(com.clipnest.R.string.merged_document_name, loadedDocuments.size) else name, externalDocumentUri = first.uri.toString(), externalDocumentSaveAsOnly = candidates.size > 1, externalDocumentFileCount = loadedDocuments.size, externalDocumentEncrypted = false, showSaveNewFileDialog = false, lastSavedTimestamp = System.currentTimeMillis())
                    _openWithDiagnostic.value = null
                    if (failedCandidates.isNotEmpty()) emitToast(com.clipnest.R.string.opened_files_with_failures, loadedDocuments.size, candidates.size)
                    viewModelScope.launch(Dispatchers.IO) { runCatching { writeDocumentSnapshot(previousState, contentResolver, previousText, previousPassword) } }
                }.onFailure { error ->
                    emitToast(com.clipnest.R.string.could_not_open_file)
                    _openWithDiagnostic.value = if (failedCandidates.isEmpty()) buildOpenWithDiagnostic(uri, openContext, error) else buildOpenWithDiagnostic(failedCandidates, openContext)
                }
            }
        }
    }

    fun dismissOpenWithDiagnostic() { _openWithDiagnostic.value = null }

    /** Cancels an encrypted-open prompt without touching the currently open document. */
    fun dismissPendingEncryptedOpen() { _pendingEncryptedOpen.value = null }

    /**
     * Attempts to decrypt the pending encrypted document with [password]. On success,
     * loads the plaintext into the editor and remembers the password in memory so
     * autosave can re-encrypt with it. On failure, keeps the prompt open and marks it
     * with an error so the dialog can show a retry message.
     */
    fun confirmDecryptAndOpen(password: String, contentResolver: ContentResolver) {
        val pending = _pendingEncryptedOpen.value ?: return
        val previousState = _uiState.value
        val previousText = currentDocumentText()
        val previousPassword = currentDocumentPassword
        viewModelScope.launch(Dispatchers.Default) {
            val decoded = runCatching { EncryptedDocumentCodec.decode(pending.encryptedJson, password) }
            withContext(Dispatchers.Main.immediate) {
                decoded.onSuccess { text ->
                    _pendingEncryptedOpen.value = null
                    resetSearchState()
                    nativeEditor?.setEditorText(text, text.length)
                    _uiState.value.content.setFallback(text, TextRange(text.length))
                    editorDocumentGeneration++
                    currentDocumentPassword = password
                    _uiState.value = _uiState.value.copy(documentRevision = _uiState.value.documentRevision + 1, isDirty = false, documentName = pending.displayName, externalDocumentUri = pending.uri.toString(), externalDocumentSaveAsOnly = false, externalDocumentFileCount = 1, externalDocumentEncrypted = true, showSaveNewFileDialog = false, lastSavedTimestamp = System.currentTimeMillis())
                    _openWithDiagnostic.value = null
                    editorLoaded = true
                    viewModelScope.launch(Dispatchers.IO) { runCatching { writeDocumentSnapshot(previousState, contentResolver, previousText, previousPassword) } }
                }.onFailure {
                    _pendingEncryptedOpen.value = pending.copy(error = true)
                }
            }
        }
    }

    private fun mergeExternalDocuments(loaded: List<Pair<IncomingDocumentUri, String>>, resolver: ContentResolver): String = loaded.mapIndexed { index, (candidate, content) ->
        val name = queryDisplayName(candidate.uri, resolver).takeUnless { it == appContext.withAppLanguage(settings.value.language).getString(com.clipnest.R.string.open_file) } ?: appContext.withAppLanguage(settings.value.language).getString(com.clipnest.R.string.external_file_number, index + 1)
        ExternalDocumentBlock(name, content)
    }.let(::mergeExternalDocumentBlocks)

    private fun readExternalDocument(uri: Uri, resolver: ContentResolver): String = decodeUtf8(when (uri.scheme?.lowercase()) {
        ContentResolver.SCHEME_CONTENT -> readContentUri(uri, resolver)
        ContentResolver.SCHEME_FILE -> readFileUri(uri, resolver)
        else -> throw IllegalArgumentException("Unsupported URI scheme for document: $uri")
    })
    private fun readFileUri(uri: Uri, resolver: ContentResolver): ByteArray = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() ?: File(uri.path ?: error("File URI has no path")).readBytes()
    private fun readContentUri(uri: Uri, resolver: ContentResolver): ByteArray {
        var failure: Throwable? = null
        fun attempt(block: () -> ByteArray?): ByteArray? = runCatching(block).onFailure { failure = it }.getOrNull()
        return attempt { resolver.openInputStream(uri)?.use { it.readBytes() } } ?: attempt { resolver.openFileDescriptor(uri, "r")?.let { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { input -> input.readBytes() } } } ?: throw IllegalStateException("Unable to read content URI: $uri", failure)
    }
    private fun decodeUtf8(bytes: ByteArray): String { val offset = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) 3 else 0; return bytes.copyOfRange(offset, bytes.size).toString(Charsets.UTF_8) }
    private fun writeExternalDocument(uri: Uri, resolver: ContentResolver, text: String, encrypted: Boolean, password: String?) {
        val output = if (encrypted) {
            val resolvedPassword = password
                ?: error("Missing in-memory password for encrypted document; cannot save without prompting again")
            EncryptedDocumentCodec.encode(text, resolvedPassword)
        } else {
            text
        }
        val stream = if (uri.scheme == ContentResolver.SCHEME_FILE) File(uri.path ?: error("File URI has no path")).outputStream() else resolver.openOutputStream(uri, "wt") ?: resolver.openOutputStream(uri) ?: error("Unable to save file")
        stream.use { it.write(output.toByteArray(Charsets.UTF_8)); it.flush() }
    }
    private fun queryDisplayName(uri: Uri, resolver: ContentResolver): String = runCatching { resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } }.getOrNull()?.takeIf(String::isNotBlank) ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf(String::isNotBlank) ?: appContext.withAppLanguage(settings.value.language).getString(com.clipnest.R.string.open_file)
    private fun writeDocumentSnapshot(state: EditorUiState, resolver: ContentResolver, text: String = state.content.text, password: String? = currentDocumentPassword) {
        if (state.externalDocumentSaveAsOnly) return
        val uri = state.externalDocumentUri?.let(Uri::parse)
        if (uri == null) {
            if (state.isDirty) { fileManager.writeEditor(text); fileManager.writeEditorTitle(state.title) }
        } else if (state.isDirty) writeExternalDocument(uri, resolver, text, state.externalDocumentEncrypted, password)
    }

    fun returnToInternalEditor(contentResolver: ContentResolver, onComplete: () -> Unit = {}) {
        val state = _uiState.value
        val text = currentDocumentText()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { writeDocumentSnapshot(state, contentResolver, text); fileManager.readEditor() }.onSuccess { internal -> withContext(Dispatchers.Main.immediate) { nativeEditor?.setEditorText(internal, internal.length); _uiState.value.content.setFallback(internal, TextRange(internal.length)); editorDocumentGeneration++; currentDocumentPassword = null; _uiState.value = _uiState.value.copy(documentRevision = _uiState.value.documentRevision + 1, isDirty = false, documentName = internalDocumentName(), externalDocumentUri = null, externalDocumentSaveAsOnly = false, externalDocumentFileCount = 0, externalDocumentEncrypted = false); onComplete() } }
        }
    }
    private fun internalDocumentName() = appContext.withAppLanguage(settings.value.language).getString(com.clipnest.R.string.editor)

    private fun resetSearchState() { _isSearchOpen.value = false; _searchQuery.value = ""; _replaceQuery.value = ""; searchMatchStarts = emptyList(); _searchMatchCount.value = 0; _activeSearchMatch.value = 0 }
    fun onDocumentTextChanged() { editorLoaded = true; _uiState.value = _uiState.value.copy(documentRevision = _uiState.value.documentRevision + 1, isDirty = true); if (_searchQuery.value.isNotBlank()) scheduleSearchResults(_searchQuery.value, true); scheduleDebouncedAutoSave() }

    fun configureNoteMode(origin: EditorNoteOrigin?) { _uiState.value = _uiState.value.copy(mode = EditorMode.NOTE, noteOrigin = origin) }
    fun onTitleChange(newTitle: String) { editorLoaded = true; _uiState.value = _uiState.value.copy(title = newTitle, isDirty = true); scheduleDebouncedAutoSave() }
    fun flushPendingSaveAndExit(contentResolver: ContentResolver = appContext.contentResolver, onComplete: () -> Unit = {}) {
        autoSaveJob?.cancel()
        val state = _uiState.value
        val text = currentDocumentText()
        val revision = state.documentRevision
        _uiState.value.content.setFallback(text, nativeEditor?.let { TextRange(it.selectionStart, it.selectionEnd) } ?: TextRange(text.length))
        viewModelScope.launch(Dispatchers.IO) {
            val saved = runCatching { writeDocumentSnapshot(state, contentResolver, text) }.isSuccess
            withContext(Dispatchers.Main.immediate) { if (saved && _uiState.value.documentRevision == revision) _uiState.value = _uiState.value.copy(isDirty = false, lastSavedTimestamp = System.currentTimeMillis()); onComplete() }
        }
    }
    @Deprecated("Use the native editor directly") fun onContentChange(newValue: TextFieldValue, coalesceUndo: Boolean = false) { setEditorSelection(newValue.selection.min, newValue.selection.max) }
    private fun openSearch() { _isSearchOpen.value = true }
    fun openSearchPublic() = openSearch()
    fun closeSearch() { resetSearchState(); nativeEditor?.let { setEditorSelection(it.selectionEnd) } }
    fun setSearchQuery(query: String) { _searchQuery.value = query; scheduleSearchResults(query, false) }
    fun setReplaceQuery(query: String) { _replaceQuery.value = query }
    private fun scheduleSearchResults(query: String, preserveSelection: Boolean) { searchJob?.cancel(); if (query.isBlank()) { searchMatchStarts = emptyList(); _searchMatchCount.value = 0; _activeSearchMatch.value = 0; return }; searchJob = viewModelScope.launch { delay(400); val text = currentDocumentText(); val revision = _uiState.value.documentRevision; val selection = nativeEditor?.let { TextRange(it.selectionStart, it.selectionEnd) } ?: TextRange.Zero; val matches = withContext(Dispatchers.Default) { EditorSearchEngine.findMatches(text, query) }; if (_searchQuery.value != query || _uiState.value.documentRevision != revision) return@launch; applySearchResults(query, matches, selection, preserveSelection) } }
    private fun applySearchResults(query: String, matches: List<Int>, selection: TextRange, preserveSelection: Boolean) { searchMatchStarts = matches; _searchMatchCount.value = matches.size; if (matches.isEmpty()) { _activeSearchMatch.value = 0; return }; if (preserveSelection) { _activeSearchMatch.value = matches.indexOfFirst { it == selection.min && it + query.length == selection.max }.coerceAtLeast(0); return }; val at = matches.indexOfFirst { it >= selection.start }; selectSearchMatch(if (at >= 0) at else 0) }
    fun nextSearchMatch() { if (searchMatchStarts.isNotEmpty()) { nativeEditor?.hideKeyboardAndClearFocus(); selectSearchMatch((_activeSearchMatch.value + 1) % searchMatchStarts.size) } }
    fun previousSearchMatch() { if (searchMatchStarts.isNotEmpty()) { nativeEditor?.hideKeyboardAndClearFocus(); selectSearchMatch((_activeSearchMatch.value - 1 + searchMatchStarts.size) % searchMatchStarts.size) } }
    private fun selectSearchMatch(index: Int) { val start = searchMatchStarts.getOrNull(index) ?: return; _activeSearchMatch.value = index; setEditorSelection(start, start + _searchQuery.value.length); nativeEditor?.scrollSelectionIntoView(start, start + _searchQuery.value.length) }

    fun replaceCurrentMatch() {
        val editor = nativeEditor ?: return
        val query = _searchQuery.value
        val replacement = _replaceQuery.value
        val start = searchMatchStarts.getOrNull(_activeSearchMatch.value) ?: return
        val end = start + query.length
        if (query.isBlank() || end > editor.length()) return
        val current = editor.text?.subSequence(start, end)?.toString() ?: return
        if (current != query) { scheduleSearchResults(query, false); return }
        editor.replaceText(start, end, replacement, start + replacement.length, start + replacement.length)
    }

    fun replaceAllMatches() {
        val editor = nativeEditor ?: return
        val query = _searchQuery.value
        val replacement = _replaceQuery.value
        val matches = searchMatchStarts
        if (query.isBlank() || matches.isEmpty()) return
        val active = _activeSearchMatch.value.coerceIn(0, matches.lastIndex)
        val activeStart = matches[active]
        val delta = replacement.length - query.length
        val activeMatchesBefore = matches.count { it < activeStart }
        val finalStart = (activeStart + activeMatchesBefore * delta).coerceIn(0, editor.length())
        val finalEnd = (finalStart + replacement.length).coerceIn(finalStart, editor.length())
        editor.transaction {
            for (start in matches.asReversed()) {
                replaceText(start, start + query.length, replacement)
            }
            setSelection(finalStart, finalEnd)
        }
    }

    fun pasteFromClipboard(context: Context) { val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; val clip = runCatching { clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() }.getOrNull(); if (clip.isNullOrEmpty()) { emitToast(com.clipnest.R.string.clipboard_empty); return }; replaceSelection(clip.replace("\r\n", "\n").replace('\r', '\n')); emitToast(com.clipnest.R.string.pasted) }
    fun copySelectedText(context: Context) { val editor = nativeEditor ?: return; if (editor.selectionStart == editor.selectionEnd) { emitToast(com.clipnest.R.string.select_text_to_copy); return }; val selected = editor.text?.subSequence(editor.selectionStart, editor.selectionEnd).toString(); (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("Editor selection", selected)); emitToast(com.clipnest.R.string.copied) }
    fun cutSelectedText(context: Context) { val editor = nativeEditor ?: return; if (editor.selectionStart == editor.selectionEnd) { emitToast(com.clipnest.R.string.select_text_to_delete); return }; val selected = editor.text?.subSequence(editor.selectionStart, editor.selectionEnd).toString(); (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("Editor selection", selected)); replaceSelection(""); emitToast(com.clipnest.R.string.copied) }
    fun moveCursorLeft() { nativeEditor?.let { setEditorSelection(if (it.selectionStart == it.selectionEnd) max(0, it.selectionStart - 1) else min(it.selectionStart, it.selectionEnd)) } }
    fun moveCursorRight() { nativeEditor?.let { setEditorSelection(if (it.selectionStart == it.selectionEnd) min(it.length(), it.selectionEnd + 1) else max(it.selectionStart, it.selectionEnd)) } }
    fun selectAll() { nativeEditor?.let { it.requestFocus(); it.selectAll() } }
    fun toggleMarkdownTools() { _uiState.value = _uiState.value.copy(isMarkdownToolsExpanded = !_uiState.value.isMarkdownToolsExpanded) }
    fun toggleMarkdownPreview() { val s = _uiState.value; _uiState.value = s.copy(showMarkdownPreview = !s.showMarkdownPreview) }
    fun setPreviewSplitFraction(fraction: Float) { _uiState.value = _uiState.value.copy(previewSplitFraction = fraction.coerceIn(0f, 1f)) }
    fun insertMarkdownHeading(level: Int) { applyLinePrefix("${"#".repeat(level)} ") }
    fun toggleMarkdownStrong() { applyInlineDelimiter("**") }
    fun toggleMarkdownEmphasis() { applyInlineDelimiter("*") }
    fun insertMarkdownQuote() { applyLinePrefix("> ") }
    fun insertMarkdownCodeBlock() { val editor = nativeEditor ?: return; val selection = TextRange(editor.selectionStart, editor.selectionEnd); if (selection.collapsed) replaceRange(selection.start, selection.end, "```\n\n```", selection.start + 4, selection.start + 4) else { val selected = editor.text?.subSequence(selection.min, selection.max).toString(); replaceRange(selection.min, selection.max, "```\n$selected\n```", selection.min + 4, selection.min + 4 + selected.length) } }
    fun insertMarkdownBullets() { applyLinePrefix("- ") }
    fun insertMarkdownNumbers() { applyLinePrefix("1. ") }
    fun insertMarkdownHorizontalRule() { val editor = nativeEditor ?: return; val caret = editor.selectionEnd; val before = if (caret > 0 && editor.text?.get(caret - 1) != '\n') "\n" else ""; val after = if (caret < editor.length() && editor.text?.get(caret) != '\n') "\n" else ""; replaceRange(caret, caret, "$before---$after", caret + before.length + 3 + after.length, caret + before.length + 3 + after.length) }
    private fun applyInlineDelimiter(delimiter: String) { val editor = nativeEditor ?: return; val s = TextRange(editor.selectionStart, editor.selectionEnd); if (s.collapsed) replaceRange(s.start, s.end, delimiter + delimiter, s.start + delimiter.length, s.start + delimiter.length) else { val selected = editor.text?.subSequence(s.min, s.max).toString(); replaceRange(s.min, s.max, delimiter + selected + delimiter, s.min + delimiter.length, s.min + delimiter.length + selected.length) } }
    private fun applyLinePrefix(prefix: String) { val editor = nativeEditor ?: return; val s = TextRange(editor.selectionStart, editor.selectionEnd); val text = editor.text?.toString() ?: return; val start = text.lastIndexOf('\n', (s.min - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }; val end = text.indexOf('\n', s.max).let { if (it < 0) text.length else it }; val block = text.substring(start, end); val transformed = block.split('\n').joinToString("\n") { prefix + it }; val inserted = transformed.length - block.length; replaceRange(start, end, transformed, s.min + prefix.length, if (s.collapsed) s.min + prefix.length else s.max + inserted) }
    private fun replaceRange(start: Int, end: Int, replacement: String, selectionStart: Int, selectionEnd: Int) { nativeEditor?.replaceText(start, end, replacement, selectionStart, selectionEnd) }
    private fun setEditorSelection(start: Int, end: Int = start) { nativeEditor?.setSelection(start.coerceIn(0, nativeEditor?.length() ?: 0), end.coerceIn(0, nativeEditor?.length() ?: 0)) }
    fun undo() { nativeEditor?.undo() }
    fun redo() { nativeEditor?.redo() }
    fun deleteSelectedText() { nativeEditor?.let { if (it.selectionStart != it.selectionEnd) replaceSelection("") else emitToast(com.clipnest.R.string.select_text_to_delete) } }
    private fun replaceSelection(replacement: String) { nativeEditor?.let { replaceRange(it.selectionStart, it.selectionEnd, replacement, it.selectionStart + replacement.length, it.selectionStart + replacement.length) } }
    private fun scheduleDebouncedAutoSave() { autoSaveJob?.cancel(); autoSaveJob = viewModelScope.launch { delay(1500); if (_uiState.value.isDirty) saveCurrentDocumentSilently() } }
    fun saveCurrentDocumentSilently() {
        if (!_uiState.value.isDirty) return
        val text = currentDocumentText()
        val state = _uiState.value
        val revision = state.documentRevision
        _uiState.value.content.setFallback(text, nativeEditor?.let { TextRange(it.selectionStart, it.selectionEnd) } ?: TextRange(text.length))
        viewModelScope.launch(Dispatchers.IO) {
            val saved = runCatching { writeDocumentSnapshot(state, appContext.contentResolver, text) }.isSuccess
            withContext(Dispatchers.Main.immediate) { if (saved && _uiState.value.documentRevision == revision) _uiState.value = _uiState.value.copy(isDirty = false, lastSavedTimestamp = System.currentTimeMillis()) }
        }
    }
    fun onSaveClicked(contentResolver: ContentResolver = appContext.contentResolver) { val state = _uiState.value; if (state.externalDocumentSaveAsOnly || state.externalDocumentUri == null) _uiState.value = state.copy(showSaveNewFileDialog = true) else saveExternalDocument(Uri.parse(state.externalDocumentUri), contentResolver) }
    private fun saveExternalDocument(uri: Uri, resolver: ContentResolver) { val text = currentDocumentText(); val revision = _uiState.value.documentRevision; val encrypted = _uiState.value.externalDocumentEncrypted; viewModelScope.launch(Dispatchers.IO) { val ok = runCatching { writeExternalDocument(uri, resolver, text, encrypted) }.isSuccess; withContext(Dispatchers.Main.immediate) { if (ok) { if (_uiState.value.documentRevision == revision) _uiState.value = _uiState.value.copy(isDirty = false, lastSavedTimestamp = System.currentTimeMillis()); emitToast(com.clipnest.R.string.saved_current_file) } else { _uiState.value = _uiState.value.copy(showSaveNewFileDialog = true); emitToast(com.clipnest.R.string.could_not_save_open_file) } } } }
    fun dismissSaveNewFileDialog() { _uiState.value = _uiState.value.copy(showSaveNewFileDialog = false) }
    fun confirmSaveToNewFile(fileName: String, format: ExportFormat, contentResolver: ContentResolver) { if (fileName.isBlank()) return; val folder = _uiState.value.defaultSaveFolderUri; if (folder.isNullOrBlank()) { pendingSave = PendingSave(fileName.trim(), format); dismissSaveNewFileDialog(); viewModelScope.launch { _eventFlow.emit(EditorEvent.RequestSaveFolder) } } else saveToFolder(contentResolver, Uri.parse(folder), fileName.trim(), format) }
    fun setDefaultSaveFolder(uri: Uri, resolver: ContentResolver) { runCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }; viewModelScope.launch { settingsDataStore.setDefaultSaveFolderUri(uri.toString()) }; _uiState.value = _uiState.value.copy(defaultSaveFolderUri = uri.toString()); val request = pendingSave ?: return; pendingSave = null; saveToFolder(resolver, uri, request.fileName, request.format) }
    private fun saveToFolder(resolver: ContentResolver, folder: Uri, fileName: String, format: ExportFormat) { val text = currentDocumentText(); val revision = _uiState.value.documentRevision; viewModelScope.launch(Dispatchers.IO) { val saved = runCatching { fileManager.saveNewFileToTree(resolver, folder, fileName, format, text) }.getOrNull(); withContext(Dispatchers.Main.immediate) { if (saved == null) emitToast(com.clipnest.R.string.could_not_save_file) else { _uiState.value = _uiState.value.copy(showSaveNewFileDialog = false, isDirty = if (_uiState.value.documentRevision == revision) false else _uiState.value.isDirty, lastSavedTimestamp = if (_uiState.value.documentRevision == revision) System.currentTimeMillis() else _uiState.value.lastSavedTimestamp); emitToast(com.clipnest.R.string.saved_file, fileName, format.extension) } } } }
    fun onPauseOrExit() { if (_uiState.value.isDirty) saveCurrentDocumentSilently() }
    private fun emitToast(message: String) { viewModelScope.launch { _eventFlow.emit(EditorEvent.ShowToast(message)) } }
    private fun emitToast(@StringRes resourceId: Int, vararg args: Any) { emitToast(appContext.withAppLanguage(settings.value.language).getString(resourceId, *args)) }
    companion object { private const val TAG = "XBoard.Editor" }
}

class EditorViewModelFactory(private val fileManager: FileManager, private val settingsDataStore: SettingsDataStore, private val appContext: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T { if (modelClass.isAssignableFrom(EditorViewModel::class.java)) return EditorViewModel(fileManager, settingsDataStore, appContext) as T; throw IllegalArgumentException("Unknown ViewModel class") }
}