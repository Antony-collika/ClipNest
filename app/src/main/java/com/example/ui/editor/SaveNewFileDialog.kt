package com.example.ui.editor

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.data.local.ExportFormat

@Composable
fun SaveNewFileDialog(
    defaultFolderUri: String?,
    initialFileName: String = "Editor",
    onChooseFolder: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (fileName: String, format: ExportFormat) -> Unit
) {
    var fileName by remember(initialFileName) { mutableStateOf(initialFileName.substringBeforeLast('.').ifBlank { "Editor" }) }
    var selectedFormat by remember { mutableStateOf(ExportFormat.MARKDOWN) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = stringResource(com.example.R.string.save_file),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text(stringResource(com.example.R.string.filename)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("save_new_filename_input")
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = stringResource(com.example.R.string.format),
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                ) {
                    RadioButton(
                        selected = selectedFormat == ExportFormat.MARKDOWN,
                        onClick = { selectedFormat = ExportFormat.MARKDOWN },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colorScheme.primary,
                            unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.testTag("save_new_format_markdown")
                    )
                    Text(stringResource(com.example.R.string.markdown_format))

                    Spacer(modifier = Modifier.width(10.dp))

                    RadioButton(
                        selected = selectedFormat == ExportFormat.PLAIN_TEXT,
                        onClick = { selectedFormat = ExportFormat.PLAIN_TEXT },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colorScheme.primary,
                            unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.testTag("save_new_format_plain_text")
                    )
                    Text(stringResource(com.example.R.string.plain_text_format))
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (defaultFolderUri.isNullOrBlank()) {
                        stringResource(com.example.R.string.save_location_not_set)
                    } else {
                        stringResource(com.example.R.string.save_location, folderLabel(defaultFolderUri))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("save_new_folder_label")
                )
                TextButton(
                    onClick = onChooseFolder,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("save_new_choose_folder_button")
                ) {
                    Text(stringResource(com.example.R.string.choose_folder))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (fileName.isNotBlank()) onConfirm(fileName, selectedFormat) },
                enabled = fileName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.testTag("save_new_confirm_button")
            ) {
                Text(stringResource(com.example.R.string.save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.testTag("save_new_cancel_button")
            ) {
                Text(stringResource(com.example.R.string.cancel))
            }
        },
        modifier = Modifier.testTag("save_new_file_dialog")
    )
}

private fun folderLabel(uri: String): String {
    val segment = Uri.parse(uri).lastPathSegment.orEmpty()
    return segment.substringAfterLast(':').ifBlank { "Selected folder" }
}
