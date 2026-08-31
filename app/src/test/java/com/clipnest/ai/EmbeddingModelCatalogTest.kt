package com.clipnest.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class EmbeddingModelCatalogTest {
    @Test
    fun defaultEmbeddingModelIsGeminiEmbedding2() {
        assertEquals("gemini-embedding-2", EmbeddingModelCatalog.default.id)
        assertEquals(3072, EmbeddingModelCatalog.default.defaultOutputDimensions)
    }
}
