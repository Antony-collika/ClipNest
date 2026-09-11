package com.clipnest.ai

data class AiSettings(
    val provider: AiProviderType = AiProviderType.GEMINI,
    val geminiModelId: String = GeminiModelCatalog.default.id,
    val vercelModelId: String = GeminiModelCatalog.default.id,
    val prompt: String = ""
) {
    val selectedModelId: String
        get() = when (provider) {
            AiProviderType.GEMINI -> geminiModelId
            AiProviderType.VERCEL -> vercelModelId
        }
}
