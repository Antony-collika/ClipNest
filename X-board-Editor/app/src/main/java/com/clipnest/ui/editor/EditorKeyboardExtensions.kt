package com.clipnest.ui.editor

/**
 * Keyboard hiding is handled by the native editor during its Compose update pass.
 * Kept as a no-op compatibility hook for screen lifecycle effects.
 */
internal fun EditorViewModel.hideNativeKeyboard() {
    // NativeEditorView.hideKeyboardAndClearFocus() is invoked by EditorScreen.update
    // whenever the editor must relinquish keyboard ownership.
}
