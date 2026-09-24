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
import com.clipnest.data.local.NoteDao
import com.clipnest.data.local.TopicDao
import com.clipnest.data.model.Note
import com.clipnest.data.model.NoteTopicCrossRef
import com.clipnest.data.model.NoteTopicRole
import com.clipnest.data.model.Topic
import com.clipnest.data.model.TopicLevel
import com.clipnest.data.local.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class PendingSave(val fileName: String, val format: ExportFormat)

enum class ExternalExitAction { RETURN_TO_EDITOR, OPEN_REPLACEMENT }

private data class PendingExternalOpen(
    val uri: Uri,
    val openContext: ExternalDocumentOpenContext?,
    val candidates: List<IncomingDocumentUri>
)

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
    val showExternalUnsavedChangesDialog: Boolean = false,
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
    val noteOrigin: EditorNoteOrigin? = null,
    val activeNoteId: Long? = null,
    val showEmptyNoteExitDialog: Boolean = false
)

sealed class EditorEvent {
    data class ShowToast(val message: String) : EditorEvent()
    data object RequestSaveFolder : EditorEvent()
    data class RequestExternalSaveAs(val suggestedFileName: String, val mimeType: String) : EditorEvent()
}

class EditorViewModel(
    private val fileManager: FileManager,
    private val settingsDataStore: SettingsDataStore,
    private val appContext: Context,
    private val noteDao: NoteDao,
    private val topicDao: TopicDao
) : ViewModel() {
    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()
    private val _topicSuggestionQuery = MutableStateFlow<String?>(null)
    val topicSuggestionQuery: StateFlow<String?> = _topicSuggestionQuery.asStateFlow()
    private val _topicSuggestions = MutableStateFlow<List<Topic>>(emptyList())
    val topicSuggestions: StateFlow<List<Topic>> = _topicSuggestions.asStateFlow()
    private val _eventFlow = MutableSharedFlow<EditorEvent>()
    val eventFlow: SharedFlow<EditorEvent> = _eventFlow.asSharedFlow()
    private val _openWithDiagnostic = MutableStateFlow<String?>(null)
    val openWithDiagnostic: StateFlow<String?> = _openWithDiagnostic.asStateFlow()
    private val _pendingEncryptedOpen = MutableStateFlow<PendingEncryptedOpen?>(null)
    val pendingEncryptedOpen: StateFlow<PendingEncryptedOpen?> = _pendingEncryptedOpen.asStateFlow()
    // Held only in memory for the lifetime of the currently open encrypted document,
    // so autosave/save can re-encrypt without prompting again. Cleared whenever the
    // document is closed, replaced, or returned to the internal editor.
    private var currentDocumentPassword: CharArray? = null
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
    private var pendingExternalOpen: PendingExternalOpen? = null
    private var pendingExternalExitAction: ExternalExitAction? = null
    private var pendingExternalReturnCallback: (() -> Unit)? = null
    private var pendingNoteReturnCallback: (() -> Unit)? = null
    // The internal document's caret position and viewport anchor, captured right
    // before switching to an external document session, so "Return to editor" can
    // restore both instead of defaulting the caret to end-of-text. Kept as two
    // separate fields deliberately — the user may have scrolled away from the caret
    // to read before opening the external document, and that scroll position should
    // come back too, not just the caret.
    private var savedInternalSelection: TextRange? = null
    private var savedInternalViewportAnchor: Int? = null
    private var autoSaveJob: Job? = null
    private var topicSuggestionJob: Job? = null
    private var cursorSaveJob: Job? = null
    private var searchJob: Job? = null
    private var nativeEditor: NativeEditorView? = null
    private var editorDocumentGeneration = 0L

    internal val editorDocumentResetKey: Long get() = editorDocumentGeneration
    val settings: StateFlow<com.clipnest.data.local.UserSettings> = settingsDataStore.userSettingsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.clipnest.data.local.UserSettings())

    fun bindNativeEditor(editor: NativeEditorView) {
        if (nativeEditor === editor) return
        nativeEditor?.let {
            val anchor = it.currentViewportAnchor()
            _uiState.value.content.setFallback(it.contentText(), TextRange(it.selectionStart, it.selectionEnd), anchor)
            _uiState.value.content.setNativeState(TextRange(it.selectionStart, it.selectionEnd), anchor)
        }
        nativeEditor?.setTextChangeListener(null)
        nativeEditor?.setSelectionChangeListener(null)
        nativeEditor?.setViewportChangeListener(null)
        nativeEditor?.setSectionChangeListener(null)
        nativeEditor = editor
        editor.setTextChangeListener { onDocumentTextChanged() }
        editor.setSelectionChangeListener { start, end ->
            val anchor = _uiState.value.content.viewportAnchor
            _uiState.value.content.setFallback(editor.contentText(), TextRange(start, end), anchor)
            _uiState.value.content.setNativeState(TextRange(start, end), editor.currentViewportAnchor())
            if (!hasExternalSession()) {
                cursorSaveJob?.cancel()
                cursorSaveJob = viewModelScope.launch(Dispatchers.Default) {
                    delay(500)
                    runCatching { fileManager.writeEditorCursor(start, end, editor.currentViewportAnchor()) }
                }
            }
        }
        editor.setViewportChangeListener { anchor ->
            val current = _uiState.value.content
            current.setFallback(current.text, current.selection, anchor)
            current.setNativeState(current.nativeSelectionRange, anchor)
            if (!hasExternalSession()) {
                cursorSaveJob?.cancel()
                cursorSaveJob = viewModelScope.launch(Dispatchers.Default) {
                    delay(500)
                    val sel = current.nativeSelectionRange
                    runCatching { fileManager.writeEditorCursor(sel.start, sel.end, anchor) }
                }
            }
        }
        editor.setEditorTextSize(settings.value.editorTextSize)
        if (!editorLoaded || editor.text?.isEmpty() == true) {
            val content = _uiState.value.content
            editor.setStructuredDocument(
                _uiState.value.title,
                content.text,
                content.nativeSelectionRange.start,
                content.nativeSelectionRange.end,
                content.nativeViewport
            )
        }
    }

    fun currentDocumentText(): String = nativeEditor?.contentText() ?: _uiState.value.content.text
    fun currentDocumentTitle(): String = nativeEditor?.titleText() ?: _uiState.value.title
    fun currentDocumentSnapshot(): EditorSnapshot = EditorSnapshot(_uiState.value.documentRevision, currentDocumentText())

    init {
        loadEditor()
        viewModelScope.launch { settings.collect { _uiState.value = _uiState.value.copy(defaultSaveFolderUri = it.defaultSaveFolderUri); nativeEditor?.setEditorTextSize(it.editorTextSize) } }
    }

    private fun clearCurrentDocumentPassword() {
        currentDocumentPassword?.fill('\u0000')
        currentDocumentPassword = null
    }

    private fun loadEditor() {
        viewModelScope.launch(Dispatchers.IO) {
            val text = fileManager.readEditor()
            val title = fileManager.readEditorTitle()
            // Restore the caret and viewport from disk (survives the process being
            // killed, not just backgrounded — see writeEditorCursor). Falls back to
            // end-of-text / end-of-text for both only if nothing was ever saved (fresh
            // install) or the saved values no longer fit the current document length.
            // The viewport anchor is read as its own field, not derived from the
            // caret — the two can legitimately be far apart if the user had scrolled
            // away from the caret before the process was killed.
            val savedCursor = fileManager.readEditorCursor()
            val titlePrefix = if (title.isNotEmpty()) title.length + 1 else 0
            val restoreSelection = savedCursor
                ?.let { (start, end, _) -> TextRange((start + titlePrefix).coerceIn(0, text.length + titlePrefix), (end + titlePrefix).coerceIn(0, text.length + titlePrefix)) }
                ?: TextRange(text.length + titlePrefix)
            val restoreViewportAnchor = savedCursor?.third?.let { it + titlePrefix } ?: restoreSelection.start
            withContext(Dispatchers.Main.immediate) {
                if (!editorLoaded && !_uiState.value.isDirty) {
                    _uiState.value.content.setFallback(text, restoreSelection, restoreViewportAnchor)
                    _uiState.value.content.setNativeState(restoreSelection, restoreViewportAnchor)
                    nativeEditor?.setStructuredDocument(title, text, restoreSelection.start, restoreSelection.end, restoreViewportAnchor)
                    editorDocumentGeneration++
                    _uiState.value = _uiState.value.copy(documentRevision = _uiState.value.documentRevision + 1, isDirty = false, title = title, lastSavedTimestamp = System.currentTimeMillis())
                }
                editorLoaded = true
            }
        }
    }

    private fun hasExternalSession(): Boolean = _uiState.value.externalDocumentUri != null

    private fun requestExternalExit(action: ExternalExitAction) {
        autoSaveJob?.cancel()
        pendingExternalExitAction = action
        _uiState.value = _uiState.value.copy(showExternalUnsavedChangesDialog = true)
    }

    fun cancelExternalExit() {
        pendingExternalOpen = null
        pendingExternalExitAction = null
        pendingExternalReturnCallback = null
        _uiState.value = _uiState.value.copy(showExternalUnsavedChangesDialog = false)
    }

    fun chooseExternalNoSave(contentResolver: ContentResolver) {
        val action = pendingExternalExitAction ?: return
        _uiState.value = _uiState.value.copy(showExternalUnsavedChangesDialog = false)
        finishExternalSession(contentResolver)
        continueExternalExit(action, contentResolver)
    }

    fun chooseExternalSave(contentResolver: ContentResolver) {
        val action = pendingExternalExitAction ?: return
        val state = _uiState.value
        if (state.externalDocumentSaveAsOnly) {
            // A merged external session has no source URI to overwrite, so Save uses
            // the same destination/format dialog while still completing the exit action.
            _uiState.value = state.copy(
                showExternalUnsavedChangesDialog = false,
                showSaveNewFileDialog = true
            )
            return
        }
        val text = currentDocumentText()
        val password = currentDocumentPassword?.copyOf()
        viewModelScope.launch(Dispatchers.IO) {
            val saved = try {
                runCatching {
                    val uri = state.externalDocumentUri?.let(Uri::parse) ?: error("Missing external document URI")
                    writeExternalDocument(uri, contentResolver, text, state.externalDocumentEncrypted, password)
                }.isSuccess
            } finally {
                password?.fill('\u0000')
            }
            withContext(Dispatchers.Main.immediate) {
                if (!saved) {
                    emitToast(com.clipnest.R.string.could_not_save_open_file)
                } else {
                    finishExternalSession(contentResolver)
                    continueExternalExit(action, contentResolver)
                }
            }
        }
    }

    fun chooseExternalSaveAs() {
        // Save As is part of Return-to-editor. Reuse the normal Save File dialog so
        // the destination format, including encrypted .cne, can be selected.
        _uiState.value = _uiState.value.copy(
            showExternalUnsavedChangesDialog = false,
            showSaveNewFileDialog = true
        )
    }

    fun completeExternalSaveAs(uri: Uri?, contentResolver: ContentResolver) {
        if (uri == null) return
        val action = pendingExternalExitAction ?: return
        val state = _uiState.value
        val text = currentDocumentText()
        val password = currentDocumentPassword?.copyOf()
        viewModelScope.launch(Dispatchers.IO) {
            val saved = try {
                runCatching { writeExternalDocument(uri, contentResolver, text, state.externalDocumentEncrypted, password) }.isSuccess
            } finally {
                password?.fill('\u0000')
            }
            withContext(Dispatchers.Main.immediate) {
                if (!saved) {
                    emitToast(com.clipnest.R.string.could_not_save_file)
                    _uiState.value = _uiState.value.copy(showExternalUnsavedChangesDialog = false)
                } else {
                    finishExternalSession(contentResolver)
                    continueExternalExit(action, contentResolver)
                }
            }
        }
    }

    private fun continueExternalExit(action: ExternalExitAction, contentResolver: ContentResolver) {
        val open = pendingExternalOpen
        val callback = pendingExternalReturnCallback
        pendingExternalOpen = null
        pendingExternalExitAction = null
        pendingExternalReturnCallback = null
        when (action) {
            // finishExternalSession() (called by the caller before this runs) clears
            // the editor to empty text — reload the internal document here so the user
            // actually sees it again, regardless of which dialog option they picked.
            ExternalExitAction.RETURN_TO_EDITOR -> loadInternalDocumentIntoEditor { callback?.invoke() }
            ExternalExitAction.OPEN_REPLACEMENT -> open?.let { openExternalDocument(it.uri, contentResolver, it.openContext, it.candidates) }
        }
    }

    private fun finishExternalSession(contentResolver: ContentResolver) {
        autoSaveJob?.cancel()
        searchJob?.cancel()
        resetSearchState()
        clearCurrentDocumentPassword()
        _pendingEncryptedOpen.value = null
        nativeEditor?.setPlainEditorText("", 0)
        _uiState.value.content.setFallback("", TextRange.Zero)
        _uiState.value = _uiState.value.copy(
            externalDocumentUri = null,
            externalDocumentSaveAsOnly = false,
            externalDocumentFileCount = 0,
            externalDocumentEncrypted = false,
            showSaveNewFileDialog = false,
            showExternalUnsavedChangesDialog = false,
            isDirty = false
        )
    }

    fun openExternalDocument(uri: Uri, contentResolver: ContentResolver, openContext: ExternalDocumentOpenContext? = null, candidates: List<IncomingDocumentUri> = listOf(IncomingDocumentUri(uri, openContext?.source ?: com.clipnest.IncomingUriSource.FILE_PICKER))) {
        if (hasExternalSession()) {
            if (_uiState.value.isDirty) {
                pendingExternalOpen = PendingExternalOpen(uri, openContext, candidates)
                requestExternalExit(ExternalExitAction.OPEN_REPLACEMENT)
                return
            }
            finishExternalSession(contentResolver)
        } else {
            // Capture where the caret AND the viewport were in the internal document
            // before switching away, so "Return to editor" can restore both instead of
            // defaulting to end-of-text. Only done when actually leaving the internal
            // document (not when already inside an external session, e.g. opening a
            // second external file — that has no internal caret/viewport to preserve
            // here). Captured as two independent values: if the user had scrolled away
            // from the caret to read before opening this external file, that scroll
            // position is what should come back, not the caret's position.
            nativeEditor?.let {
                savedInternalSelection = TextRange(it.selectionStart, it.selectionEnd)
                savedInternalViewportAnchor = it.currentViewportAnchor()
            }
        }
        val previousState = _uiState.value
        val previousText = currentDocumentText()
        editorLoaded = true
        autoSaveJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            val failedCandidates = mutableListOf<ExternalDocumentReadFailure>()
            val internalSaved = runCatching {
                if (previousState.externalDocumentUri == null && previousState.isDirty) {
                    writeDocumentSnapshot(previousState, contentResolver, previousText)
                }
            }.isSuccess
            if (!internalSaved) {
                withContext(Dispatchers.Main.immediate) {
                    emitToast(com.clipnest.R.string.could_not_save_file)
                }
                return@launch
            }
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
                        _pendingEncryptedOpen.value = PendingEncryptedOpen(uri = loadedDocuments.first().first.uri, encryptedJson = text, displayName = name)
                        _openWithDiagnostic.value = null
                        return@onSuccess
                    }
                    resetSearchState()
                    val first = loadedDocuments.first().first
                    nativeEditor?.setStructuredDocument(name, text, name.length + 1 + text.length, name.length + 1 + text.length, name.length + 1 + text.length)
                    _uiState.value.content.setFallback(text, TextRange(name.length + 1 + text.length))
                    editorDocumentGeneration++
                    clearCurrentDocumentPassword()
                    _uiState.value = _uiState.value.copy(documentRevision = _uiState.value.documentRevision + 1, isDirty = false, documentName = if (candidates.size > 1) appContext.withAppLanguage(settings.value.language).getString(com.clipnest.R.string.merged_document_name, loadedDocuments.size) else name, externalDocumentUri = first.uri.toString(), externalDocumentSaveAsOnly = candidates.size > 1, externalDocumentFileCount = loadedDocuments.size, externalDocumentEncrypted = false, showSaveNewFileDialog = false, lastSavedTimestamp = System.currentTimeMillis())
                    _openWithDiagnostic.value = null
                    if (failedCandidates.isNotEmpty()) emitToast(com.clipnest.R.string.opened_files_with_failures, loadedDocuments.size, candidates.size)
                }.onFailure { error ->
                    emitToast(com.clipnest.R.string.could_not_open_file)
                    _openWithDiagnostic.value = if (failedCandidates.isEmpty()) buildOpenWithDiagnostic(uri, openContext, error) else buildOpenWithDiagnostic(failedCandidates, openContext)
                }
            }
        }
    }

    fun dismissOpenWithDiagnostic() { _openWithDiagnostic.value = null }
    fun dismissPendingEncryptedOpen() { _pendingEncryptedOpen.value = null }

    fun confirmDecryptAndOpen(password: String, contentResolver: ContentResolver) {
        val pending = _pendingEncryptedOpen.value ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val sessionPassword = password.toCharArray()
            val decoded = try {
                runCatching { EncryptedDocumentCodec.decode(pending.encryptedJson, sessionPassword) }
            } catch (error: Throwable) {
                sessionPassword.fill('\u0000')
                throw error
            }
            withContext(Dispatchers.Main.immediate) {
                decoded.onSuccess { text ->
                    clearCurrentDocumentPassword()
                    currentDocumentPassword = sessionPassword
                    _pendingEncryptedOpen.value = null
                    resetSearchState()
                    nativeEditor?.setEditorText(text, text.length)
                    _uiState.value.content.setFallback(text, TextRange(text.length))
                    editorDocumentGeneration++
                    _uiState.value = _uiState.value.copy(documentRevision = _uiState.value.documentRevision + 1, isDirty = false, documentName = pending.displayName, externalDocumentUri = pending.uri.toString(), externalDocumentSaveAsOnly = false, externalDocumentFileCount = 1, externalDocumentEncrypted = true, showSaveNewFileDialog = false, lastSavedTimestamp = System.currentTimeMillis())
                    _openWithDiagnostic.value = null
                    editorLoaded = true
                }.onFailure {
                    sessionPassword.fill('\u0000')
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
    private fun writeExternalDocument(uri: Uri, resolver: ContentResolver, text: String, encrypted: Boolean, password: CharArray?) {
        val output = if (encrypted) {
            val resolvedPassword = password ?: error("Missing in-memory password for encrypted document; cannot save without prompting again")
            EncryptedDocumentCodec.encode(text, resolvedPassword)
        } else {
            text
        }
        val stream = if (uri.scheme == ContentResolver.SCHEME_FILE) File(uri.path ?: error("File URI has no path")).outputStream() else resolver.openOutputStream(uri, "wt") ?: resolver.openOutputStream(uri) ?: error("Unable to save file")
        stream.use { it.write(output.toByteArray(Charsets.UTF_8)); it.flush() }
    }
    private fun queryDisplayName(uri: Uri, resolver: ContentResolver): String = runCatching { resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } }.getOrNull()?.takeIf(String::isNotBlank) ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf(String::isNotBlank) ?: appContext.withAppLanguage(settings.value.language).getString(com.clipnest.R.string.open_file)
    private suspend fun writeDocumentSnapshot(state: EditorUiState, resolver: ContentResolver, text: String = state.content.text, password: CharArray? = currentDocumentPassword) {
        if (state.externalDocumentSaveAsOnly) return
        val noteId = state.activeNoteId
        if (noteId != null) {
            if (state.isDirty) {
                noteDao.updateContentAndBumpEditSession(
                    id = noteId,
                    title = currentDocumentTitle(),
                    content = text,
                    now = System.currentTimeMillis()
                )
            }
            return
        }
        val uri = state.externalDocumentUri?.let(Uri::parse)
        if (uri == null) {
            if (state.isDirty) { fileManager.writeEditor(text); fileManager.writeEditorTitle(currentDocumentTitle()) }
        } else if (state.isDirty) {
            val passwordCopy = password?.copyOf()
            try {
                writeExternalDocument(uri, resolver, text, state.externalDocumentEncrypted, passwordCopy)
            } finally {
                passwordCopy?.fill('\u0000')
            }
        }
    }

    fun returnToInternalEditor(contentResolver: ContentResolver, onComplete: () -> Unit = {}) {
        if (!hasExternalSession()) { onComplete(); return }
        if (_uiState.value.isDirty) {
            pendingExternalReturnCallback = onComplete
            requestExternalExit(ExternalExitAction.RETURN_TO_EDITOR)
            return
        }
        finishExternalSession(contentResolver)
        loadInternalDocumentIntoEditor(onComplete)
    }

    /**
     * Reads the internal document from disk and loads it into the editor, restoring
     * the caret from savedInternalSelection and the scroll position from
     * savedInternalViewportAnchor (both captured before we switched to an external
     * session). Called both when returning to the editor directly (no unsaved
     * external changes) and after the user resolves the unsaved-changes dialog (Save /
     * Save As / Don't Save) — previously only the direct path did this, so resolving
     * the dialog left the editor showing the empty text finishExternalSession() sets,
     * losing the internal document from view until something reloaded it.
     */
    private fun loadInternalDocumentIntoEditor(onComplete: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val internal = fileManager.readEditor()
            val title = fileManager.readEditorTitle()
            withContext(Dispatchers.Main.immediate) {
                // Restore the caret where the user left it in the internal document,
                // not end-of-text — the document on disk hasn't changed since we
                // captured this, so the offsets are still valid. Falls back to
                // end-of-text only if we never captured a position (e.g. the app was
                // relaunched directly into an external session).
                val restoreSelection = savedInternalSelection?.let {
                    TextRange(it.start.coerceIn(0, internal.length), it.end.coerceIn(0, internal.length))
                } ?: TextRange(internal.length)
                // Restore the viewport independently — if the user had scrolled away
                // from the caret before opening the external document, this brings the
                // screen back to what they were looking at, not to the caret.
                val restoreViewportAnchor = savedInternalViewportAnchor?.coerceIn(0, internal.length) ?: restoreSelection.start
                savedInternalSelection = null
                savedInternalViewportAnchor = null
                nativeEditor?.setStructuredDocument(title, internal, restoreSelection.start, restoreSelection.end, restoreViewportAnchor)
                _uiState.value.content.setFallback(internal, restoreSelection, restoreViewportAnchor)
                _uiState.value.content.setNativeState(restoreSelection, restoreViewportAnchor)
                editorDocumentGeneration++
                _uiState.value = _uiState.value.copy(documentRevision = _uiState.value.documentRevision + 1, isDirty = false, documentName = internalDocumentName(), title = title)
                onComplete()
            }
        }
    }
    private fun internalDocumentName() = appContext.withAppLanguage(settings.value.language).getString(com.clipnest.R.string.editor)

    private fun resetSearchState() { _isSearchOpen.value = false; _searchQuery.value = ""; _replaceQuery.value = ""; searchMatchStarts = emptyList(); _searchMatchCount.value = 0; _activeSearchMatch.value = 0 }
    fun onDocumentTextChanged() {
        editorLoaded = true
        val editor = nativeEditor
        if (editor != null) {
            val selection = TextRange(editor.selectionStart, editor.selectionEnd)
            _uiState.value.content.setFallback(editor.contentText(), selection, _uiState.value.content.viewportAnchor)
            _uiState.value.content.setNativeState(selection, editor.currentViewportAnchor())
        }
        _uiState.value = _uiState.value.copy(
            title = editor?.titleText().orEmpty(),
            documentRevision = _uiState.value.documentRevision + 1,
            isDirty = true
        )
        updateTopicSuggestions()
        if (_searchQuery.value.isNotBlank()) scheduleSearchResults(_searchQuery.value, true)
        if (!hasExternalSession()) scheduleDebouncedAutoSave()
    }

    fun returnToFreeEditor(onComplete: () -> Unit = {}) {
        pendingNoteReturnCallback = onComplete
        val state = _uiState.value
        if (state.mode != EditorMode.NOTE || state.activeNoteId == null) return
        autoSaveJob?.cancel()
        val text = currentDocumentText()
        if (state.title.isBlank() && text.isBlank()) {
            _uiState.value = state.copy(showEmptyNoteExitDialog = true)
            return
        }
        persistNoteAndReturnToFreeEditor(deleteNote = false)
    }

    fun cancelEmptyNoteExit() {
        _uiState.value = _uiState.value.copy(showEmptyNoteExitDialog = false)
    }

    fun saveEmptyNoteAndExit() {
        if (_uiState.value.activeNoteId == null) return
        persistNoteAndReturnToFreeEditor(deleteNote = false)
    }

    fun discardEmptyNoteAndExit() {
        if (_uiState.value.activeNoteId == null) return
        persistNoteAndReturnToFreeEditor(deleteNote = true)
    }

    private fun persistNoteAndReturnToFreeEditor(deleteNote: Boolean) {
        val state = _uiState.value
        val noteId = state.activeNoteId ?: return
        val text = currentDocumentText()
        val title = state.title
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val now = System.currentTimeMillis()
                if (deleteNote) {
                    noteDao.setDeleted(
                        id = noteId,
                        isDeleted = true,
                        deletedAtMillis = now,
                        updatedAtMillis = now
                    )
                } else {
                    noteDao.updateContentAndBumpEditSession(
                        id = noteId,
                        title = title,
                        content = text,
                        now = now
                    )
                }
            }
            if (result.isFailure) return@launch
            withContext(Dispatchers.Main.immediate) {
                val returnCallback = pendingNoteReturnCallback
                pendingNoteReturnCallback = null
                _uiState.value = _uiState.value.copy(
                    mode = EditorMode.PLAIN,
                    title = "",
                    noteOrigin = null,
                    activeNoteId = null,
                    showEmptyNoteExitDialog = false,
                    isDirty = false,
                    documentName = internalDocumentName()
                )
                loadInternalDocumentIntoEditor()
                returnCallback?.invoke()
            }
        }
    }

    /**
     * Detects only the hashtag token immediately before the caret.
     *
     * This deliberately does not create a substring of the whole document or run
     * a regex over it. The scan walks backwards from the caret until whitespace or
     * another '#' is reached, so its work is proportional to the current hashtag
     * token, not to the size of the document.
     */
    private fun updateTopicSuggestions() {
        val state = _uiState.value
        val editor = nativeEditor
        if (state.mode != EditorMode.NOTE || state.activeNoteId == null || editor == null) {
            dismissTopicSuggestions()
            return
        }
        if (editor.selectionStart != editor.selectionEnd) {
            dismissTopicSuggestions()
            return
        }

        val cursor = editor.selectionStart
        val length = editor.length()
        if (cursor <= editor.titleBoundaryOffset()) {
            dismissTopicSuggestions()
            return
        }
        if (cursor < 0 || cursor > length) {
            dismissTopicSuggestions()
            return
        }

        var tokenStart = cursor
        while (tokenStart > 0) {
            val ch = editor.text?.get(tokenStart - 1) ?: break
            if (ch.isWhitespace() || ch == '#') break
            tokenStart--
        }

        if (tokenStart <= 0 || editor.text?.get(tokenStart - 1) != '#') {
            dismissTopicSuggestions()
            return
        }

        val query = editor.text
            ?.subSequence(tokenStart, cursor)
            ?.toString()
            .orEmpty()

        _topicSuggestionQuery.value = query
        topicSuggestionJob?.cancel()
        topicSuggestionJob = viewModelScope.launch(Dispatchers.IO) {
            val suggestions = runCatching { topicDao.searchTopics(query).first() }.getOrDefault(emptyList())
            withContext(Dispatchers.Main.immediate) {
                if (_topicSuggestionQuery.value == query && _uiState.value.mode == EditorMode.NOTE) {
                    _topicSuggestions.value = suggestions
                }
            }
        }
    }

    fun dismissTopicSuggestions() {
        _topicSuggestionQuery.value = null
        _topicSuggestions.value = emptyList()
        topicSuggestionJob?.cancel()
    }

    fun selectExistingTopic(topic: Topic) {
        val noteId = _uiState.value.activeNoteId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                topicDao.addNoteTopicCrossRef(NoteTopicCrossRef(noteId = noteId, topicId = topic.id))
            }
            withContext(Dispatchers.Main.immediate) {
                dismissTopicSuggestions()
            }
        }
    }

    fun createTopicFromSuggestion() {
        val noteId = _uiState.value.activeNoteId ?: return
        val name = _topicSuggestionQuery.value?.trim().orEmpty()
        if (name.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val topic = runCatching {
                topicDao.searchTopics(name).first().firstOrNull { it.name.equals(name, ignoreCase = true) }
                    ?: runCatching {
                        val id = topicDao.insertTopic(
                            Topic(
                                name = name,
                                level = TopicLevel.PARENT,
                                createdAtMillis = System.currentTimeMillis()
                            )
                        )
                        topicDao.getTopicById(id)
                    }.getOrNull()
            }.getOrNull() ?: return@launch
            runCatching {
                topicDao.addNoteTopicCrossRef(NoteTopicCrossRef(noteId = noteId, topicId = topic.id, role = NoteTopicRole.USER_TAG))
            }
            withContext(Dispatchers.Main.immediate) {
                dismissTopicSuggestions()
            }
        }
    }

    fun configureNoteMode(origin: EditorNoteOrigin?) {
        _uiState.value = _uiState.value.copy(mode = EditorMode.NOTE, noteOrigin = origin)
    }

    fun createNoteAndEnterNoteMode(
        initialContent: String = "",
        origin: EditorNoteOrigin? = null,
        title: String = "",
        topicId: Long? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val noteId = noteDao.insertNote(
                Note(
                    title = title,
                    content = initialContent,
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
            )
            if (topicId != null) {
                topicDao.addNoteTopicCrossRef(
                    NoteTopicCrossRef(
                        noteId = noteId,
                        topicId = topicId,
                        role = NoteTopicRole.USER_TAG
                    )
                )
            }
            withContext(Dispatchers.Main.immediate) {
                configureNoteMode(origin)
                val content = initialContent.replace("\r\n", "\n").replace('\r', '\n')
                val structuredLength = title.length + 1 + content.length
                nativeEditor?.setStructuredDocument(title, content, structuredLength, structuredLength, structuredLength)
                _uiState.value.content.setFallback(content, TextRange(structuredLength), structuredLength)
                _uiState.value.content.setNativeState(TextRange(structuredLength), structuredLength)
                _uiState.value = _uiState.value.copy(
                    mode = EditorMode.NOTE,
                    title = title,
                    noteOrigin = origin,
                    activeNoteId = noteId,
                    documentRevision = _uiState.value.documentRevision + 1,
                    isDirty = false,
                    documentName = "Note"
                )
                editorDocumentGeneration++
            }
        }
    }

    fun openNoteInEditor(noteId: Long, origin: EditorNoteOrigin? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val note = noteDao.getNoteById(noteId) ?: return@launch
            withContext(Dispatchers.Main.immediate) {
                configureNoteMode(origin)
                val content = note.content.replace("\r\n", "\n").replace('\r', '\n')
                val structuredLength = note.title.length + 1 + content.length
                nativeEditor?.setStructuredDocument(note.title, content, structuredLength, structuredLength, structuredLength)
                _uiState.value.content.setFallback(content, TextRange(structuredLength), structuredLength)
                _uiState.value.content.setNativeState(TextRange(structuredLength), structuredLength)
                _uiState.value = _uiState.value.copy(
                    mode = EditorMode.NOTE,
                    title = note.title,
                    noteOrigin = origin,
                    activeNoteId = note.id,
                    documentRevision = _uiState.value.documentRevision + 1,
                    isDirty = false,
                    documentName = "Note"
                )
                editorDocumentGeneration++
            }
        }
    }

    fun saveCurrentNoteNow() {
        val state = _uiState.value
        val noteId = state.activeNoteId ?: return
        autoSaveJob?.cancel()
        val text = currentDocumentText()
        val revision = state.documentRevision
        _uiState.value.content.setFallback(
            text,
            nativeEditor?.let { TextRange(it.selectionStart, it.selectionEnd) } ?: TextRange(text.length)
        )
        viewModelScope.launch(Dispatchers.IO) {
            val saved = runCatching {
                noteDao.updateContentAndBumpEditSession(
                    id = noteId,
                    title = currentDocumentTitle(),
                    content = text,
                    now = System.currentTimeMillis()
                )
            }.isSuccess
            withContext(Dispatchers.Main.immediate) {
                if (saved && _uiState.value.documentRevision == revision) {
                    _uiState.value = _uiState.value.copy(
                        isDirty = false,
                        lastSavedTimestamp = System.currentTimeMillis()
                    )
                }
            }
        }
    }

    fun onTitleChange(newTitle: String) { editorLoaded = true; _uiState.value = _uiState.value.copy(title = newTitle, isDirty = true); if (!hasExternalSession()) scheduleDebouncedAutoSave() }
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
    private fun scheduleSearchResults(query: String, preserveSelection: Boolean) { searchJob?.cancel(); if (query.isBlank()) { searchMatchStarts = emptyList(); _searchMatchCount.value = 0; _activeSearchMatch.value = 0; return }; searchJob = viewModelScope.launch { delay(400); val text = nativeEditor?.text?.toString().orEmpty(); val revision = _uiState.value.documentRevision; val selection = nativeEditor?.let { TextRange(it.selectionStart, it.selectionEnd) } ?: TextRange.Zero; val matches = withContext(Dispatchers.Default) { EditorSearchEngine.findMatches(text, query) }; if (_searchQuery.value != query || _uiState.value.documentRevision != revision) return@launch; applySearchResults(query, matches, selection, preserveSelection) } }
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
        if (hasExternalSession()) return
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
    fun onSaveClicked(contentResolver: ContentResolver = appContext.contentResolver) {
        val state = _uiState.value
        if (state.externalDocumentUri != null) {
            if (state.externalDocumentSaveAsOnly) {
                _uiState.value = state.copy(showSaveNewFileDialog = true)
            } else {
                saveExternalDocument(Uri.parse(state.externalDocumentUri), contentResolver)
            }
        } else {
            _uiState.value = state.copy(showSaveNewFileDialog = true)
        }
    }

    private fun saveExternalDocument(uri: Uri, resolver: ContentResolver) {
        val text = currentDocumentText()
        val revision = _uiState.value.documentRevision
        val encrypted = _uiState.value.externalDocumentEncrypted
        val password = currentDocumentPassword?.copyOf()
        viewModelScope.launch(Dispatchers.IO) {
            val ok = try {
                runCatching {
                    writeExternalDocument(uri, resolver, text, encrypted, password)
                }.isSuccess
            } finally {
                password?.fill('\u0000')
            }
            withContext(Dispatchers.Main.immediate) {
                if (ok) {
                    if (_uiState.value.documentRevision == revision) {
                        _uiState.value = _uiState.value.copy(
                            isDirty = false,
                            lastSavedTimestamp = System.currentTimeMillis()
                        )
                    }
                    emitToast(com.clipnest.R.string.saved_current_file)
                } else {
                    emitToast(com.clipnest.R.string.could_not_save_open_file)
                }
            }
        }
    }

    fun dismissSaveNewFileDialog() {
        val wasExternalExitSaveAs = pendingExternalExitAction != null
        _uiState.value = _uiState.value.copy(showSaveNewFileDialog = false)
        if (wasExternalExitSaveAs) {
            _uiState.value = _uiState.value.copy(showExternalUnsavedChangesDialog = true)
        }
    }

    fun confirmSaveToNewFile(fileName: String, format: ExportFormat, contentResolver: ContentResolver) {
        if (fileName.isBlank()) return
        val folder = _uiState.value.defaultSaveFolderUri
        if (folder.isNullOrBlank()) {
            pendingSave = PendingSave(fileName.trim(), format)
            _uiState.value = _uiState.value.copy(showSaveNewFileDialog = false)
            viewModelScope.launch { _eventFlow.emit(EditorEvent.RequestSaveFolder) }
        } else {
            saveToFolder(contentResolver, Uri.parse(folder), fileName.trim(), format)
        }
    }
    fun setDefaultSaveFolder(uri: Uri, resolver: ContentResolver) { runCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }; viewModelScope.launch { settingsDataStore.setDefaultSaveFolderUri(uri.toString()) }; _uiState.value = _uiState.value.copy(defaultSaveFolderUri = uri.toString()); val request = pendingSave ?: return; pendingSave = null; saveToFolder(resolver, uri, request.fileName, request.format) }
    private fun saveToFolder(resolver: ContentResolver, folder: Uri, fileName: String, format: ExportFormat) {
        val text = currentDocumentText()
        val revision = _uiState.value.documentRevision
        val savedExtension = if (format.isEncrypted) ".cne" else format.extension
        viewModelScope.launch(Dispatchers.IO) {
            val saved = runCatching {
                fileManager.saveNewFileToTree(resolver, folder, fileName, format, text)
            }.getOrNull()
            withContext(Dispatchers.Main.immediate) {
                if (saved == null) {
                    emitToast(com.clipnest.R.string.could_not_save_file)
                } else {
                    val exitAction = pendingExternalExitAction
                    _uiState.value = _uiState.value.copy(
                        showSaveNewFileDialog = false,
                        isDirty = if (_uiState.value.documentRevision == revision) false else _uiState.value.isDirty,
                        lastSavedTimestamp = if (_uiState.value.documentRevision == revision) System.currentTimeMillis() else _uiState.value.lastSavedTimestamp
                    )
                    emitToast(com.clipnest.R.string.saved_file, fileName, savedExtension)
                    if (exitAction != null) {
                        finishExternalSession(resolver)
                        continueExternalExit(exitAction, resolver)
                    }
                }
            }
        }
    }
    fun onPauseOrExit() { if (!hasExternalSession() && _uiState.value.isDirty) saveCurrentDocumentSilently() }
    override fun onCleared() {
        clearCurrentDocumentPassword()
        // Best-effort: write the last known caret + viewport synchronously, in case
        // this runs before the process is actually killed. Not guaranteed on a hard
        // swipe-away, which is why the debounced write in bindNativeEditor's
        // selection/viewport listeners is the primary mechanism — this is just a
        // secondary safety net. Reads both fields independently off content rather
        // than re-deriving one from the other.
        if (!hasExternalSession()) {
            val content = _uiState.value.content
            runCatching { fileManager.writeEditorCursor(content.selection.start, content.selection.end, content.viewportAnchor) }
        }
        super.onCleared()
    }
    private fun emitToast(message: String) { viewModelScope.launch { _eventFlow.emit(EditorEvent.ShowToast(message)) } }
    private fun emitToast(@StringRes resourceId: Int, vararg args: Any) { emitToast(appContext.withAppLanguage(settings.value.language).getString(resourceId, *args)) }
    companion object { private const val TAG = "XBoard.Editor" }
}

class EditorViewModelFactory(
    private val fileManager: FileManager,
    private val settingsDataStore: SettingsDataStore,
    private val appContext: Context,
    private val noteDao: NoteDao,
    private val topicDao: TopicDao
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditorViewModel::class.java)) {
            return EditorViewModel(fileManager, settingsDataStore, appContext, noteDao, topicDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}