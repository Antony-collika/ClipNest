package com.clipnest.ui.note

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.clipnest.R
import com.clipnest.data.model.NoteCardProjection
import com.clipnest.data.model.Topic

internal data class NoteRecyclerColors(
    val surface: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val primary: Int,
    val primaryContainer: Int,
    val outlineVariant: Int
)

internal data class NoteHeaderState(
    val title: String,
    val allNotesLabel: String,
    val noteTabLabel: String,
    val topics: List<Topic>,
    val colors: androidx.compose.material3.ColorScheme,
    val typography: androidx.compose.material3.Typography,
    val onTopicSelected: (Long?) -> Unit
)

internal data class NoteRecyclerCallbacks(
    val onToggleSelect: (Long) -> Unit,
    val onLongPress: (Long, Float) -> Unit,
    val onEdit: (Long) -> Unit
)

internal class NoteRecyclerView(context: Context) : RecyclerView(context) {
    private val headerAdapter = HeaderAdapter(context)
    private val listAdapter = NoteAdapter(context)
    private val concatAdapter = androidx.recyclerview.widget.ConcatAdapter(headerAdapter, listAdapter)
    private var callbacks = NoteRecyclerCallbacks(
        onToggleSelect = {},
        onLongPress = { _, _ -> },
        onEdit = {}
    )
    private var currentColors = NoteRecyclerColors(
        surface = Color.WHITE,
        onSurface = Color.BLACK,
        onSurfaceVariant = Color.DKGRAY,
        primary = Color.DKGRAY,
        primaryContainer = Color.LTGRAY,
        outlineVariant = Color.LTGRAY
    )
    private val spacingDecoration = NoteSpacingDecoration(dp(8))

    init {
        layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        adapter = concatAdapter
        setHasFixedSize(false)
        clipToPadding = false
        addItemDecoration(spacingDecoration)
    }

    fun render(
        header: NoteHeaderState,
        notes: List<NoteCardProjection>,
        selectedIds: Set<Long>,
        pinnedIds: Set<Long>,
        colors: NoteRecyclerColors,
        callbacks: NoteRecyclerCallbacks
    ) {
        this.callbacks = callbacks
        val decorationChanged = currentColors.outlineVariant != colors.outlineVariant
        currentColors = colors
        if (decorationChanged) {
            spacingDecoration.setSpacingColor(colors.outlineVariant)
            invalidateItemDecorations()
        }

        headerAdapter.setState(header)
        listAdapter.setVisualState(selectedIds, pinnedIds, colors)
        if (listAdapter.isSameData(notes)) return

        if (listAdapter.ids() == notes.map { it.id }) {
            listAdapter.replaceDataWithoutChangingOrder(notes)
        } else {
            listAdapter.replace(notes)
        }
    }

    private inner class HeaderAdapter(private val context: Context) : RecyclerView.Adapter<HeaderViewHolder>() {
        private var state: NoteHeaderState? = null
        init { setHasStableIds(true) }
        override fun getItemCount(): Int = 1
        override fun getItemId(position: Int): Long = Long.MIN_VALUE
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderViewHolder =
            HeaderViewHolder(ComposeView(context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            })
        override fun onBindViewHolder(holder: HeaderViewHolder, position: Int) {
            state?.let { holder.bind(it) }
        }
        fun setState(value: NoteHeaderState) {
            if (state == value) return
            state = value
            if (itemCount == 1) notifyItemChanged(0)
        }
    }

    private inner class HeaderViewHolder(private val composeView: ComposeView) : RecyclerView.ViewHolder(composeView) {
        fun bind(state: NoteHeaderState) {
            composeView.setContent {
                MaterialTheme(colorScheme = state.colors, typography = state.typography) {
                    NoteHeaderContent(state)
                }
            }
        }
    }

