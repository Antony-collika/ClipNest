package com.clipnest.ui.editor

import androidx.compose.ui.text.TextRange

/** Editor-owned selection/caret state, independent from UI event types. */
data class EditorSelection(
    val start: Int,
    val end: Int = start
) {
    val min: Int get() = minOf(start, end)
    val max: Int get() = maxOf(start, end)
    val collapsed: Boolean get() = start == end
    val range: TextRange get() = TextRange(start, end)

    fun normalized(length: Int): EditorSelection {
        return EditorSelection(
            start = start.coerceIn(0, length),
            end = end.coerceIn(0, length)
        )
    }

    companion object {
        fun from(range: TextRange): EditorSelection = EditorSelection(range.start, range.end)
    }
}
