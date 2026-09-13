from pathlib import Path

root=Path('.')

# EditorViewModel.kt
p=root/'app/src/main/java/com/clipnest/ui/editor/EditorViewModel.kt'
s=p.read_text()

s=s.replace('private data class PendingSave(val fileName: String, val format: ExportFormat)\n', '''private data class PendingSave(val fileName: String, val format: ExportFormat)

enum class ExternalExitAction { RETURN_TO_EDITOR, OPEN_REPLACEMENT }

private data class PendingExternalOpen(
    val uri: Uri,
    val openContext: ExternalDocumentOpenContext?,
    val candidates: List<IncomingDocumentUri>
)
''')

s=s.replace('    data object RequestSaveFolder : EditorEvent()\n', '    data object RequestSaveFolder : EditorEvent()\n    data class RequestExternalSaveAs(val suggestedFileName: String, val mimeType: String) : EditorEvent()\n')

s=s.replace('    val showSaveNewFileDialog: Boolean = false,\n', '    val showSaveNewFileDialog: Boolean = false,\n    val showExternalUnsavedChangesDialog: Boolean = false,\n')

needle='    private var pendingSave: PendingSave? = null\n'
repl='''    private var pendingSave: PendingSave? = null
    private var pendingExternalOpen: PendingExternalOpen? = null
    private var pendingExternalExitAction: ExternalExitAction? = null
    private var pendingExternalReturnCallback: (() -> Unit)? = null
'''
s=s.replace(needle,repl)

marker='    fun openExternalDocument(uri: Uri, contentResolver: ContentResolver, openContext: ExternalDocumentOpenContext? = null, candidates: List<IncomingDocumentUri> = listOf(IncomingDocumentUri(uri, openContext?.source ?: com.clipnest.IncomingUriSource.FILE_PICKER))) {\n'
insert='''    private fun hasExternalSession(): Boolean = _uiState.value.externalDocumentUri != null

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
        val text = currentDocumentText()
        val password = currentDocumentPassword
        viewModelScope.launch(Dispatchers.IO) {
            val saved = runCatching {
                val uri = state.externalDocumentUri?.let(Uri::parse) ?: error("Missing external document URI")
                if (state.externalDocumentSaveAsOnly) error("Merged external documents must use Save as")
                writeExternalDocument(uri, contentResolver, text, state.externalDocumentEncrypted, password)
            }.isSuccess
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
        val state = _uiState.value
        val extension = if (state.externalDocumentEncrypted) ".cne" else {
            val name = state.documentName
            if (name.contains('.')) "." + name.substringAfterLast('.') else ".txt"
        }
        val base = state.documentName.substringBeforeLast('.', state.documentName).ifBlank { "ClipNest_Document" }
        val suggested = if (base.endsWith(extension, ignoreCase = true)) base else base + extension
        val mime = if (state.externalDocumentEncrypted) "application/octet-stream" else "text/plain"
        _uiState.value = _uiState.value.copy(showExternalUnsavedChangesDialog = false)
        viewModelScope.launch { _eventFlow.emit(EditorEvent.RequestExternalSaveAs(suggested, mime)) }
    }

    fun completeExternalSaveAs(uri: Uri?, contentResolver: ContentResolver) {
        if (uri == null) return
        val action = pendingExternalExitAction ?: return
        val state = _uiState.value
        val text = currentDocumentText()
        val password = currentDocumentPassword
        viewModelScope.launch(Dispatchers.IO) {
            val saved = runCatching { writeExternalDocument(uri, contentResolver, text, state.externalDocumentEncrypted, password) }.isSuccess
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
            ExternalExitAction.RETURN_TO_EDITOR -> callback?.invoke()
            ExternalExitAction.OPEN_REPLACEMENT -> open?.let { openExternalDocument(it.uri, contentResolver, it.openContext, it.candidates) }
        }
    }

    private fun finishExternalSession(contentResolver: ContentResolver) {
        autoSaveJob?.cancel()
        searchJob?.cancel()
        resetSearchState()
        currentDocumentPassword = null
        _pendingEncryptedOpen.value = null
        nativeEditor?.setEditorText("", 0)
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

'''
s=s.replace(marker,insert+marker)

old='''    fun openExternalDocument(uri: Uri, contentResolver: ContentResolver, openContext: ExternalDocumentOpenContext? = null, candidates: List<IncomingDocumentUri> = listOf(IncomingDocumentUri(uri, openContext?.source ?: com.clipnest.IncomingUriSource.FILE_PICKER))) {
        val previousState = _uiState.value
'''
new='''    fun openExternalDocument(uri: Uri, contentResolver: ContentResolver, openContext: ExternalDocumentOpenContext? = null, candidates: List<IncomingDocumentUri> = listOf(IncomingDocumentUri(uri, openContext?.source ?: com.clipnest.IncomingUriSource.FILE_PICKER))) {
        if (hasExternalSession()) {
            if (_uiState.value.isDirty) {
                pendingExternalOpen = PendingExternalOpen(uri, openContext, candidates)
                requestExternalExit(ExternalExitAction.OPEN_REPLACEMENT)
                return
            }
            finishExternalSession(contentResolver)
        }
        val previousState = _uiState.value
'''
s=s.replace(old,new)

