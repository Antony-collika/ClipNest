package com.clipnest.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorNoteDocumentTest {
    @Test
    fun editableText_roundTripsTitleAndContent() {
        val original = EditorNoteDocument("Tiêu đề dòng 1\nTiêu đề dòng 2", "Nội dung\nDòng 2")
        val restored = EditorNoteDocument.fromEditable(original.editableText, original.titleBoundary)
        assertEquals(original, restored)
    }

    @Test
    fun fromEditable_excludesStructuralSeparatorFromContent() {
        val restored = EditorNoteDocument.fromEditable("Title\nContent", 5)
        assertEquals("Title", restored.title)
        assertEquals("Content", restored.content)
    }

    @Test
    fun normalize_handlesWindowsAndLegacyCarriageReturns() {
        val restored = EditorNoteDocument.fromEditable("Title\r\nContent\rMore", 5)
        assertEquals("Title", restored.title)
        assertEquals("Content\nMore", restored.content)
    }

    @Test
    fun emptyTitleStillHasStructuralBoundary() {
        val original = EditorNoteDocument("", "Content")
        assertEquals("\nContent", original.editableText)
        assertEquals(original, EditorNoteDocument.fromEditable(original.editableText, original.titleBoundary))
    }
}
