package com.clipnest.ui.note

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Drawable
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import com.clipnest.domain.RelativeDateLabels
import com.clipnest.domain.RelativeTimeFormatter

internal data class NoteRecyclerColors(
    val surface: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val primary: Int,
    val primaryContainer: Int,
    val outlineVariant: Int,
    val pinned: Int,
    val noteCard: Int,
    val noteSelectedCard: Int,
    val notePinnedSurface: Int,
    val noteTagSurface: Int,
    val noteTagContent: Int,
    val sectionText: Int,
    val cardTitle: Int,
    val cardPreview: Int,
    val cardActionIcon: Int
)

internal enum class NoteViewMode { LIST, GRID }

internal data class NoteHeaderState(
    val avatarIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val myTopicsLabel: String,
    val currentTopicLabel: String,
    val allNotesLabel: String,
    val topics: List<Topic>,
    val colors: androidx.compose.material3.ColorScheme,
    val typography: androidx.compose.material3.Typography,
    val topBarBackground: androidx.compose.ui.graphics.Color,
    val topBarContent: androidx.compose.ui.graphics.Color,
    val breadcrumb: androidx.compose.ui.graphics.Color,
    val breadcrumbBackground: androidx.compose.ui.graphics.Color,
    val sectionText: androidx.compose.ui.graphics.Color,
    val tagSurface: androidx.compose.ui.graphics.Color,
    val tagContent: androidx.compose.ui.graphics.Color,
    val viewMode: NoteViewMode,
    val pinnedLabel: String,
    val pinnedExpanded: Boolean,
    val onPinnedExpandedChanged: (Boolean) -> Unit,
    val todayLabel: String,
    val yesterdayLabel: String,
    val previous7DaysLabel: String,
    val previous30DaysLabel: String,
    val olderLabel: String,
    val relativeDateLabels: RelativeDateLabels,
    val onTopicSelected: (Long?) -> Unit,
    val onViewModeChanged: (NoteViewMode) -> Unit
)
internal data class NoteRecyclerCallbacks(
    val onToggleSelect: (Long) -> Unit,
    val onLongPress: (Long, Float) -> Unit,
    val onEdit: (Long) -> Unit
)

@Composable
internal fun NoteHeaderContent(state: NoteHeaderState, modifier: Modifier = Modifier) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    Surface(
        modifier = modifier,
        shape = androidx.compose.ui.graphics.RectangleShape,
        color = state.topBarBackground,
        contentColor = state.topBarContent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Avatar — trang trí, không bấm
            Surface(
                shape = RoundedCornerShape(50),
                color = state.tagSurface,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = state.avatarIcon,
                        contentDescription = null,
                        tint = state.tagContent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 2-4. Breadcrumb — nền và chữ lấy riêng từ theme palette.
            Surface(
                color = state.breadcrumbBackground,
                contentColor = state.breadcrumb,
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        TextButton(
                            onClick = { expanded = true },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = state.myTopicsLabel,
                                color = state.breadcrumb,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1
                            )
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
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

                    Text(
                        text = "|",
                        style = MaterialTheme.typography.labelLarge,
                        color = state.breadcrumb,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    Text(
                        text = state.currentTopicLabel,
                        color = state.breadcrumb,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 5. Bộ chuyển List / Grid — chỉ một pill 80dp
            ViewModeButton(
                selected = state.viewMode,
                onViewModeChanged = state.onViewModeChanged
            )
        }
    }
}


@Composable
private fun ViewModeButton(
    selected: NoteViewMode,
    onViewModeChanged: (NoteViewMode) -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = 80.dp, height = 40.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        val selectedOffset by animateDpAsState(
            targetValue = if (selected == NoteViewMode.LIST) 4.dp else 44.dp,
            label = "view mode selection"
        )

        Box(
            modifier = Modifier
                .size(32.dp)
                .align(Alignment.CenterStart)
                .offset(x = selectedOffset)
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(9.dp)
                )
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            ViewModeIconButton(
                icon = Icons.Default.List,
                description = "List view",
                selected = selected == NoteViewMode.LIST,
                onClick = { onViewModeChanged(NoteViewMode.LIST) }
            )
            ViewModeIconButton(
                icon = Icons.Default.GridView,
                description = "Grid view",
                selected = selected == NoteViewMode.GRID,
                onClick = { onViewModeChanged(NoteViewMode.GRID) }
            )
        }
    }
}

@Composable
private fun ViewModeIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clickable(interactionSource = null, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp)
        )
    }
}

