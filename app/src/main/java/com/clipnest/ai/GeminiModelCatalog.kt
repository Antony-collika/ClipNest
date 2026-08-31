package com.clipnest.ai

/** Models explicitly supported by the current X-board Gemini integration. */
object GeminiModelCatalog {
    data class Model(
        val id: String,
        val displayName: String
    )

    // The current GeminiAdapter uses the Interactions API. Keep this catalog
    // limited to models currently documented as supported there.
    val models: List<Model> = listOf(
        Model("gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite"),
        Model("gemini-3.1-flash-lite", "Gemini 3.1 Flash-Lite"),
        Model("gemini-3.5-flash", "Gemini 3.5 Flash"),
        Model("gemini-3-flash-preview", "Gemini 3 Flash (Preview)"),
        Model("gemma-4-31b-it", "Gemma 4 31B"),
        Model("gemma-4-26b-a4b-it", "Gemma 4 26B")
    )

    val default: Model = models.first()

    fun find(id: String): Model? = models.firstOrNull { it.id == id }
}
