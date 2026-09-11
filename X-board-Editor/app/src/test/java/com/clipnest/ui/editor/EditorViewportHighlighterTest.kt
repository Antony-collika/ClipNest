package com.clipnest.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorViewportHighlighterTest {
    @Test
    fun highlightsOnlyLinesIntersectingViewport() {
        val text = "# first\nplain\n> quote\n- item\n**bold**"
        val ranges = EditorViewportHighlighter.highlightViewport(text, 9, 20)
        assertTrue(ranges.any { it.kind == EditorViewportHighlighter.Kind.QUOTE })
        assertTrue(ranges.none { it.kind == EditorViewportHighlighter.Kind.HEADING })
    }

    @Test
    fun scannerHandlesLargeDocumentWithoutCreatingWholeDocumentOutput() {
        val text = buildString {
            repeat(20_000) { append("- item $it\n") }
        }
        val start = text.indexOf("- item 10000")
        val ranges = EditorViewportHighlighter.highlightViewport(text, start, start + 120)
        assertTrue(ranges.isNotEmpty())
        assertTrue(ranges.size < 20)
        assertTrue(ranges.all { it.end - it.start <= 120 })
    }

    @Test
    fun sliceScannerRestoresAbsoluteOffsets() {
        val slice = "plain\n**bold**\n"
        val ranges = EditorViewportHighlighter.highlightSlice(slice, 500)
        assertEquals(500 + slice.indexOf("**bold**"), ranges.single { it.kind == EditorViewportHighlighter.Kind.EMPHASIS }.start)
        assertEquals(500 + slice.indexOf("**bold**") + "**bold**".length, ranges.single { it.kind == EditorViewportHighlighter.Kind.EMPHASIS }.end)
    }

    @Test
    fun emptyViewportReturnsNoWork() {
        assertEquals(emptyList<EditorViewportHighlighter.HighlightRange>(), EditorViewportHighlighter.highlightViewport("# title", 2, 2))
    }
}
