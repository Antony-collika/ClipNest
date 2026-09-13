package com.clipnest.ui.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.Layout
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
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
    private data class EditOperation(
        val start: Int,
        val removed: String,
        val insertedLength: Int,
        val insertedContent: String?,
        val beforeSelectionStart: Int,
        val beforeSelectionEnd: Int,
        val afterSelectionStart: Int,
        val afterSelectionEnd: Int
    )

    private data class PendingChange(
        val start: Int,
        val removed: String,
        val originalLength: Int,
        val selectionStart: Int,
        val selectionEnd: Int
    )

    companion object {
        private const val ACCESSIBILITY_TEXT_LIMIT = 10_000
        private const val MAX_UNDO_STEPS = 100
        private const val MAX_HISTORY_CHARS = 100_000
    }

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
    private var draggingFastScroll = false
    private var lastTouchY = 0f
    private var downTouchX = 0f
    private var downTouchY = 0f
    private var scrollRemainderY = 0f
    private val fastScrollHitWidthPx = dp(24)
    private val fastScrollThumbWidthPx = dp(4)
    private val fastScrollMinThumbHeightPx = dp(32)
    private val fastScrollPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val fastScrollRect = RectF()
    private val staticCursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private var staticCursorEnabled = true

    init {
        setSingleLine(false)
        gravity = Gravity.TOP or Gravity.START
        maxLines = Int.MAX_VALUE
        minLines = 1
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
        setPadding(dp(12), dp(8), dp(12), dp(8))
        setHorizontallyScrolling(false)
        inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
        setTextIsSelectable(true)

        // Large documents are especially sensitive to Android's extra text-layout work.
        // These options mirror Markor's large-file safeguards.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            setFallbackLineSpacing(false)
        }
        setEmojiCompatEnabled(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_AUTO
        }
        staticCursorPaint.strokeWidth = dp(2).toFloat()
        staticCursorPaint.color = currentTextColor
        setCursorVisible(false)

        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (!internalMutation) {
                    pendingBefore = PendingChange(
                        start,
                        s?.subSequence(start, start + count)?.toString().orEmpty(),
                        s?.length ?: length(),
                        selectionStart,
                        selectionEnd
                    )
                }
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: android.text.Editable?) {
                if (internalMutation) return
                val before = pendingBefore ?: return
                val editable = s ?: return
                val insertedLength = editable.length - before.originalLength + before.removed.length
                val operation = EditOperation(
                    start = before.start,
                    removed = before.removed,
                    insertedLength = insertedLength.coerceAtLeast(0),
                    insertedContent = null,
                    beforeSelectionStart = before.selectionStart,
                    beforeSelectionEnd = before.selectionEnd,
                    afterSelectionStart = selectionStart,
                    afterSelectionEnd = selectionEnd
                )
                if (transactionDepth == 0) {
                    recordUndo(operation)
                    textChangeListener?.invoke(this@NativeEditorView)
                }
                pendingBefore = null
                invalidate()
            }
        })
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawStaticCursor(canvas)
        val saveCount = canvas.save()
        canvas.translate(0f, scrollY.toFloat())
        drawFastScrollThumb(canvas)
        canvas.restoreToCount(saveCount)
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        invalidate()
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        invalidate()
    }

    private fun drawStaticCursor(canvas: Canvas) {
        if (!staticCursorEnabled || !hasFocus() || selectionStart != selectionEnd) return
        val layout: Layout = layout ?: return
        val offset = selectionStart.coerceIn(0, length())
        val line = layout.getLineForOffset(offset)
        val x = layout.getPrimaryHorizontal(offset) + compoundPaddingLeft.toFloat()
        val top = compoundPaddingTop + layout.getLineTop(line).toFloat()
        val bottom = compoundPaddingTop + layout.getLineBottom(line).toFloat()
        staticCursorPaint.color = currentTextColor
        canvas.drawLine(x, top, x, bottom, staticCursorPaint)
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
                draggingFastScroll = isFastScrollHit(event.x, event.y)
                scrollRemainderY = 0f
                downTouchX = event.x
                downTouchY = event.y
                lastTouchY = event.y
                if (draggingFastScroll) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    scrollToFastScroll(event.y)
                    return true
                }
                return super.onTouchEvent(event)
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                if (draggingFastScroll) {
                    scrollToFastScroll(event.y)
                    return true
                }
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
                if (draggingFastScroll) {
                    velocityTracker?.recycle()
                    velocityTracker = null
                    draggingFastScroll = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
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
                val wasFastScroll = draggingFastScroll
                draggingFastScroll = false
                draggingScroll = false
                scrollRemainderY = 0f
                parent?.requestDisallowInterceptTouchEvent(false)
                val maxScrollY = maxScrollY()
                if (!wasFastScroll && (scrollY < 0 || scrollY > maxScrollY)) {
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

    override fun sendAccessibilityEventUnchecked(event: AccessibilityEvent) {
        if (length() < ACCESSIBILITY_TEXT_LIMIT) {
            super.sendAccessibilityEventUnchecked(event)
        }
    }

    override fun getAutofillType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && length() >= ACCESSIBILITY_TEXT_LIMIT) {
            View.AUTOFILL_TYPE_NONE
        } else {
            super.getAutofillType()
        }
    }

    fun hideKeyboardAndClearFocus() {
        requestFocus()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(windowToken, 0)
    }

    fun setEditorTextColor(color: Int) {
        setTextColor(color)
        staticCursorPaint.color = color
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            textCursorDrawable = GradientDrawable().apply {
                setColor(color)
                setSize(dp(2), dp(24))
            }
        }
        invalidate()
    }

    fun setStaticCursorEnabled(enabled: Boolean) {
        staticCursorEnabled = enabled
        setCursorVisible(!enabled)
        invalidate()
    }

    private fun drawFastScrollThumb(canvas: Canvas) {
        val range = computeVerticalScrollRange()
        val extent = computeVerticalScrollExtent()
        val maxScroll = (range - extent).coerceAtLeast(0)
        if (maxScroll <= 0 || height <= 0 || extent <= 0) return
        val thumbHeight = (height.toFloat() * extent / range).toInt().coerceAtLeast(fastScrollMinThumbHeightPx).coerceAtMost(height)
        val travel = (height - thumbHeight).coerceAtLeast(0)
        val top = if (travel == 0) 0f else travel.toFloat() * scrollY.coerceIn(0, maxScroll) / maxScroll
        fastScrollPaint.color = currentTextColor
        fastScrollPaint.alpha = if (draggingFastScroll) 190 else 110
        val left = width - fastScrollThumbWidthPx.toFloat()
        fastScrollRect.set(left, top, width.toFloat(), top + thumbHeight)
        canvas.drawRoundRect(fastScrollRect, fastScrollThumbWidthPx.toFloat(), fastScrollThumbWidthPx.toFloat(), fastScrollPaint)
    }

    private fun isFastScrollHit(x: Float, y: Float): Boolean {
        val range = computeVerticalScrollRange()
        val extent = computeVerticalScrollExtent()
        if (range <= extent || width <= 0 || height <= 0) return false
        if (x < width - fastScrollHitWidthPx) return false
        val maxScroll = (range - extent).coerceAtLeast(0)
        val thumbHeight = (height.toFloat() * extent / range).toInt().coerceAtLeast(fastScrollMinThumbHeightPx).coerceAtMost(height)
        val travel = (height - thumbHeight).coerceAtLeast(0)
        val top = if (travel == 0) 0f else travel.toFloat() * scrollY.coerceIn(0, maxScroll) / maxScroll
        return y >= top - fastScrollHitWidthPx / 2f && y <= top + thumbHeight + fastScrollHitWidthPx / 2f
    }

    private fun scrollToFastScroll(touchY: Float) {
        val range = computeVerticalScrollRange()
        val extent = computeVerticalScrollExtent()
        val maxScroll = (range - extent).coerceAtLeast(0)
        if (maxScroll <= 0) return
        val thumbHeight = (height.toFloat() * extent / range).toInt().coerceAtLeast(fastScrollMinThumbHeightPx).coerceAtMost(height)
        val travel = (height - thumbHeight).coerceAtLeast(0)
        val target = if (travel == 0) 0 else ((touchY - thumbHeight / 2f).coerceIn(0f, travel.toFloat()) * maxScroll / travel).toInt()
        scrollToClamped(target)
    }

    private fun maxScrollY(): Int = (computeVerticalScrollRange() - computeVerticalScrollExtent()).coerceAtLeast(0)
    private fun scrollToClamped(targetY: Int) { scrollTo(scrollX, targetY.coerceIn(0, maxScrollY())) }
    private fun scrollForDrag(targetY: Int) { val maxScrollY = maxScrollY(); val resistedY = when { targetY < 0 -> -overscrollDistance(-targetY); targetY > maxScrollY -> maxScrollY + overscrollDistance(targetY - maxScrollY); else -> targetY }; scrollTo(scrollX, resistedY) }
    private fun overscrollDistance(distance: Int): Int = (distance * 0.75f).toInt().coerceAtMost(overscrollLimitPx)
    private fun springBackToBounds(maxScrollY: Int) { if (flingScroller.springBack(scrollX, scrollY, 0, 0, 0, maxScrollY)) postInvalidateOnAnimation() else scrollToClamped(scrollY) }

    fun setTextChangeListener(listener: ((NativeEditorView) -> Unit)?) { textChangeListener = listener }

    fun setEditorTextSize(size: EditorTextSize) {
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, size.sp.toFloat())
        setLineSpacing(0f, size.lineHeightSp.toFloat() / size.sp.toFloat())
        invalidate()
    }

    fun beginTransaction() {
        if (transactionDepth == 0) {
            transactionBeforeText = text?.toString().orEmpty()
            transactionBeforeSelectionStart = selectionStart
            transactionBeforeSelectionEnd = selectionEnd
            beginBatchEdit()
        }
        transactionDepth++
    }

    fun endTransaction() {
        if (transactionDepth == 0) return
        transactionDepth--
        if (transactionDepth != 0) return
        try {
            val before = transactionBeforeText ?: return
            transactionBeforeText = null
            val after = text?.toString().orEmpty()
            if (before == after) return
            val prefix = commonPrefix(before, after)
            val suffix = commonSuffix(before, after, prefix)
            val removedEnd = before.length - suffix
            val insertedEnd = after.length - suffix
            recordUndo(
                EditOperation(
                    prefix,
                    before.substring(prefix, removedEnd),
                    insertedEnd - prefix,
                    null,
                    transactionBeforeSelectionStart,
                    transactionBeforeSelectionEnd,
                    selectionStart,
                    selectionEnd
                )
            )
            textChangeListener?.invoke(this)
        } finally {
            endBatchEdit()
            invalidate()
        }
    }

    fun <T> transaction(block: NativeEditorView.() -> T): T {
        beginTransaction()
        return try {
            block()
        } finally {
            endTransaction()
        }
    }

    fun replaceText(start: Int, end: Int, replacement: CharSequence, selectionStart: Int? = null, selectionEnd: Int? = null) {
        val safeStart = start.coerceIn(0, length())
        val safeEnd = end.coerceIn(safeStart, length())
        val beforeStart = this.selectionStart
        val beforeEnd = this.selectionEnd
        val removed = text?.subSequence(safeStart, safeEnd)?.toString().orEmpty()
        val inserted = replacement.toString()
        val shouldBatch = transactionDepth == 0
        if (shouldBatch) beginBatchEdit()
        internalMutation = true
        try {
            text?.replace(safeStart, safeEnd, inserted)
            val targetStart = (selectionStart ?: safeStart + inserted.length).coerceIn(0, length())
            val targetEnd = (selectionEnd ?: targetStart).coerceIn(targetStart, length())
            setSelection(targetStart, targetEnd)
        } finally {
            internalMutation = false
            if (shouldBatch) endBatchEdit()
        }
        if (transactionDepth == 0) {
            recordUndo(EditOperation(safeStart, removed, inserted.length, null, beforeStart, beforeEnd, selectionStart ?: safeStart + inserted.length, selectionEnd ?: selectionStart ?: safeStart + inserted.length))
            redoStack.clear()
            textChangeListener?.invoke(this)
        }
        invalidate()
    }

    fun setEditorText(value: CharSequence, selectionStart: Int = value.length, selectionEnd: Int = selectionStart) {
        beginBatchEdit()
        internalMutation = true
        try {
            setText(value)
            val safeStart = selectionStart.coerceIn(0, length())
            val safeEnd = selectionEnd.coerceIn(safeStart, length())
            setSelection(safeStart, safeEnd)
            undoStack.clear()
            redoStack.clear()
        } finally {
            internalMutation = false
            endBatchEdit()
        }
        invalidate()
    }

    fun undo() {
        val operation = undoStack.removeLastOrNull() ?: return
        val currentInserted = readText(operation.start, operation.insertedLength)
        internalMutation = true
        beginBatchEdit()
        try {
            text?.replace(operation.start.coerceIn(0, length()), (operation.start + operation.insertedLength).coerceIn(operation.start, length()), operation.removed)
            setSelection(operation.beforeSelectionStart.coerceIn(0, length()), operation.beforeSelectionEnd.coerceIn(operation.beforeSelectionStart.coerceIn(0, length()), length()))
        } finally {
            internalMutation = false
            endBatchEdit()
        }
        redoStack.addLast(operation.copy(insertedContent = currentInserted))
        trimHistory()
        textChangeListener?.invoke(this)
        invalidate()
    }

    fun redo() {
        val operation = redoStack.removeLastOrNull() ?: return
        val inserted = operation.insertedContent ?: return
        internalMutation = true
        beginBatchEdit()
        try {
            text?.replace(operation.start.coerceIn(0, length()), (operation.start + operation.removed.length).coerceIn(operation.start, length()), inserted)
            setSelection(operation.afterSelectionStart.coerceIn(0, length()), operation.afterSelectionEnd.coerceIn(operation.afterSelectionStart.coerceIn(0, length()), length()))
        } finally {
            internalMutation = false
            endBatchEdit()
        }
        undoStack.addLast(operation.copy(insertedContent = null, insertedLength = inserted.length))
        trimHistory()
        textChangeListener?.invoke(this)
        invalidate()
    }

    fun withInternalMutation(block: () -> Unit) {
        internalMutation = true
        try {
            block()
        } finally {
            internalMutation = false
        }
    }

    private fun readText(start: Int, count: Int): String {
        if (count <= 0 || start < 0 || start >= length()) return ""
        val end = (start + count).coerceAtMost(length())
        return text?.subSequence(start, end)?.toString().orEmpty()
    }

    private fun recordUndo(operation: EditOperation) {
        if (operation.removed.isEmpty() && operation.insertedLength == 0) return
        redoStack.clear()
        val previous = undoStack.lastOrNull()
        if (previous != null && canCoalesce(previous, operation)) {
            undoStack.removeLast()
            undoStack.addLast(coalesce(previous, operation))
        } else {
            undoStack.addLast(operation)
        }
        trimHistory()
    }

    private fun canCoalesce(previous: EditOperation, current: EditOperation): Boolean {
        if (previous.insertedContent != null || current.insertedContent != null) return false
        val bothInsert = previous.removed.isEmpty() && current.removed.isEmpty() && previous.insertedLength > 0 && current.insertedLength > 0
        if (bothInsert) return current.start == previous.start + previous.insertedLength && current.beforeSelectionStart == previous.afterSelectionStart && current.beforeSelectionEnd == previous.afterSelectionEnd
        val bothDelete = previous.insertedLength == 0 && current.insertedLength == 0 && previous.removed.isNotEmpty() && current.removed.isNotEmpty()
        if (!bothDelete) return false
        return (current.start == previous.start || current.start + current.removed.length == previous.start) &&
            current.beforeSelectionStart == previous.afterSelectionStart && current.beforeSelectionEnd == previous.afterSelectionEnd
    }

    private fun coalesce(previous: EditOperation, current: EditOperation): EditOperation {
        val bothInsert = previous.removed.isEmpty() && current.removed.isEmpty()
        if (bothInsert) {
            return previous.copy(
                insertedLength = previous.insertedLength + current.insertedLength,
                afterSelectionStart = current.afterSelectionStart,
                afterSelectionEnd = current.afterSelectionEnd
            )
        }
        val currentBeforePrevious = current.start + current.removed.length == previous.start
        return previous.copy(
            start = if (currentBeforePrevious) current.start else previous.start,
            removed = if (currentBeforePrevious) current.removed + previous.removed else previous.removed + current.removed,
            beforeSelectionStart = current.beforeSelectionStart,
            beforeSelectionEnd = current.beforeSelectionEnd,
            afterSelectionStart = current.afterSelectionStart,
            afterSelectionEnd = current.afterSelectionEnd
        )
    }

    private fun trimHistory() {
        while (undoStack.size > MAX_UNDO_STEPS) undoStack.removeFirst()
        while (redoStack.size > MAX_UNDO_STEPS) redoStack.removeFirst()
        while (historyWeight(undoStack) > MAX_HISTORY_CHARS && undoStack.isNotEmpty()) undoStack.removeFirst()
        while (historyWeight(redoStack) > MAX_HISTORY_CHARS && redoStack.isNotEmpty()) redoStack.removeFirst()
    }

    private fun historyWeight(stack: ArrayDeque<EditOperation>): Int = stack.sumOf { it.removed.length + (it.insertedContent?.length ?: 0) }

    private fun commonPrefix(a: String, b: String): Int { val max = minOf(a.length, b.length); var i = 0; while (i < max && a[i] == b[i]) i++; return i }
    private fun commonSuffix(a: String, b: String, prefix: Int): Int { val max = minOf(a.length, b.length) - prefix; var i = 0; while (i < max && a[a.length - 1 - i] == b[b.length - 1 - i]) i++; return i }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
