package com.clipnest.ui.editor

import android.content.Context
import android.graphics.Typeface
import android.text.Editable
import android.text.Spannable
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.view.Gravity
import android.widget.EditText

/**
 * Native Android editor used as the text-editing surface inside the Compose UI.
 * The underlying text stays plain text; #tag recognition is only visual spans.
 */
class HighlightingEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : EditText(context, attrs, defStyleAttr) {

    var tagColor: Int = currentTextColor
        set(value) {
            field = value
            applyTagHighlighting()
        }

    var onEditorTextChanged: ((text: String, selectionStart: Int, selectionEnd: Int) -> Unit)? = null
    var onEditorSelectionChanged: ((selectionStart: Int, selectionEnd: Int) -> Unit)? = null

    private var suppressCallbacks = false

    init {
        setBackground(null)
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
        if (!suppressCallbacks) {
            onEditorSelectionChanged?.invoke(selStart, selEnd)
        }
    }

    fun setEditorText(text: String, selectionStart: Int, selectionEnd: Int) {
        if (this.text?.toString() == text) {
            setEditorSelection(selectionStart, selectionEnd)
            return
        }

        suppressCallbacks = true
        try {
            setText(text)
            setEditorSelection(selectionStart, selectionEnd)
            applyTagHighlighting()
        } finally {
            suppressCallbacks = false
        }
    }

    fun setEditorSelection(selectionStart: Int, selectionEnd: Int) {
        val safeStart = selectionStart.coerceIn(0, text?.length ?: 0)
        val safeEnd = selectionEnd.coerceIn(safeStart, text?.length ?: 0)
        if (selectionStart == this.selectionStart && selectionEnd == this.selectionEnd) return
        suppressCallbacks = true
        try {
            setSelection(safeStart, safeEnd)
        } finally {
            suppressCallbacks = false
        }
    }

    fun setSelectionCallbackEnabled(enabled: Boolean) {
        suppressCallbacks = !enabled
    }

    private fun applyTagHighlighting(editable: Editable? = text) {
        if (editable == null) return

        val existingColorSpans = editable.getSpans(0, editable.length, ForegroundColorSpan::class.java)
            .filter { editable.getSpanFlags(it) and TAG_SPAN_FLAG != 0 }
        val existingStyleSpans = editable.getSpans(0, editable.length, StyleSpan::class.java)
            .filter { editable.getSpanFlags(it) and TAG_SPAN_FLAG != 0 }
        existingColorSpans.forEach(editable::removeSpan)
        existingStyleSpans.forEach(editable::removeSpan)

        val regex = TAG_REGEX
        regex.findAll(editable).forEach { match ->
            editable.setSpan(
                ForegroundColorSpan(tagColor),
                match.range.first,
                match.range.last + 1,
                TAG_SPAN_FLAG
            )
            editable.setSpan(
                StyleSpan(Typeface.BOLD),
                match.range.first,
                match.range.last + 1,
                TAG_SPAN_FLAG
            )
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val TAG_SPAN_FLAG = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE or 0x100
        private val TAG_REGEX = Regex("(?<!\\S)#[\\p{L}\\p{N}_-]+")
    }
}

private fun Float.roundToInt(): Int = kotlin.math.round(this).toInt()
