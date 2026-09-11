package com.clipnest.domain

import com.clipnest.data.model.ClipboardCard
import com.clipnest.data.model.ContentType
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportFormatterTest {

    @Test
    fun formatMarkdown_generatesValidMarkdownWithFrontmatterOrHeaders() {
        val cards = listOf(
            ClipboardCard(
                id = 1,
                content = "# My Title\nFirst line",
                preview = "# My Title",
                createdAtMillis = 1700000000000L,
                sortOrder = 100,
                sourceApp = "Chrome",
                contentType = ContentType.TEXT,
                pinned = true,
                isSensitive = false
            ),
            ClipboardCard(
                id = 2,
                content = "https://example.com",
                preview = "https://example.com",
                createdAtMillis = 1700000010000L,
                sortOrder = 200,
                sourceApp = null,
                contentType = ContentType.URL,
                pinned = false,
                isSensitive = true
            )
        )

        val md = ExportFormatter.formatMarkdown(cards)
        assertTrue(md.contains("# ClipNest Export"))
        assertTrue(md.contains("### 1. Item [Pinned]"))
        assertTrue(md.contains("<https://example.com>"))
    }

    @Test
    fun formatPlainText_generatesCleanDelimitedText() {
        val cards = listOf(
            ClipboardCard(
                id = 1,
                content = "Line 1\nLine 2",
                preview = "Line 1",
                createdAtMillis = 1700000000000L,
                sortOrder = 100,
                sourceApp = null,
                contentType = ContentType.TEXT,
                pinned = false,
                isSensitive = false
            ),
            ClipboardCard(
                id = 2,
                content = "Second Card",
                preview = "Second Card",
                createdAtMillis = 1700000010000L,
                sortOrder = 200,
                sourceApp = null,
                contentType = ContentType.TEXT,
                pinned = false,
                isSensitive = false
            )
        )

        val txt = ExportFormatter.formatPlainText(cards)
        assertTrue(txt.contains("Line 1\nLine 2"))
        assertTrue(txt.contains("========================================"))
        assertTrue(txt.contains("Second Card"))
    }
}