s=s.replace('                    viewModelScope.launch(Dispatchers.IO) { runCatching { writeDocumentSnapshot(previousState, contentResolver, previousText, previousPassword) } }\n','')

old='''    fun returnToInternalEditor(contentResolver: ContentResolver, onComplete: () -> Unit = {}) {
        val state = _uiState.value
        val text = currentDocumentText()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { writeDocumentSnapshot(state, contentResolver, text); fileManager.readEditor() }.onSuccess { internal -> withContext(Dispatchers.Main.immediate) { nativeEditor?.setEditorText(internal, internal.length); _uiState.value.content.setFallback(internal, TextRange(internal.length)); editorDocumentGeneration++; currentDocumentPassword = null; _uiState.value = _uiState.value.copy(documentRevision = _uiState.value.documentRevision + 1, isDirty = false, documentName = internalDocumentName(), externalDocumentUri = null, externalDocumentSaveAsOnly = false, externalDocumentFileCount = 0, externalDocumentEncrypted = false); onComplete() } }
        }
    }
'''
new='''    fun returnToInternalEditor(contentResolver: ContentResolver, onComplete: () -> Unit = {}) {
        if (!hasExternalSession()) { onComplete(); return }
        if (_uiState.value.isDirty) {
            pendingExternalReturnCallback = onComplete
            requestExternalExit(ExternalExitAction.RETURN_TO_EDITOR)
            return
        }
        finishExternalSession(contentResolver)
        viewModelScope.launch(Dispatchers.IO) {
            val internal = fileManager.readEditor()
            withContext(Dispatchers.Main.immediate) {
                nativeEditor?.setEditorText(internal, internal.length)
                _uiState.value.content.setFallback(internal, TextRange(internal.length))
                editorDocumentGeneration++
                _uiState.value = _uiState.value.copy(documentRevision = _uiState.value.documentRevision + 1, isDirty = false, documentName = internalDocumentName())
                onComplete()
            }
        }
    }
'''
if old not in s: raise SystemExit('return method pattern not found')
s=s.replace(old,new)

s=s.replace('fun onDocumentTextChanged() { editorLoaded = true; _uiState.value = _uiState.value.copy(documentRevision = _uiState.value.documentRevision + 1, isDirty = true); if (_searchQuery.value.isNotBlank()) scheduleSearchResults(_searchQuery.value, true); scheduleDebouncedAutoSave() }', 'fun onDocumentTextChanged() { editorLoaded = true; _uiState.value = _uiState.value.copy(documentRevision = _uiState.value.documentRevision + 1, isDirty = true); if (_searchQuery.value.isNotBlank()) scheduleSearchResults(_searchQuery.value, true); if (!hasExternalSession()) scheduleDebouncedAutoSave() }')
s=s.replace('fun onTitleChange(newTitle: String) { editorLoaded = true; _uiState.value = _uiState.value.copy(title = newTitle, isDirty = true); scheduleDebouncedAutoSave() }', 'fun onTitleChange(newTitle: String) { editorLoaded = true; _uiState.value = _uiState.value.copy(title = newTitle, isDirty = true); if (!hasExternalSession()) scheduleDebouncedAutoSave() }')
s=s.replace('    fun onPauseOrExit() { if (_uiState.value.isDirty) saveCurrentDocumentSilently() }', '    fun onPauseOrExit() { /* External document sessions intentionally do not autosave on lifecycle changes. */ if (!hasExternalSession() && _uiState.value.isDirty) saveCurrentDocumentSilently() }')
s=s.replace('writeExternalDocument(uri, resolver, text, encrypted)', 'writeExternalDocument(uri, resolver, text, encrypted, currentDocumentPassword)')
s=s.replace('    fun saveCurrentDocumentSilently() {\n        if (!_uiState.value.isDirty) return', '    fun saveCurrentDocumentSilently() {\n        if (hasExternalSession()) return\n        if (!_uiState.value.isDirty) return')
p.write_text(s)

