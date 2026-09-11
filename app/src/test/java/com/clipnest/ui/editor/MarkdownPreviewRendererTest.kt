package com.clipnest.ui.editor

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownPreviewRendererTest {
    private val colors = MarkdownPreviewColors.from(
        background = Color.White,
        onSurface = Color.Black,
        onSurfaceVariant = Color.DarkGray,
        surfaceVariant = Color.LightGray,
        outline = Color.Gray,
        outlineVariant = Color.LightGray,
        primary = Color(0xFF00695C),
        codeBackground = Color(0xFFECEFF1),
        isDark = false
    )

    @Test
    fun rendersSupportedMarkdownBlocks() {
        val html = MarkdownPreviewRenderer.render(
            "# Title\n\n**bold** and *italic*\n\n> quote\n\n- one\n- two\n\n1. first\n2. second\n\n---\n\n```\nval answer = 42\n```",
            colors
        )

        assertTrue(html.contains("<h1 id=\"md-heading-0\">Title</h1>"))
        assertTrue(html.contains("<strong>bold</strong>"))
        assertTrue(html.contains("<em>italic</em>"))
        assertTrue(html.contains("<blockquote>"))
        assertTrue(html.contains("<ul>"))
        assertTrue(html.contains("<ol>"))
        assertTrue(html.contains("<hr />"))
        assertTrue(html.contains("<pre><code>"))
    }

    @Test
    fun rendersGithubStyleTablesWithResponsivePreviewStyles() {
        val html = MarkdownPreviewRenderer.render(
            "| Name | Description |\n| --- | --- |\n| ClipNest | A very long value that should wrap inside the cell |",
            colors
        )

        assertTrue(html.contains("<table>"))
        assertTrue(html.contains("<thead>"))
        assertTrue(html.contains("<tbody>"))
        assertTrue(html.contains("table-layout: fixed"))
        assertTrue(html.contains("overflow-wrap: anywhere"))
        assertTrue(html.contains("user-scalable=yes"))
    }

    @Test
    fun escapesRawHtmlInUserContent() {
        val html = MarkdownPreviewRenderer.render("<script>alert('x')</script>", colors)

        assertTrue(html.contains("function jumpToHeading(id)"))
        assertTrue(!html.contains("<script>alert('x')</script>"))
        assertTrue(!html.contains("alert('x')"))
    }

    @Test
    fun buildsTableOfContentsForHeadings() {
        val markdown = "# Overview\n\n## Setup\n\n### Configuration"
        val html = MarkdownPreviewRenderer.render(markdown, colors)
        val headings = MarkdownPreviewRenderer.extractHeadings(markdown)

        assertTrue(html.contains("id=\"md-heading-0\""))
        assertTrue(html.contains("id=\"md-heading-1\""))
        assertTrue(html.contains("id=\"md-heading-2\""))
        assertEquals(
            listOf(
                MarkdownHeading(1, "Overview", 0),
                MarkdownHeading(2, "Setup", 1),
                MarkdownHeading(3, "Configuration", 2)
            ),
            headings
        )
        assertTrue(html.contains("function jumpToHeading(id)"))
    }
}