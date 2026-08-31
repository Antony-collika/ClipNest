package com.clipnest.ui.settings

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clipnest.ai.AiProviderType
import com.clipnest.ai.GeminiModelCatalog
import com.clipnest.data.local.AppLanguage
import com.clipnest.data.local.EditorTextSize
import com.clipnest.data.local.ThemePreset
import com.clipnest.data.local.ViewerTextSize
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onRequestSaveFolder: () -> Unit,
    onRequestBackup: () -> Unit,
    onRequestRestore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val userSettings by viewModel.userSettings.collectAsStateWithLifecycle()
    val exportedFiles by viewModel.exportedFiles.collectAsStateWithLifecycle()
    var geminiApiKey by remember { mutableStateOf("") }
    var apiKeyStatusVersion by remember { mutableStateOf(0) }
    val hasGeminiApiKey = remember(apiKeyStatusVersion) { viewModel.geminiApiKeyConfigured() }

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is SettingsEvent.ShowToast -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        contentPadding = PaddingValues(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SettingsSectionHeader(title = stringResource(com.clipnest.R.string.language), icon = Icons.Default.Language)
            OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeChip(stringResource(com.clipnest.R.string.english), userSettings.language == AppLanguage.ENGLISH, { viewModel.setLanguage(AppLanguage.ENGLISH) }, Modifier.testTag("language_chip_english"))
                    ThemeChip(stringResource(com.clipnest.R.string.vietnamese), userSettings.language == AppLanguage.VIETNAMESE, { viewModel.setLanguage(AppLanguage.VIETNAMESE) }, Modifier.testTag("language_chip_vietnamese"))
                }
            }
        }

        item {
            SettingsSectionHeader(title = "AI", icon = Icons.Default.Security)
            OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Provider", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        FilterChip(
                            selected = userSettings.aiProvider == AiProviderType.GEMINI.name,
                            onClick = { viewModel.setAiProvider(AiProviderType.GEMINI) },
                            label = { Text("Gemini BYOK") },
                            modifier = Modifier.testTag("ai_provider_gemini")
                        )
                        FilterChip(
                            selected = userSettings.aiProvider == AiProviderType.VERCEL.name,
                            onClick = { viewModel.setAiProvider(AiProviderType.VERCEL) },
                            label = { Text("Vercel") },
                            modifier = Modifier.testTag("ai_provider_vercel")
                        )
                    }

                    Text("Gemini model", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        GeminiModelCatalog.models.forEach { model ->
                            FilterChip(
                                selected = userSettings.geminiModelId == model.id,
                                onClick = { viewModel.setGeminiModel(model.id) },
                                label = { Text(model.displayName) },
                                modifier = Modifier.testTag("gemini_model_${model.id}")
                            )
                        }
                    }

                    OutlinedTextField(
                        value = geminiApiKey,
                        onValueChange = { geminiApiKey = it },
                        label = { Text("Gemini API key") },
                        placeholder = { Text(if (hasGeminiApiKey) "Key already configured" else "Enter API key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("gemini_api_key_input")
                    )
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            onClick = {
                                if (geminiApiKey.isNotBlank()) {
                                    viewModel.saveGeminiApiKey(geminiApiKey)
                                    geminiApiKey = ""
                                    apiKeyStatusVersion++
                                }
                            },
                            enabled = geminiApiKey.isNotBlank(),
                            modifier = Modifier.testTag("gemini_api_key_save")
                        ) { Text("Save key") }
                        if (hasGeminiApiKey) {
                            TextButton(
                                onClick = {
                                    viewModel.deleteGeminiApiKey()
                                    apiKeyStatusVersion++
                                },
                                modifier = Modifier.testTag("gemini_api_key_delete")
                            ) { Text("Delete key") }
                        }
                    }
                    Text(
                        if (hasGeminiApiKey) "Gemini API key is configured securely on this device." else "No Gemini API key is configured.",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }
        }

        item {
            SettingsSectionHeader(title = stringResource(com.clipnest.R.string.appearance), icon = Icons.Default.BrightnessMedium)
            OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(com.clipnest.R.string.theme_preset), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                    Text(stringResource(com.clipnest.R.string.theme_preset_description), style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant), modifier = Modifier.padding(top = 4.dp))
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemePreset.entries.forEach { preset -> ThemeChip(themePresetLabel(preset), userSettings.themePreset == preset, { viewModel.setThemePreset(preset) }, Modifier.testTag("theme_preset_${preset.name.lowercase(Locale.ROOT)}")) }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(stringResource(com.clipnest.R.string.editor_text_size), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                    Text(stringResource(com.clipnest.R.string.editor_text_size_description), style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant), modifier = Modifier.padding(top = 4.dp))
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EditorTextSize.entries.forEach { size -> TextSizeChip("${size.sp}sp", userSettings.editorTextSize == size, { viewModel.setEditorTextSize(size) }, Modifier.testTag("editor_text_size_${size.name.lowercase(Locale.ROOT)}")) }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(stringResource(com.clipnest.R.string.viewer_text_size), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                    Text(stringResource(com.clipnest.R.string.viewer_text_size_description), style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant), modifier = Modifier.padding(top = 4.dp))
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ViewerTextSize.entries.forEach { size -> TextSizeChip("${size.px}px", userSettings.viewerTextSize == size, { viewModel.setViewerTextSize(size) }, Modifier.testTag("viewer_text_size_${size.name.lowercase(Locale.ROOT)}")) }
                    }
                }
            }
        }

        item {
            SettingsSectionHeader(title = stringResource(com.clipnest.R.string.vault_preferences), icon = Icons.Default.PushPin)
            OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(com.clipnest.R.string.show_pinned_first), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                        Text(stringResource(com.clipnest.R.string.pinned_first_description), style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                    }
                    Switch(userSettings.showPinnedFirst, viewModel::setShowPinnedFirst, modifier = Modifier.testTag("settings_switch_pinned_first"))
                }
            }
        }

        item {
            SettingsSectionHeader(title = stringResource(com.clipnest.R.string.file_storage), icon = Icons.Default.Folder)
            OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(com.clipnest.R.string.default_save_folder), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                    Text(userSettings.defaultSaveFolderUri?.let { folderLabel(context, it) } ?: stringResource(com.clipnest.R.string.save_folder_not_set), style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant), modifier = Modifier.padding(top = 4.dp))
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onRequestSaveFolder) { Text(stringResource(com.clipnest.R.string.choose_folder)) }
                        if (userSettings.defaultSaveFolderUri != null) TextButton(onClick = viewModel::clearDefaultSaveFolder) { Text(stringResource(com.clipnest.R.string.clear)) }
                    }
                }
            }
        }

        item {
            SettingsSectionHeader(title = stringResource(com.clipnest.R.string.backup_restore), icon = Icons.Default.Folder)
            OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(com.clipnest.R.string.backup_restore_description), style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onRequestBackup, modifier = Modifier.testTag("settings_backup_button")) { Text(stringResource(com.clipnest.R.string.backup_vault)) }
                        TextButton(onClick = onRequestRestore, modifier = Modifier.testTag("settings_restore_button")) { Text(stringResource(com.clipnest.R.string.restore_vault)) }
                    }
                }
            }
        }

        item {
            SettingsSectionHeader(title = stringResource(com.clipnest.R.string.capture_shortcuts), icon = Icons.Default.Notifications)
            OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(com.clipnest.R.string.capture_notification), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                            Text(stringResource(com.clipnest.R.string.capture_notification_description), style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                        }
                        Switch(userSettings.notificationEnabled, { enabled -> viewModel.setNotificationEnabled(enabled, context) }, modifier = Modifier.testTag("settings_switch_notification"))
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Widgets, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(stringResource(com.clipnest.R.string.quick_settings_tile), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text(stringResource(com.clipnest.R.string.quick_settings_description), style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                        }
                    }
                }
            }
        }

        item {
            if (exportedFiles.isNotEmpty()) {
                SettingsSectionHeader(title = stringResource(com.clipnest.R.string.exported_documents), icon = Icons.Default.Folder)
                OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        exportedFiles.forEachIndexed { index, file ->
                            if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(file.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
                                    Text(stringResource(com.clipnest.R.string.file_details, dateStr, file.length()), style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                                }
                                IconButton(onClick = { shareFile(context, file) }) { Icon(Icons.Default.Share, contentDescription = stringResource(com.clipnest.R.string.share_file), tint = MaterialTheme.colorScheme.primary) }
                            }
                        }
                    }
                }
            }
        }

        item {
            SettingsSectionHeader(title = stringResource(com.clipnest.R.string.privacy_security), icon = Icons.Default.Security)
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(com.clipnest.R.string.private_by_design), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(com.clipnest.R.string.private_notice), style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface, lineHeight = 18.sp))
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun folderLabel(context: Context, uri: String): String {
    val segment = android.net.Uri.parse(uri).lastPathSegment.orEmpty()
    return context.getString(com.clipnest.R.string.selected_folder, segment.substringAfterLast(':').ifBlank { context.getString(com.clipnest.R.string.selected_folder_fallback) })
}

