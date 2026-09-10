package com.clipnest.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorSearchEngineTest {
    @Test
    fun searchesLiveCharSequenceWithoutDocumentSnapshot() {
        val document = EditorDocument("One two ONE two")
        assertEquals(listOf(0, 8), EditorSearchEngine.findMatches(document, "one"))
        assertEquals(listOf(4, 12), EditorSearchEngine.findMatches(document.charSequence, "two"))
    }

    @Test
    fun overlappingMatchesAreHandledDeterministically() {
        assertEquals(listOf(0, 2), EditorSearchEngine.findMatches("aaaaa", "aa"))
    }

    @Test
    fun emptyOrTooLongQueryReturnsNoMatches() {
        assertEquals(emptyList<Int>(), EditorSearchEngine.findMatches("abc", ""))
        assertEquals(emptyList<Int>(), EditorSearchEngine.findMatches("abc", "abcd"))
    }
}
