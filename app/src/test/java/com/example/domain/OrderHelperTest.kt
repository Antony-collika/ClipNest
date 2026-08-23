package com.example.domain

import com.example.data.model.ClipboardCardProjection
import com.example.data.model.ContentType
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
        assertEquals(listOf(1L, 2L, 3L), result.map { it.id })
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
        assertEquals(listOf(2L, 4L, 1L, 3L), result.map { it.id })

        // Toggle back to false
        val restored = OrderHelper.applyPresentationOrder(result, showPinnedFirst = false)
        assertEquals(listOf(1L, 2L, 3L, 4L), restored.map { it.id })
    }

    @Test
    fun calculateNewSortOrders_producesMonotonicIndices() {
        val items = listOf(
            createSample(id = 1, sortOrder = 1000, pinned = false),
            createSample(id = 2, sortOrder = 2000, pinned = false),
            createSample(id = 3, sortOrder = 3000, pinned = false)
        )

        // Move item 1 to index 2 (end) -> [2, 3, 1]
        val updates = OrderHelper.calculateNewSortOrders(items, fromIndex = 0, toIndex = 2)
        assertEquals(3, updates.size)
        assertEquals(2L to 1000L, updates[0])
        assertEquals(3L to 2000L, updates[1])
        assertEquals(1L to 3000L, updates[2])
    }
}
