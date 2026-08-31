package com.clipnest.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AiRepository(
    private val providerResolver: AiProviderResolver
) {
    suspend fun generate(
        content: String,
        settings: AiSettings
    ): Result<String> = withContext(Dispatchers.IO) {
        if (content.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Editor content is empty"))
        }
        val provider = providerResolver.resolve(settings.provider)
        provider.generate(
            AiRequest(
                content = content,
                model = settings.geminiModelId
            )
        ).map { it.text }
    }
}
