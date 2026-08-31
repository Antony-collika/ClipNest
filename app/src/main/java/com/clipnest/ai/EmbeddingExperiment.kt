package com.clipnest.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class EmbeddingExperiment {
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

            val connection = (URL("https://generativelanguage.googleapis.com/v1beta/models/${EmbeddingModelCatalog.default.id}:embedContent")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 60_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("x-goog-api-key", apiKey)
            }

            try {
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (status !in 200..299) error("HTTP $status: $raw")
                raw
            } finally {
                connection.disconnect()
            }
        }
    }
}
