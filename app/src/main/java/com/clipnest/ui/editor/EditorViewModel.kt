package com.clipnest.ui.editor

import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.text.TextRange
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.clipnest.data.local.EditorTextSize
import com.clipnest.data.local.FileManager
import com.clipnest.data.local.SettingsDataStore
import com.clipnest.ui.editor.search.EditorSearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

class EditorViewModel(private val fileManager: FileManager, private val settingsDataStore: SettingsDataStore, private val appContext: Context) : ViewModel() {
    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()
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
    private var searchJob: Job? = null
    private var autoSaveJob: Job? = null
    private var nativeEditor: NativeEditorView? = null
    private var editorLoaded = false
    private var editorDocumentGeneration = 0L
    private val settings = settingsDataStore.settingsFlow
    private val _eventFlow = kotlinx.coroutines.flow.MutableSharedFlow<EditorEvent>()
    val eventFlow = _eventFlow.asSharedFlow()
    private val _openWithDiagnostic = MutableStateFlow<OpenWithDiagnostic?>(null)
    val openWithDiagnostic: StateFlow<OpenWithDiagnostic?> = _openWithDiagnostic.asStateFlow()
    private var pendingSave: PendingSave? = null

    init {
        viewModelScope.launch {
            settings.collect { }
        }
    }

    fun bindNativeEditor(editor: NativeEditorView) {
        if (nativeEditor === editor) return
        nativeEditor = editor
        editor.setTextChangeListener { onDocumentTextChanged() }
        if (!editorLoaded) {
            val text = fileManager.readEditor()
            val title = fileManager.readEditorTitle()
            editor.setEditorText(text, text.length)
            _uiState.value.content.setFallback(text, TextRange(text.length))
            editorDocumentGeneration++
            _uiState.value = _uiState.value.copy(documentRevision = _uiState.value.documentRevision + 1, isDirty = false, title = title, lastSavedTimestamp = System.currentTimeMillis())
            editorLoaded = true
        }
    }

    fun onDocumentTextChanged() { editorLoaded = true; _uiState.value = _uiState.value.copy(documentRevision = _uiState.value.documentRevision + 1, isDirty = true); if (_searchQuery.value.isNotBlank()) scheduleSearchResults(_searchQuery.value, true); scheduleDebouncedAutoSave() }
    fun openSearchPublic() { _isSearchOpen.value = true }
    fun closeSearch() { resetSearchState(); nativeEditor?.let { setEditorSelection(it.selectionEnd) } }
    fun setSearchQuery(query: String) { _searchQuery.value = query; scheduleSearchResults(query, false) }
    fun setReplaceQuery(query: String) { _replaceQuery.value = query }

    private fun resetSearchState() { _isSearchOpen.value = false; _searchQuery.value = ""; _replaceQuery.value = ""; searchMatchStarts = emptyList(); _searchMatchCount.value = 0; _activeSearchMatch.value = 0 }
    private fun scheduleSearchResults(query: String, preserveSelection: Boolean) {
        searchJob?.cancel()
        if (query.isBlank()) { searchMatchStarts = emptyList(); _searchMatchCount.value = 0; _activeSearchMatch.value = 0; return }
        searchJob = viewModelScope.launch {
            delay(400)
            val text = currentDocumentText()
            val revision = _uiState.value.documentRevision
            val selection = nativeEditor?.let { TextRange(it.selectionStart, it.selectionEnd) } ?: TextRange.Zero
            val matches = withContext(Dispatchers.Default) { EditorSearchEngine.findMatches(text, query) }
            if (_searchQuery.value != query || _uiState.value.documentRevision != revision) return@launch
            applySearchResults(query, matches, selection, preserveSelection)
        }
    }

    private fun applySearchResults(query: String, matches: List<Int>, selection: TextRange, preserveSelection: Boolean) {
        searchMatchStarts = matches
        _searchMatchCount.value = matches.size
        if (matches.isEmpty()) { _activeSearchMatch.value = 0; return }
        if (preserveSelection) {
            _activeSearchMatch.value = matches.indexOfFirst { it == selection.min && it + query.length == selection.max }.coerceAtLeast(0)
            return
        }
        val at = matches.indexOfFirst { it >= selection.start }
        selectSearchMatch(if (at >= 0) at else 0)
    }

    fun nextSearchMatch() {
        if (searchMatchStarts.isNotEmpty()) {
            nativeEditor?.requestFocus()
            selectSearchMatch((_activeSearchMatch.value + 1) % searchMatchStarts.size)
            hideSearchKeyboard()
        }
    }

    fun previousSearchMatch() {
        if (searchMatchStarts.isNotEmpty()) {
            nativeEditor?.requestFocus()
            selectSearchMatch((_activeSearchMatch.value - 1 + searchMatchStarts.size) % searchMatchStarts.size)
            hideSearchKeyboard()
        }
    }

