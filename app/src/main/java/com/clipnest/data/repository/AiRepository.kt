package com.clipnest.data.repository

import com.clipnest.data.ai.AiApi
import com.clipnest.data.ai.AiGenerateRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AiRepository(
    private val api: AiApi
) {
    suspend fun generate(content: String): Result<String> = withContext(Dispatchers.IO) {
        if (content.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Editor content is empty"))
        }

        runCatching {
            val response = api.generate(AiGenerateRequest(content))
            if (!response.success) {
                error(response.error ?: "Ask AI failed")
            }
            response.text?.takeIf { it.isNotBlank() }
                ?: error("AI returned an empty response")
        }
    }
}
