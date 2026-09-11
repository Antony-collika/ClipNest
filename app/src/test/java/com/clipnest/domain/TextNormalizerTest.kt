package com.clipnest.domain

import com.clipnest.data.model.ContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextNormalizerTest {

    @Test
    fun normalize_convertsLineEndingsAndTrimsEmpty() {
        val crlf = "Hello\r\nWorld\rAgain"
        val normalized = TextNormalizer.normalize(crlf)
        assertEquals("Hello\nWorld\nAgain", normalized)

        assertNull(TextNormalizer.normalize(null))
        assertNull(TextNormalizer.normalize("   \n\r  "))
    }

    @Test
    fun generatePreview_limitsLinesAndChars() {
        val longMultiline = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5"
        val preview = TextNormalizer.generatePreview(longMultiline, maxLines = 3)
        assertEquals("Line 1\nLine 2\nLine 3…", preview)

        val longSingleLine = "a".repeat(250)
        val charLimited = TextNormalizer.generatePreview(longSingleLine, maxChars = 50)
        assertEquals("a".repeat(50) + "…", charLimited)
    }

    @Test
    fun detectContentType_accuratelyDifferentiatesUrlAndText() {
        assertEquals(ContentType.URL, TextNormalizer.detectContentType("https://developer.android.com"))
        assertEquals(ContentType.URL, TextNormalizer.detectContentType("http://example.com/path?param=1#anchor"))
        assertEquals(ContentType.TEXT, TextNormalizer.detectContentType("Check this link: https://google.com"))
        assertEquals(ContentType.TEXT, TextNormalizer.detectContentType("Regular plain text content"))
    }

    @Test
    fun combine_placesFirstTextBeforeSourceWithoutBlankLines() {
        val combined = TextNormalizer.combine("Copied clipboard", "https://example.com")

        assertEquals("Copied clipboard\n---\nsource: https://example.com", combined)
        assertEquals(-1, combined.indexOf("\\\\n"))
        assertEquals(-1, combined.indexOf("\\n"))
        assertEquals(-1, combined.indexOf("\n\n"))
        assertEquals("Shared Only", TextNormalizer.combine("Shared Only", ""))
        assertEquals("Clip Only", TextNormalizer.combine("", "Clip Only"))
    }

    @Test
    fun formatSelectedCards_addsHeadersOnlyForOpenEditorMultiCardFlow() {
        val contents = listOf("Content 1", "Content 2", "Content 3")

        assertEquals(
            "## Clipboard #1\n\nContent 1\n\n---\n\n## Clipboard #2\n\nContent 2\n\n---\n\n## Clipboard #3\n\nContent 3",
            TextNormalizer.formatSelectedCards(contents, includeHeaders = true)
        )
        assertEquals(
            "Content 1\n\n---\n\nContent 2\n\n---\n\nContent 3",
            TextNormalizer.formatSelectedCards(contents, includeHeaders = false)
        )
    }

    @Test
    fun formatSelectedCards_normalizesEdgesAndPreservesInternalNewlines() {
        val formatted = TextNormalizer.formatSelectedCards(
            listOf("  First\r\nline  \n", "\nSecond\n"),
            includeHeaders = false
        )

        assertEquals("First\nline\n\n---\n\nSecond", formatted)
        assertEquals(-1, formatted.indexOf("\\n"))
    }
}
