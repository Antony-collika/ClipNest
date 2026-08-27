package com.example.ui.settings

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.AppLanguage
import com.example.data.local.EditorTextSize
import com.example.data.local.ThemeMode
import com.example.data.local.ThemePreset
import com.example.data.local.ViewerTextSize
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

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is SettingsEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SettingsSectionHeader(title = stringResource(com.example.R.string.language), icon = Icons.Default.Language)

            OutlinedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeChip(
                        label = stringResource(com.example.R.string.english),
                        selected = userSettings.language == AppLanguage.ENGLISH,
                        onClick = { viewModel.setLanguage(AppLanguage.ENGLISH) },
                        modifier = Modifier.testTag("language_chip_english")
                    )
                    ThemeChip(
                        label = stringResource(com.example.R.string.vietnamese),
                        selected = userSettings.language == AppLanguage.VIETNAMESE,
                        onClick = { viewModel.setLanguage(AppLanguage.VIETNAMESE) },
                        modifier = Modifier.testTag("language_chip_vietnamese")
                    )
                }
            }
            }

            // 1. Appearance Section
            item {
                SettingsSectionHeader(title = stringResource(com.example.R.string.appearance), icon = Icons.Default.BrightnessMedium)

            OutlinedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(com.example.R.string.theme_mode),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeChip(
                            label = stringResource(com.example.R.string.system),
                            selected = userSettings.themeMode == ThemeMode.SYSTEM,
                            onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
                            modifier = Modifier.testTag("theme_chip_system")
                        )
                        ThemeChip(
                            label = stringResource(com.example.R.string.light),
                            selected = userSettings.themeMode == ThemeMode.LIGHT,
                            onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) },
                            modifier = Modifier.testTag("theme_chip_light")
                        )
                        ThemeChip(
                            label = stringResource(com.example.R.string.dark),
                            selected = userSettings.themeMode == ThemeMode.DARK,
                            onClick = { viewModel.setThemeMode(ThemeMode.DARK) },
                            modifier = Modifier.testTag("theme_chip_dark")
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(com.example.R.string.theme_preset),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                    Text(
                        text = stringResource(com.example.R.string.theme_preset_description),
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemePreset.entries.forEach { preset ->
                            ThemeChip(
                                label = themePresetLabel(preset),
                                selected = userSettings.themePreset == preset,
                                onClick = { viewModel.setThemePreset(preset) },
                                modifier = Modifier.testTag("theme_preset_${preset.name.lowercase(Locale.ROOT)}")
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = stringResource(com.example.R.string.editor_text_size),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                    Text(
                        text = stringResource(com.example.R.string.editor_text_size_description),
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EditorTextSize.entries.forEach { size ->
                            TextSizeChip(
                                label = "${size.sp}sp",
                                selected = userSettings.editorTextSize == size,
                                onClick = { viewModel.setEditorTextSize(size) },
                                modifier = Modifier.testTag("editor_text_size_${size.name.lowercase(Locale.ROOT)}")
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = stringResource(com.example.R.string.viewer_text_size),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                    Text(
                        text = stringResource(com.example.R.string.viewer_text_size_description),
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ViewerTextSize.entries.forEach { size ->
                            TextSizeChip(
                                label = "${size.px}px",
                                selected = userSettings.viewerTextSize == size,
                                onClick = { viewModel.setViewerTextSize(size) },
                                modifier = Modifier.testTag("viewer_text_size_${size.name.lowercase(Locale.ROOT)}")
                            )
                        }
                    }
                }
            }
            }

            // 2. Vault Preferences Section
            item {
                SettingsSectionHeader(title = stringResource(com.example.R.string.vault_preferences), icon = Icons.Default.PushPin)

            OutlinedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    // Show pinned first
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(com.example.R.string.show_pinned_first),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = stringResource(com.example.R.string.pinned_first_description),
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                        Switch(
                            checked = userSettings.showPinnedFirst,
                            onCheckedChange = viewModel::setShowPinnedFirst,
                            modifier = Modifier.testTag("settings_switch_pinned_first")
                        )
                    }

                }
            }
            }

            // 3. File export location
            item {
                SettingsSectionHeader(title = stringResource(com.example.R.string.file_storage), icon = Icons.Default.Folder)

            OutlinedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(com.example.R.string.default_save_folder),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                    Text(
                        text = userSettings.defaultSaveFolderUri?.let { folderLabel(context, it) } ?: stringResource(com.example.R.string.save_folder_not_set),
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onRequestSaveFolder) {
                            Text(stringResource(com.example.R.string.choose_folder))
                        }
                        if (userSettings.defaultSaveFolderUri != null) {
                            TextButton(onClick = viewModel::clearDefaultSaveFolder) {
                                Text(stringResource(com.example.R.string.clear))
                            }
                        }
                    }
                }
            }
            }

            // 4. Backup & Restore Section
            item {
                SettingsSectionHeader(title = stringResource(com.example.R.string.backup_restore), icon = Icons.Default.Folder)

            OutlinedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(com.example.R.string.backup_restore_description),
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = onRequestBackup,
                            modifier = Modifier.testTag("settings_backup_button")
                        ) {
                            Text(stringResource(com.example.R.string.backup_vault))
                        }
                        TextButton(
                            onClick = onRequestRestore,
                            modifier = Modifier.testTag("settings_restore_button")
                        ) {
                            Text(stringResource(com.example.R.string.restore_vault))
                        }
                    }
                }
            }
            }

            // 5. Capture & Notifications Section
            item {
                SettingsSectionHeader(title = stringResource(com.example.R.string.capture_shortcuts), icon = Icons.Default.Notifications)

            OutlinedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(com.example.R.string.capture_notification),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = stringResource(com.example.R.string.capture_notification_description),
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                        Switch(
                            checked = userSettings.notificationEnabled,
                            onCheckedChange = { enabled -> viewModel.setNotificationEnabled(enabled, context) },
                            modifier = Modifier.testTag("settings_switch_notification")
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Widgets,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(com.example.R.string.quick_settings_tile),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = stringResource(com.example.R.string.quick_settings_description),
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                    }
                }
            }
            }

            // 6. Exported Documents Section
            item {
                if (exportedFiles.isNotEmpty()) {
                SettingsSectionHeader(title = stringResource(com.example.R.string.exported_documents), icon = Icons.Default.Folder)

                OutlinedCard(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        exportedFiles.forEachIndexed { index, file ->
                            if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = file.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
                                    Text(
                                        text = stringResource(
                                            com.example.R.string.file_details,
                                            dateStr,
                                            file.length()
                                        ),
                                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    )
                                }
                                IconButton(onClick = {
                                    shareFile(context, file)
                                }) {
                                    Icon(Icons.Default.Share, contentDescription = stringResource(com.example.R.string.share_file), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
            }

            // 7. Privacy & Security Notice
            item {
                SettingsSectionHeader(title = stringResource(com.example.R.string.privacy_security), icon = Icons.Default.Security)

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(com.example.R.string.private_by_design),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(com.example.R.string.private_notice),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 18.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
            }
}

private fun folderLabel(context: Context, uri: String): String {
    val segment = android.net.Uri.parse(uri).lastPathSegment.orEmpty()
    return context.getString(com.example.R.string.selected_folder, segment.substringAfterLast(':').ifBlank { context.getString(com.example.R.string.selected_folder_fallback) })
}

private fun shareFile(context: Context, file: File) {
    try {
        val content = file.readText()
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, content)
            type = if (file.name.endsWith(".md")) "text/markdown" else "text/plain"
        }
        val shareIntent = Intent.createChooser(
            sendIntent,
            context.getString(com.example.R.string.share_named_file, file.name)
        )
        context.startActivity(shareIntent)
    } catch (_: Exception) {
        Toast.makeText(context, context.getString(com.example.R.string.could_not_open_file), Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun SettingsSectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        )
    }
}

@Composable
private fun themePresetLabel(preset: ThemePreset): String = when (preset) {
    ThemePreset.EMERALD -> stringResource(com.example.R.string.theme_emerald)
    ThemePreset.OCEAN -> stringResource(com.example.R.string.theme_ocean)
    ThemePreset.VIOLET -> stringResource(com.example.R.string.theme_violet)
    ThemePreset.SUNSET -> stringResource(com.example.R.string.theme_sunset)
    ThemePreset.GRAPHITE -> stringResource(com.example.R.string.theme_graphite)
    ThemePreset.NORD -> stringResource(com.example.R.string.theme_nord)
    ThemePreset.SOLARIZED -> stringResource(com.example.R.string.theme_solarized)
    ThemePreset.SOFT_PAPER_CREAM -> stringResource(com.example.R.string.theme_soft_paper_cream)
    ThemePreset.MIDNIGHT_OLED -> stringResource(com.example.R.string.theme_midnight_oled)
    ThemePreset.SAGE_SLATE -> stringResource(com.example.R.string.theme_sage_slate)
}

@Composable
private fun TextSizeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
        } else null,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = modifier
    )
}

@Composable
private fun ThemeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
        } else null,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = modifier
    )
}
