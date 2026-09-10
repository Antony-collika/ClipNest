package com.clipnest.domain

import com.clipnest.data.model.ClipboardCardProjection
import com.clipnest.data.model.ContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderHelperTest {

    private fun createSample(id: Long, sortOrder: Long, pinned: Boolean): ClipboardCardProjection {
        return ClipboardCardProjection(
            id = id,
            preview = "Item $id",
            createdAtMillis = 1000L + id,
            sortOrder = sortOrder,
            sourceApp = null,
            contentType = ContentType.TEXT,
            pinned = pinned,
            isSensitive = false
        )
    }

    @Test
    fun applyPresentationOrder_maintainsManualOrderWhenShowPinnedFirstIsFalse() {
        val items = listOf(
            createSample(id = 1, sortOrder = 100, pinned = false),
            createSample(id = 2, sortOrder = 200, pinned = true),
            createSample(id = 3, sortOrder = 300, pinned = false)
        )

        val result = OrderHelper.applyPresentationOrder(items, showPinnedFirst = false)
        // Descending order by sortOrder (newest/highest sortOrder on top)
        assertEquals(listOf(3L, 2L, 1L), result.map { it.id })
    }

    @Test
    fun applyPresentationOrder_placesPinnedFirstWhilePreservingRelativeOrder() {
        val items = listOf(
            createSample(id = 1, sortOrder = 100, pinned = false),
            createSample(id = 2, sortOrder = 200, pinned = true),
            createSample(id = 3, sortOrder = 300, pinned = false),
            createSample(id = 4, sortOrder = 400, pinned = true)
        )

        val result = OrderHelper.applyPresentationOrder(items, showPinnedFirst = true)
        // Pinned first: id 4 (400), id 2 (200), then normal: id 3 (300), id 1 (100)
        assertEquals(listOf(4L, 2L, 3L, 1L), result.map { it.id })

        // Toggle back to false
        val restored = OrderHelper.applyPresentationOrder(result, showPinnedFirst = false)
        assertEquals(listOf(4L, 3L, 2L, 1L), restored.map { it.id })
    }

    @Test
    fun calculateNewSortOrders_producesMonotonicIndices() {
        val items = listOf(
            createSample(id = 1, sortOrder = 1000, pinned = false),
            createSample(id = 2, sortOrder = 2000, pinned = false),
            createSample(id = 3, sortOrder = 3000, pinned = false)
        )

        // Move item 1 (index 0) to index 2 (end) -> new list order: [2, 3, 1]
        val updates = OrderHelper.calculateNewSortOrders(items, fromIndex = 0, toIndex = 2)
        assertEquals(3, updates.size)
        // Highest sortOrder on top (index 0)
        assertEquals(2L to 3000L, updates[0])
        assertEquals(3L to 2000L, updates[1])
        assertEquals(1L to 1000L, updates[2])
    }
}
