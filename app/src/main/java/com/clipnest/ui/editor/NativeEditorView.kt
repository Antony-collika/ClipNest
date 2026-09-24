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
        val afterSelectionEnd: Int,
        val beforeTitleBoundary: Int = 0,
        val afterTitleBoundary: Int = 0
    )

    private data class PendingChange(
        val start: Int,
        val removed: String,
        val originalLength: Int,
        val selectionStart: Int,
        val selectionEnd: Int,
        val titleBoundary: Int
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
    private var transactionBeforeTitleBoundary = 0
    private var textChangeListener: ((NativeEditorView) -> Unit)? = null
    private var selectionChangeListener: ((Int, Int) -> Unit)? = null
    private var viewportChangeListener: ((Int) -> Unit)? = null
    private var pendingViewportAnchorRestore: Int? = null
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
    private var appliedEditorTextSize: EditorTextSize? = null
    private var appliedEditorTextColor: Int? = null
    private var structuredDocument = false
    private var titleBoundary = 0
    private var sectionChangeListener: ((Boolean) -> Unit)? = null

    /**
     * "Follow mode": whether the viewport should keep tracking the caret.
     *
     * True by default (typing near the bottom edge should push the screen up, same as
     * any normal text editor). Set to false the moment the user drags/flings the
     * viewport by hand — see onScrollChanged below — because that's an explicit signal
     * they want to read somewhere else and no longer want the caret pulling the screen
     * around. Set back to true the moment the user does anything that expresses intent
     * to look at the caret again: typing, pasting, undo/redo, or tapping to place the
     * caret — see onSelectionChanged and the text-changed listener below.
     *
     * This replaces trying to allow-list every caller of bringPointIntoView (typing,
     * paste, undo, redo, arrow keys, autocomplete, ...) or trying to distinguish
     * "legitimate" focus/window callbacks from "restore" ones — both approaches either
     * miss cases or rely on signals (like focus) that mean different things in
     * different situations. A single durable flag, flipped only by the two things that
     * actually carry unambiguous intent (a manual drag vs. any caret-directed action),
     * covers every caller without needing to know who they are.
     */
    private var followCaret = true

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
                    pendingBefore = PendingChange(start, s?.subSequence(start, start + count)?.toString().orEmpty(), s?.length ?: length(), selectionStart, selectionEnd, titleBoundary)
                }
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                if (internalMutation) return
                val before = pendingBefore ?: return
                val editable = s ?: return
                val insertedLength = editable.length - before.originalLength + before.removed.length
                if (structuredDocument) updateTitleBoundaryForEdit(before.start, before.removed.length, insertedLength.coerceAtLeast(0), before.titleBoundary)
                val operation = EditOperation(before.start, before.removed, insertedLength.coerceAtLeast(0), null, before.selectionStart, before.selectionEnd, selectionStart, selectionEnd, before.titleBoundary, titleBoundary)
                if (transactionDepth == 0) {
                    ensureStructuredSeparator()
                    recordUndo(operation.copy(afterTitleBoundary = titleBoundary))
                    textChangeListener?.invoke(this@NativeEditorView)
                }
                pendingBefore = null
                // A real text change the user caused (typing, pasting, autocomplete,
                // IME composition) — they're actively working at the caret and want to
                // see it, so the screen should follow again from here on.
                followCaret = true
                invalidate()
            }
        })
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (structuredDocument) drawTitleDivider(canvas)
        // View.draw() translates the canvas by -scrollY before onDraw(). Restore viewport
        // coordinates for the custom fast-scroll thumb so it never drifts with the document.
        canvas.save()
        canvas.translate(0f, scrollY.toFloat())
        drawFastScrollThumb(canvas)
        canvas.restore()
    }

    /**
     * TextView (this class's grandparent) calls bringPointIntoView(getSelectionEnd())
     * on its own, internally, in several situations: typing near the edge, regaining
     * window/view focus, the IME showing/hiding and resizing this view, and some
     * layout passes during rotation or multi-window transitions.
     *
     * Whether to honor that call is decided by [followCaret], not by who's calling.
     * When true (the normal state, and the state any caret-directed user action
     * restores), every caller is allowed through — including typing near the bottom
     * edge, which is what should push the screen up as the user types. When false
     * (only right after the user has manually scrolled away to read), every caller is
     * refused, including the platform's own focus/IME/layout-driven calls — this is
     * what stops resuming the app, opening Settings, or the IME resizing the view from
     * silently dragging the screen back to the caret.
     */
    override fun bringPointIntoView(offset: Int): Boolean {
        if (!followCaret) return false
        return super.bringPointIntoView(offset)
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
        val scrollYBefore = scrollY
        super.onLayout(changed, left, top, right, bottom)
        updateStableScrollBounds()
        if (scrollY != scrollYBefore) {
            // scrollY moved as a side effect of layout itself (not through our own
            // scrollTo/scrollToClamped calls, not through a user touch path) — most
            // likely the IME showing/hiding and resizing this view, or TextView's own
            // internal re-clamping of scrollY against the newly measured height/maxScrollY.
            // Logged distinctly from the routine onLayout line below so this is easy to
            // find: if this fires with a large jump right before a background/detach,
            // that confirms layout-driven scroll drift rather than a real user scroll.
            EditorDiagnosticLog.log("LIFECYCLE", "onLayout  scrollY changed as a side effect of layout itself: $scrollYBefore -> $scrollY  height=$height  maxScrollY=${maxScrollY()}  followCaret=$followCaret")
        }
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
            val anchor = topOfViewportOffset()
            viewportChangeListener?.invoke(anchor)
            EditorDiagnosticLog.log("LIFECYCLE", "onWindowVisibilityChanged  reported viewportAnchor=$anchor (actualCaret=$selectionStart-$selectionEnd)")
        }
    }

    override fun onDetachedFromWindow() {
        EditorDiagnosticLog.log("LIFECYCLE", "onDetachedFromWindow BEGIN  text.length=${length()}  selection=$selectionStart-$selectionEnd  scrollY=$scrollY  identity=${System.identityHashCode(this)}")
        // This is the one callback guaranteed to fire when Compose Navigation removes
        // this screen from composition (e.g. navigating to Settings) or when the
        // process is killed and this view is torn down — well before any save/pause
        // hook might run. Report the final state here so the external "last known"
        // caret/viewport never goes stale.
        //
        // Caret and viewport are reported as two independent values, not one. Scrolling
        // to read (without tapping) moves the viewport but not the caret, and that's a
        // very common thing for a user to do right before switching screens — so on
        // restore, the caret must go back to its own last position and the viewport
        // must go back to its own last position, independently of each other.
        if (!internalMutation) {
            val anchor = topOfViewportOffset()
            selectionChangeListener?.invoke(selectionStart, selectionEnd)
            viewportChangeListener?.invoke(anchor)
            EditorDiagnosticLog.log("LIFECYCLE", "onDetachedFromWindow END  reported caret=$selectionStart-$selectionEnd  viewportAnchor=$anchor")
        } else {
            EditorDiagnosticLog.log("LIFECYCLE", "onDetachedFromWindow END  skipped report (internalMutation=true)")
        }
        super.onDetachedFromWindow()
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        invalidate()
        EditorDiagnosticLog.log("SELECTION", "onSelectionChanged  new=$selStart-$selEnd  internalMutation=$internalMutation  text.length=${length()}  followCaret=$followCaret")
        // Reports every real selection/caret move so callers can keep an external
        // "last known selection" in sync, for restoring the caret when this view is
        // torn down and recreated (e.g. Compose Navigation removing this screen from
        // composition, then recomposing it on Back) rather than only at save points.
        // This never reports a viewport anchor — moving the caret while typing should
        // never overwrite "where the user was scrolled to".
        if (!internalMutation) {
            selectionChangeListener?.invoke(selStart, selEnd)
            sectionChangeListener?.invoke(structuredDocument && selStart <= titleBoundary)
            // A real (non-programmatic) selection change is always something the user
            // did on purpose to the caret — most commonly tapping to place it — and
            // that's a clear signal they want the screen following it again, including
            // right now: re-enabling here (rather than waiting for the next keystroke)
            // is what keeps the IME from covering the line the user just tapped into.
            followCaret = true
        }
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        EditorDiagnosticLog.log("SCROLL", "onScrollChanged  oldScrollY=$oldt  newScrollY=$t  internalMutation=$internalMutation  userDriven=${isUserDrivenScroll()}  selection=$selectionStart-$selectionEnd")
        // Reports scroll so callers can keep an external "last known viewport" in sync,
        // the same way onSelectionChanged keeps the caret in sync. But unlike caret
        // moves, scrollY can also change for reasons that have nothing to do with the
        // user scrolling to read: the IME showing/hiding resizes this view and the
        // platform TextView machinery re-clamps scrollY to fit the new size, layout
        // passes during rotation/multi-window transitions do the same, and
        // setEditorText's own scroll-restore also calls scrollTo(). None of those are
        // "the user scrolled to a new place to read" — reporting them as if they were
        // would silently overwrite a correct saved viewport with wherever the platform
        // happened to clamp scrollY to mid-transition (this is what caused the anchor
        // to jump to near-zero right before backgrounding in earlier logs).
        //
        // Only report when scroll changed via one of the three user-driven paths this
        // view itself tracks: an active drag, an active fast-scroll drag, or an active
        // fling — see isUserDrivenScroll(). Programmatic/platform-driven scrollY
        // changes (internalMutation, layout re-clamping, IME resize) are never reported.
        if (!internalMutation && isUserDrivenScroll()) {
            viewportChangeListener?.invoke(topOfViewportOffset())
            // The user just moved the screen with their own hand while reading — that's
            // an explicit signal they no longer want the caret pulling the viewport
            // around. Turn follow mode off so subsequent typing (or a focus/IME/layout
            // event) doesn't drag the screen back to the caret out from under them.
            // It comes back on the moment they do anything caret-directed again: type,
            // paste, undo/redo (all go through the text-changed listener below), or tap
            // to place the caret (onSelectionChanged above).
            followCaret = false
        }
    }

    /** True only while the user is actively dragging, fast-scrolling, or a fling from either is still animating. */
    private fun isUserDrivenScroll(): Boolean = draggingScroll || draggingFastScroll || !flingScroller.isFinished

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
        if (appliedEditorTextColor == color) return
        appliedEditorTextColor = color
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
     * Returns the offset of the first character currently visible at the top of the
     * viewport. This is always reported as its own value — independent of the caret —
     * because scrolling to read doesn't move the caret, and "what's on screen" is a
     * separate fact from "where the caret is" that both need to be restorable on their
     * own.
     */
    private fun topOfViewportOffset(): Int {
        val lay = layout ?: return 0
        if (length() == 0 || height <= 0) return 0
        val topLine = lay.getLineForVertical(scrollY)
        return lay.getLineStart(topLine).coerceIn(0, length())
    }

    fun setTextChangeListener(listener: ((NativeEditorView) -> Unit)?) { textChangeListener = listener }
    fun setSelectionChangeListener(listener: ((Int, Int) -> Unit)?) { selectionChangeListener = listener }
    fun setViewportChangeListener(listener: ((Int) -> Unit)?) { viewportChangeListener = listener }

    /** Current viewport anchor, for callers that want to read it without waiting for a callback. */
    fun currentViewportAnchor(): Int = topOfViewportOffset()

    fun setEditorTextSize(size: EditorTextSize) {
        if (appliedEditorTextSize == size) return
        appliedEditorTextSize = size
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, size.sp.toFloat())
        setLineSpacing(0f, size.lineHeightSp.toFloat() / size.sp.toFloat())
        invalidate()
    }

    fun beginTransaction() {
        if (transactionDepth == 0) {
            transactionBeforeText = text?.toString().orEmpty()
            transactionBeforeSelectionStart = selectionStart
            transactionBeforeSelectionEnd = selectionEnd
            transactionBeforeTitleBoundary = titleBoundary
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
            recordUndo(EditOperation(prefix, before.substring(prefix, removedEnd), insertedEnd - prefix, null, transactionBeforeSelectionStart, transactionBeforeSelectionEnd, selectionStart, selectionEnd, transactionBeforeTitleBoundary, titleBoundary))
            textChangeListener?.invoke(this)
            // A transaction that actually changed text is a programmatic edit the user
            // asked for (e.g. a formatting toolbar button) — same intent as typing.
            followCaret = true
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
        val beforeBoundary = titleBoundary
        val removed = text?.subSequence(safeStart, safeEnd)?.toString().orEmpty()
        val inserted = replacement.toString()
        val shouldBatch = transactionDepth == 0
        if (shouldBatch) beginBatchEdit()
        internalMutation = true
        try {
            text?.replace(safeStart, safeEnd, inserted)
            if (structuredDocument) updateTitleBoundaryForEdit(safeStart, safeEnd - safeStart, inserted.length, beforeBoundary)
            val targetStart = (selectionStart ?: safeStart + inserted.length).coerceIn(0, length())
            val targetEnd = (selectionEnd ?: targetStart).coerceIn(targetStart, length())
            setSelection(targetStart, targetEnd)
        } finally {
            internalMutation = false
            if (shouldBatch) endBatchEdit()
        }
        if (transactionDepth == 0) {
            ensureStructuredSeparator()
            recordUndo(EditOperation(safeStart, removed, inserted.length, null, beforeStart, beforeEnd, selectionStart ?: safeStart + inserted.length, selectionEnd ?: selectionStart ?: safeStart + inserted.length, beforeBoundary, titleBoundary))
            redoStack.clear()
            textChangeListener?.invoke(this)
            // Same reasoning as the transaction path above: a direct programmatic edit
            // is still something the user asked for.
            followCaret = true
        }
        invalidate()
    }

    /**
     * Sets the editor's text, caret, and (optionally) the scroll position — as three
     * independent things.
     *
     * [viewportAnchor], when provided, is the offset of the character that should end
     * up at the top of the viewport after this call — i.e. "what the user was looking
     * at", which may be far from the caret if they scrolled to read without tapping.
     * When omitted, this scrolls to the caret instead (the old default-to-end / open a
     * document behavior), which is what you want for brand-new documents or when there
     * is no prior scroll position to restore.
     */
    fun setStructuredDocument(
        title: String,
        content: String,
        selectionStart: Int? = null,
        selectionEnd: Int? = selectionStart,
        viewportAnchor: Int? = null
    ) {
        val doc = EditorNoteDocument(EditorNoteDocument.normalize(title), EditorNoteDocument.normalize(content))
        structuredDocument = true
        titleBoundary = doc.titleBoundary
        hint = "Title"
        val start = selectionStart ?: doc.editableText.length
        val end = selectionEnd ?: start
        setEditorText(doc.editableText, start, end, viewportAnchor)
        applyTitleSpans()
    }

    fun setPlainEditorText(
        value: CharSequence,
        selectionStart: Int = value.length,
        selectionEnd: Int = selectionStart,
        viewportAnchor: Int? = null
    ) {
        structuredDocument = false
        titleBoundary = 0
        hint = null
        setEditorText(value, selectionStart, selectionEnd, viewportAnchor)
    }

    fun titleText(): String = if (!structuredDocument) "" else EditorNoteDocument.fromEditable(text, titleBoundary).title
    fun contentText(): String = if (!structuredDocument) text?.toString().orEmpty() else EditorNoteDocument.fromEditable(text, titleBoundary).content
    fun titleBoundaryOffset(): Int = titleBoundary
    fun isCaretInTitle(): Boolean = structuredDocument && selectionStart <= titleBoundary
    fun setSectionChangeListener(listener: ((Boolean) -> Unit)?) { sectionChangeListener = listener }

    fun setEditorText(
        value: CharSequence,
        selectionStart: Int = value.length,
        selectionEnd: Int = selectionStart,
        viewportAnchor: Int? = null
    ) {
        EditorDiagnosticLog.log("SET_TEXT", "setEditorText CALLED  requestedSelection=$selectionStart-$selectionEnd  requestedViewportAnchor=$viewportAnchor  value.length=${value.length}  (default-to-end used = ${selectionStart == value.length})  identity=${System.identityHashCode(this)}")
        beginBatchEdit()
        internalMutation = true
        try {
            setText(value)
            val safeStart = selectionStart.coerceIn(0, length())
            val safeEnd = selectionEnd.coerceIn(safeStart, length())
            setSelection(safeStart, safeEnd)
            EditorDiagnosticLog.log("SET_TEXT", "setEditorText applied  safeSelection=$safeStart-$safeEnd")
            undoStack.clear(); redoStack.clear()
            if (structuredDocument) {
                ensureStructuredSeparator()
                applyTitleSpans()
            }
        } finally {
            internalMutation = false
            endBatchEdit()
        }
        invalidate()
        if (viewportAnchor != null) {
            // Restoring a previously-saved reading position — this is the platform
            // opening/recreating the view (app resume, Compose recomposition after
            // Back, etc.), not the user doing anything to the caret right now. Turn
            // follow mode off *before* the pending restore runs, so that neither the
            // setSelection() call above nor any focus/layout/IME callback that fires
            // in between now and applyPendingViewportAnchorRestore() can drag the
            // screen back to the caret and stomp the restore.
            followCaret = false
            pendingViewportAnchorRestore = viewportAnchor.coerceIn(0, length())
            post { applyPendingViewportAnchorRestore() }
        } else {
            // No saved viewport to restore (fresh document, new file opened, etc.) —
            // fall back to scrolling to the caret, same as opening any new document.
            followCaret = true
            post { bringPointIntoView(selectionStart.coerceIn(0, length())) }
        }
    }

    private fun applyPendingViewportAnchorRestore() {
        val anchor = pendingViewportAnchorRestore ?: return
        pendingViewportAnchorRestore = null
        val lay = layout ?: run {
            // Layout not ready yet on this pass; try again on the next one rather than
            // silently dropping the restore.
            pendingViewportAnchorRestore = anchor
            post { applyPendingViewportAnchorRestore() }
            return
        }
        val line = lay.getLineForOffset(anchor.coerceIn(0, length()))
        val target = (lay.getLineTop(line) - paddingTop)
        internalMutation = true
        try {
            scrollTo(scrollX, target.coerceIn(0, maxScrollY()))
        } finally {
            internalMutation = false
        }
    }

    fun undo() {
        val operation = undoStack.removeLastOrNull() ?: return
        val currentInserted = readText(operation.start, operation.insertedLength)
        internalMutation = true
        beginBatchEdit()
        try {
            text?.replace(operation.start.coerceIn(0, length()), (operation.start + operation.insertedLength).coerceIn(operation.start, length()), operation.removed)
            if (structuredDocument) titleBoundary = operation.beforeTitleBoundary.coerceIn(0, length())
            val safeStart = operation.beforeSelectionStart.coerceIn(0, length())
            setSelection(safeStart, operation.beforeSelectionEnd.coerceIn(safeStart, length()))
        } finally {
            internalMutation = false
            endBatchEdit()
        }
        redoStack.addLast(operation.copy(insertedContent = currentInserted))
        trimHistory()
        if (structuredDocument) { ensureStructuredSeparator(); applyTitleSpans() }
        textChangeListener?.invoke(this)
        // undo() sets internalMutation itself, so afterTextChanged's own followCaret
        // reset above never runs for this path — set it here instead. The user asked
        // to undo, so they want to see where that landed.
        followCaret = true
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
            if (structuredDocument) titleBoundary = operation.afterTitleBoundary.coerceIn(0, length())
            val safeStart = operation.afterSelectionStart.coerceIn(0, length())
            setSelection(safeStart, operation.afterSelectionEnd.coerceIn(safeStart, length()))
        } finally {
            internalMutation = false
            endBatchEdit()
        }
        undoStack.addLast(operation.copy(insertedContent = null, insertedLength = inserted.length))
        trimHistory()
        if (structuredDocument) { ensureStructuredSeparator(); applyTitleSpans() }
        textChangeListener?.invoke(this)
        // Same reasoning as undo(): this path sets internalMutation itself, so it
        // never reaches the text-changed listener's followCaret reset.
        followCaret = true
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

    private fun updateTitleBoundaryForEdit(start: Int, removedLength: Int, insertedLength: Int, oldBoundary: Int) {
        if (!structuredDocument) return
        val removedEnd = start + removedLength
        titleBoundary = when {
            start < oldBoundary && removedEnd <= oldBoundary -> oldBoundary + insertedLength - removedLength
            start <= oldBoundary && removedEnd >= oldBoundary -> start + insertedLength
            else -> oldBoundary
        }.coerceIn(0, length())
    }

    private fun ensureStructuredSeparator() {
        if (!structuredDocument) return
        if (titleBoundary >= length()) {
            internalMutation = true
            try { text?.insert(length(), "\n") } finally { internalMutation = false }
        } else if (text?.getOrNull(titleBoundary) != '\n') {
            internalMutation = true
            try { text?.insert(titleBoundary, "\n") } finally { internalMutation = false }
        }
    }

    private fun applyTitleSpans() {
        if (!structuredDocument) return
        val editable = text ?: return
        editable.getSpans(0, editable.length, android.text.style.CharacterStyle::class.java)
            .forEach { editable.removeSpan(it) }
        if (titleBoundary > 0) {
            editable.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, titleBoundary, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            editable.setSpan(android.text.style.RelativeSizeSpan(1.12f), 0, titleBoundary, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun drawTitleDivider(canvas: Canvas) {
        val lay = layout ?: return
        val line = lay.getLineForOffset(titleBoundary.coerceIn(0, length()))
        val y = lay.getLineBottom(line).toFloat() + dp(5).toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = currentTextColor and 0x55FFFFFF
            strokeWidth = dp(1).toFloat()
        }
        canvas.drawLine(paddingLeft.toFloat(), y, (width - paddingRight).toFloat(), y, paint)
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