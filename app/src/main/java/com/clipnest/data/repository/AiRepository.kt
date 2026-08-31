package com.clipnest.data.repository

import com.clipnest.ai.AiProviderResolver
import com.clipnest.ai.AiRequest
import com.clipnest.ai.AiSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AiRepository(
    private val providerResolver: AiProviderResolver
) {
    suspend fun generate(content: String, settings: AiSettings): Result<String> = withContext(Dispatchers.IO) {
        if (content.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Editor content is empty"))
        }
        providerResolver.resolve(settings.provider)
            .generate(AiRequest(content = content, model = settings.geminiModelId))
            .map { it.text }
    }
}
