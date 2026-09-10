package com.clipnest.ui.editor

import android.content.Context
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.OverScroller
import androidx.appcompat.widget.AppCompatEditText
import com.clipnest.data.local.EditorTextSize

/** Native text editing surface; the Android Editable is the live editor buffer. */
class NativeEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {
    private data class EditOperation(val start: Int, val removed: String, val inserted: String, val beforeSelectionStart: Int, val beforeSelectionEnd: Int, val afterSelectionStart: Int, val afterSelectionEnd: Int)
    private data class PendingChange(val start: Int, val removed: String, val originalLength: Int, val selectionStart: Int, val selectionEnd: Int)

    private var internalMutation = false
    private var transactionDepth = 0
    private var transactionBeforeText: String? = null
    private var transactionBeforeSelectionStart = 0
    private var transactionBeforeSelectionEnd = 0
    private var textChangeListener: ((NativeEditorView) -> Unit)? = null
    private val undoStack = ArrayDeque<EditOperation>()
    private val redoStack = ArrayDeque<EditOperation>()
    private var pendingBefore: PendingChange? = null

    private val flingScroller = OverScroller(context)
    private var velocityTracker: VelocityTracker? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minimumFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private val maximumFlingVelocity = ViewConfiguration.get(context).scaledMaximumFlingVelocity
    private val overscrollLimitPx = (150 * resources.displayMetrics.density).toInt()
    private var draggingScroll = false
    private var lastTouchY = 0f
    private var downTouchX = 0f
    private var downTouchY = 0f
    private var scrollRemainderY = 0f

