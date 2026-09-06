package com.clipnest.ui.editor

import android.content.Context
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.view.Gravity
import android.widget.EditText

/**
 * Native Android text surface with lightweight visual highlighting of #tags.
 * 
 * Key improvements:
 * - No highlight on scroll (scroll doesn't change content)
 * - Incremental highlighting: only process the changed range
 * - Does NOT send full text on every keystroke - only position and change info
 * - Supports direct Editable manipulation for paste/delete/format
 * - Supports Vietnamese characters in #tags
 */
class HighlightingEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : EditText(context, attrs, defStyleAttr) {

    var tagColor: Int = currentTextColor
        set(value) {
            if (field == value) return
            field = value
            refreshAllTags()
        }

    /**
     * Callback for when text changes. Instead of sending full text,
     * we send what changed so ViewModel can update efficiently.
     */
    var onTextChange: ((change: TextChange) -> Unit)? = null
    
    var onSelectionChange: ((start: Int, end: Int) -> Unit)? = null

    private var suppressCallbacks = false
    private var isTagHighlightingEnabled = true
    private var hasLoadedContent = false

    init {
        background = null
        gravity = Gravity.TOP or Gravity.START
        isSingleLine = false
        inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        setHorizontallyScrolling(false)
        setTextIsSelectable(true)
        setSelectAllOnFocus(false)
        includeFontPadding = false
        setTypeface(Typeface.MONOSPACE, Typeface.NORMAL)
        overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
        isVerticalScrollBarEnabled = true
        isHorizontalScrollBarEnabled = false
        setPadding(dp(8), dp(8), dp(8), dp(8))

        addTextChangedListener(object : TextWatcher {
            private var changeStart = 0
            private var changeBefore = 0
            private var changeAfter = 0

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                changeStart = start
                changeBefore = count
                changeAfter = after
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (suppressCallbacks || !isTagHighlightingEnabled) return
                
                // Process only the changed range incrementally
                val editable = text ?: return
                processChangedRange(editable, changeStart, changeBefore, changeAfter)
                
                // Notify ViewModel with what changed - NOT the full text
                onTextChange?.invoke(
                    TextChange(
                        start = changeStart,
                        removedLength = changeBefore,
                        addedLength = changeAfter
                    )
                )
            }

            override fun afterTextChanged(s: Editable?) {
                // All done in onTextChanged
            }
        })
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (!suppressCallbacks) {
            onSelectionChange?.invoke(selStart, selEnd)
        }
    }

    /**
     * Process only the range that actually changed.
     * Instead of re-highlighting everything, we only look at the affected area.
     * 
     * @param editable The Editable containing the text
     * @param start The position where change started
     * @param before Length of text removed
     * @param after Length of text added
     */
    private fun processChangedRange(editable: Editable, start: Int, before: Int, after: Int) {
        if (editable.isEmpty()) return

        // Determine the affected range:
        // - Start from the beginning of the word that contains 'start'
        // - Include the word that contains 'start + after' (where insertion ended)
        val searchStart = findWordStart(editable, start)
        val searchEnd = findWordEnd(editable, start + after.coerceAtLeast(0))
        
        // Also need to check for #tag that might start BEFORE the change
        // (e.g., user typed "#" and is about to type more)
        val extendedStart = (searchStart - 1).coerceAtLeast(0)
        val actualStart = if (extendedStart < searchStart) {
            // Check if there's a # at extendedStart
            val charBefore = editable.getOrNull(extendedStart)
            if (charBefore == '#') extendedStart else searchStart
        } else {
            searchStart
        }
        
        // Remove all tag spans in the affected range
        removeTagSpansInRange(editable, actualStart, searchEnd)
        
        // Find #tags in the affected range and apply highlighting
        findTagsInRange(editable, actualStart, searchEnd)
    }

    /**
     * Find the start of the word containing the given position.
     * A word is defined by: letters (including Vietnamese), numbers, and underscores.
     */
    private fun findWordStart(editable: Editable, position: Int): Int {
        if (position <= 0) return 0
        val text = editable.toString()
        var i = position.coerceAtMost(text.length - 1)
        while (i > 0 && isWordChar(text[i])) {
            i--
        }
        // Check if we're in the middle of a #tag
        if (i > 0 && text[i] == '#') {
            // Include the # in the range
            return i
        }
        return if (i < position) i + 1 else i
    }

    /**
     * Find the end of the word containing the given position.
     */
    private fun findWordEnd(editable: Editable, position: Int): Int {
        val text = editable.toString()
        var i = position.coerceAtMost(text.length - 1)
        while (i < text.length && isWordChar(text[i])) {
            i++
        }
        return i
    }

    /**
     * Check if a character is part of a word/tag.
     * Includes: letters (any script), numbers, underscore, hyphen.
     * This properly supports Vietnamese and other Unicode characters.
     */
    private fun isWordChar(char: Char): Boolean {
        return char.isLetterOrDigit() || char == '_' || char == '-'
    }

    /**
     * Find all #tags in the given range and apply foreground color + bold styling.
     * Only looks at the specified range - efficient and incremental.
     */
    private fun findTagsInRange(editable: Editable, start: Int, end: Int) {
        if (start >= end || start >= editable.length) return
        val actualEnd = end.coerceAtMost(editable.length)
        if (start > actualEnd) return
        
        val text = editable.subSequence(start, actualEnd)
        TAG_REGEX.findAll(text).forEach { match ->
            val matchStart = start + match.range.first
            val matchEnd = start + match.range.last + 1
            applyTagSpan(editable, matchStart, matchEnd)
        }
    }

    /**
     * Apply tag styling to a specific range.
     */
    private fun applyTagSpan(editable: Editable, start: Int, end: Int) {
        if (start >= end || start < 0 || end > editable.length) return
        
        // Remove any existing tag spans in this range to avoid duplication
        removeTagSpansInRange(editable, start, end)
        
        editable.setSpan(TagForegroundSpan(tagColor), start, end, TAG_SPAN_FLAGS)
        editable.setSpan(TagStyleSpan(), start, end, TAG_SPAN_FLAGS)
    }

    /**
     * Remove all Tag spans in the given range.
     */
    private fun removeTagSpansInRange(editable: Editable, start: Int, end: Int) {
        if (start >= end || start >= editable.length) return
        val actualEnd = end.coerceAtMost(editable.length)
        if (start > actualEnd) return
        
        editable.getSpans(start, actualEnd, TagForegroundSpan::class.java)
            .forEach { span ->
                val spanStart = editable.getSpanStart(span)
                val spanEnd = editable.getSpanEnd(span)
                if (spanStart < actualEnd && spanEnd > start) {
                    editable.removeSpan(span)
                }
            }
        editable.getSpans(start, actualEnd, TagStyleSpan::class.java)
            .forEach { span ->
                val spanStart = editable.getSpanStart(span)
                val spanEnd = editable.getSpanEnd(span)
                if (spanStart < actualEnd && spanEnd > start) {
                    editable.removeSpan(span)
                }
            }
    }

    /**
     * Refresh all #tag highlights in the entire document.
     * Call this when loading a new document or when tag color changes.
     * This is intentionally a full scan because it's used rarely (load/setting change).
     */
    fun refreshAllTags() {
        val editable = text ?: return
        if (editable.isEmpty()) return
        
        // Remove all existing tag spans
        removeAllTagSpans(editable)
        
        // Find all #tags in the entire document
        val fullText = editable.toString()
        TAG_REGEX.findAll(fullText).forEach { match ->
            val matchStart = match.range.first
            val matchEnd = match.range.last + 1
            editable.setSpan(TagForegroundSpan(tagColor), matchStart, matchEnd, TAG_SPAN_FLAGS)
            editable.setSpan(TagStyleSpan(), matchStart, matchEnd, TAG_SPAN_FLAGS)
        }
    }

    /**
     * Remove ALL tag spans from the document.
     */
    private fun removeAllTagSpans(editable: Editable) {
        editable.getSpans(0, editable.length, TagForegroundSpan::class.java)
            .forEach(editable::removeSpan)
        editable.getSpans(0, editable.length, TagStyleSpan::class.java)
            .forEach(editable::removeSpan)
    }

    /**
     * Enable or disable tag highlighting temporarily.
     * Useful when programmatically changing text.
     */
    fun setTagHighlightingEnabled(enabled: Boolean) {
        isTagHighlightingEnabled = enabled
    }

    /**
     * Get the current text as String.
     * Use sparingly - only when needed (e.g., saving, copying full content).
     */
    fun getFullText(): String = text?.toString().orEmpty()

    /**
     * Get a reference to the Editable for direct manipulation.
     * This allows ViewModel to apply changes directly.
     */
    fun getEditable(): Editable? = text

    /**
     * Apply a change directly to the Editable.
     * This is the primary method that ViewModel should use for paste/delete/format.
     * 
     * @param operation Lambda that performs the edit on the Editable
     * @param affectedStart Start of the range affected by the edit (for tag refresh)
     * @param affectedEnd End of the range affected by the edit (for tag refresh)
     */
    fun applyDirectEdit(operation: (Editable) -> Unit, affectedStart: Int? = null, affectedEnd: Int? = null) {
        val editable = text ?: return
        suppressCallbacks = true
        try {
            operation(editable)
            
            // Refresh tags in the affected range
            val start = affectedStart ?: 0
            val end = affectedEnd ?: editable.length
            if (start < end) {
                // Remove old spans and re-apply in the affected range
                processChangedRange(editable, start, 0, end - start)
            }
        } finally {
            suppressCallbacks = false
            // Notify that content changed - still send TextChange info
            onTextChange?.invoke(
                TextChange(
                    start = affectedStart ?: 0,
                    removedLength = 0,
                    addedLength = (affectedEnd ?: editable.length) - (affectedStart ?: 0)
                )
            )
        }
    }

    /**
     * Set the full text of the editor.
     * Use this when loading a new document, not for incremental edits.
     */
    fun setFullText(text: String, selectionStart: Int, selectionEnd: Int) {
        suppressCallbacks = true
        try {
            setText(text)
            setEditorSelectionInternal(selectionStart, selectionEnd)
            // After loading new text, refresh all tags (full scan - acceptable for load)
            refreshAllTags()
            hasLoadedContent = true
        } finally {
            suppressCallbacks = false
        }
        onTextChange?.invoke(
            TextChange(
                start = 0,
                removedLength = 0,
                addedLength = text.length,
                isFullReplacement = true
            )
        )
    }

    fun setEditorSelectionIfNeeded(selectionStart: Int, selectionEnd: Int) {
        if (selectionStart == this.selectionStart && selectionEnd == this.selectionEnd) return
        suppressCallbacks = true
        try {
            setEditorSelectionInternal(selectionStart, selectionEnd)
        } finally {
            suppressCallbacks = false
        }
    }

    private fun setEditorSelectionInternal(selectionStart: Int, selectionEnd: Int) {
        val length = text?.length ?: 0
        val start = selectionStart.coerceIn(0, length)
        val end = selectionEnd.coerceIn(start, length)
        setSelection(start, end)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG_SPAN_FLAGS = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        private val TAG_REGEX = Regex("(?<!\\S)#[\\p{L}\\p{N}_-]+")
    }
}

/**
 * Data class representing a text change event.
 * This is sent to ViewModel instead of the full text.
 */
data class TextChange(
    val start: Int,
    val removedLength: Int,
    val addedLength: Int,
    val isFullReplacement: Boolean = false
)

private class TagForegroundSpan(color: Int) : ForegroundColorSpan(color)
private class TagStyleSpan : StyleSpan(Typeface.BOLD)