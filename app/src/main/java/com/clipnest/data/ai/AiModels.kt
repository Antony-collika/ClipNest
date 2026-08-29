package com.clipnest.data.ai

data class AiGenerateRequest(
    val content: String
)

data class AiGenerateResponse(
    val success: Boolean,
    val text: String? = null,
    val error: String? = null
)