internal class NoteRecyclerView(context: Context) : RecyclerView(context) {
    private val listAdapter = NoteAdapter(context)
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
        pinned = Color.DKGRAY,
        noteCard = Color.WHITE,
        noteSelectedCard = Color.LTGRAY,
        notePinnedSurface = Color.LTGRAY,
        noteTagSurface = Color.LTGRAY,
        noteTagContent = Color.DKGRAY,
        sectionText = Color.DKGRAY,
        cardTitle = Color.BLACK,
        cardPreview = Color.DKGRAY,
        cardActionIcon = Color.DKGRAY
    )
    private val spacingDecoration = NoteSpacingDecoration(dp(14))

    init {
        layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        adapter = listAdapter
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

        if (header.viewMode == NoteViewMode.GRID) {
            val grid = (layoutManager as? androidx.recyclerview.widget.GridLayoutManager)
                ?: androidx.recyclerview.widget.GridLayoutManager(context, 2).also { layoutManager = it }
            grid.spanSizeLookup = object : androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int =
                    if (listAdapter.isFullSpanPosition(position)) 2 else 1
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
                setPadding(dp(8), dp(0), dp(8), dp(0))
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 14f
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
            val pinnedStateChanged = currentHeader?.pinnedExpanded != null &&
                currentHeader?.pinnedExpanded != header.pinnedExpanded
            currentHeader = header
            pinnedToggle = header.onPinnedExpandedChanged
            if (modeChanged) {
                notifyDataSetChanged()
            } else if (pinnedStateChanged) {
                val pinnedPosition = currentList.indexOfFirst { it is NoteListItem.Section && it.id == "pinned" }
                if (pinnedPosition >= 0) notifyItemChanged(pinnedPosition)
            }
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
        private var pinnedExpandedState = true

        fun bind(item: NoteListItem.Section, colors: NoteRecyclerColors, pinnedExpanded: Boolean, onPinnedToggle: ((Boolean) -> Unit)?) {
            pinnedExpandedState = pinnedExpanded

            if (item.id == "pinned" && onPinnedToggle != null) {
                view.text = "📌 ${item.title} · ${item.count} notes"
                view.setTextColor(colors.sectionText)
                view.setBackgroundColor(Color.TRANSPARENT)
                view.elevation = 0f
                view.setCompoundDrawablePadding(dp(8))
                view.setCompoundDrawablesWithIntrinsicBounds(null, null, ChevronDrawable(view.resources.displayMetrics.density, pinnedExpandedState, colors.onSurface), null)
                view.contentDescription = item.title + " " + item.count
                view.isClickable = true
                view.isFocusable = true
                view.setOnClickListener {
                    pinnedExpandedState = !pinnedExpandedState
                    onPinnedToggle(pinnedExpandedState)
                }
            } else {
                view.text = item.title
                view.setTextColor(colors.onSurface)
                view.background = null
                view.elevation = 0f
                view.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                view.contentDescription = null
                view.isClickable = false
                view.isFocusable = false
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
        override fun areContentsTheSame(oldItem: NoteListItem, newItem: NoteListItem): Boolean = when {
            oldItem is NoteListItem.Section && newItem is NoteListItem.Section ->
                oldItem.id == newItem.id && oldItem.title == newItem.title && oldItem.count == newItem.count
            else -> oldItem == newItem
        }

        override fun getChangePayload(oldItem: NoteListItem, newItem: NoteListItem): Any? =
            if (oldItem is NoteListItem.Section && newItem is NoteListItem.Section && oldItem.id == "pinned") PAYLOAD_STATE else null
    }

    private fun buildListItems(notes: List<NoteCardProjection>, pinnedIds: Set<Long>, header: NoteHeaderState): List<NoteListItem> {
        val result = mutableListOf<NoteListItem>()
        val pinned = notes.filter { it.id in pinnedIds }
        if (pinned.isNotEmpty()) {
            result += NoteListItem.Section("pinned", header.pinnedLabel, pinned.size)
            if (header.pinnedExpanded) pinned.forEach { result += NoteListItem.Note(it) }
        }
        val groups = linkedMapOf<String, MutableList<NoteCardProjection>>()
        notes.filterNot { it.id in pinnedIds }.forEach { note ->
            val key = RelativeTimeFormatter.relativeDateLabel(note.createdAtMillis, labels = header.relativeDateLabels)
            groups.getOrPut(key) { mutableListOf() }.add(note)
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
            setPadding(dp(14), dp(14), dp(14), dp(14)); minimumHeight = dp(172); isClickable = true; isFocusable = true
            val actions = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)) }
            checkbox = CheckBox(context).apply { layoutParams = LayoutParams(dp(40), dp(40)); minWidth = dp(40); minHeight = dp(40); contentDescription = context.getString(R.string.select_card) }
            editButton = ImageButton(context).apply { layoutParams = LayoutParams(dp(40), dp(40)); setPadding(dp(9), dp(9), dp(9), dp(9)); setImageResource(android.R.drawable.ic_menu_edit); background = null; contentDescription = context.getString(R.string.edit_note) }
            actions.addView(checkbox); actions.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f)); actions.addView(editButton);
            title = TextView(context).apply { layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; textSize = 14f; setTypeface(Typeface.DEFAULT, Typeface.BOLD) }; addView(title)
            preview = TextView(context).apply { layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) }; maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; textSize = 12f }; addView(preview)
            label = TextView(context).apply { layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(6) }; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; textSize = 12f; gravity = Gravity.BOTTOM or Gravity.START }; addView(label);addView(actions)
        }
        fun bind(note: NoteCardProjection, selected: Boolean, colors: NoteRecyclerColors, onToggleSelect: () -> Unit, onLongPress: () -> Unit, onEdit: () -> Unit) {
            background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(18).toFloat(); setColor(if (selected) colors.noteSelectedCard else colors.noteCard) }
            elevation = dp(2).toFloat()
            checkbox.buttonTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(colors.primary, colors.onSurfaceVariant)); checkbox.isChecked = selected; checkbox.setOnClickListener { onToggleSelect() }
            title.text = note.title.ifBlank { context.getString(R.string.untitled) }; title.setTextColor(colors.cardTitle)
            preview.text = note.preview.ifBlank { context.getString(R.string.untitled) }; preview.setTextColor(colors.cardPreview)
            label.text = note.topicLabels
            label.visibility = if (note.topicLabels.isBlank()) GONE else VISIBLE
            label.setTextColor(colors.noteTagContent)
            label.background = if (note.topicLabels.isBlank()) null else GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(10).toFloat()
                setColor(colors.noteTagSurface)
            }
            label.setPadding(dp(8), dp(3), dp(8), dp(3))
            editButton.imageTintList = ColorStateList.valueOf(colors.cardActionIcon); editButton.setOnClickListener { onEdit() }
            setOnClickListener { onToggleSelect() }; setOnLongClickListener { onLongPress(); true }
        }
        private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or ((alpha.coerceIn(0, 255)) shl 24)
        private fun dp(value: Int): Int = (value * density + 0.5f).toInt()
    }

    private class ChevronDrawable(
        private val density: Float,
        private val expanded: Boolean,
        color: Int
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            setColor(color)
        }

        override fun draw(canvas: Canvas) {
            val w = bounds.width().toFloat()
            val h = bounds.height().toFloat()
            val path = Path().apply {
                if (expanded) {
                    moveTo(w * 0.2f, h * 0.62f)
                    lineTo(w * 0.5f, h * 0.38f)
                    lineTo(w * 0.8f, h * 0.62f)
                } else {
                    moveTo(w * 0.2f, h * 0.38f)
                    lineTo(w * 0.5f, h * 0.62f)
                    lineTo(w * 0.8f, h * 0.38f)
                }
            }
            canvas.drawPath(path, paint)
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
        override fun getIntrinsicWidth(): Int = (20 * density).roundToInt()
        override fun getIntrinsicHeight(): Int = (20 * density).roundToInt()
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
            val position = parent.getChildAdapterPosition(view)
            outRect.top = if (position == 0) (spacing * 1.5f).roundToInt() else 0
            outRect.bottom = if (position == 0) spacing / 2 else spacing

            if (parent.layoutManager is androidx.recyclerview.widget.GridLayoutManager) {
                val params = view.layoutParams as? androidx.recyclerview.widget.GridLayoutManager.LayoutParams
                if (params != null && params.spanSize == 1) {
                    val halfColumnSpacing = spacing / 2
                    if (params.spanIndex == 0) {
                        outRect.right = halfColumnSpacing
                    } else {
                        outRect.left = halfColumnSpacing
                    }
                }
            }
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
            setPadding(dp(8), dp(10), dp(10), dp(10))
            minimumHeight = dp(88)
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
                    setMargins(dp(6), dp(2), dp(8), dp(2))
                }
            }
            addView(contentColumn)

            title = TextView(context).apply {
                layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                textSize = 14f
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            }
            contentColumn.addView(title)

            label = TextView(context).apply {
                layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
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
                textSize = 12f
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
            elevation = dp(2).toFloat()

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
            title.setTextColor(colors.cardTitle)

            label.text = note.topicLabels
            label.visibility = if (note.topicLabels.isBlank()) GONE else VISIBLE
            label.setTextColor(colors.noteTagContent)
            label.background = if (note.topicLabels.isBlank()) null else GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(9).toFloat()
                setColor(colors.noteTagSurface)
            }
            label.setPadding(dp(8), dp(3), dp(8), dp(3))

            preview.text = note.preview.ifBlank { context.getString(R.string.untitled) }
            preview.setTextColor(colors.cardPreview)

            editButton.imageTintList = ColorStateList.valueOf(colors.cardActionIcon)
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
                cornerRadius = dp(18).toFloat()
                setColor(if (isSelected) colors.noteSelectedCard else colors.noteCard)
            }
        }

        private fun withAlpha(color: Int, alpha: Int): Int {
            return (color and 0x00FFFFFF) or ((alpha.coerceIn(0, 255)) shl 24)
        }

    private fun dp(value: Int): Int = (value * density + 0.5f).toInt()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

}
