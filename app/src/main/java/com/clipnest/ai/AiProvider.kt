package com.clipnest.ai

/** Provider-neutral contract for a single completed AI generation. */
interface AiProvider {
    suspend fun generate(request: AiRequest): Result<AiResponse>
}

data class AiRequest(
    val content: String,
    val model: String
)

data class AiResponse(
    val text: String
)

/** Reserved for future streaming without making the current generation API streaming-specific. */
sealed interface AiStreamEvent {
    data class TextDelta(val text: String) : AiStreamEvent
    data object Completed : AiStreamEvent
    data class Failed(val error: Throwable) : AiStreamEvent
}
