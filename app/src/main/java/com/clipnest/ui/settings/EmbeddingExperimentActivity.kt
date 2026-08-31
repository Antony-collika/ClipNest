package com.clipnest.ui.settings

import android.app.Activity
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.clipnest.ai.EmbeddingExperiment
import com.clipnest.security.SecureApiKeyStore
import kotlinx.coroutines.launch
import org.json.JSONObject

class EmbeddingExperimentActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val keyStore = SecureApiKeyStore(this)
        setContent {
            val scope = rememberCoroutineScope()
            var input by remember { mutableStateOf("ClipNest is a personal clipboard manager.") }
            var output by remember { mutableStateOf("") }
            var running by remember { mutableStateOf(false) }

            MaterialTheme {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Gemini Embedding 2", style = MaterialTheme.typography.titleLarge)
                    Text("Phòng thí nghiệm: gửi văn bản trực tiếp tới Gemini và xem response/vector thô.")
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4
                    )
                    Button(
                        enabled = !running && input.isNotBlank(),
                        onClick = {
                            scope.launch {
                                running = true
                                output = keyStore.getGeminiApiKey()?.let { apiKey ->
                                    EmbeddingExperiment().embed(apiKey, input).fold(
                                        onSuccess = { raw ->
                                            runCatching {
                                                val embedding = JSONObject(raw).optJSONObject("embedding")
                                                val values = embedding?.optJSONArray("values")
                                                buildString {
                                                    appendLine("Vector length: ${values?.length() ?: 0}")
                                                    appendLine("Shape: ${embedding?.optJSONArray("shape")}")
                                                    appendLine()
                                                    appendLine("Raw response:")
                                                    appendLine(raw)
                                                    if (values != null) {
                                                        appendLine()
                                                        appendLine("Vector values:")
                                                        appendLine(values.toString())
                                                    }
                                                }
                                            }.getOrDefault(raw)
                                        },
                                        onFailure = { "Error: ${it.message}" }
                                    )
                                } ?: "Chưa có Gemini API key. Hãy nhập key trong Settings trước."
                                running = false
                            }
                        }
                    ) {
                        Text(if (running) "Generating..." else "Generate embedding")
                    }
                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        item { Text(output, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}
