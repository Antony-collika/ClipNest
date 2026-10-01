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
import kotlin.math.roundToInt
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
    val outlineVariant: Int,
    val pinned: Int
)

internal enum class NoteViewMode { LIST, GRID }

internal data class NoteHeaderState(
    val title: String,
    val breadcrumb: String,
    val subTopics: List<Topic>,
    val allNotesLabel: String,
    val noteTabLabel: String,
    val topics: List<Topic>,
    val colors: androidx.compose.material3.ColorScheme,
    val typography: androidx.compose.material3.Typography,
    val viewMode: NoteViewMode,
    val pinnedLabel: String,
    val pinnedExpanded: Boolean,
    val onPinnedExpandedChanged: (Boolean) -> Unit,
    val todayLabel: String,
    val yesterdayLabel: String,
    val previous7DaysLabel: String,
    val previous30DaysLabel: String,
    val olderLabel: String,
    val onTopicSelected: (Long?) -> Unit,
    val onViewModeChanged: (NoteViewMode) -> Unit
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
        outlineVariant = Color.LTGRAY,
        pinned = Color.DKGRAY
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
        if (header.viewMode == NoteViewMode.GRID) {
            val grid = (layoutManager as? androidx.recyclerview.widget.GridLayoutManager)
                ?: androidx.recyclerview.widget.GridLayoutManager(context, 2).also { layoutManager = it }
            grid.spanSizeLookup = object : androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    if (position == 0) return 2
                    return if (listAdapter.isFullSpanPosition(position - 1)) 2 else 1
                }
            }
        } else if (layoutManager !is androidx.recyclerview.widget.LinearLayoutManager ||
            layoutManager is androidx.recyclerview.widget.GridLayoutManager) {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        }
        listAdapter.setVisualState(selectedIds, pinnedIds, colors)
        listAdapter.setSectionState(header, notes)
        val items = buildListItems(notes, pinnedIds.toSet(), header)
        listAdapter.replace(items, notes, header)
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
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 0.dp, vertical = 8.dp)
        ) {
            if (state.breadcrumb.isNotBlank()) {
                Text(
                    text = state.breadcrumb,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        state.onViewModeChanged(
                            if (state.viewMode == NoteViewMode.LIST) NoteViewMode.GRID else NoteViewMode.LIST
                        )
                    }) {
                        Text(if (state.viewMode == NoteViewMode.LIST) androidx.compose.ui.res.stringResource(R.string.note_grid_view) else androidx.compose.ui.res.stringResource(R.string.note_list_view))
                    }
                    Text(text = state.noteTabLabel, style = MaterialTheme.typography.titleMedium)
                }
            }
            if (state.subTopics.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    state.subTopics.forEach { topic ->
                        TextButton(onClick = { state.onTopicSelected(topic.id) }) {
                            Text(topic.name, maxLines = 1)
                        }
                    }
                }
            }
        }
    }

    private sealed class NoteListItem {
        data class Section(val id: String, val title: String, val count: Int) : NoteListItem()
        data class Note(val value: NoteCardProjection) : NoteListItem()
    }

    private inner class NoteAdapter(private val context: Context) : ListAdapter<NoteListItem, RecyclerView.ViewHolder>(ITEM_DIFF_CALLBACK) {
        private var selectedIds: Set<Long> = emptySet()
        private var pinnedIds: Set<Long> = emptySet()
        private var colors = currentColors
        private var currentSourceIds: List<Long> = emptyList()
        private var currentHeader: NoteHeaderState? = null
        private var pinnedToggle: ((Boolean) -> Unit)? = null

        init { setHasStableIds(true) }

        override fun getItemViewType(position: Int): Int = when { getItem(position) is NoteListItem.Section -> 0; currentHeader?.viewMode == NoteViewMode.GRID -> 2; else -> 1 }

        override fun getItemId(position: Int): Long = when (val item = getItem(position)) {
            is NoteListItem.Section -> item.id.hashCode().toLong()
            is NoteListItem.Note -> item.value.id
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            if (viewType == 0) SectionViewHolder(TextView(context).apply {
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setPadding(dp(8), dp(14), dp(8), dp(6))
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 13f
            }) else if (viewType == 2) GridNoteViewHolder(NoteGridView(context)) else NoteViewHolder(NoteRowView(context))

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = getItem(position)) {
                is NoteListItem.Section -> (holder as SectionViewHolder).bind(item, colors, if (item.id == "pinned") (currentHeader?.pinnedExpanded ?: true) else true, if (item.id == "pinned") pinnedToggle else null)
                is NoteListItem.Note -> {
                    val note = item.value
                    if (holder is NoteViewHolder) holder.bind(note, selectedIds.contains(note.id), pinnedIds.contains(note.id), colors, callbacks) else (holder as GridNoteViewHolder).bind(note, selectedIds.contains(note.id), colors, callbacks)
                }
            }
        }

        fun isFullSpanPosition(position: Int): Boolean = getItem(position) is NoteListItem.Section

        fun setSectionState(header: NoteHeaderState, notes: List<NoteCardProjection>) {
            val modeChanged = currentHeader?.viewMode != null && currentHeader?.viewMode != header.viewMode
            currentHeader = header
            pinnedToggle = header.onPinnedExpandedChanged
            if (modeChanged) notifyDataSetChanged()
        }

        fun replace(value: List<NoteListItem>, source: List<NoteCardProjection>, header: NoteHeaderState) {
            currentSourceIds = source.map { it.id }
            currentHeader = header
            submitList(value)
        }

        fun setVisualState(selectedIds: Set<Long>, pinnedIds: Set<Long>, colors: NoteRecyclerColors) {
            val changed = this.selectedIds != selectedIds || this.pinnedIds != pinnedIds || this.colors != colors
            this.selectedIds = selectedIds
            this.pinnedIds = pinnedIds
            this.colors = colors
            if (changed && currentList.isNotEmpty()) notifyItemRangeChanged(0, currentList.size, PAYLOAD_STATE)
        }

        fun ids(): List<Long> = currentList.mapNotNull { (it as? NoteListItem.Note)?.value?.id }
    }

    private class SectionViewHolder(private val view: TextView) : RecyclerView.ViewHolder(view) {
        fun bind(item: NoteListItem.Section, colors: NoteRecyclerColors, pinnedExpanded: Boolean, onPinnedToggle: ((Boolean) -> Unit)?) {
            view.text = item.title + " · " + item.count
            view.setTextColor(if (item.id == "pinned") colors.pinned else colors.onSurface)
            view.setCompoundDrawablePadding(dp(6))
            if (item.id == "pinned" && onPinnedToggle != null) {
                val expanded = pinnedExpanded
                view.setCompoundDrawablesWithIntrinsicBounds(
                    if (expanded) android.R.drawable.arrow_down_float else 0,
                    0, 0, 0
                )
                view.text = if (expanded) item.title + " · " + item.count else "› " + item.title + " · " + item.count
                view.contentDescription = item.title + " " + item.count
                view.setOnClickListener {
                    view.tag = !expanded
                    onPinnedToggle(!expanded)
                }
            } else {
                view.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                view.contentDescription = null
                view.setOnClickListener(null)
            }
        }

        private fun dp(value: Int): Int =
            (value * view.resources.displayMetrics.density).roundToInt()
    }

    private object ITEM_DIFF_CALLBACK : DiffUtil.ItemCallback<NoteListItem>() {
        override fun areItemsTheSame(oldItem: NoteListItem, newItem: NoteListItem): Boolean = when {
            oldItem is NoteListItem.Section && newItem is NoteListItem.Section -> oldItem.id == newItem.id
            oldItem is NoteListItem.Note && newItem is NoteListItem.Note -> oldItem.value.id == newItem.value.id
            else -> false
        }
        override fun areContentsTheSame(oldItem: NoteListItem, newItem: NoteListItem): Boolean = oldItem == newItem
    }

    private fun buildListItems(notes: List<NoteCardProjection>, pinnedIds: Set<Long>, header: NoteHeaderState): List<NoteListItem> {
        val result = mutableListOf<NoteListItem>()
        val pinned = notes.filter { it.id in pinnedIds }
        if (pinned.isNotEmpty()) {
            result += NoteListItem.Section("pinned", header.pinnedLabel, pinned.size)
            if (header.pinnedExpanded) pinned.forEach { result += NoteListItem.Note(it) }
        }
        val groups = linkedMapOf(
            header.todayLabel to mutableListOf<NoteCardProjection>(),
            header.yesterdayLabel to mutableListOf(),
            header.previous7DaysLabel to mutableListOf(),
            header.previous30DaysLabel to mutableListOf(),
            header.olderLabel to mutableListOf()
        )
        val today = java.time.LocalDate.now()
        notes.filterNot { it.id in pinnedIds }.forEach { note ->
            val date = java.time.Instant.ofEpochMilli(note.createdAtMillis)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            val days = java.time.temporal.ChronoUnit.DAYS.between(date, today)
            val key = when {
                days <= 0L -> header.todayLabel
                days == 1L -> header.yesterdayLabel
                days <= 7L -> header.previous7DaysLabel
                days <= 30L -> header.previous30DaysLabel
                else -> header.olderLabel
            }
            groups.getValue(key).add(note)
        }
        groups.forEach { (title, items) ->
            if (items.isNotEmpty()) {
                result += NoteListItem.Section("time:$title", title, items.size)
                items.forEach { result += NoteListItem.Note(it) }
            }
        }
        return result
    }

    private companion object {
        const val PAYLOAD_STATE = "note_state"
        const val LONG_PRESS_DELAY_MS = 280L
    }

    private inner class GridNoteViewHolder(itemView: NoteGridView) : ViewHolder(itemView) {
        fun bind(note: NoteCardProjection, selected: Boolean, colors: NoteRecyclerColors, callbacks: NoteRecyclerCallbacks) {
            (itemView as NoteGridView).bind(note, selected, colors, { callbacks.onToggleSelect(note.id) }, { callbacks.onLongPress(note.id, anchorY(itemView)) }, { callbacks.onEdit(note.id) })
        }
        private fun anchorY(view: View): Float { val location = IntArray(2); view.getLocationOnScreen(location); return location[1] + view.height / 2f }
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

    private class NoteGridView(context: Context) : LinearLayout(context) {
        private val checkbox: CheckBox
        private val editButton: ImageButton
        private val title: TextView
        private val preview: TextView
        private val label: TextView
        private val density = resources.displayMetrics.density
        init {
            orientation = VERTICAL; layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(dp(10), dp(10), dp(10), dp(10)); minimumHeight = dp(156); isClickable = true; isFocusable = true
            val actions = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)) }
            checkbox = CheckBox(context).apply { layoutParams = LayoutParams(dp(44), dp(44)); minWidth = dp(44); minHeight = dp(44); contentDescription = context.getString(R.string.select_card) }
            editButton = ImageButton(context).apply { layoutParams = LayoutParams(dp(44), dp(44)); setPadding(dp(10), dp(10), dp(10), dp(10)); setImageResource(android.R.drawable.ic_menu_edit); background = null; contentDescription = context.getString(R.string.edit_note) }
            actions.addView(checkbox); actions.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f)); actions.addView(editButton); addView(actions)
            title = TextView(context).apply { layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; textSize = 16f; setTypeface(Typeface.DEFAULT, Typeface.BOLD) }; addView(title)
            preview = TextView(context).apply { layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) }; maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; textSize = 14f }; addView(preview)
            label = TextView(context).apply { layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(3) }; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; textSize = 12f }; addView(label)
        }
        fun bind(note: NoteCardProjection, selected: Boolean, colors: NoteRecyclerColors, onToggleSelect: () -> Unit, onLongPress: () -> Unit, onEdit: () -> Unit) {
            background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(16).toFloat(); setColor(if (selected) withAlpha(colors.primaryContainer, 90) else colors.surface) }
            checkbox.buttonTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(colors.primary, colors.onSurfaceVariant)); checkbox.isChecked = selected; checkbox.setOnClickListener { onToggleSelect() }
            title.text = note.title.ifBlank { context.getString(R.string.untitled) }; title.setTextColor(colors.onSurface)
            preview.text = note.preview.ifBlank { context.getString(R.string.untitled) }; preview.setTextColor(colors.onSurfaceVariant)
            label.text = note.topicLabels; label.visibility = if (note.topicLabels.isBlank()) GONE else VISIBLE; label.setTextColor(colors.primary)
            editButton.imageTintList = ColorStateList.valueOf(colors.onSurfaceVariant); editButton.setOnClickListener { onEdit() }
            setOnClickListener { onToggleSelect() }; setOnLongClickListener { onLongPress(); true }
        }
        private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or ((alpha.coerceIn(0, 255)) shl 24)
        private fun dp(value: Int): Int = (value * density + 0.5f).toInt()
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
            preview = TextView(context).apply {
                layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(4)
                }
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                textSize = 14f
            }
            contentColumn.addView(preview)
            contentColumn.addView(label)

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
