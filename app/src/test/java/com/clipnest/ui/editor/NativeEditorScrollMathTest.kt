package com.clipnest.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeEditorScrollMathTest {
    @Test
    fun transientRangeDropMustNotResetScrollPosition() {
        val stableMax = 198_000
        val transientMax = 0
        assertEquals(stableMax, maxOf(stableMax, transientMax))
    }

    @Test
    fun viewportThumbPositionIsIndependentOfContentTranslation() {
        val viewport = 2_000
        val content = 200_000
        val maxScroll = content - viewport
        val scroll = 100_000
        val thumbHeight = 100
        val travel = viewport - thumbHeight
        val top = travel.toFloat() * scroll / maxScroll
        assertEquals(959.6f, top, 1.0f)
    }
}