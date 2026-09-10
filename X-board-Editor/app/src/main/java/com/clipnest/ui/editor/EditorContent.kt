package com.clipnest.ui.editor

import androidx.compose.ui.text.TextRange

/** Lightweight compatibility view for non-editing UI state. */
class EditorContent {
    private var fallbackText: String = ""
    private var fallbackSelection: TextRange = TextRange.Zero

    val text: String get() = fallbackText
    val selection: TextRange get() = fallbackSelection

    fun setFallback(text: String, selection: TextRange) {
        fallbackText = text
        fallbackSelection = selection
    }

    fun setFallbackSelection(start: Int, end: Int = start) {
        fallbackSelection = TextRange(
            start.coerceIn(0, fallbackText.length),
            end.coerceIn(0, fallbackText.length)
        )
    }
}
