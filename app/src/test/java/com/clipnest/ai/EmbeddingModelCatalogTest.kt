package com.clipnest.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class EmbeddingModelCatalogTest {
    @Test
    fun bothEmbeddingModelsAreAvailableInGeminiCatalog() {
        assertEquals(GeminiModelCatalog.Kind.EMBEDDING, GeminiModelCatalog.find("gemini-embedding-001")?.kind)
        assertEquals(GeminiModelCatalog.Kind.EMBEDDING, GeminiModelCatalog.find("gemini-embedding-2")?.kind)
        assertNotNull(GeminiModelCatalog.find("gemini-embedding-001"))
        assertNotNull(GeminiModelCatalog.find("gemini-embedding-2"))
    }
}
