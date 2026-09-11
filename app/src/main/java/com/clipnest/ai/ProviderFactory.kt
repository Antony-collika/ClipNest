package com.clipnest.ai

import android.content.Context
import com.clipnest.data.ai.AiApi
import com.clipnest.security.SecureApiKeyStore

object ProviderFactory {
    fun create(context: Context, vercelBaseUrl: String): AiProviderResolver {
        val secureStore = SecureApiKeyStore(context.applicationContext)
        return AiProviderResolver(
            gemini = GeminiAdapter(secureStore),
            vercel = VercelAdapter(AiApi(vercelBaseUrl))
        )
    }
}
