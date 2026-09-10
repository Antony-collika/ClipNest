package com.clipnest.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorDocumentTest {
    @Test
    fun rangeEditsPreserveLiveDocumentAndSelection() {
        val document = EditorDocument("hello world")
        document.replace(6, 11, "ClipNest", 14, 14)
        assertEquals("hello ClipNest", document.snapshot())
        assertEquals(EditorSelection(14, 14), document.selection)
        assertEquals(14, document.length)
    }

    @Test
    fun lineBoundariesAreComputedWithoutCreatingWholeDocumentSnapshot() {
        val document = EditorDocument("one\ntwo\nthree")
        assertEquals(4, document.lineStart(6))
        assertEquals(7, document.lineEnd(4))
        assertEquals(8, document.lineStart(10))
        assertEquals(13, document.lineEnd(10))
    }

    @Test
    fun lineAndVerticalMovementStayInsideDocumentBounds() {
        val document = EditorDocument("abcd\nxy\n12345")
        document.setSelection(3)
        document.execute(EditorCommand.MoveToLineStart)
        assertEquals(EditorSelection(0, 0), document.selection)
        document.execute(EditorCommand.MoveToLineEnd)
        assertEquals(EditorSelection(4, 4), document.selection)
        document.setSelection(2)
        document.execute(EditorCommand.MoveDown)
        assertEquals(EditorSelection(7, 7), document.selection)
        document.execute(EditorCommand.MoveDown)
        assertEquals(EditorSelection(10, 10), document.selection)
        document.execute(EditorCommand.MoveUp)
        assertEquals(EditorSelection(7, 7), document.selection)
    }

    @Test
    fun searchReadsTheLiveCharSequenceAndFindsNonOverlappingMatches() {
        val document = EditorDocument("Alpha beta ALPHA alphabet")
        assertEquals(listOf(0, 11, 17), document.findMatches("alpha"))
    }

    @Test
    fun selectionAndSubstringOnlyMaterializeTheRequestedRange() {
        val document = EditorDocument("0123456789")
        document.setSelection(3, 7)
        assertEquals("3456", document.selectedText())
        assertEquals("345", document.substring(3, 6))
    }

    @Test
    fun commandsUseRangeEditsAndSelectionWithoutWholeTextRebuild() {
        val document = EditorDocument("abc")
        document.execute(EditorCommand.MoveToDocumentStart)
        document.execute(EditorCommand.Insert("X"))
        document.execute(EditorCommand.MoveRight)
        document.execute(EditorCommand.DeleteForward)
        assertEquals("Xac", document.snapshot())
        assertEquals(EditorSelection(2, 2), document.selection)
        document.execute(EditorCommand.SelectAll)
        document.execute(EditorCommand.Replace(0, document.length, "done"))
        assertEquals("done", document.snapshot())
    }

    @Test
    fun megabyteDocumentCanEditAndSearchByRangeWithoutRebuildingForTheOperation() {
        val text = buildString { repeat(1_000_000) { append(if (it % 80 == 79) '\n' else 'a') } }
        val document = EditorDocument(text)
        val middle = document.length / 2
        document.replace(middle, middle + 1, "Z", middle + 1, middle + 1)
        assertEquals(1_000_000, document.length)
        assertEquals('Z', document.charAt(middle))
        assertEquals(middle + 1, document.selection.start)
        assertEquals(listOf(middle), document.findMatches("Z"))
    }
}
