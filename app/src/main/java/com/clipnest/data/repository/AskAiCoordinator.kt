package com.clipnest.data.repository

/**
 * Coordinates the persistence side of an Ask AI response.
 * The AI response is stored as a normal Vault card so it follows the
 * existing Vault ordering, preview and content-type rules.
 */
class AskAiCoordinator(
    private val aiRepository: AiRepository,
    private val clipboardRepository: ClipboardRepository
) {
    suspend fun askAndSave(editorContent: String): Result<String> {
        val response = aiRepository.generate(editorContent)
            .getOrElse { return Result.failure(it) }

        clipboardRepository.saveCard(
            content = response,
            sourceApp = "Ask AI"
        ) ?: return Result.failure(IllegalStateException("Could not save AI response"))

        return Result.success(response)
    }
}
