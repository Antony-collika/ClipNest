package com.clipnest.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GeminiModelCatalogTest {
    @Test
    fun defaultAndCurrentModelsMatchCatalog() {
        assertEquals("gemini-3.5-flash-lite", GeminiModelCatalog.default.id)
        assertNotNull(GeminiModelCatalog.find("gemini-3.1-flash-lite"))
        assertNotNull(GeminiModelCatalog.find("gemma-4-31b-it"))
        assertNotNull(GeminiModelCatalog.find("gemma-4-26b-a4b-it"))
        assertNull(GeminiModelCatalog.find("gemini-2.5-flash"))
        assertNull(GeminiModelCatalog.find("gemini-2.5-flash-lite"))
    }
}
