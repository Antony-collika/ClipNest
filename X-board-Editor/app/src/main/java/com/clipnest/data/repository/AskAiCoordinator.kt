package com.clipnest.data.repository

import com.clipnest.ai.AiSettings

/** Coordinates Editor content -> AI -> Vault persistence without knowing the provider. */
class AskAiCoordinator(
    private val aiRepository: AiRepository,
    private val clipboardRepository: ClipboardRepository
) {
    suspend fun askAndSave(editorContent: String): Result<String> =
        saveResponse(aiRepository.generate(editorContent))

    suspend fun askAndSave(editorContent: String, settings: AiSettings): Result<String> =
        saveResponse(aiRepository.generate(editorContent, settings))

    private suspend fun saveResponse(result: Result<String>): Result<String> {
        val response = result.getOrElse { return Result.failure(it) }
        clipboardRepository.saveCard(content = response, sourceApp = "Ask AI")
            ?: return Result.failure(IllegalStateException("Could not save AI response"))
        return Result.success(response)
    }
}
