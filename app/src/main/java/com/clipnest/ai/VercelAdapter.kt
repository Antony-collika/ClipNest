package com.clipnest.ai

import com.clipnest.data.ai.AiApi

class VercelAdapter(
    private val api: AiApi
) : AiProvider {
    override suspend fun generate(request: AiRequest): Result<AiResponse> = runCatching {
        api.generate(com.clipnest.data.ai.AiGenerateRequest(request.content)).let { response ->
            if (!response.success) error(response.error ?: "Vercel AI request failed")
            AiResponse(response.text?.takeIf { it.isNotBlank() } ?: error("Vercel returned an empty response"))
        }
    }
}
