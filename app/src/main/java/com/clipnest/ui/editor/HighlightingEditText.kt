package com.clipnest.ui.editor

import android.content.Context
import android.graphics.Typeface
import android.text.Editable
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
            applyTagHighlighting()
        }

    var onEditorTextChanged: ((text: String, selectionStart: Int, selectionEnd: Int) -> Unit)? = null
    var onEditorSelectionChanged: ((selectionStart: Int, selectionEnd: Int) -> Unit)? = null

    private var suppressCallbacks = false

    init {
        background = null
        gravity = Gravity.TOP or Gravity.START
        isSingleLine = false
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
                applyTagHighlighting(s)
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

    fun setEditorText(text: String, selectionStart: Int, selectionEnd: Int) {
        suppressCallbacks = true
        try {
            if (this.text?.toString() != text) setText(text)
            setEditorSelectionInternal(selectionStart, selectionEnd)
            applyTagHighlighting()
        } finally {
            suppressCallbacks = false
        }
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

    private fun applyTagHighlighting(editable: Editable? = text) {
        if (editable == null) return

        editable.getSpans(0, editable.length, TagForegroundSpan::class.java)
            .forEach(editable::removeSpan)
        editable.getSpans(0, editable.length, TagStyleSpan::class.java)
            .forEach(editable::removeSpan)

        TAG_REGEX.findAll(editable).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            editable.setSpan(TagForegroundSpan(tagColor), start, end, TAG_SPAN_FLAGS)
            editable.setSpan(TagStyleSpan(), start, end, TAG_SPAN_FLAGS)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG_SPAN_FLAGS = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        private val TAG_REGEX = Regex("(?<!\\S)#[\\p{L}\\p{N}_-]+")
    }
}

private class TagForegroundSpan(color: Int) : ForegroundColorSpan(color)
private class TagStyleSpan : StyleSpan(Typeface.BOLD)
