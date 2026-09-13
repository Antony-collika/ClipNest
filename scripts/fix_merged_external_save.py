from pathlib import Path
p = Path('app/src/main/java/com/clipnest/ui/editor/EditorViewModel.kt')
s = p.read_text()
old = '''    fun chooseExternalSave(contentResolver: ContentResolver) {
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
'''
new = '''    fun chooseExternalSave(contentResolver: ContentResolver) {
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
        val password = currentDocumentPassword
        viewModelScope.launch(Dispatchers.IO) {
            val saved = runCatching {
                val uri = state.externalDocumentUri?.let(Uri::parse) ?: error("Missing external document URI")
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
'''
assert old in s
p.write_text(s.replace(old, new))
