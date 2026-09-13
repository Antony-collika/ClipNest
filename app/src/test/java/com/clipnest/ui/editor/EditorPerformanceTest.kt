package com.clipnest.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorPerformanceTest {
    @Test
    fun largeDocumentSearchUsesLiveCharSequence() {
        val document = buildString { repeat(1_000_000 / 32) { append("0123456789abcdef markdown line\n") } }
        val matches = EditorSearchEngine.findMatches(document, "markdown")
        assertEquals(1_000_000 / 32, matches.size)
    }
}
