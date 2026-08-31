package com.clipnest.ai

/** Models explicitly supported by the current X-board Gemini integration. */
object GeminiModelCatalog {
    data class Model(
        val id: String,
        val displayName: String
    )

    // IDs are taken from Google's current Gemini API model catalog.
    val models: List<Model> = listOf(
        Model("gemini-2.5-flash-lite", "Gemini 2.5 Flash-Lite"),
        Model("gemma-4-31b-it", "Gemma 4")
    )

    val default: Model = models.first()

    fun find(id: String): Model? = models.firstOrNull { it.id == id }
}
