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
 * Native Android text surface embedded in the Compose editor.
 * The document remains plain text; #tag recognition is visual only.
 *
 * Highlighting deliberately follows a lightweight, editor-first model:
 * only the visible text region is re-highlighted, and updates are debounced
 * so typing and scrolling stay on the native EditText path.
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
            clearAllTagSpans()
            scheduleVisibleTagHighlighting(0L)
        }

    var onEditorTextChanged: ((text: String, selectionStart: Int, selectionEnd: Int) -> Unit)? = null
    var onEditorSelectionChanged: ((selectionStart: Int, selectionEnd: Int) -> Unit)? = null

    private var suppressCallbacks = false
    private var highlightRunnable: Runnable? = null

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
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                if (suppressCallbacks) return
                scheduleVisibleTagHighlighting()
                onEditorTextChanged?.invoke(
                    s?.toString().orEmpty(),
                    selectionStart.coerceAtLeast(0),
                    selectionEnd.coerceAtLeast(0)
                )
            }
        })
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (!suppressCallbacks) onEditorSelectionChanged?.invoke(selStart, selEnd)
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (!suppressCallbacks && t != oldt) {
            scheduleVisibleTagHighlighting()
        }
    }

    override fun onDetachedFromWindow() {
        highlightRunnable?.let(::removeCallbacks)
        highlightRunnable = null
        super.onDetachedFromWindow()
    }

    fun setEditorText(text: String, selectionStart: Int, selectionEnd: Int) {
        suppressCallbacks = true
        try {
            if (this.text?.toString() != text) setText(text)
            setEditorSelectionInternal(selectionStart, selectionEnd)
            clearAllTagSpans()
        } finally {
            suppressCallbacks = false
        }
        scheduleVisibleTagHighlighting(0L)
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

    private fun scheduleVisibleTagHighlighting(delayMs: Long = TAG_HIGHLIGHT_DEBOUNCE_MS) {
        highlightRunnable?.let(::removeCallbacks)
        val runnable = Runnable {
            highlightRunnable = null
            highlightVisibleTags()
        }
        highlightRunnable = runnable
        postDelayed(runnable, delayMs)
    }

    private fun highlightVisibleTags() {
        val editable = text ?: return
        if (editable.isEmpty()) return

        val layout = layout ?: run {
            scheduleVisibleTagHighlighting(0L)
            return
        }

        val firstLine = layout.getLineForVertical(scrollY.coerceAtLeast(0))
        val lastLine = layout.getLineForVertical(
            (scrollY + height).coerceAtLeast(scrollY)
        )
        val startLine = (firstLine - TAG_HIGHLIGHT_LINE_PADDING).coerceAtLeast(0)
        val endLine = (lastLine + TAG_HIGHLIGHT_LINE_PADDING).coerceAtMost(layout.lineCount - 1)
        val start = layout.getLineStart(startLine).coerceIn(0, editable.length)
        val end = layout.getLineEnd(endLine).coerceIn(start, editable.length)

        clearTagSpansInRange(editable, start, end)
        val visibleText = editable.subSequence(start, end)
        TAG_REGEX.findAll(visibleText).forEach { match ->
            val matchStart = start + match.range.first
            val matchEnd = start + match.range.last + 1
            editable.setSpan(TagForegroundSpan(tagColor), matchStart, matchEnd, TAG_SPAN_FLAGS)
            editable.setSpan(TagStyleSpan(), matchStart, matchEnd, TAG_SPAN_FLAGS)
        }
    }

    private fun clearAllTagSpans() {
        val editable = text ?: return
        editable.getSpans(0, editable.length, TagForegroundSpan::class.java)
            .forEach(editable::removeSpan)
        editable.getSpans(0, editable.length, TagStyleSpan::class.java)
            .forEach(editable::removeSpan)
    }

    private fun clearTagSpansInRange(editable: Editable, start: Int, end: Int) {
        editable.getSpans(start, end, TagForegroundSpan::class.java)
            .forEach { span ->
                val spanStart = editable.getSpanStart(span)
                val spanEnd = editable.getSpanEnd(span)
                if (spanStart < end && spanEnd > start) editable.removeSpan(span)
            }
        editable.getSpans(start, end, TagStyleSpan::class.java)
            .forEach { span ->
                val spanStart = editable.getSpanStart(span)
                val spanEnd = editable.getSpanEnd(span)
                if (spanStart < end && spanEnd > start) editable.removeSpan(span)
            }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG_SPAN_FLAGS = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        private const val TAG_HIGHLIGHT_DEBOUNCE_MS = 90L
        private const val TAG_HIGHLIGHT_LINE_PADDING = 8
        private val TAG_REGEX = Regex("(?<!\\S)#[\\p{L}\\p{N}_-]+")
    }
}

private class TagForegroundSpan(color: Int) : ForegroundColorSpan(color)
private class TagStyleSpan : StyleSpan(Typeface.BOLD)
