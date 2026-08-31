package com.clipnest.ai

data class EmbeddingModel(
    val id: String,
    val displayName: String,
    val defaultOutputDimensions: Int = 3072
)

object EmbeddingModelCatalog {
    val models = listOf(
        EmbeddingModel(
            id = "gemini-embedding-2",
            displayName = "Gemini Embedding 2"
        )
    )

    val default: EmbeddingModel = models.first()
}
