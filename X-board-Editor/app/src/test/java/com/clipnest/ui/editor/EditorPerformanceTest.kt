package com.clipnest.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorPerformanceTest {
    @Test
    fun largeDocumentSearchUsesLiveCharSequence() {
        val document = buildString { repeat(1_000_000 / 32) { append("0123456789abcdef markdown line\n") } }
        val matches = EditorSearchEngine.findMatches(document, "markdown")
        assertEquals(1_000_000 / 32, matches.size)
    }

    @Test
    fun viewportHighlightWorkStaysBoundedForOneMegabyteDocument() {
        val document = buildString { repeat(1_000_000 / 24) { append("plain line with **markdown** and [link](url)\n") } }
        val start = document.length / 2
        val ranges = EditorViewportHighlighter.highlightViewport(document, start, start + 2_000)
        assertTrue(ranges.isNotEmpty())
        assertTrue(ranges.size < 200)
    }
}
