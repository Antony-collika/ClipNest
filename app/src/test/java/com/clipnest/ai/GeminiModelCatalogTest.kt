package com.clipnest.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GeminiModelCatalogTest {
    @Test
    fun defaultAndInitialModelsMatchCurrentCatalog() {
        assertEquals("gemini-3.5-flash-lite", GeminiModelCatalog.default.id)
        assertNotNull(GeminiModelCatalog.find("gemma-4-31b-it"))
    }
}
