package com.clipnest.ui.editor

/**
 * Changes only when the editor switches between documents, so native text
 * undo history can be discarded without clearing it on every text edit.
 */
internal val EditorViewModel.undoHistoryResetKey: Long
    get() = uiState.value.externalDocumentUri?.hashCode()?.toLong()?.plus(1L) ?: Long.MIN_VALUE
