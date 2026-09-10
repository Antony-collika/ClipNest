package com.clipnest.ai

/** Models explicitly supported by the current X-board Gemini integration. */
object GeminiModelCatalog {
    enum class Kind {
        GENERATION,
        EMBEDDING
    }

    data class Model(
        val id: String,
        val displayName: String,
        val kind: Kind
    )

    val models: List<Model> = listOf(
        Model("gemini-3.7-flash", "Gemini 3.7 Flash", Kind.GENERATION),
        Model("gemini-3.6-flash", "Gemini 3.6 Flash", Kind.GENERATION),
        Model("gemini-3.5-flash", "Gemini 3.5 Flash", Kind.GENERATION),
        Model("gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite", Kind.GENERATION),
        Model("gemini-3.1-flash-lite", "Gemini 3.1 Flash-Lite", Kind.GENERATION),
        Model("gemini-3-flash-preview", "Gemini 3 Flash (Preview)", Kind.GENERATION),
        Model("gemma-4-31b-it", "Gemma 4 31B", Kind.GENERATION),
        Model("gemma-4-26b-a4b-it", "Gemma 4 26B", Kind.GENERATION),
        Model("gemini-embedding-001", "Gemini Embedding", Kind.EMBEDDING),
        Model("gemini-embedding-2", "Gemini Embedding 2", Kind.EMBEDDING)
    )

    val default: Model = models.first { it.id == "gemini-3.5-flash-lite" }

    fun find(id: String): Model? = models.firstOrNull { it.id == id }
}