    @Composable
    private fun NoteHeaderContent(state: NoteHeaderState) {
        var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 0.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.layout.Box {
                TextButton(onClick = { expanded = true }) { Text(state.title) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text(state.allNotesLabel) },
                        onClick = { expanded = false; state.onTopicSelected(null) }
                    )
                    state.topics.forEach { topic ->
                        DropdownMenuItem(
                            text = { Text(topic.name) },
                            onClick = { expanded = false; state.onTopicSelected(topic.id) }
                        )
                    }
                }
            }
            Text(text = state.noteTabLabel, style = MaterialTheme.typography.titleMedium)
        }
    }

    private inner class NoteAdapter(private val context: Context) : ListAdapter<NoteCardProjection, NoteViewHolder>(DIFF_CALLBACK) {
        private var selectedIds: Set<Long> = emptySet()
        private var pinnedIds: Set<Long> = emptySet()
        private var colors = currentColors

        init {
            setHasStableIds(true)
        }

        override fun getItemId(position: Int): Long = getItem(position).id

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
            return NoteViewHolder(NoteRowView(context))
        }

        override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
            val note = getItem(position)
            holder.bind(
                note = note,
                selected = selectedIds.contains(note.id),
                pinned = pinnedIds.contains(note.id),
                colors = colors,
                callbacks = callbacks
            )
        }

        fun isSameData(value: List<NoteCardProjection>): Boolean = currentList == value

        fun replace(value: List<NoteCardProjection>) {
            submitList(value)
        }

        fun replaceDataWithoutChangingOrder(value: List<NoteCardProjection>) {
            if (currentList.map { it.id } != value.map { it.id }) return
            submitList(value)
        }

        fun setVisualState(
            selectedIds: Set<Long>,
            pinnedIds: Set<Long>,
            colors: NoteRecyclerColors
        ) {
            val changed = this.selectedIds != selectedIds ||
                this.pinnedIds != pinnedIds ||
                this.colors != colors
            this.selectedIds = selectedIds
            this.pinnedIds = pinnedIds
            this.colors = colors
            if (changed && currentList.isNotEmpty()) {
                notifyItemRangeChanged(0, currentList.size, PAYLOAD_STATE)
            }
        }

        fun ids(): List<Long> = currentList.map { it.id }
    }

    private object NoteDiffCallback : DiffUtil.ItemCallback<NoteCardProjection>() {
        override fun areItemsTheSame(oldItem: NoteCardProjection, newItem: NoteCardProjection): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: NoteCardProjection, newItem: NoteCardProjection): Boolean =
            oldItem == newItem
    }

    private companion object {
        val DIFF_CALLBACK = NoteDiffCallback
        const val PAYLOAD_STATE = "note_state"
        const val LONG_PRESS_DELAY_MS = 280L
    }

    private inner class NoteViewHolder(itemView: NoteRowView) : ViewHolder(itemView) {
        private val row = itemView

        fun bind(
            note: NoteCardProjection,
            selected: Boolean,
            pinned: Boolean,
            colors: NoteRecyclerColors,
            callbacks: NoteRecyclerCallbacks
        ) {
            row.bind(
                note = note,
                selected = selected,
                pinned = pinned,
                colors = colors,
                onToggleSelect = { callbacks.onToggleSelect(note.id) },
                onLongPress = { callbacks.onLongPress(note.id, anchorY(row)) },
                onEdit = { callbacks.onEdit(note.id) }
            )
        }

        private fun anchorY(view: View): Float {
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            return location[1] + view.height / 2f
        }
    }

    private class NoteSpacingDecoration(
        private val spacing: Int
    ) : ItemDecoration() {
        private var spacingColor: Int = Color.TRANSPARENT

        fun setSpacingColor(color: Int) {
            spacingColor = color
        }

        override fun getItemOffsets(
            outRect: android.graphics.Rect,
            view: View,
            parent: RecyclerView,
            state: State
        ) {
            outRect.bottom = if (parent.getChildAdapterPosition(view) == 0) spacing / 2 else spacing
        }
    }

    private class NoteRowView(context: Context) : LinearLayout(context) {
        private val checkbox: CheckBox
        private val contentColumn: LinearLayout
        private val title: TextView
        private val labels: TextView
        private val preview: TextView
        private val label: TextView
        private val editButton: ImageButton
        private val density = resources.displayMetrics.density
        private var selectedState = false
        private var colors: NoteRecyclerColors? = null
        private var touchDown = false
        private var longPressFired = false
        private var downX = 0f
        private var downY = 0f
        private var pendingLongPress: Runnable? = null
        private val longPressHandler = Handler(Looper.getMainLooper())
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(dp(6), dp(6), dp(8), dp(6))
            minimumHeight = dp(72)
            isClickable = true
            isFocusable = true

            checkbox = CheckBox(context).apply {
                layoutParams = LayoutParams(dp(48), ViewGroup.LayoutParams.WRAP_CONTENT)
                setPadding(0, 0, 0, 0)
                minWidth = dp(48)
                minHeight = dp(48)
                contentDescription = context.getString(R.string.select_card)
            }
            addView(checkbox)

            contentColumn = LinearLayout(context).apply {
                orientation = VERTICAL
                layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(4), dp(3), dp(4), dp(3))
                }
            }
            addView(contentColumn)

            title = TextView(context).apply {
                layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                textSize = 16f
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            }
            contentColumn.addView(title)

            label = TextView(context).apply {
                layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(3)
                }
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                textSize = 12f
            }
            contentColumn.addView(label)

            preview = TextView(context).apply {
                layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(4)
                }
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                textSize = 14f
            }
            contentColumn.addView(preview)

            editButton = ImageButton(context).apply {
                layoutParams = LayoutParams(dp(48), dp(48))
                setPadding(dp(10), dp(10), dp(10), dp(10))
                setImageResource(android.R.drawable.ic_menu_edit)
                background = null
                contentDescription = context.getString(R.string.edit_note)
            }
            addView(editButton)
        }

        @SuppressLint("ClickableViewAccessibility")
        fun bind(
            note: NoteCardProjection,
            selected: Boolean,
            pinned: Boolean,
            colors: NoteRecyclerColors,
            onToggleSelect: () -> Unit,
            onLongPress: () -> Unit,
            onEdit: () -> Unit
        ) {
            this.colors = colors
            selectedState = selected
            setBackgroundDrawable(backgroundFor(colors, selected))

            checkbox.buttonTintList = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf()
                ),
                intArrayOf(colors.primary, colors.onSurfaceVariant)
            )
            checkbox.setOnClickListener { onToggleSelect() }
            checkbox.isChecked = selected

            title.text = note.title.ifBlank { context.getString(R.string.untitled) }
            title.setTextColor(colors.onSurface)

            label.text = note.topicLabels
            label.visibility = if (note.topicLabels.isBlank()) GONE else VISIBLE
            label.setTextColor(colors.primary)

            preview.text = note.preview.ifBlank { context.getString(R.string.untitled) }
            preview.setTextColor(colors.onSurfaceVariant)

            editButton.imageTintList = ColorStateList.valueOf(colors.onSurfaceVariant)
            editButton.setOnClickListener { onEdit() }

            setOnClickListener {
                if (!longPressFired) onToggleSelect()
            }
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        pendingLongPress?.let(longPressHandler::removeCallbacks)
                        touchDown = true
                        longPressFired = false
                        downX = event.rawX
                        downY = event.rawY
                        pendingLongPress = Runnable {
                            if (touchDown && !longPressFired) {
                                longPressFired = true
                                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                onLongPress()
                            }
                        }.also { longPressHandler.postDelayed(it, LONG_PRESS_DELAY_MS) }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (kotlin.math.abs(event.rawX - downX) > touchSlop ||
                            kotlin.math.abs(event.rawY - downY) > touchSlop
                        ) {
                            pendingLongPress?.let(longPressHandler::removeCallbacks)
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        pendingLongPress?.let(longPressHandler::removeCallbacks)
                        touchDown = false
                        if (event.actionMasked == MotionEvent.ACTION_UP) {
                            post { longPressFired = false }
                        } else {
                            longPressFired = false
                        }
                    }
                }
                false
            }
        }

        private fun backgroundFor(colors: NoteRecyclerColors, isSelected: Boolean): GradientDrawable {
            return GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor(if (isSelected) withAlpha(colors.primaryContainer, 90) else colors.surface)
            }
        }

        private fun withAlpha(color: Int, alpha: Int): Int {
            return (color and 0x00FFFFFF) or ((alpha.coerceIn(0, 255)) shl 24)
        }

        private fun dp(value: Int): Int = (value * density + 0.5f).toInt()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

}
