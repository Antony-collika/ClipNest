package com.clipnest.data.repository

import com.clipnest.ai.AiProviderResolver
import com.clipnest.ai.AiRequest
import com.clipnest.ai.AiSettings
import com.clipnest.ai.ProviderFactory
import com.clipnest.data.local.AppDatabase
import com.clipnest.data.local.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class AiRepository(
    private val providerResolver: AiProviderResolver,
    private val settingsDataStore: SettingsDataStore
) {
    constructor(api: com.clipnest.data.ai.AiApi) : this(
        providerResolver = ProviderFactory.create(
            AppDatabase.applicationContext(),
            apiBaseUrl = api.baseUrl
        ),
        settingsDataStore = SettingsDataStore(AppDatabase.applicationContext())
    )

    suspend fun generate(content: String): Result<String> = withContext(Dispatchers.IO) {
        val settings = settingsDataStore.userSettingsFlow.first()
        generate(content, AiSettings(settings.aiProvider.toProviderType(), settings.geminiModelId))
    }

    suspend fun generate(content: String, settings: AiSettings): Result<String> = withContext(Dispatchers.IO) {
        if (content.isBlank()) return@withContext Result.failure(IllegalArgumentException("Editor content is empty"))
        providerResolver.resolve(settings.provider)
            .generate(AiRequest(content = content, model = settings.geminiModelId))
            .map { it.text }
    }

    private fun String.toProviderType() =
        runCatching { com.clipnest.ai.AiProviderType.valueOf(this) }
            .getOrDefault(com.clipnest.ai.AiProviderType.GEMINI)
}
