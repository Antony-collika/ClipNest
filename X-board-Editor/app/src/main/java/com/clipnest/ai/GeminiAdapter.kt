package com.clipnest.ai

import com.clipnest.security.SecureApiKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
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

        if (request.model == "gemini-embedding-001" || request.model == "gemini-embedding-2") {
            return@withContext embed(request, apiKey)
        }

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
                val bodyJson = JSONObject()
                    .put("model", request.model)
                    .put("input", request.content)
                    .put("store", false)
                    .put("stream", false)
                if (request.prompt.isNotBlank()) {
                    bodyJson.put("system_instruction", request.prompt.trim())
                }
                val body = bodyJson.toString()
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val responseBody = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (status !in 200..299) throw apiException(status, responseBody)

                val text = extractText(JSONObject(responseBody))
                    ?: throw IOException("Gemini returned no text output")
                AiResponse(text)
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun embed(request: AiRequest, apiKey: String): Result<AiResponse> = runCatching {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/${request.model}:embedContent"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
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
                .put("model", "models/${request.model}")
                .put("content", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", request.content))))
                .put("output_dimensionality", 3072)
                .toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw apiException(status, responseBody)

            val json = JSONObject(responseBody)
            val embedding = json.optJSONObject("embedding")
                ?: throw IOException("Gemini returned no embedding")
            val values = embedding.optJSONArray("values")
                ?: throw IOException("Gemini returned no embedding values")
            AiResponse(formatEmbedding(request.model, values))
        } finally {
            connection.disconnect()
        }
    }

    private fun formatEmbedding(model: String, values: JSONArray): String = buildString {
        append("Embedding model: ").append(model).append('\n')
        append("Dimensions: ").append(values.length()).append('\n')
        append("\nVector:\n[")
        for (i in 0 until values.length()) {
            if (i > 0) append(", ")
            append(values.optDouble(i))
        }
        append("]")
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

    private fun apiException(status: Int, body: String): GeminiApiException {
        val json = runCatching { JSONObject(body) }.getOrNull()
        val nestedError = json?.optJSONObject("error")
        val message = nestedError?.optString("message")?.takeIf { it.isNotBlank() }
            ?: json?.optString("message")?.takeIf { it.isNotBlank() }
        return GeminiApiException(status, message ?: "Gemini request failed")
    }

    class GeminiApiException(val statusCode: Int, message: String) : IOException(message)
}
