package com.clipnest.data.ai

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

class AiApi(
    val baseUrl: String
) {
    suspend fun generate(request: AiGenerateRequest): AiGenerateResponse {
        if (baseUrl.isBlank()) throw IOException("AI backend URL is not configured")

        val endpoint = baseUrl.trimEnd('/') + "/api/ai/generate"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }

        return try {
            val body = JSONObject()
                .put("content", request.content)
                .put("model", request.model)
                .put("prompt", request.prompt)
                .toString()
            connection.outputStream.use { output -> output.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(responseBody) }.getOrNull()
            if (status !in 200..299) {
                AiGenerateResponse(false, error = json?.optString("error")?.takeIf { it.isNotBlank() } ?: "AI request failed")
            } else {
                AiGenerateResponse(
                    success = json?.optBoolean("success", false) == true,
                    text = json?.optString("text")?.takeIf { it.isNotBlank() },
                    error = json?.optString("error")?.takeIf { it.isNotBlank() }
                )
            }
        } finally {
            connection.disconnect()
        }
    }
}
