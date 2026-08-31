package com.clipnest.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class EmbeddingExperiment(
    private val client: OkHttpClient = OkHttpClient()
) {
    suspend fun embed(apiKey: String, text: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(text.isNotBlank()) { "Text cannot be empty" }
            val body = JSONObject()
                .put("content", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", text))))
                .put(
                    "embedContentConfig",
                    JSONObject().put("outputDimensionality", EmbeddingModelCatalog.default.defaultOutputDimensions)
                )
                .toString()
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/${EmbeddingModelCatalog.default.id}:embedContent")
                .header("x-goog-api-key", apiKey)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("HTTP ${response.code}: $raw")
                raw
            }
        }
    }
}
