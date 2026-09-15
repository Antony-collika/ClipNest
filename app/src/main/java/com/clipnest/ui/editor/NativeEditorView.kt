package com.clipnest.ui.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Build
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
    private var selectionChangeListener: ((Int, Int) -> Unit)? = null
    private var scrollPositionListener: ((Int) -> Unit)? = null
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
    private var stableMaxScrollY = 0
    private val fastScrollHitWidthPx = dp(48)
    private val fastScrollThumbWidthPx = dp(4)
    private val fastScrollActiveThumbWidthPx = dp(8)
    private val fastScrollMinThumbHeightPx = dp(32)
    private val fastScrollPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val fastScrollRect = RectF()
    private var staticCursorEnabled = false

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
        inputType = android.text.InputType.TYPE_CLASS_TEXT or
            android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
        setTextIsSelectable(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setFallbackLineSpacing(false)
        setEmojiCompatEnabled(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_AUTO
        setCursorVisible(true)

        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (!internalMutation) {
                    pendingBefore = PendingChange(start, s?.subSequence(start, start + count)?.toString().orEmpty(), s?.length ?: length(), selectionStart, selectionEnd)
                }
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                if (internalMutation) return
                val before = pendingBefore ?: return
                val editable = s ?: return
                val insertedLength = editable.length - before.originalLength + before.removed.length
                val operation = EditOperation(before.start, before.removed, insertedLength.coerceAtLeast(0), null, before.selectionStart, before.selectionEnd, selectionStart, selectionEnd)
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
        // View.draw() translates the canvas by -scrollY before onDraw(). Restore viewport
        // coordinates for the custom fast-scroll thumb so it never drifts with the document.
        canvas.save()
        canvas.translate(0f, scrollY.toFloat())
        drawFastScrollThumb(canvas)
        canvas.restore()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        EditorDiagnosticLog.log("LIFECYCLE", "onAttachedToWindow  text.length=${length()}  selection=$selectionStart-$selectionEnd  identity=${System.identityHashCode(this)}")
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateStableScrollBounds()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        updateStableScrollBounds()
        EditorDiagnosticLog.log("LIFECYCLE", "onLayout  changed=$changed  scrollY=$scrollY  selection=$selectionStart-$selectionEnd")
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        invalidate()
        EditorDiagnosticLog.log("LIFECYCLE", "onFocusChanged  focused=$focused  selection=$selectionStart-$selectionEnd  scrollY=$scrollY")
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        EditorDiagnosticLog.log("LIFECYCLE", "onWindowFocusChanged  hasWindowFocus=$hasWindowFocus  scrollY=$scrollY  selection=$selectionStart-$selectionEnd")
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        EditorDiagnosticLog.log("LIFECYCLE", "onWindowVisibilityChanged  visibility=$visibility (VISIBLE=0)  scrollY=$scrollY")
        // The view may become hidden (e.g. pressing Home) without being detached from
        // the window yet. Report what's on screen here too, for the same reason as
        // onDetachedFromWindow below, so "where the user left off" isn't lost between
        // going to background and the view eventually being torn down.
        if (visibility != View.VISIBLE && !internalMutation) {
            reportLastKnownPosition()
        }
    }

    override fun onDetachedFromWindow() {
        EditorDiagnosticLog.log("LIFECYCLE", "onDetachedFromWindow BEGIN  text.length=${length()}  selection=$selectionStart-$selectionEnd  scrollY=$scrollY  identity=${System.identityHashCode(this)}")
        // This is the one callback guaranteed to fire when Compose Navigation removes
        // this screen from composition (e.g. navigating to Settings) or when a new
        // external document replaces this view's content — well before any save/pause
        // hook might run. Report both:
        //  - the raw scrollY, which is only meaningful while the process (and the
        //    ViewModel holding it in memory) stays alive — e.g. going to Settings and
        //    back, or opening/closing an external file, where the user is actively
        //    working and expects to land back exactly where they were reading, not
        //    wherever the caret happens to be (they may not have tapped at all).
        //  - a caret/viewport-derived offset, which is what gets persisted to disk and
        //    survives the process being killed outright (swipe-away), where there's no
        //    live scrollY left to restore and the caret is the best remaining signal.
        // The ViewModel decides which one to prefer when rebinding a new view: the
        // in-memory scrollY if this is the same process, falling back to the persisted
        // caret only after a real process restart.
        if (!internalMutation) reportLastKnownPosition()
        super.onDetachedFromWindow()
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        invalidate()
        EditorDiagnosticLog.log("SELECTION", "onSelectionChanged  new=$selStart-$selEnd  internalMutation=$internalMutation  text.length=${length()}")
        // Reports every real selection/caret move so callers can keep an external
        // "last known selection" in sync, for restoring the caret when this view is
        // torn down and recreated (e.g. Compose Navigation removing this screen from
        // composition, then recomposing it on Back) rather than only at save points.
        if (!internalMutation) selectionChangeListener?.invoke(selStart, selEnd)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
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
                    invalidate()
                    return true
                }
                return super.onTouchEvent(event)
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                if (draggingFastScroll) {
                    scrollToFastScroll(event.y)
                    invalidate()
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
                    velocityTracker?.recycle(); velocityTracker = null; draggingFastScroll = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    invalidate()
                    return true
                }
                if (draggingScroll) {
                    velocityTracker?.computeCurrentVelocity(1000, maximumFlingVelocity.toFloat())
                    val velocityY = velocityTracker?.yVelocity ?: 0f
                    velocityTracker?.recycle(); velocityTracker = null
                    draggingScroll = false; scrollRemainderY = 0f
                    parent?.requestDisallowInterceptTouchEvent(false)
                    val maxScrollY = maxScrollY()
                    if (scrollY < 0 || scrollY > maxScrollY) {
                        springBackToBounds(maxScrollY)
                    } else {
                        val scrollVelocity = -velocityY
                        if (kotlin.math.abs(scrollVelocity) >= minimumFlingVelocity && maxScrollY > 0 && ((scrollVelocity > 0f && scrollY < maxScrollY) || (scrollVelocity < 0f && scrollY > 0))) {
                            flingScroller.fling(scrollX, scrollY, 0, scrollVelocity.toInt(), 0, 0, 0, maxScrollY)
                            postInvalidateOnAnimation()
                        } else scrollToClamped(scrollY)
                    }
                    return true
                }
                velocityTracker?.recycle(); velocityTracker = null
                return super.onTouchEvent(event)
            }
            MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.addMovement(event)
                velocityTracker?.recycle(); velocityTracker = null
                val wasFastScroll = draggingFastScroll
                draggingFastScroll = false; draggingScroll = false; scrollRemainderY = 0f
                parent?.requestDisallowInterceptTouchEvent(false)
                val maxScrollY = maxScrollY()
                if (!wasFastScroll && (scrollY < 0 || scrollY > maxScrollY)) springBackToBounds(maxScrollY) else flingScroller.abortAnimation()
                invalidate()
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
        if (length() < ACCESSIBILITY_TEXT_LIMIT) super.sendAccessibilityEventUnchecked(event)
    }

    override fun getAutofillType(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && length() >= ACCESSIBILITY_TEXT_LIMIT) View.AUTOFILL_TYPE_NONE else super.getAutofillType()

    fun hideKeyboardAndClearFocus() {
        requestFocus()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(windowToken, 0)
    }

    fun setEditorTextColor(color: Int) {
        setTextColor(color)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            textCursorDrawable = GradientDrawable().apply { setColor(color); setSize(dp(2), dp(24)) }
        }
        invalidate()
    }

    fun setStaticCursorEnabled(enabled: Boolean) {
        staticCursorEnabled = enabled
        setCursorVisible(!enabled)
        invalidate()
    }

    private fun scrollMetrics(): Pair<Int, Int> {
        val layoutHeight = layout?.height ?: 0
        if (layoutHeight > 0) {
            val range = (layoutHeight + compoundPaddingTop + compoundPaddingBottom).coerceAtLeast(height)
            return range to height.coerceAtLeast(0)
        }
        val range = computeVerticalScrollRange().coerceAtLeast(0)
        val extent = computeVerticalScrollExtent().coerceAtLeast(0)
        return range to extent
    }

    private fun drawFastScrollThumb(canvas: Canvas) {
        val (range, extent) = scrollMetrics()
        val maxScroll = maxScrollY()
        if (range <= extent || maxScroll <= 0 || height <= 0 || extent <= 0) return
        val thumbHeight = (height.toFloat() * extent / range).toInt().coerceAtLeast(fastScrollMinThumbHeightPx).coerceAtMost(height)
        val travel = (height - thumbHeight).coerceAtLeast(0)
        val top = if (travel == 0) 0f else travel.toFloat() * scrollY.coerceIn(0, maxScroll) / maxScroll
        fastScrollPaint.color = currentTextColor
        fastScrollPaint.alpha = if (draggingFastScroll) 190 else 110
        val thumbWidth = if (draggingFastScroll) fastScrollActiveThumbWidthPx else fastScrollThumbWidthPx
        val left = width - thumbWidth.toFloat()
        fastScrollRect.set(left, top, width.toFloat(), top + thumbHeight)
        canvas.drawRoundRect(fastScrollRect, thumbWidth.toFloat(), thumbWidth.toFloat(), fastScrollPaint)
    }

    private fun isFastScrollHit(x: Float, y: Float): Boolean {
        val (range, extent) = scrollMetrics()
        if (range <= extent || width <= 0 || height <= 0) return false
        if (x < width - fastScrollHitWidthPx) return false
        val maxScroll = maxScrollY()
        val thumbHeight = (height.toFloat() * extent / range).toInt().coerceAtLeast(fastScrollMinThumbHeightPx).coerceAtMost(height)
        val travel = (height - thumbHeight).coerceAtLeast(0)
        val top = if (travel == 0) 0f else travel.toFloat() * scrollY.coerceIn(0, maxScroll) / maxScroll
        return y >= top - fastScrollHitWidthPx / 2f && y <= top + thumbHeight + fastScrollHitWidthPx / 2f
    }

    private fun scrollToFastScroll(touchY: Float) {
        val (range, extent) = scrollMetrics()
        val maxScroll = maxScrollY()
        if (range <= extent || maxScroll <= 0) return
        val thumbHeight = (height.toFloat() * extent / range).toInt().coerceAtLeast(fastScrollMinThumbHeightPx).coerceAtMost(height)
        val travel = (height - thumbHeight).coerceAtLeast(0)
        val target = if (travel == 0) 0 else ((touchY - thumbHeight / 2f).coerceIn(0f, travel.toFloat()) * maxScroll / travel).toInt()
        scrollToClamped(target)
    }

    private fun calculatedMaxScrollY(): Int {
        val (range, extent) = scrollMetrics()
        return (range - extent).coerceAtLeast(0)
    }

    private fun updateStableScrollBounds() {
        val calculated = calculatedMaxScrollY()
        if (!draggingScroll && !draggingFastScroll && flingScroller.isFinished) {
            stableMaxScrollY = calculated
        } else if (calculated > stableMaxScrollY) {
            stableMaxScrollY = calculated
        }
    }

    private fun maxScrollY(): Int {
        val calculated = calculatedMaxScrollY()
        if (!draggingScroll && !draggingFastScroll && flingScroller.isFinished) {
            stableMaxScrollY = maxOf(stableMaxScrollY, calculated)
            return stableMaxScrollY
        }
        stableMaxScrollY = maxOf(stableMaxScrollY, calculated)
        return stableMaxScrollY
    }

    private fun scrollToClamped(targetY: Int) { scrollTo(scrollX, targetY.coerceIn(0, maxScrollY())) }
    private fun scrollForDrag(targetY: Int) {
        val max = maxScrollY()
        val resistedY = when {
            targetY < 0 -> -overscrollDistance(-targetY)
            targetY > max -> max + overscrollDistance(targetY - max)
            else -> targetY
        }
        scrollTo(scrollX, resistedY)
    }
    private fun overscrollDistance(distance: Int): Int = (distance * 0.75f).toInt().coerceAtMost(overscrollLimitPx)
    private fun springBackToBounds(maxScrollY: Int) { if (flingScroller.springBack(scrollX, scrollY, 0, 0, 0, maxScrollY)) postInvalidateOnAnimation() else scrollToClamped(scrollY) }

    /**
     * Returns the current caret offset if it's within the visible viewport, or the
     * offset of the first character currently visible at the top of the viewport
     * otherwise. Scrolling to read doesn't move the caret, so when the two disagree,
     * "what's on screen" better reflects where the user actually left off than a caret
     * that's still sitting wherever it was last typed (often end-of-text).
     */
    private fun visibleCaretOrTopOfViewport(): Int {
        val lay = layout ?: return selectionStart
        if (length() == 0 || height <= 0) return selectionStart
        val topLine = lay.getLineForVertical(scrollY)
        val bottomVisibleY = (scrollY + height - paddingTop - paddingBottom).coerceAtLeast(scrollY)
        val bottomLine = lay.getLineForVertical(bottomVisibleY)
        val caretLine = try { lay.getLineForOffset(selectionStart) } catch (e: Exception) { topLine }
        if (caretLine in topLine..bottomLine) return selectionStart
        return lay.getLineStart(topLine).coerceIn(0, length())
    }

    /**
     * Reports both signals the ViewModel needs when this view is about to go away:
     * the live scrollY (meaningful only while the process/ViewModel stays alive) and
     * a caret/viewport-derived offset (what gets persisted to disk for after a real
     * process restart). See onDetachedFromWindow for which one wins when.
     */
    private fun reportLastKnownPosition() {
        val reportOffset = visibleCaretOrTopOfViewport()
        selectionChangeListener?.invoke(reportOffset, reportOffset)
        scrollPositionListener?.invoke(scrollY)
        EditorDiagnosticLog.log("LIFECYCLE", "reportLastKnownPosition  scrollY=$scrollY  reportedOffset=$reportOffset  (actualCaret=$selectionStart-$selectionEnd)")
    }

    fun setTextChangeListener(listener: ((NativeEditorView) -> Unit)?) { textChangeListener = listener }
    fun setSelectionChangeListener(listener: ((Int, Int) -> Unit)?) { selectionChangeListener = listener }
    fun setScrollPositionListener(listener: ((Int) -> Unit)?) { scrollPositionListener = listener }

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
            recordUndo(EditOperation(prefix, before.substring(prefix, removedEnd), insertedEnd - prefix, null, transactionBeforeSelectionStart, transactionBeforeSelectionEnd, selectionStart, selectionEnd))
            textChangeListener?.invoke(this)
        } finally {
            endBatchEdit()
            invalidate()
        }
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

    fun setEditorText(value: CharSequence, selectionStart: Int = value.length, selectionEnd: Int = selectionStart, restoreScrollY: Int? = null) {
        EditorDiagnosticLog.log("SET_TEXT", "setEditorText CALLED  requestedSelection=$selectionStart-$selectionEnd  value.length=${value.length}  restoreScrollY=$restoreScrollY  (default-to-end used = ${selectionStart == value.length})  identity=${System.identityHashCode(this)}")
        beginBatchEdit()
        internalMutation = true
        try {
            setText(value)
            val safeStart = selectionStart.coerceIn(0, length())
            val safeEnd = selectionEnd.coerceIn(safeStart, length())
            setSelection(safeStart, safeEnd)
            EditorDiagnosticLog.log("SET_TEXT", "setEditorText applied  safeSelection=$safeStart-$safeEnd")
            undoStack.clear(); redoStack.clear()
        } finally {
            internalMutation = false
            endBatchEdit()
        }
        invalidate()
        if (restoreScrollY != null) {
            // A live scrollY was available (view rebound within the same process —
            // e.g. Settings, or closing an external file — not a fresh process after
            // being killed). This is what the user was actually looking at, which may
            // be nowhere near the caret if they only scrolled to read without tapping.
            // Apply it last, after layout settles, so it wins over the caret-follow
            // behavior below.
            post { scrollToClamped(restoreScrollY) }
        } else {
            // No live scroll to restore (fresh process, or first load) — fall back to
            // scrolling to the caret the normal Android way. Calling this directly
            // (not requestFocus()) scrolls without forcing focus/keyboard open.
            post { bringPointIntoView(selectionStart) }
        }
    }

    fun undo() {
        val operation = undoStack.removeLastOrNull() ?: return
        val currentInserted = readText(operation.start, operation.insertedLength)
        internalMutation = true
        beginBatchEdit()
        try {
            text?.replace(operation.start.coerceIn(0, length()), (operation.start + operation.insertedLength).coerceIn(operation.start, length()), operation.removed)
            val safeStart = operation.beforeSelectionStart.coerceIn(0, length())
            setSelection(safeStart, operation.beforeSelectionEnd.coerceIn(safeStart, length()))
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
        val inserted = operation.insertedContent
        if (inserted == null) return
        internalMutation = true
        beginBatchEdit()
        try {
            text?.replace(operation.start.coerceIn(0, length()), (operation.start + operation.removed.length).coerceIn(operation.start, length()), inserted)
            val safeStart = operation.afterSelectionStart.coerceIn(0, length())
            setSelection(safeStart, operation.afterSelectionEnd.coerceIn(safeStart, length()))
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
        try { block() } finally { internalMutation = false }
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
            undoStack.removeLast(); undoStack.addLast(coalesce(previous, operation))
        } else undoStack.addLast(operation)
        trimHistory()
    }

    private fun canCoalesce(previous: EditOperation, current: EditOperation): Boolean {
        if (previous.insertedContent != null || current.insertedContent != null) return false
        val bothInsert = previous.removed.isEmpty() && current.removed.isEmpty() && previous.insertedLength > 0 && current.insertedLength > 0
        if (bothInsert) return current.start == previous.start + previous.insertedLength && current.beforeSelectionStart == previous.afterSelectionStart && current.beforeSelectionEnd == previous.afterSelectionEnd
        val bothDelete = previous.insertedLength == 0 && current.insertedLength == 0 && previous.removed.isNotEmpty() && current.removed.isNotEmpty()
        if (!bothDelete) return false
        return (current.start == previous.start || current.start + current.removed.length == previous.start) && current.beforeSelectionStart == previous.afterSelectionStart && current.beforeSelectionEnd == previous.afterSelectionEnd
    }

    private fun coalesce(previous: EditOperation, current: EditOperation): EditOperation {
        val bothInsert = previous.removed.isEmpty() && current.removed.isEmpty()
        if (bothInsert) return previous.copy(insertedLength = previous.insertedLength + current.insertedLength, afterSelectionStart = current.afterSelectionStart, afterSelectionEnd = current.afterSelectionEnd)
        val currentBeforePrevious = current.start + current.removed.length == previous.start
        return previous.copy(start = if (currentBeforePrevious) current.start else previous.start, removed = if (currentBeforePrevious) current.removed + previous.removed else previous.removed + current.removed, beforeSelectionStart = current.beforeSelectionStart, beforeSelectionEnd = current.beforeSelectionEnd, afterSelectionStart = current.afterSelectionStart, afterSelectionEnd = current.afterSelectionEnd)
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