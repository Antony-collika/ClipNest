package com.clipnest.ai

data class AiSettings(
    val provider: AiProviderType = AiProviderType.GEMINI,
    val geminiModelId: String = GeminiModelCatalog.default.id
)
