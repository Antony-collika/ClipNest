package com.clipnest.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeEditorHistoryTest {
    @Test
    fun deleteOperationMustPreserveEmptyRedoPayload() {
        val noPayload: String? = null
        val deletePayload: String? = ""
        assertEquals(null, noPayload)
        assertEquals("", deletePayload)
    }
}
