package com.example.ui.vault

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.ViewConfiguration
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.data.model.ClipboardCardProjection
import com.example.domain.RelativeTimeFormatter

internal data class VaultRecyclerColors(
    val surface: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val primary: Int,
    val primaryContainer: Int,
    val outlineVariant: Int,
    val pinned: Int,
    val sensitive: Int
)

internal data class VaultRecyclerCallbacks(
    val onToggleSelect: (Long) -> Unit,
    val onLongPress: (Long, Float) -> Unit,
    val onCopy: (Long) -> Unit,
    val onToggleRevealSensitive: (Long) -> Unit,
    val onReorder: (List<Long>) -> Unit
)

/**
 * View-based Vault list. ItemTouchHelper owns the drag transaction; Compose only
 * supplies immutable card state and receives semantic callbacks.
 */
internal class VaultRecyclerView(context: Context) : RecyclerView(context) {
    private val listAdapter = VaultAdapter(context)
    private val dragHelper: ItemTouchHelper
    private var callbacks = VaultRecyclerCallbacks({}, { _, _ -> }, {}, {}, {})
    private var currentColors = VaultRecyclerColors(
        surface = Color.WHITE,
        onSurface = Color.BLACK,
        onSurfaceVariant = Color.DKGRAY,
        primary = Color.DKGRAY,
        primaryContainer = Color.LTGRAY,
        outlineVariant = Color.LTGRAY,
        pinned = Color.rgb(21, 128, 61),
        sensitive = Color.rgb(217, 119, 6)
    )
    private var showPinnedFirst = false
    private var searchActive = false
    private var dragging = false
    private var deferredCards: List<ClipboardCardProjection>? = null
    private val spacingDecoration = VaultSpacingDecoration(dp(8), dp(1))

    init {
        layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        adapter = listAdapter
        setHasFixedSize(false)
        clipToPadding = false
        addItemDecoration(spacingDecoration)
        dragHelper = ItemTouchHelper(DragCallback())
        dragHelper.attachToRecyclerView(this)
    }

