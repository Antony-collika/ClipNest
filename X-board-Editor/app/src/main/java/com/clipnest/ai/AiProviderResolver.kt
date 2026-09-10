package com.clipnest.ai

class AiProviderResolver(
    private val gemini: AiProvider,
    private val vercel: AiProvider
) {
    fun resolve(provider: AiProviderType): AiProvider = when (provider) {
        AiProviderType.GEMINI -> gemini
        AiProviderType.VERCEL -> vercel
    }
}

enum class AiProviderType {
    GEMINI,
    VERCEL
}