    init {
        setSingleLine(false)
        gravity = Gravity.TOP or Gravity.START
        maxLines = Int.MAX_VALUE
        minLines = 1
        isVerticalScrollBarEnabled = true
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
        setPadding(dp(12), dp(8), dp(12), dp(8))
        setHorizontallyScrolling(false)
        inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
        setTextIsSelectable(true)
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (!internalMutation) pendingBefore = PendingChange(start, s?.subSequence(start, start + count)?.toString().orEmpty(), s?.length ?: length(), selectionStart, selectionEnd)
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                if (internalMutation) return
                val before = pendingBefore ?: return
                val editable = s ?: return
                val insertedLength = editable.length - before.originalLength + before.removed.length
                val inserted = if (insertedLength > 0) editable.subSequence(before.start, (before.start + insertedLength).coerceAtMost(editable.length)).toString() else ""
                if (transactionDepth == 0) {
                    undoStack.addLast(EditOperation(before.start, before.removed, inserted, before.selectionStart, before.selectionEnd, selectionStart, selectionEnd))
                    redoStack.clear()
                    trimHistory()
                    textChangeListener?.invoke(this@NativeEditorView)
                }
                pendingBefore = null
            }
        })
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                flingScroller.abortAnimation()
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)
                draggingScroll = false
                scrollRemainderY = 0f
                downTouchX = event.x
                downTouchY = event.y
                lastTouchY = event.y
                return super.onTouchEvent(event)
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val dyFromDown = event.y - downTouchY
                val dxFromDown = event.x - downTouchX
                val verticalDominance = kotlin.math.abs(dyFromDown) > kotlin.math.abs(dxFromDown) * 0.75f
                if (!draggingScroll && kotlin.math.abs(dyFromDown) > touchSlop && verticalDominance) {
                    draggingScroll = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    val cancel = MotionEvent.obtain(event)
                    cancel.action = MotionEvent.ACTION_CANCEL
                    super.onTouchEvent(cancel)
                    cancel.recycle()
                }
                if (draggingScroll) {
                    val deltaY = event.y - lastTouchY
                    if (deltaY != 0f) {
                        scrollRemainderY += -deltaY
                        val integerDelta = scrollRemainderY.toInt()
                        scrollRemainderY -= integerDelta
                        if (integerDelta != 0) scrollForDrag(scrollY + integerDelta)
                    }
                    lastTouchY = event.y
                    return true
                }
                return super.onTouchEvent(event)
            }
            MotionEvent.ACTION_UP -> {
                velocityTracker?.addMovement(event)
                if (draggingScroll) {
                    velocityTracker?.computeCurrentVelocity(1000, maximumFlingVelocity.toFloat())
                    val velocityY = velocityTracker?.yVelocity ?: 0f
                    velocityTracker?.recycle()
                    velocityTracker = null
                    draggingScroll = false
                    scrollRemainderY = 0f
                    parent?.requestDisallowInterceptTouchEvent(false)
                    val maxScrollY = maxScrollY()
                    if (scrollY < 0 || scrollY > maxScrollY) {
                        springBackToBounds(maxScrollY)
                    } else {
                        val scrollVelocity = -velocityY
                        if (kotlin.math.abs(scrollVelocity) >= minimumFlingVelocity && maxScrollY > 0 &&
                            ((scrollVelocity > 0f && scrollY < maxScrollY) || (scrollVelocity < 0f && scrollY > 0))) {
                            flingScroller.fling(scrollX, scrollY, 0, scrollVelocity.toInt(), 0, 0, 0, maxScrollY)
                            postInvalidateOnAnimation()
                        } else {
                            scrollToClamped(scrollY)
                        }
                    }
                    return true
                }
                velocityTracker?.recycle()
                velocityTracker = null
                return super.onTouchEvent(event)
            }
            MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.addMovement(event)
                velocityTracker?.recycle()
                velocityTracker = null
                draggingScroll = false
                scrollRemainderY = 0f
                parent?.requestDisallowInterceptTouchEvent(false)
                val maxScrollY = maxScrollY()
                if (scrollY < 0 || scrollY > maxScrollY) {
                    springBackToBounds(maxScrollY)
                } else {
                    flingScroller.abortAnimation()
                }
                return super.onTouchEvent(event)
            }
        }
        return super.onTouchEvent(event)
    }

    override fun computeScroll() {
        super.computeScroll()
        if (flingScroller.computeScrollOffset()) {
            scrollTo(scrollX, flingScroller.currY)
            postInvalidateOnAnimation()
        }
    }

    fun hideKeyboardAndClearFocus() {
        clearFocus()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(windowToken, 0)
    }

    fun setEditorTextColor(color: Int) {
        setTextColor(color)
    }

    private fun maxScrollY(): Int = (computeVerticalScrollRange() - computeVerticalScrollExtent()).coerceAtLeast(0)
    private fun scrollToClamped(targetY: Int) { scrollTo(scrollX, targetY.coerceIn(0, maxScrollY())) }

    private fun scrollForDrag(targetY: Int) {
        val maxScrollY = maxScrollY()
        val resistedY = when {
            targetY < 0 -> -overscrollDistance(-targetY)
            targetY > maxScrollY -> maxScrollY + overscrollDistance(targetY - maxScrollY)
            else -> targetY
        }
        scrollTo(scrollX, resistedY)
    }

    private fun overscrollDistance(distance: Int): Int =
        (distance * 0.75f).toInt().coerceAtMost(overscrollLimitPx)

    private fun springBackToBounds(maxScrollY: Int) {
        if (flingScroller.springBack(scrollX, scrollY, 0, 0, 0, maxScrollY)) {
            postInvalidateOnAnimation()
        } else {
            scrollToClamped(scrollY)
        }
    }

    fun setTextChangeListener(listener: ((NativeEditorView) -> Unit)?) { textChangeListener = listener }
    fun setEditorTextSize(size: EditorTextSize) { setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, size.sp.toFloat()); setLineSpacing(0f, size.lineHeightSp.toFloat() / size.sp.toFloat()) }

    /** Starts a nested-safe transaction. All edits are exposed as one undo/redo boundary. */
    fun beginTransaction() {
        if (transactionDepth == 0) {
            transactionBeforeText = text?.toString().orEmpty()
            transactionBeforeSelectionStart = selectionStart
            transactionBeforeSelectionEnd = selectionEnd
        }
        transactionDepth++
    }

    /** Commits the outermost transaction as one replace operation and one change notification. */
    fun endTransaction() {
        if (transactionDepth == 0) return
        transactionDepth--
        if (transactionDepth != 0) return
        val before = transactionBeforeText ?: return
        transactionBeforeText = null
        val after = text?.toString().orEmpty()
        if (before == after) return
        val prefix = commonPrefix(before, after)
        val suffix = commonSuffix(before, after, prefix)
        val removedEnd = before.length - suffix
        val insertedEnd = after.length - suffix
        undoStack.addLast(EditOperation(prefix, before.substring(prefix, removedEnd), after.substring(prefix, insertedEnd), transactionBeforeSelectionStart, transactionBeforeSelectionEnd, selectionStart, selectionEnd))
        redoStack.clear()
        trimHistory()
        textChangeListener?.invoke(this)
    }

    fun <T> transaction(block: NativeEditorView.() -> T): T {
        beginTransaction()
        return try { block() } finally { endTransaction() }
    }

    fun replaceText(start: Int, end: Int, replacement: CharSequence, selectionStart: Int? = null, selectionEnd: Int? = null) {
        val safeStart = start.coerceIn(0, length())
        val safeEnd = end.coerceIn(safeStart, length())
        val beforeStart = this.selectionStart
        val beforeEnd = this.selectionEnd
        val removed = text?.subSequence(safeStart, safeEnd)?.toString().orEmpty()
        val inserted = replacement.toString()
        internalMutation = true
        try {
            text?.replace(safeStart, safeEnd, inserted)
            val targetStart = (selectionStart ?: safeStart + inserted.length).coerceIn(0, length())
            val targetEnd = (selectionEnd ?: targetStart).coerceIn(targetStart, length())
            setSelection(targetStart, targetEnd)
        } finally { internalMutation = false }
        if (transactionDepth == 0) {
            undoStack.addLast(EditOperation(safeStart, removed, inserted, beforeStart, beforeEnd, selectionStart ?: safeStart + inserted.length, selectionEnd ?: selectionStart ?: safeStart + inserted.length))
            redoStack.clear()
            trimHistory()
            textChangeListener?.invoke(this)
        }
    }

    fun setEditorText(value: CharSequence, selectionStart: Int = value.length, selectionEnd: Int = selectionStart) {
        internalMutation = true
        try { setText(value); setSelection(selectionStart.coerceIn(0, length()), selectionEnd.coerceIn(0, length())); undoStack.clear(); redoStack.clear() }
        finally { internalMutation = false }
    }

    fun undo() { undoStack.removeLastOrNull()?.let { operation -> applyOperation(operation, true); redoStack.addLast(operation) } }
    fun redo() { redoStack.removeLastOrNull()?.let { operation -> applyOperation(operation, false); undoStack.addLast(operation) } }
    fun withInternalMutation(block: () -> Unit) { internalMutation = true; try { block() } finally { internalMutation = false } }

    private fun applyOperation(operation: EditOperation, undo: Boolean) {
        internalMutation = true
        try {
            val start = operation.start
            val currentLength = if (undo) operation.inserted.length else operation.removed.length
            val replacement = if (undo) operation.removed else operation.inserted
            text?.replace(start.coerceIn(0, length()), (start + currentLength).coerceIn(start, length()), replacement)
            val targetStart = if (undo) operation.beforeSelectionStart else operation.afterSelectionStart
            val targetEnd = if (undo) operation.beforeSelectionEnd else operation.afterSelectionEnd
            setSelection(targetStart.coerceIn(0, length()), targetEnd.coerceIn(targetStart, length()))
        } finally { internalMutation = false }
        textChangeListener?.invoke(this)
    }

    private fun trimHistory() { while (undoStack.size > 100) undoStack.removeFirst() }
    private fun commonPrefix(a: String, b: String): Int { val max = minOf(a.length, b.length); var i = 0; while (i < max && a[i] == b[i]) i++; return i }
    private fun commonSuffix(a: String, b: String, prefix: Int): Int { val max = minOf(a.length, b.length) - prefix; var i = 0; while (i < max && a[a.length - 1 - i] == b[b.length - 1 - i]) i++; return i }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}