    private fun hideSearchKeyboard() {
        nativeEditor?.let { editor ->
            (appContext.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
                ?.hideSoftInputFromWindow(editor.windowToken, 0)
        }
    }

    private fun selectSearchMatch(index: Int) {
        val start = searchMatchStarts.getOrNull(index) ?: return
        _activeSearchMatch.value = index
        setEditorSelection(start, start + _searchQuery.value.length)
        nativeEditor?.scrollSelectionIntoView(start, start + _searchQuery.value.length)
    }

    private fun setEditorSelection(start: Int, end: Int = start) { nativeEditor?.setSelection(start.coerceIn(0, nativeEditor?.length() ?: 0), end.coerceIn(0, nativeEditor?.length() ?: 0)) }
    private fun currentDocumentText(): String = nativeEditor?.text?.toString().orEmpty()
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
    fun onPauseOrExit() { if (_uiState.value.isDirty) saveCurrentDocumentSilently() }
    private fun writeDocumentSnapshot(state: EditorUiState, resolver: ContentResolver, text: String = state.content.text) {
        if (state.externalDocumentSaveAsOnly) return
        val uri = state.externalDocumentUri?.let(Uri::parse)
        if (uri == null) { if (state.isDirty) { fileManager.writeEditor(text); fileManager.writeEditorTitle(state.title) } }
        else if (state.isDirty) writeExternalDocument(uri, resolver, text)
    }
    private fun writeExternalDocument(uri: Uri, resolver: ContentResolver, text: String) { val output = if (uri.scheme == ContentResolver.SCHEME_FILE) File(uri.path ?: error("File URI has no path")).outputStream() else resolver.openOutputStream(uri, "wt") ?: resolver.openOutputStream(uri) ?: error("Unable to save file"); output.use { it.write(text.toByteArray(Charsets.UTF_8)); it.flush() } }
    fun setReplaceQueryAndKeepSearch(query: String) { setReplaceQuery(query) }
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
        editor.transaction { for (start in matches.asReversed()) replaceText(start, start + query.length, replacement); setSelection(finalStart, finalEnd) }
    }

    fun pasteFromClipboard(context: Context) { val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; val clip = runCatching { clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() }.getOrNull(); if (clip.isNullOrEmpty()) { emitToast(com.clipnest.R.string.clipboard_empty); return }; replaceSelection(clip.replace("\r\n", "\n").replace('\r', '\n')); emitToast(com.clipnest.R.string.pasted) }
    fun copySelectedText(context: Context) { val editor = nativeEditor ?: return; if (editor.selectionStart == editor.selectionEnd) { emitToast(com.clipnest.R.string.select_text_to_copy); return }; val selected = editor.text?.subSequence(editor.selectionStart, editor.selectionEnd).toString(); (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("Editor selection", selected)); emitToast(com.clipnest.R.string.copied) }
    fun cutSelectedText(context: Context) { val editor = nativeEditor ?: return; if (editor.selectionStart == editor.selectionEnd) { emitToast(com.clipnest.R.string.select_text_to_delete); return }; val selected = editor.text?.subSequence(editor.selectionStart, editor.selectionEnd).toString(); (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)?.hideSoftInputFromWindow(editor.windowToken, 0); (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("Editor selection", selected)); replaceSelection(""); emitToast(com.clipnest.R.string.copied) }
    fun moveCursorLeft() { nativeEditor?.let { setEditorSelection(if (it.selectionStart == it.selectionEnd) max(0, it.selectionStart - 1) else min(it.selectionStart, it.selectionEnd)) } }
    fun moveCursorRight() { nativeEditor?.let { setEditorSelection(if (it.selectionStart == it.selectionEnd) min(it.length(), it.selectionEnd + 1) else max(it.selectionStart, it.selectionEnd)) } }
    fun selectAll() { nativeEditor?.let { it.requestFocus(); it.selectAll() } }
    private fun replaceSelection(replacement: String) { nativeEditor?.let { it.replaceText(it.selectionStart, it.selectionEnd, replacement, it.selectionStart + replacement.length, it.selectionStart + replacement.length) } }
    private fun emitToast(message: String) { viewModelScope.launch { _eventFlow.emit(EditorEvent.ShowToast(message)) } }
    private fun emitToast(@StringRes resourceId: Int, vararg args: Any) { emitToast(appContext.getString(resourceId, *args)) }
    companion object { private const val TAG = "XBoard.Editor" }
}

class EditorViewModelFactory(private val fileManager: FileManager, private val settingsDataStore: SettingsDataStore, private val appContext: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T { if (modelClass.isAssignableFrom(EditorViewModel::class.java)) return EditorViewModel(fileManager, settingsDataStore, appContext) as T; throw IllegalArgumentException("Unknown ViewModel class") }
}
