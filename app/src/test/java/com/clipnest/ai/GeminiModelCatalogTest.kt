package com.clipnest.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class GeminiModelCatalogTest {
    @Test
    fun defaultAndInitialModelsMatchCurrentCatalog() {
        assertEquals("gemini-3.5-flash-lite", GeminiModelCatalog.default.id)
        assertNotNull(GeminiModelCatalog.find("gemma-4-31b-it"))
    }
}
