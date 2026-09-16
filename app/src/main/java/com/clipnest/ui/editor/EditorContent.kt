package com.clipnest.ui.editor

import androidx.compose.ui.text.TextRange

/**
 * Lightweight compatibility view for non-editing UI state.
 *
 * Holds two deliberately separate positions:
 * - [selection]: the caret / text selection. Changes only when the user types
 *   or taps/drags to select — never as a side effect of scrolling to read.
 * - [viewportAnchor]: the offset of the first character visible at the top of
 *   the editor viewport. Changes only when the user scrolls. This is what
 *   "where the user left off" should mean when they scrolled away from the
 *   caret to read something else before switching screens.
 *
 * These used to be collapsed into a single [selection] field, which made the
 * editor jump to the caret (often end-of-text) whenever the screen was
 * recreated after the user had scrolled elsewhere without moving the caret.
 * Keeping them separate lets restore logic put the caret back without also
 * forcing the scroll position to follow it.
 */
class EditorContent {
    private var fallbackText: String = ""
    private var fallbackSelection: TextRange = TextRange.Zero
    private var fallbackViewportAnchor: Int = 0

    val text: String get() = fallbackText
    val selection: TextRange get() = fallbackSelection
    val viewportAnchor: Int get() = fallbackViewportAnchor

    /**
     * Sets text + caret only. Viewport anchor is coerced to the new text length
     * if it no longer fits, but is otherwise left as-is — most callers of this
     * overload (mid-session caret bookkeeping, e.g. while typing) have no new
     * scroll information to report.
     */
    fun setFallback(text: String, selection: TextRange) {
        fallbackText = text
        fallbackSelection = selection
        fallbackViewportAnchor = fallbackViewportAnchor.coerceIn(0, text.length)
    }

    /** Sets text, caret, and viewport anchor together, e.g. when leaving the screen. */
    fun setFallback(text: String, selection: TextRange, viewportAnchor: Int) {
        fallbackText = text
        fallbackSelection = selection
        fallbackViewportAnchor = viewportAnchor.coerceIn(0, text.length)
    }

    fun setFallbackSelection(start: Int, end: Int = start) {
        fallbackSelection = TextRange(
            start.coerceIn(0, fallbackText.length),
            end.coerceIn(0, fallbackText.length)
        )
    }
}
