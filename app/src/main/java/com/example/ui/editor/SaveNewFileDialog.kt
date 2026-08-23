package com.example.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.ExportFormat

@Composable
fun SaveNewFileDialog(
    onDismiss: () -> Unit,
    onConfirm: (fileName: String, format: ExportFormat) -> Unit
) {
    var fileName by remember { mutableStateOf("New_Note") }
    var selectedFormat by remember { mutableStateOf(ExportFormat.MARKDOWN) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Save to new file",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Export the current draft content to a new independent file in local documents:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("Filename") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("save_new_filename_input")
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("Format:", style = MaterialTheme.typography.labelLarge)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    RadioButton(
                        selected = selectedFormat == ExportFormat.MARKDOWN,
                        onClick = { selectedFormat = ExportFormat.MARKDOWN },
                        modifier = Modifier.testTag("save_new_format_markdown")
                    )
                    Text("Markdown (.md)")

                    Spacer(modifier = Modifier.width(16.dp))

                    RadioButton(
                        selected = selectedFormat == ExportFormat.PLAIN_TEXT,
                        onClick = { selectedFormat = ExportFormat.PLAIN_TEXT },
                        modifier = Modifier.testTag("save_new_format_plain_text")
                    )
                    Text("Plain text (.txt)")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (fileName.isNotBlank()) {
                        onConfirm(fileName, selectedFormat)
                    }
                },
                enabled = fileName.isNotBlank(),
                modifier = Modifier.testTag("save_new_confirm_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("save_new_cancel_button")
            ) {
                Text("Cancel")
            }
        },
        modifier = Modifier.testTag("save_new_file_dialog")
    )
}
