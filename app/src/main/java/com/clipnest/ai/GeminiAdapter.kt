package com.clipnest.ai

import com.clipnest.security.SecureApiKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class GeminiAdapter(
    private val apiKeyStore: SecureApiKeyStore,
    private val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta/interactions"
) : AiProvider {
    override suspend fun generate(request: AiRequest): Result<AiResponse> = withContext(Dispatchers.IO) {
        val apiKey = apiKeyStore.getGeminiApiKey()
            ?: return@withContext Result.failure(IllegalStateException("Gemini API key is not configured"))
        if (request.content.isBlank()) return@withContext Result.failure(IllegalArgumentException("AI request content is empty"))
        if (request.model.isBlank()) return@withContext Result.failure(IllegalArgumentException("Gemini model is not configured"))

        runCatching {
            val connection = (URL(baseUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 60_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("x-goog-api-key", apiKey)
            }
            try {
                val body = JSONObject()
                    .put("model", request.model)
                    .put("input", request.content)
                    .put("store", false)
                    .put("stream", false)
                    .toString()
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val responseBody = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (status !in 200..299) {
                    val json = runCatching { JSONObject(responseBody) }.getOrNull()
                    val nestedError = json?.optJSONObject("error")
                    val message = nestedError?.optString("message")?.takeIf { it.isNotBlank() }
                        ?: json?.optString("message")?.takeIf { it.isNotBlank() }
                    throw GeminiApiException(status, message ?: "Gemini request failed")
                }

                val text = extractText(JSONObject(responseBody))
                    ?: throw IOException("Gemini returned no text output")
                AiResponse(text)
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun extractText(json: JSONObject): String? {
        val steps = json.optJSONArray("steps") ?: return null
        val parts = buildString {
            for (i in 0 until steps.length()) {
                val step = steps.optJSONObject(i) ?: continue
                if (step.optString("type") != "model_output") continue
                val content = step.optJSONArray("content") ?: continue
                for (j in 0 until content.length()) {
                    val item = content.optJSONObject(j) ?: continue
                    if (item.optString("type") == "text") append(item.optString("text"))
                }
            }
        }.trim()
        return parts.takeIf { it.isNotEmpty() }
    }

    class GeminiApiException(val statusCode: Int, message: String) : IOException(message)
}