    fun render(
        cards: List<ClipboardCardProjection>,
        selectedIds: Set<Long>,
        revealedSensitiveIds: Set<Long>,
        isMaskingEnabled: Boolean,
        showPinnedFirst: Boolean,
        searchQuery: String,
        colors: VaultRecyclerColors,
        callbacks: VaultRecyclerCallbacks
    ) {
        this.callbacks = callbacks
        this.currentColors = colors
        spacingDecoration.setDividerColor(colors.outlineVariant)
        invalidateItemDecorations()
        this.showPinnedFirst = showPinnedFirst
        this.searchActive = searchQuery.isNotBlank()
        listAdapter.setVisualState(selectedIds, revealedSensitiveIds, isMaskingEnabled, colors, showPinnedFirst)

        if (dragging) {
            deferredCards = cards
            return
        }

        if (listAdapter.ids() == cards.map { it.id }) {
            listAdapter.replaceDataWithoutChangingOrder(cards)
        } else {
            listAdapter.replace(cards)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        dragHelper.attachToRecyclerView(this)
    }

    override fun onDetachedFromWindow() {
        dragging = false
        dragHelper.attachToRecyclerView(null)
        super.onDetachedFromWindow()
    }

    private inner class DragCallback : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
        override fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: ViewHolder
        ): Int {
            if (searchActive) return makeMovementFlags(0, 0)
            return super.getMovementFlags(recyclerView, viewHolder)
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: ViewHolder,
            target: ViewHolder
        ): Boolean {
            if (searchActive) return false
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from == NO_POSITION || to == NO_POSITION) return false
            if (showPinnedFirst && listAdapter.isPinned(from) != listAdapter.isPinned(to)) {
                return false
            }
            listAdapter.moveItem(from, to)
            return true
        }

        override fun onSwiped(viewHolder: ViewHolder, direction: Int) {
            // Swiping is intentionally disabled. Reorder starts only on the :: handle.
        }

        override fun isLongPressDragEnabled(): Boolean = false

        override fun isItemViewSwipeEnabled(): Boolean = false

        override fun onSelectedChanged(viewHolder: ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder is VaultViewHolder) {
                dragging = true
                viewHolder.setDragging(true, currentColors)
            }
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            if (viewHolder is VaultViewHolder) {
                viewHolder.setDragging(false, currentColors)
            }
            if (dragging) {
                dragging = false
                val finalIds = listAdapter.ids()
                callbacks.onReorder(finalIds)
                val deferred = deferredCards
                deferredCards = null
                if (deferred != null && deferred.map { it.id } == finalIds) {
                    listAdapter.replaceDataWithoutChangingOrder(deferred)
                }
            }
        }
    }

    private inner class VaultAdapter(private val context: Context) : Adapter<VaultViewHolder>() {
        private val cards = mutableListOf<ClipboardCardProjection>()
        private var selectedIds: Set<Long> = emptySet()
        private var revealedSensitiveIds: Set<Long> = emptySet()
        private var maskingEnabled = false
        private var colors = currentColors
        private var showPinnedFirst = false

        init {
            setHasStableIds(true)
        }

        override fun getItemId(position: Int): Long = cards[position].id

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VaultViewHolder {
            return VaultViewHolder(VaultRowView(context))
        }

        override fun getItemCount(): Int = cards.size

        override fun onBindViewHolder(holder: VaultViewHolder, position: Int) {
            holder.bind(
                card = cards[position],
                selected = selectedIds.contains(cards[position].id),
                isSensitiveRevealed = revealedSensitiveIds.contains(cards[position].id),
                isMaskingEnabled = maskingEnabled,
                showGroupBoundary = showPinnedFirst && position > 0 && cards[position - 1].pinned != cards[position].pinned,
                colors = colors,
                callbacks = callbacks,
                dragHelper = dragHelper
            )
        }

        fun replace(value: List<ClipboardCardProjection>) {
            cards.clear()
            cards.addAll(value)
            notifyDataSetChanged()
        }

        fun replaceDataWithoutChangingOrder(value: List<ClipboardCardProjection>) {
            if (cards.map { it.id } != value.map { it.id }) return
            cards.indices.forEach { index -> cards[index] = value[index] }
            notifyItemRangeChanged(0, cards.size, PAYLOAD_STATE)
        }

        fun setVisualState(
            selectedIds: Set<Long>,
            revealedSensitiveIds: Set<Long>,
            maskingEnabled: Boolean,
            colors: VaultRecyclerColors,
            showPinnedFirst: Boolean
        ) {
            val changed = this.selectedIds != selectedIds ||
                this.revealedSensitiveIds != revealedSensitiveIds ||
                this.maskingEnabled != maskingEnabled ||
                this.colors != colors ||
                this.showPinnedFirst != showPinnedFirst
            this.selectedIds = selectedIds
            this.revealedSensitiveIds = revealedSensitiveIds
            this.maskingEnabled = maskingEnabled
            this.colors = colors
            this.showPinnedFirst = showPinnedFirst
            if (changed && !dragging && cards.isNotEmpty()) {
                notifyItemRangeChanged(0, cards.size, PAYLOAD_STATE)
            }
        }

        fun moveItem(from: Int, to: Int) {
            if (from !in cards.indices || to !in cards.indices) return
            val moved = cards.removeAt(from)
            cards.add(to, moved)
            notifyItemMoved(from, to)
        }

        fun ids(): List<Long> = cards.map { it.id }

        fun isPinned(position: Int): Boolean = cards.getOrNull(position)?.pinned == true
    }

    private inner class VaultViewHolder(itemView: VaultRowView) : ViewHolder(itemView) {
        private val row = itemView

        fun bind(
            card: ClipboardCardProjection,
            selected: Boolean,
            isSensitiveRevealed: Boolean,
            isMaskingEnabled: Boolean,
            showGroupBoundary: Boolean,
            colors: VaultRecyclerColors,
            callbacks: VaultRecyclerCallbacks,
            dragHelper: ItemTouchHelper
        ) {
            row.bind(
                card = card,
                selected = selected,
                isSensitiveRevealed = isSensitiveRevealed,
                isMaskingEnabled = isMaskingEnabled,
                showGroupBoundary = showGroupBoundary,
                colors = colors,
                onToggleSelect = { callbacks.onToggleSelect(card.id) },
                onLongPress = { callbacks.onLongPress(card.id, anchorY(row)) },
                onCopy = { callbacks.onCopy(card.id) },
                onToggleRevealSensitive = { callbacks.onToggleRevealSensitive(card.id) },
                onStartDrag = { dragHelper.startDrag(this) }
            )
        }

        fun setDragging(isDragging: Boolean, colors: VaultRecyclerColors) {
            row.setDragging(isDragging, colors)
        }

        private fun anchorY(view: View): Float {
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            return location[1] + view.height / 2f
        }
    }

    private class VaultSpacingDecoration(
        private val spacing: Int,
        private val dividerHeight: Int
    ) : ItemDecoration() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var dividerColor: Int = Color.TRANSPARENT

        fun setDividerColor(color: Int) {
            dividerColor = color
        }

        override fun getItemOffsets(
            outRect: android.graphics.Rect,
            view: View,
            parent: RecyclerView,
            state: State
        ) {
            outRect.bottom = spacing
        }

        override fun onDraw(canvas: Canvas, parent: RecyclerView, state: State) {
            val adapter = parent.adapter as? VaultAdapter ?: return
            paint.color = dividerColor
            for (index in 0 until parent.childCount) {
                val child = parent.getChildAt(index)
                val position = parent.getChildAdapterPosition(child)
                if (position <= 0 || position == NO_POSITION) continue
                if (adapter.isPinned(position) != adapter.isPinned(position - 1)) {
                    val y = child.top - spacing / 2f
                    canvas.drawRect(
                        parent.paddingLeft.toFloat(),
                        y - dividerHeight / 2f,
                        (parent.width - parent.paddingRight).toFloat(),
                        y + dividerHeight / 2f,
                        paint
                    )
                }
            }
        }
    }

    private class VaultRowView(context: Context) : LinearLayout(context) {
        private val checkbox: CheckBox
        private val preview: TextView
        private val sensitiveRow: LinearLayout
        private val sensitiveLabel: TextView
        private val revealButton: ImageButton
        private val timestamp: TextView
        private val pinnedLabel: TextView
        private val copyButton: ImageButton
        private val dragHandle: DragHandleView
        private val cardContent: LinearLayout
        private val density = resources.displayMetrics.density
        private var baseColors: VaultRecyclerColors? = null
        private var selectedState = false
        private var touchDown = false
        private var longPressFired = false
        private var downX = 0f
        private var downY = 0f
        private var pendingLongPress: Runnable? = null
        private var pressAnimator: ValueAnimator? = null
        private var pressProgress = 0f
        private val longPressHandler = Handler(Looper.getMainLooper())
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

        init {
            orientation = VERTICAL
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(dp(6), dp(6), dp(8), dp(6))
            minimumHeight = dp(72)
            isClickable = true
            isFocusable = true

            val contentRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            addView(contentRow)

            checkbox = CheckBox(context).apply {
                layoutParams = LayoutParams(dp(48), ViewGroup.LayoutParams.WRAP_CONTENT)
                setPadding(0, 0, 0, 0)
                minWidth = dp(48)
                minHeight = dp(48)
                contentDescription = "Select card"
            }
            contentRow.addView(checkbox)

            cardContent = LinearLayout(context).apply {
                orientation = VERTICAL
                minimumHeight = dp(58)
                layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(4), dp(3), dp(4), dp(3))
                }
            }
            contentRow.addView(cardContent)

            preview = TextView(context).apply {
                layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                textSize = 13.5f
                setLineSpacing(0f, 1.0f)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            cardContent.addView(preview)

            sensitiveRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            sensitiveLabel = TextView(context).apply {
                layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                text = "Sensitive"
                textSize = 11f
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            }
            sensitiveRow.addView(sensitiveLabel)
            revealButton = ImageButton(context).apply {
                layoutParams = LayoutParams(dp(32), dp(32))
                setPadding(dp(4), dp(4), dp(4), dp(4))
                setImageResource(android.R.drawable.ic_menu_view)
                contentDescription = "Reveal sensitive content"
                background = null
            }
            sensitiveRow.addView(revealButton)
            cardContent.addView(sensitiveRow)

            val metadataRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(2)
                }
            }
            timestamp = TextView(context).apply {
                layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                textSize = 11f
            }
            metadataRow.addView(timestamp)
            pinnedLabel = TextView(context).apply {
                layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                text = "Pinned"
                textSize = 11f
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            }
            metadataRow.addView(pinnedLabel)
            cardContent.addView(metadataRow)

            copyButton = ImageButton(context).apply {
                layoutParams = LayoutParams(dp(48), dp(48))
                setPadding(dp(10), dp(10), dp(10), dp(10))
                setImageResource(com.example.R.drawable.ic_copy)
                contentDescription = "Copy item"
                background = null
            }
            contentRow.addView(copyButton)

            dragHandle = DragHandleView(context).apply {
                layoutParams = LayoutParams(dp(48), dp(48))
                contentDescription = "Drag handle"
                isClickable = true
                isFocusable = false
            }
            contentRow.addView(dragHandle)
        }

        @SuppressLint("ClickableViewAccessibility")
        fun bind(
            card: ClipboardCardProjection,
            selected: Boolean,
            isSensitiveRevealed: Boolean,
            isMaskingEnabled: Boolean,
            showGroupBoundary: Boolean,
            colors: VaultRecyclerColors,
            onToggleSelect: () -> Unit,
            onLongPress: () -> Unit,
            onCopy: () -> Unit,
            onToggleRevealSensitive: () -> Unit,
            onStartDrag: () -> Unit
        ) {
            baseColors = colors
            selectedState = selected
            pressAnimator?.cancel()
            pressProgress = 0f
            setBackgroundDrawable(backgroundFor(colors, selected, false))
            setPadding(dp(6), if (showGroupBoundary) dp(12) else dp(6), dp(8), dp(6))
            minimumHeight = dp(if (showGroupBoundary) 78 else 72)
            checkbox.buttonTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(colors.primary, colors.onSurfaceVariant)
            )
            checkbox.setOnClickListener { onToggleSelect() }
            checkbox.isChecked = selected

            val masked = card.isSensitive && isMaskingEnabled && !isSensitiveRevealed
            preview.text = if (masked) "•••••••••••••••• (Sensitive)" else card.preview
            preview.setTextColor(if (masked) colors.sensitive else colors.onSurface)
            preview.setTypeface(Typeface.DEFAULT, if (masked) Typeface.BOLD else Typeface.NORMAL)
            sensitiveRow.visibility = if (card.isSensitive) VISIBLE else GONE
            sensitiveLabel.visibility = if (card.isSensitive && !masked) VISIBLE else GONE
            revealButton.visibility = if (card.isSensitive) VISIBLE else GONE
            sensitiveLabel.setTextColor(colors.sensitive)
            revealButton.imageTintList = ColorStateList.valueOf(colors.onSurfaceVariant)
            revealButton.contentDescription = if (masked) "Reveal sensitive content" else "Mask sensitive content"
            revealButton.setOnClickListener { onToggleRevealSensitive() }

            timestamp.text = RelativeTimeFormatter.format(card.createdAtMillis)
            timestamp.setTextColor(colors.onSurfaceVariant)
            pinnedLabel.visibility = if (card.pinned) VISIBLE else GONE
            pinnedLabel.setTextColor(colors.pinned)
            copyButton.imageTintList = ColorStateList.valueOf(colors.onSurfaceVariant)
            copyButton.setOnClickListener { onCopy() }
            dragHandle.setDotColor(colors.onSurfaceVariant)

            setOnClickListener {
                if (!longPressFired) onToggleSelect()
            }
            setOnLongClickListener(null)
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        pendingLongPress?.let(longPressHandler::removeCallbacks)
                        touchDown = true
                        longPressFired = false
                        downX = event.rawX
                        downY = event.rawY
                        startPressFeedback()
                        val callback = Runnable {
                            if (touchDown && !longPressFired) {
                                longPressFired = true
                                stopPressFeedback()
                                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                onLongPress()
                            }
                        }
                        pendingLongPress = callback
                        longPressHandler.postDelayed(callback, LONG_PRESS_DELAY_MS)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (kotlin.math.abs(event.rawX - downX) > touchSlop ||
                            kotlin.math.abs(event.rawY - downY) > touchSlop
                        ) {
                            pendingLongPress?.let(longPressHandler::removeCallbacks)
                            stopPressFeedback()
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        pendingLongPress?.let(longPressHandler::removeCallbacks)
                        touchDown = false
                        stopPressFeedback()
                        if (event.actionMasked == MotionEvent.ACTION_UP) {
                            post { longPressFired = false }
                        } else {
                            longPressFired = false
                        }
                    }
                }
                false
            }
            dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onStartDrag()
                }
                true
            }
        }

        private fun startPressFeedback() {
            pressAnimator?.cancel()
            pressAnimator = ValueAnimator.ofFloat(pressProgress, 1f).apply {
                duration = LONG_PRESS_DELAY_MS
                interpolator = DecelerateInterpolator()
                addUpdateListener { animator ->
                    pressProgress = animator.animatedValue as Float
                    updatePressBackground()
                }
                start()
            }
        }

        private fun stopPressFeedback() {
            pressAnimator?.cancel()
            pressAnimator = ValueAnimator.ofFloat(pressProgress, 0f).apply {
                duration = 90L
                interpolator = DecelerateInterpolator()
                addUpdateListener { animator ->
                    pressProgress = animator.animatedValue as Float
                    updatePressBackground()
                }
                start()
            }
        }

        private fun updatePressBackground() {
            val colors = baseColors ?: return
            if (pressProgress <= 0.001f) {
                setBackgroundDrawable(backgroundFor(colors, selectedState, false))
                return
            }
            val base = backgroundColor(colors, selectedState)
            val target = blendColors(base, colors.primaryContainer, 0.22f)
            val current = ArgbEvaluator().evaluate(pressProgress, base, target) as Int
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor(current)
                setStroke(dp(1 + kotlin.math.round(pressProgress).toInt()), colors.primary)
            }.also(::setBackgroundDrawable)
        }

        fun setDragging(isDragging: Boolean, colors: VaultRecyclerColors) {
            setBackgroundDrawable(backgroundFor(colors, isSelected = selectedState, isDragging = isDragging))
            elevation = if (isDragging) dp(6).toFloat() else 0f
            scaleX = if (isDragging) 1.015f else 1f
            scaleY = if (isDragging) 1.015f else 1f
        }

        private fun backgroundColor(colors: VaultRecyclerColors, isSelected: Boolean): Int {
            return if (isSelected) withAlpha(colors.primaryContainer, 90) else colors.surface
        }

        private fun blendColors(from: Int, to: Int, amount: Float): Int {
            return ArgbEvaluator().evaluate(amount.coerceIn(0f, 1f), from, to) as Int
        }

        private fun backgroundFor(
            colors: VaultRecyclerColors,
            isSelected: Boolean,
            isDragging: Boolean
        ): GradientDrawable {
            val background = GradientDrawable()
            background.shape = GradientDrawable.RECTANGLE
            background.cornerRadius = dp(16).toFloat()
            background.setColor(
                when {
                    isDragging -> colors.primaryContainer
                    else -> backgroundColor(colors, isSelected)
                }
            )
            when {
                isDragging -> background.setStroke(dp(2), colors.primary)
                isSelected -> background.setStroke(dp(2), colors.primary)
            }
            return background
        }

        private class DragHandleView(context: Context) : View(context) {
            private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            private val density = resources.displayMetrics.density
            private var dotColor = Color.DKGRAY

            init {
                setWillNotDraw(false)
            }

            fun setDotColor(color: Int) {
                dotColor = color
                invalidate()
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                dotPaint.color = dotColor
                val centerX = width / 2f
                val columnOffset = 4f * density
                val rowOffset = 6f * density
                val centerY = height / 2f
                val radius = 1.8f * density
                for (column in -1..1 step 2) {
                    for (row in -1..1) {
                        canvas.drawCircle(
                            centerX + column * columnOffset,
                            centerY + row * rowOffset,
                            radius,
                            dotPaint
                        )
                    }
                }
            }
        }

        private fun withAlpha(color: Int, alpha: Int): Int {
            return (color and 0x00FFFFFF) or ((alpha.coerceIn(0, 255)) shl 24)
        }

        private fun dp(value: Int): Int = (value * density + 0.5f).toInt()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        const val PAYLOAD_STATE = "vault_state"
        const val LONG_PRESS_DELAY_MS = 280L
    }
}