# EditorScreen
p=root/'app/src/main/java/com/clipnest/ui/editor/EditorScreen.kt'
s=p.read_text()
s=s.replace('''    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) viewModel.onPauseOrExit()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onPauseOrExit()
        }
    }
''','')
s=s.replace('''                is EditorEvent.ShowToast -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                is EditorEvent.RequestSaveFolder -> onRequestSaveFolder()
''','''                is EditorEvent.ShowToast -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                is EditorEvent.RequestSaveFolder -> onRequestSaveFolder()
                is EditorEvent.RequestExternalSaveAs -> onRequestExternalSaveAs(event.suggestedFileName, event.mimeType)
''')
s=s.replace('''    onRequestSaveFolder: () -> Unit,
    onRequestOpenFile: () -> Unit,
''','''    onRequestSaveFolder: () -> Unit,
    onRequestOpenFile: () -> Unit,
    onRequestExternalSaveAs: (String, String) -> Unit,
''')
needle='''    if (uiState.showSaveNewFileDialog) {
        SaveNewFileDialog(
            defaultFolderUri = uiState.defaultSaveFolderUri,
            initialFileName = uiState.title.ifBlank { uiState.documentName },
            onChooseFolder = onRequestSaveFolder,
            onDismiss = viewModel::dismissSaveNewFileDialog,
            onConfirm = { fileName, format ->
                viewModel.confirmSaveToNewFile(fileName, format, context.contentResolver)
            }
        )
    }
'''
insert=needle+'''
    if (uiState.showExternalUnsavedChangesDialog) {
        AlertDialog(
            onDismissRequest = viewModel::cancelExternalExit,
            title = { Text("Unsaved changes") },
            text = { Text("This external file has unsaved changes. What would you like to do?") },
            confirmButton = {
                TextButton(onClick = { viewModel.chooseExternalSave(context.contentResolver) }) { Text("Save file") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = viewModel::chooseExternalSaveAs) { Text("Save as") }
                    TextButton(onClick = { viewModel.chooseExternalNoSave(context.contentResolver) }) { Text("No save") }
                    TextButton(onClick = viewModel::cancelExternalExit) { Text("Cancel") }
                }
            }
        )
    }
'''
if needle not in s: raise SystemExit('save dialog pattern not found')
s=s.replace(needle,insert)
s=s.replace('import androidx.lifecycle.Lifecycle\n','').replace('import androidx.lifecycle.LifecycleEventObserver\n','').replace('import androidx.lifecycle.compose.LocalLifecycleOwner\n','')
s=s.replace('    val lifecycleOwner = LocalLifecycleOwner.current\n','')
p.write_text(s)

# MainActivity
p=root/'app/src/main/java/com/clipnest/MainActivity.kt'
s=p.read_text()
needle='''    private val backupFileLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
'''
insert='''    private var pendingExternalSaveAs: ((Uri?) -> Unit)? = null
    private val externalSaveAsLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val callback = pendingExternalSaveAs
        pendingExternalSaveAs = null
        callback?.invoke(uri)
    }
    private val backupFileLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
'''
if needle not in s: raise SystemExit('backup launcher marker not found')
s=s.replace(needle,insert)
s=s.replace('''    onRequestOpenFile: ((Uri) -> Unit) -> Unit,
    onShareText: (String, String) -> Unit,
''','''    onRequestOpenFile: ((Uri) -> Unit) -> Unit,
    onRequestExternalSaveAs: (String, String, (Uri?) -> Unit) -> Unit,
    onShareText: (String, String) -> Unit,
''')
s=s.replace('''                        onRequestOpenFile = ::requestOpenFile,
                        onShareText = ::shareTextExternally,
''','''                        onRequestOpenFile = ::requestOpenFile,
                        onRequestExternalSaveAs = ::requestExternalSaveAs,
                        onShareText = ::shareTextExternally,
''')
needle='''    private fun requestBackupFile() { backupPasswordRequest.value = BackupPasswordAction.EXPORT }
'''
insert='''    private fun requestExternalSaveAs(suggestedFileName: String, mimeType: String, callback: (Uri?) -> Unit) {
        pendingExternalSaveAs = callback
        externalSaveAsLauncher.launch(suggestedFileName)
    }
    private fun requestBackupFile() { backupPasswordRequest.value = BackupPasswordAction.EXPORT }
'''
s=s.replace(needle,insert)
s=s.replace('''                            onRequestSaveFolder = { onRequestFolder { uri -> editorViewModel.setDefaultSaveFolder(uri, context.contentResolver) } },
                            onRequestOpenFile = ::openExternalFile,
''','''                            onRequestSaveFolder = { onRequestFolder { uri -> editorViewModel.setDefaultSaveFolder(uri, context.contentResolver) } },
                            onRequestOpenFile = ::openExternalFile,
                            onRequestExternalSaveAs = { name, mime -> onRequestExternalSaveAs(name, mime) { uri -> editorViewModel.completeExternalSaveAs(uri, context.contentResolver) } },
''')
p.write_text(s)
