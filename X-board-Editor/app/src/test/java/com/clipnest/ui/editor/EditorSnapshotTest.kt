package com.clipnest.ui.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorSnapshotTest {
    @Test
    fun snapshotCarriesRevisionAndText() {
        val document = EditorDocument("hello")
        val snapshot = document.snapshot(revision = 7L)
        assertTrue(snapshot.isCurrent(7L))
        assertFalse(snapshot.isCurrent(8L))
    }

    @Test
    fun snapshotIsAnExplicitWholeDocumentBoundary() {
        val document = EditorDocument("hello world")
        val snapshot = document.snapshot(revision = 3L)
        document.execute(EditorCommand.Replace(6, 11, "editor"))
        assertTrue(snapshot.text == "hello world")
        assertTrue(document.snapshot() == "hello editor")
    }
}