private fun shareFile(context: Context, file: File) {
    try {
        val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, contentUri)
            type = when {
                file.name.endsWith(".md", ignoreCase = true) -> "text/markdown"
                file.name.endsWith(".json", ignoreCase = true) -> "application/json"
                else -> "text/plain"
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri(file.name, contentUri)
        }
        context.startActivity(Intent.createChooser(sendIntent, context.getString(com.clipnest.R.string.share_named_file, file.name)))
    } catch (_: Exception) {
        Toast.makeText(context, context.getString(com.clipnest.R.string.could_not_open_file), Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun SettingsSectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface))
    }
}

@Composable
private fun themePresetLabel(preset: ThemePreset): String = when (preset) {
    ThemePreset.LIGHT -> stringResource(com.clipnest.R.string.theme_light)
    ThemePreset.DARK -> stringResource(com.clipnest.R.string.theme_dark)
    ThemePreset.MIDNIGHT_BLUE -> stringResource(com.clipnest.R.string.theme_midnight_blue)
    ThemePreset.FOREST -> stringResource(com.clipnest.R.string.theme_forest)
    ThemePreset.LAVENDER -> stringResource(com.clipnest.R.string.theme_lavender)
    ThemePreset.NORD -> stringResource(com.clipnest.R.string.theme_nord)
    ThemePreset.SOLARIZED -> stringResource(com.clipnest.R.string.theme_solarized)
    ThemePreset.SOFT_PAPER_CREAM -> stringResource(com.clipnest.R.string.theme_soft_paper_cream)
    ThemePreset.MIDNIGHT_OLED -> stringResource(com.clipnest.R.string.theme_midnight_oled)
    ThemePreset.SAGE_SLATE -> stringResource(com.clipnest.R.string.theme_sage_slate)
}

@Composable
private fun TextSizeChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) }, leadingIcon = if (selected) { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) } } else null, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer, selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer), modifier = modifier)
}

@Composable
private fun ThemeChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) }, leadingIcon = if (selected) { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) } } else null, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer, selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer), modifier = modifier)
}
