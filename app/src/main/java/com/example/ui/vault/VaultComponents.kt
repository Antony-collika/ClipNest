package com.example.ui.vault

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ExportFormat
import com.example.data.model.ClipboardCardProjection
import com.example.data.model.ContentType
import com.example.ui.theme.PinnedGreenDark
import com.example.ui.theme.PinnedGreenLight
import com.example.ui.theme.SensitiveAmberDark
import com.example.ui.theme.SensitiveAmberLight
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timestampFormatter = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClipboardCardItem(
    card: ClipboardCardProjection,
    isSelected: Boolean,
    isSensitiveRevealed: Boolean,
    isMaskingEnabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggleSelect: () -> Unit,
    onLongPress: () -> Unit,
    onCopy: () -> Unit,
    onToggleRevealSensitive: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSensitive = card.isSensitive
    val isMasked = isSensitive && isMaskingEnabled && !isSensitiveRevealed

    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    val pinnedColor = if (isDark) PinnedGreenDark else PinnedGreenLight
    val sensitiveColor = if (isDark) SensitiveAmberDark else SensitiveAmberLight

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 0.5.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_item_${card.id}")
            .combinedClickable(
                onClick = onToggleSelect,
                onLongClick = onLongPress
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Checkbox on the left
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .testTag("card_checkbox_${card.id}")
                    .minimumInteractiveComponentSize()
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Main Card Content Column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 8.dp, bottom = 4.dp)
            ) {
                // Content Preview
                if (isMasked) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Sensitive",
                            tint = sensitiveColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "•••••••••••••••• (Sensitive)",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                color = sensitiveColor
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = onToggleRevealSensitive,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = "Reveal sensitive content",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else {
                    Text(
                        text = card.preview,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 20.sp
                        ),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("card_preview_${card.id}")
                    )
                    if (isSensitive) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Sensitive",
                                tint = sensitiveColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Sensitive",
                                style = MaterialTheme.typography.labelSmall.copy(color = sensitiveColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = onToggleRevealSensitive,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VisibilityOff,
                                    contentDescription = "Mask sensitive content",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Metadata Row: Timestamp on the left (Neutral text color), Pinned on the right (Green accent)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Timestamp on left
                    val dateStr = timestampFormatter.format(Date(card.createdAtMillis))
                    val sourceStr = if (!card.sourceApp.isNullOrBlank()) " • ${card.sourceApp}" else ""
                    val typeStr = when (card.contentType) {
                        ContentType.URL -> " • URL"
                        ContentType.COMBINED -> " • Combined"
                        ContentType.TEXT -> ""
                    }
                    Text(
                        text = "$dateStr$sourceStr$typeStr",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Normal
                        ),
                        modifier = Modifier.testTag("card_timestamp_${card.id}")
                    )

                    // Pinned on right
                    if (card.pinned) {
                        Text(
                            text = "Pinned",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = pinnedColor,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .testTag("card_pinned_badge_${card.id}")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Action buttons on right: Copy and Drag handle / Reorder
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier
                        .testTag("copy_button_${card.id}")
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy item",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                var showReorderMenu by remember { mutableStateOf(false) }

                Box {
                    IconButton(
                        onClick = { showReorderMenu = true },
                        modifier = Modifier
                            .testTag("drag_handle_${card.id}")
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = "Reorder handle",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showReorderMenu,
                        onDismissRequest = { showReorderMenu = false }
                    ) {
                        if (canMoveUp) {
                            DropdownMenuItem(
                                text = { Text("Move up") },
                                leadingIcon = {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = null)
                                },
                                onClick = {
                                    showReorderMenu = false
                                    onMoveUp()
                                }
                            )
                        }
                        if (canMoveDown) {
                            DropdownMenuItem(
                                text = { Text("Move down") },
                                leadingIcon = {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = null)
                                },
                                onClick = {
                                    showReorderMenu = false
                                    onMoveDown()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultTopBar(
    hasSelection: Boolean,
    selectedCount: Int,
    allSelectedPinned: Boolean,
    isSearchOpen: Boolean,
    searchQuery: String,
    showPinnedFirst: Boolean,
    onSearchOpen: () -> Unit,
    onSearchClose: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onPinSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onCopySelected: () -> Unit,
    onToggleShowPinnedFirst: () -> Unit,
    onSelectAll: () -> Unit,
    onSaveFile: () -> Unit,
    onOpenEditor: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var overflowExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            if (isSearchOpen) {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Search vault...") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vault_search_input")
                )
            } else {
                Text(
                    text = if (hasSelection) "Selected $selectedCount" else "Clipboard Vault",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.testTag("vault_title")
                )
            }
        },
        actions = {
            if (isSearchOpen) {
                IconButton(
                    onClick = onSearchClose,
                    modifier = Modifier.testTag("vault_close_search_button")
                ) {
                    Icon(Icons.Default.Clear, contentDescription = "Close search")
                }
            } else {
                // 4 Direct actions in exact order: Pin, Delete, Copy, Search, Overflow (⋮)
                // Pin Action
                IconButton(
                    onClick = onPinSelected,
                    enabled = hasSelection,
                    modifier = Modifier.testTag("vault_action_pin")
                ) {
                    Icon(
                        imageVector = if (hasSelection && allSelectedPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (hasSelection && allSelectedPinned) "Unpin" else "Pin",
                        tint = if (hasSelection) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }

                // Delete Action
                IconButton(
                    onClick = onDeleteSelected,
                    enabled = hasSelection,
                    modifier = Modifier.testTag("vault_action_delete")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = if (hasSelection) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }

                // Copy Action
                IconButton(
                    onClick = onCopySelected,
                    enabled = hasSelection,
                    modifier = Modifier.testTag("vault_action_copy")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = if (hasSelection) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }

                // Search Action
                IconButton(
                    onClick = onSearchOpen,
                    modifier = Modifier.testTag("vault_action_search")
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Overflow Action (⋮)
                Box {
                    IconButton(
                        onClick = { overflowExpanded = true },
                        modifier = Modifier.testTag("vault_action_overflow")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Overflow menu items in exact order:
                    // 1. Show pinned first [checkable]
                    // 2. Select all
                    // 3. Save file
                    // 4. Open editor
                    // 5. Settings
                    DropdownMenu(
                        expanded = overflowExpanded,
                        onDismissRequest = { overflowExpanded = false },
                        modifier = Modifier.testTag("vault_overflow_menu")
                    ) {
                        DropdownMenuItem(
                            text = { Text("Show pinned first") },
                            trailingIcon = {
                                if (showPinnedFirst) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Active",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            onClick = {
                                overflowExpanded = false
                                onToggleShowPinnedFirst()
                            },
                            modifier = Modifier.testTag("vault_menu_show_pinned_first")
                        )

                        DropdownMenuItem(
                            text = { Text("Select all") },
                            onClick = {
                                overflowExpanded = false
                                onSelectAll()
                            },
                            modifier = Modifier.testTag("vault_menu_select_all")
                        )

                        DropdownMenuItem(
                            text = { Text("Save file") },
                            onClick = {
                                overflowExpanded = false
                                onSaveFile()
                            },
                            modifier = Modifier.testTag("vault_menu_save_file")
                        )

                        DropdownMenuItem(
                            text = { Text("Open editor") },
                            onClick = {
                                overflowExpanded = false
                                onOpenEditor()
                            },
                            modifier = Modifier.testTag("vault_menu_open_editor")
                        )

                        DropdownMenuItem(
                            text = { Text("Settings") },
                            onClick = {
                                overflowExpanded = false
                                onOpenSettings()
                            },
                            modifier = Modifier.testTag("vault_menu_settings")
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InAppCaptureSheet(
    onDismiss: () -> Unit,
    onSave: (text: String, isSensitive: Boolean) -> Unit
) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var isSensitive by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("in_app_capture_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Capture to Vault",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text("Content") },
                placeholder = { Text("Type or paste content here...") },
                minLines = 4,
                maxLines = 8,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("capture_content_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = isSensitive,
                    onCheckedChange = { isSensitive = it },
                    modifier = Modifier.testTag("capture_sensitive_checkbox")
                )
                Text(
                    text = "Mark as sensitive content",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        val clipManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = clipManager.primaryClip?.getItemAt(0)?.text?.toString()
                        if (!clip.isNullOrBlank()) {
                            inputText = clip
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("capture_paste_button")
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Paste")
                }

                Button(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            onSave(inputText, isSensitive)
                        }
                    },
                    enabled = inputText.isNotBlank(),
                    modifier = Modifier
                        .weight(1.2f)
                        .testTag("capture_save_button")
                ) {
                    Text("Save to vault")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun ShareCaptureDialog(
    sharedText: String,
    onDismiss: () -> Unit,
    onChoiceSelected: (ShareChoice) -> Unit
) {
    val context = LocalContext.current
    val clipManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager }
    val clipboardText = remember { clipManager.primaryClip?.getItemAt(0)?.text?.toString() ?: "" }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Save Shared Content", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Select content source to save to your private vault:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Shared Content",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        )
                        Text(
                            text = sharedText.ifBlank { "(Empty)" },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (clipboardText.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Current Device Clipboard",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                            )
                            Text(
                                text = clipboardText,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = { onChoiceSelected(ShareChoice.USE_SHARED) },
                    modifier = Modifier.fillMaxWidth().testTag("share_use_shared_button")
                ) {
                    Text("Use shared")
                }
                if (clipboardText.isNotBlank()) {
                    OutlinedButton(
                        onClick = { onChoiceSelected(ShareChoice.USE_CLIPBOARD) },
                        modifier = Modifier.fillMaxWidth().testTag("share_use_clipboard_button")
                    ) {
                        Text("Use clipboard")
                    }
                    Button(
                        onClick = { onChoiceSelected(ShareChoice.USE_BOTH) },
                        modifier = Modifier.fillMaxWidth().testTag("share_use_both_button")
                    ) {
                        Text("Use both")
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End).testTag("share_cancel_button")
                ) {
                    Text("Cancel")
                }
            }
        },
        modifier = Modifier.testTag("share_capture_dialog")
    )
}

@Composable
fun ExportDialog(
    onDismiss: () -> Unit,
    onConfirm: (fileName: String, format: ExportFormat) -> Unit
) {
    var fileName by remember { mutableStateOf("Clipboard_Export") }
    var selectedFormat by remember { mutableStateOf(ExportFormat.MARKDOWN) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Save file", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("Filename") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_filename_input")
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
                        modifier = Modifier.testTag("export_format_markdown")
                    )
                    Text("Markdown (.md)")

                    Spacer(modifier = Modifier.width(16.dp))

                    RadioButton(
                        selected = selectedFormat == ExportFormat.PLAIN_TEXT,
                        onClick = { selectedFormat = ExportFormat.PLAIN_TEXT },
                        modifier = Modifier.testTag("export_format_plain_text")
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
                modifier = Modifier.testTag("export_save_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("export_cancel_button")
            ) {
                Text("Cancel")
            }
        },
        modifier = Modifier.testTag("export_dialog")
    )
}

@Composable
fun DeleteConfirmDialog(
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Delete $count items?")
        },
        text = {
            Text("Are you sure you want to delete these $count selected items from your vault? This action cannot be undone.")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.testTag("delete_confirm_button")
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("delete_cancel_button")
            ) {
                Text("Cancel")
            }
        },
        modifier = Modifier.testTag("delete_confirm_dialog")
    )
}
