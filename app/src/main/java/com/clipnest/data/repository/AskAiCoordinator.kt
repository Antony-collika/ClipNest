package com.clipnest.data.repository

import com.clipnest.ai.AiSettings

/** Coordinates Editor content -> AI -> Vault persistence without knowing the provider. */
class AskAiCoordinator(
    private val aiRepository: AiRepository,
    private val clipboardRepository: ClipboardRepository
) {
    suspend fun askAndSave(editorContent: String, settings: AiSettings): Result<String> {
        val response = aiRepository.generate(editorContent, settings)
            .getOrElse { return Result.failure(it) }

        clipboardRepository.saveCard(
            content = response,
            sourceApp = "Ask AI"
        ) ?: return Result.failure(IllegalStateException("Could not save AI response"))

        return Result.success(response)
    }
}
