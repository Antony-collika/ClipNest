package com.clipnest.ui.editor

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.clipnest.data.local.ExportFormat

enum class SaveFileMode {
    MARKDOWN,
    PLAIN_TEXT,
    ENCRYPTED
}

@Composable
fun SaveNewFileDialog(
    defaultFolderUri: String?,
    initialFileName: String = "Editor",
    onChooseFolder: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (fileName: String, format: ExportFormat) -> Unit
) {
    val defaultDocumentName = stringResource(com.clipnest.R.string.editor)
    val selectedFolderFallback = stringResource(com.clipnest.R.string.selected_folder_fallback)
    var fileName by remember(initialFileName, defaultDocumentName) {
        mutableStateOf(initialFileName.substringBeforeLast('.').ifBlank { defaultDocumentName })
    }
    var selectedMode by remember { mutableStateOf(SaveFileMode.MARKDOWN) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val previewExtension = when (selectedMode) {
        SaveFileMode.MARKDOWN -> ".md"
        SaveFileMode.PLAIN_TEXT -> ".txt"
        SaveFileMode.ENCRYPTED -> ".cne"
    }
    val previewName = fileName.trim().ifBlank { "Untitled" }.let {
        if (it.endsWith(previewExtension, ignoreCase = true)) it else "$it$previewExtension"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = stringResource(com.clipnest.R.string.save_file),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text(stringResource(com.clipnest.R.string.filename)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("save_new_filename_input")
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = stringResource(com.clipnest.R.string.format),
                    style = MaterialTheme.typography.labelLarge
                )
                SaveModeRadio(
                    selected = selectedMode == SaveFileMode.MARKDOWN,
                    label = stringResource(com.clipnest.R.string.markdown_format),
                    onClick = { selectedMode = SaveFileMode.MARKDOWN },
                    tag = "save_new_format_markdown"
                )
                SaveModeRadio(
                    selected = selectedMode == SaveFileMode.PLAIN_TEXT,
                    label = stringResource(com.clipnest.R.string.plain_text_format),
                    onClick = { selectedMode = SaveFileMode.PLAIN_TEXT },
                    tag = "save_new_format_plain_text"
                )
                SaveModeRadio(
                    selected = selectedMode == SaveFileMode.ENCRYPTED,
                    label = "Encrypted (.cne)",
                    onClick = { selectedMode = SaveFileMode.ENCRYPTED },
                    tag = "save_new_format_encrypted"
                )

                if (selectedMode == SaveFileMode.ENCRYPTED) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("save_new_password_input")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "File will be saved as:\n$previewName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("save_new_filename_preview")
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (defaultFolderUri.isNullOrBlank()) {
                        stringResource(com.clipnest.R.string.save_location_not_set)
                    } else {
                        stringResource(com.clipnest.R.string.save_location, folderLabel(defaultFolderUri, selectedFolderFallback))
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
                    Text(stringResource(com.clipnest.R.string.choose_folder))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (fileName.isNotBlank() && (selectedMode != SaveFileMode.ENCRYPTED || password.isNotEmpty())) {
                        if (selectedMode == SaveFileMode.ENCRYPTED) {
                            ExportFormat.MARKDOWN.encryptionPassword = password
                            onConfirm(fileName, ExportFormat.MARKDOWN)
                        } else {
                            ExportFormat.MARKDOWN.encryptionPassword = null
                            ExportFormat.PLAIN_TEXT.encryptionPassword = null
                            onConfirm(
                                fileName,
                                if (selectedMode == SaveFileMode.MARKDOWN) ExportFormat.MARKDOWN else ExportFormat.PLAIN_TEXT
                            )
                        }
                    }
                },
                enabled = fileName.isNotBlank() && (selectedMode != SaveFileMode.ENCRYPTED || password.isNotEmpty()),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.testTag("save_new_confirm_button")
            ) {
                Text(stringResource(com.clipnest.R.string.save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.testTag("save_new_cancel_button")
            ) {
                Text(stringResource(com.clipnest.R.string.cancel))
            }
        },
        modifier = Modifier.testTag("save_new_file_dialog")
    )
}

@Composable
private fun SaveModeRadio(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    tag: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().testTag(tag)
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        Text(label)
    }
}

private fun folderLabel(uri: String, fallback: String): String {
    val segment = Uri.parse(uri).lastPathSegment.orEmpty()
    return segment.substringAfterLast(':').ifBlank { fallback }
}

/**
 * Prompts for the password of a document ClipNest itself encrypted (identified by
 * its `format` field, not by file extension). Shown instead of loading raw
 * ciphertext into the editor. [isError] reflects a failed decrypt attempt; the
 * caller clears it as soon as the person edits the password field again.
 */
@Composable
fun DecryptOpenDialog(
    displayName: String,
    isError: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var lastAttempted by remember { mutableStateOf<String?>(null) }
    val showError = isError && password == lastAttempted

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = stringResource(com.clipnest.R.string.decrypt_open_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(com.clipnest.R.string.decrypt_open_description, displayName),
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(com.clipnest.R.string.backup_password_label)) },
                    singleLine = true,
                    isError = showError,
                    supportingText = if (showError) {
                        { Text(stringResource(com.clipnest.R.string.decrypt_open_wrong_password)) }
                    } else null,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("decrypt_open_password_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (password.isNotEmpty()) { lastAttempted = password; onConfirm(password) } },
                enabled = password.isNotEmpty(),
                modifier = Modifier.testTag("decrypt_open_confirm")
            ) {
                Text(stringResource(com.clipnest.R.string.decrypt_open_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("decrypt_open_cancel")) {
                Text(stringResource(com.clipnest.R.string.cancel))
            }
        },
        modifier = Modifier.testTag("decrypt_open_dialog")
    )
}
