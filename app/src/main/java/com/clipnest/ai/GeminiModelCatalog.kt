package com.clipnest.ai

/** Models explicitly supported by the current X-board Gemini integration. */
object GeminiModelCatalog {
    data class Model(
        val id: String,
        val displayName: String
    )

    val models: List<Model> = listOf(
        Model("gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite"),
        Model("gemma-4-31b-it", "Gemma 4")
    )

    val default: Model = models.first()

    fun find(id: String): Model? = models.firstOrNull { it.id == id }
}
