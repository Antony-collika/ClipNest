package com.example.domain

import com.example.data.model.ClipboardCardProjection

object OrderHelper {

    const val ORDER_STEP = 1000L

    fun applyPresentationOrder(
        items: List<ClipboardCardProjection>,
        showPinnedFirst: Boolean
    ): List<ClipboardCardProjection> {
        val sortedByManual = items.sortedBy { it.sortOrder }
        if (!showPinnedFirst) {
            return sortedByManual
        }
        val (pinned, normal) = sortedByManual.partition { it.pinned }
        return pinned + normal
    }

    fun calculateNewSortOrders(
        visibleItems: List<ClipboardCardProjection>,
        fromIndex: Int,
        toIndex: Int
    ): List<Pair<Long, Long>> {
        if (fromIndex !in visibleItems.indices || toIndex !in visibleItems.indices) {
            return emptyList()
        }
        val mutable = visibleItems.toMutableList()
        val moved = mutable.removeAt(fromIndex)
        mutable.add(toIndex, moved)

        return mutable.mapIndexed { index, item ->
            item.id to ((index + 1) * ORDER_STEP)
        }
    }
}
