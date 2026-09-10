package com.clipnest.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorControllerTest {
    @Test
    fun commandsAreTheOnlyMutationBoundary() {
        val controller = EditorController(EditorDocument("abc"))
        controller.moveToDocumentEnd()
        controller.insert("!")
        controller.moveLeft()
        controller.deleteForward()
        assertEquals("abc", controller.snapshot())
        assertEquals(EditorSelection(3, 3), controller.selection)
    }

    @Test
    fun undoAndRedoAreExposedThroughTheSameCommandBoundary() {
        val controller = EditorController(EditorDocument("abc"))
        controller.undo()
        controller.redo()
        assertEquals("abc", controller.snapshot())
    }

    @Test
    fun selectionModelNormalizesToDocumentBounds() {
        assertEquals(EditorSelection(0, 4), EditorSelection(-3, 8).normalized(4))
        assertEquals(androidx.compose.ui.text.TextRange(1, 3), EditorSelection(1, 3).range)
    }

    @Test
    fun searchUsesTheDocumentBoundary() {
        val document = EditorDocument("one two ONE")
        val controller = EditorController(document)
        assertEquals(listOf(0, 8), controller.findMatches("one"))
        document.replace(0, 3, "ONE")
        assertEquals(listOf(0, 8), controller.findMatches("one"))
    }

    @Test
    fun replaceSelectionKeepsCaretAtReplacementEnd() {
        val controller = EditorController(EditorDocument("hello"))
        controller.setSelection(1, 4)
        controller.replaceSelection("i")
        assertEquals("hio", controller.snapshot())
        assertEquals(EditorSelection(2, 2), controller.selection)
    }
}
