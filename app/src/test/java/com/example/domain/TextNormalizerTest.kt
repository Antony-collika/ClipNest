package com.example.domain

import com.example.data.model.ContentType
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
    fun combine_mergesSharedAndClipboardDeterministically() {
        val combined = TextNormalizer.combine("Shared Text", "Clipboard Text")
        assertEquals("Shared Text\n\n---\n\nClipboard Text", combined)

        assertEquals("Shared Only", TextNormalizer.combine("Shared Only", ""))
        assertEquals("Clip Only", TextNormalizer.combine("", "Clip Only"))
    }
}
