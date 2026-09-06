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
 * The EditText owns the live editing buffer. Tag highlighting is incremental
 * and deferred so typing, deletion and scrolling stay on the native input path.
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

    var onTextChange: ((change: TextChange) -> Unit)? = null
    var onSelectionChange: ((start: Int, end: Int) -> Unit)? = null

    private var suppressCallbacks = false
    private var isTagHighlightingEnabled = true
    private var hasLoadedContent = false
    private var highlightRunnable: Runnable? = null
    private var pendingHighlightStart = 0
    private var pendingHighlightEnd = 0

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
                if (suppressCallbacks) return
                onTextChange?.invoke(
                    TextChange(
                        start = start,
                        removedLength = before,
                        addedLength = count
                    )
                )
            }

            override fun afterTextChanged(s: Editable?) {
                if (suppressCallbacks || !isTagHighlightingEnabled || s.isNullOrEmpty()) return
                val start = changeStart.coerceIn(0, s.length)
                val end = (start + changeAfter).coerceIn(start, s.length)
                scheduleChangedRangeHighlight(s, start, end)
            }
        })
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (!suppressCallbacks) {
            onSelectionChange?.invoke(selStart, selEnd)
        }
    }

    override fun onDetachedFromWindow() {
        highlightRunnable?.let(::removeCallbacks)
        highlightRunnable = null
        super.onDetachedFromWindow()
    }

    fun isContentInitialized(): Boolean = hasLoadedContent

    private fun scheduleChangedRangeHighlight(editable: Editable, start: Int, end: Int) {
        pendingHighlightStart = (start - 1).coerceAtLeast(0)
        pendingHighlightEnd = (end + 1).coerceAtMost(editable.length)
        highlightRunnable?.let(::removeCallbacks)
        val runnable = Runnable {
            highlightRunnable = null
            val current = text ?: return@Runnable
            val safeStart = pendingHighlightStart.coerceIn(0, current.length)
            val safeEnd = pendingHighlightEnd.coerceIn(safeStart, current.length)
            processChangedRange(current, safeStart, safeEnd)
        }
        highlightRunnable = runnable
        postDelayed(runnable, TAG_HIGHLIGHT_DEBOUNCE_MS)
    }

    private fun processChangedRange(editable: Editable, start: Int, end: Int) {
        if (editable.isEmpty() || start >= end) return

        val searchStart = findWordStart(editable, start)
        val searchEnd = findWordEnd(editable, end)
        val actualStart = (searchStart - 1).coerceAtLeast(0)
        val actualEnd = (searchEnd + 1).coerceAtMost(editable.length)

        removeTagSpansInRange(editable, actualStart, actualEnd)
        findTagsInRange(editable, actualStart, actualEnd)
    }

    private fun findWordStart(editable: Editable, position: Int): Int {
        var i = position.coerceIn(0, editable.length)
        while (i > 0 && isWordChar(editable[i - 1])) i--
        if (i > 0 && editable[i - 1] == '#') i--
        return i
    }

    private fun findWordEnd(editable: Editable, position: Int): Int {
        var i = position.coerceIn(0, editable.length)
        while (i < editable.length && isWordChar(editable[i])) i++
        return i
    }

    private fun isWordChar(char: Char): Boolean {
        return char.isLetterOrDigit() || char == '_' || char == '-'
    }

    private fun findTagsInRange(editable: Editable, start: Int, end: Int) {
        if (start >= end || start >= editable.length) return
        val actualEnd = end.coerceAtMost(editable.length)
        val text = editable.subSequence(start, actualEnd)
        TAG_REGEX.findAll(text).forEach { match ->
            val matchStart = start + match.range.first
            val matchEnd = start + match.range.last + 1
            applyTagSpan(editable, matchStart, matchEnd)
        }
    }

    private fun applyTagSpan(editable: Editable, start: Int, end: Int) {
        if (start >= end || start < 0 || end > editable.length) return
        removeTagSpansInRange(editable, start, end)
        editable.setSpan(TagForegroundSpan(tagColor), start, end, TAG_SPAN_FLAGS)
        editable.setSpan(TagStyleSpan(), start, end, TAG_SPAN_FLAGS)
    }

    private fun removeTagSpansInRange(editable: Editable, start: Int, end: Int) {
        if (start >= end || start >= editable.length) return
        val actualEnd = end.coerceAtMost(editable.length)
        editable.getSpans(start, actualEnd, TagForegroundSpan::class.java)
            .forEach { span ->
                val spanStart = editable.getSpanStart(span)
                val spanEnd = editable.getSpanEnd(span)
                if (spanStart < actualEnd && spanEnd > start) editable.removeSpan(span)
            }
        editable.getSpans(start, actualEnd, TagStyleSpan::class.java)
            .forEach { span ->
                val spanStart = editable.getSpanStart(span)
                val spanEnd = editable.getSpanEnd(span)
                if (spanStart < actualEnd && spanEnd > start) editable.removeSpan(span)
            }
    }

    fun refreshAllTags() {
        val editable = text ?: return
        if (editable.isEmpty()) return
        removeAllTagSpans(editable)
        val fullText = editable.toString()
        TAG_REGEX.findAll(fullText).forEach { match ->
            val matchStart = match.range.first
            val matchEnd = match.range.last + 1
            editable.setSpan(TagForegroundSpan(tagColor), matchStart, matchEnd, TAG_SPAN_FLAGS)
            editable.setSpan(TagStyleSpan(), matchStart, matchEnd, TAG_SPAN_FLAGS)
        }
    }

    private fun removeAllTagSpans(editable: Editable) {
        editable.getSpans(0, editable.length, TagForegroundSpan::class.java)
            .forEach(editable::removeSpan)
        editable.getSpans(0, editable.length, TagStyleSpan::class.java)
            .forEach(editable::removeSpan)
    }

    fun setTagHighlightingEnabled(enabled: Boolean) {
        isTagHighlightingEnabled = enabled
    }

    fun getFullText(): String = text?.toString().orEmpty()

    fun getEditable(): Editable? = text

    fun applyDirectEdit(
        operation: (Editable) -> Unit,
        affectedStart: Int? = null,
        affectedEnd: Int? = null
    ) {
        val editable = text ?: return
        suppressCallbacks = true
        try {
            operation(editable)
            val start = affectedStart ?: 0
            val end = affectedEnd ?: editable.length
            if (start < end) {
                processChangedRange(editable, start, end)
            } else {
                val safeStart = (start - 1).coerceAtLeast(0)
                val safeEnd = (start + 1).coerceAtMost(editable.length)
                if (safeStart < safeEnd) processChangedRange(editable, safeStart, safeEnd)
            }
        } finally {
            suppressCallbacks = false
        }
    }

    fun setFullText(text: String, selectionStart: Int, selectionEnd: Int) {
        suppressCallbacks = true
        try {
            setText(text)
            setEditorSelectionInternal(selectionStart, selectionEnd)
            removeAllTagSpans(this.text ?: return)
            hasLoadedContent = true
        } finally {
            suppressCallbacks = false
        }
        refreshAllTags()
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
        private const val TAG_HIGHLIGHT_DEBOUNCE_MS = 90L
        private val TAG_REGEX = Regex("(?<!\\S)#[\\p{L}\\p{N}_-]+")
    }
}

data class TextChange(
    val start: Int,
    val removedLength: Int,
    val addedLength: Int,
    val isFullReplacement: Boolean = false
)

private class TagForegroundSpan(color: Int) : ForegroundColorSpan(color)
private class TagStyleSpan : StyleSpan(Typeface.BOLD)
