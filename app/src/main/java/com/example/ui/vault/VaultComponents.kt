package com.example.ui.vault

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ExportFormat
import com.example.data.model.ClipboardCard
import com.example.data.model.ClipboardCardProjection
import com.example.data.model.ContentType
import com.example.domain.RelativeTimeFormatter
import com.example.ui.theme.PinnedGreenDark
import com.example.ui.theme.PinnedGreenLight
import com.example.ui.theme.SensitiveAmberDark
import com.example.ui.theme.SensitiveAmberLight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClipboardCardItem(
    card: ClipboardCardProjection,
    isSelected: Boolean,
    isSensitiveRevealed: Boolean,
    isMaskingEnabled: Boolean,
    isDragging: Boolean,
    onToggleSelect: () -> Unit,
    onLongPress: () -> Unit,
    onCopy: () -> Unit,
    onToggleRevealSensitive: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (PointerInputChange, Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
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
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f) else Color.White
            }
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else if (isDark) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            else Color(0xFFE6EAE5)
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
                .padding(start = 6.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox on the left
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                ),
                modifier = Modifier
                    .testTag("card_checkbox_${card.id}")
                    .minimumInteractiveComponentSize()
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Main Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp)
            ) {
                if (isMasked) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 4.dp)
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
                            fontWeight = FontWeight.Medium,
                            lineHeight = 17.sp,
                            fontSize = 13.5.sp
                        ),
                        maxLines = 2,
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

                Spacer(modifier = Modifier.height(2.dp))

                // Timestamp on left, Pinned on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val relativeTime = RelativeTimeFormatter.format(card.createdAtMillis)
                    Text(
                        text = relativeTime,
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Normal,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.testTag("card_timestamp_${card.id}")
                    )

                    if (card.pinned) {
                        Text(
                            text = "Pinned",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = pinnedColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            modifier = Modifier.testTag("card_pinned_badge_${card.id}")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Direct Copy Button on Right
            IconButton(
                onClick = onCopy,
                modifier = Modifier
                    .testTag("copy_button_${card.id}")
                    .minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy item",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }

            DragDots(
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isDragging) 1f else 0.85f),
                modifier = Modifier
                    .size(48.dp)
                    .testTag("drag_handle_${card.id}")
                    .pointerInput(card.id) {
                        detectDragGestures(
                            onDragStart = { onDragStart() },
                            onDragCancel = { onDragCancel() },
                            onDragEnd = { onDragEnd() },
                            onDrag = onDrag
                        )
                    }
            )
        }
    }
}

@Composable
private fun DragDots(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(2) {
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .background(tint, CircleShape)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultTopBar(
    isSearchOpen: Boolean,
    searchQuery: String,
    onMenuClick: () -> Unit,
    onSearchOpen: () -> Unit,
    onSearchClose: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSaveFile: () -> Unit,
    onOpenEditor: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var overflowExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        navigationIcon = {
            if (!isSearchOpen) {
                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier.testTag("vault_navigation_drawer_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Navigation drawer",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
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
                    text = "Clipboard Manager",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 19.sp
                    ),
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

                    DropdownMenu(
                        expanded = overflowExpanded,
                        onDismissRequest = { overflowExpanded = false },
                        modifier = Modifier.testTag("vault_overflow_menu")
                    ) {
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

@Composable
fun VaultSelectionBar(
    selectedCount: Int,
    allSelected: Boolean,
    allSelectedPinned: Boolean,
    showPinnedFirst: Boolean,
    onToggleSelectAll: () -> Unit,
    onShareSelected: () -> Unit,
    onCopySelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onPinSelected: () -> Unit,
    onToggleShowPinnedFirst: () -> Unit,
    onSaveFile: () -> Unit,
    onOpenEditor: () -> Unit,
    modifier: Modifier = Modifier
) {
    var overflowExpanded by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Checkbox and Selected Count text
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = allSelected && selectedCount > 0,
                    onCheckedChange = { onToggleSelectAll() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("vault_select_all_checkbox")
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (selectedCount > 0) "Đã chọn $selectedCount" else "Chọn tất cả",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.testTag("vault_selected_count_text")
                )
            }

            // Action Icons on Right: Pin, Copy, Delete, More (⋮)
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPinSelected,
                    enabled = selectedCount > 0,
                    modifier = Modifier.testTag("vault_action_pin_direct")
                ) {
                    Icon(
                        imageVector = if (allSelectedPinned) Icons.Outlined.PushPin else Icons.Default.PushPin,
                        contentDescription = if (allSelectedPinned) "Unpin selected" else "Pin selected",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onCopySelected,
                    enabled = selectedCount > 0,
                    modifier = Modifier.testTag("vault_action_copy")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy selected",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onDeleteSelected,
                    enabled = selectedCount > 0,
                    modifier = Modifier.testTag("vault_action_delete")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete selected",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box {
                    IconButton(
                        onClick = { overflowExpanded = true },
                        modifier = Modifier.testTag("vault_selection_more_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More selection actions",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = overflowExpanded,
                        onDismissRequest = { overflowExpanded = false },
                        modifier = Modifier.testTag("vault_selection_overflow_menu")
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (allSelected) "Clear selection" else "Select all") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                overflowExpanded = false
                                onToggleSelectAll()
                            },
                            modifier = Modifier.testTag("vault_menu_select_all")
                        )

                        DropdownMenuItem(
                            text = { Text("Share") },
                            leadingIcon = {
                                Icon(Icons.Default.Share, contentDescription = null)
                            },
                            onClick = {
                                overflowExpanded = false
                                onShareSelected()
                            },
                            modifier = Modifier.testTag("vault_menu_share_selection")
                        )

                        DropdownMenuItem(
                            text = { Text("Save file") },
                            onClick = {
                                overflowExpanded = false
                                onSaveFile()
                            },
                            modifier = Modifier.testTag("vault_menu_save_file_selection")
                        )

                        DropdownMenuItem(
                            text = { Text("Open editor") },
                            onClick = {
                                overflowExpanded = false
                                onOpenEditor()
                            },
                            modifier = Modifier.testTag("vault_menu_open_editor_selection")
                        )

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
                    }
                }
            }
        }
    }
}

@Composable
fun ShareCaptureDialog(
    sharedText: String,
    onDismiss: () -> Unit,
    onConfirm: (saveShared: Boolean, saveClipboard: Boolean) -> Unit
) {
    val context = LocalContext.current
    val clipManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager }
    val clipboardText = remember {
        try {
            clipManager.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    val hasShared = sharedText.isNotBlank()
    val hasClipboard = clipboardText.isNotBlank() && clipboardText != sharedText

    var isSharedSelected by remember { mutableStateOf(hasShared) }
    var isClipboardSelected by remember { mutableStateOf(hasClipboard) }

    val isAnySelected = (isSharedSelected && hasShared) || (isClipboardSelected && hasClipboard)
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = if (isDark) MaterialTheme.colorScheme.surface else Color.White,
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Lưu vào Clipboard",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    modifier = Modifier.testTag("share_dialog_title")
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Section 1: Clipboard hiện tại trên thiết bị
                if (hasClipboard) {
                    val isSelected = isClipboardSelected
                    val containerBg = if (isSelected) {
                        if (isDark) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        else Color(0xFFE8F5E9)
                    } else {
                        if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        else Color(0xFFF4F6F4)
                    }
                    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = containerBg),
                        border = BorderStroke(1.5.dp, borderColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isClipboardSelected = !isClipboardSelected }
                            .testTag("share_option_clipboard")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { isClipboardSelected = it },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "❝",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.padding(horizontal = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Clipboard trên thiết bị",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                )
                                Text(
                                    text = clipboardText,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = 13.5.sp
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Section 2: Nội dung chia sẻ / URL
                if (hasShared) {
                    val isSelected = isSharedSelected
                    val containerBg = if (isSelected) {
                        if (isDark) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        else Color(0xFFE8F5E9)
                    } else {
                        if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        else Color(0xFFF4F6F4)
                    }
                    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = containerBg),
                        border = BorderStroke(1.5.dp, borderColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isSharedSelected = !isSharedSelected }
                            .testTag("share_option_shared")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { isSharedSelected = it },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = if (sharedText.startsWith("http")) Icons.Default.Link else Icons.Default.Description,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Nội dung chia sẻ",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                )
                                Text(
                                    text = sharedText,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = 13.5.sp
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Smart Tip Banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = Color(0xFFEAB308),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Chạm để chọn 1 hoặc cả hai nội dung muốn lưu vào kho.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("share_dialog_cancel_button")
                ) {
                    Text("Hủy", fontSize = 14.sp)
                }

                Button(
                    onClick = { onConfirm(isSharedSelected, isClipboardSelected) },
                    enabled = isAnySelected,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("share_dialog_save_button")
                ) {
                    Text("Lưu", fontSize = 14.sp)
                }
            }
        },
        modifier = Modifier.testTag("share_capture_dialog")
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
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier.testTag("in_app_capture_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Lưu nội dung vào Clipboard",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text("Nội dung") },
                placeholder = { Text("Nhập hoặc dán nội dung bạn muốn lưu...") },
                minLines = 4,
                maxLines = 8,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("capture_content_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = isSensitive,
                    onCheckedChange = { isSensitive = it },
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("capture_sensitive_checkbox")
                )
                Text(
                    text = "Đánh dấu là nội dung nhạy cảm (ẩn xem trước)",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("capture_paste_button")
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Dán nhanh")
                }

                Button(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            onSave(inputText, isSensitive)
                        }
                    },
                    enabled = inputText.isNotBlank(),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .weight(1.2f)
                        .testTag("capture_save_button")
                ) {
                    Text("Lưu vào kho")
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
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
            Text("Lưu file", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("Tên file") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_filename_input")
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("Định dạng:", style = MaterialTheme.typography.labelLarge)

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
                Text("Lưu")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("export_cancel_button")
            ) {
                Text("Hủy")
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
            Text("Xóa $count mục đã chọn?")
        },
        text = {
            Text("Bạn có chắc chắn muốn xóa $count mục này khỏi kho clipboard không? Hành động này không thể hoàn tác.")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.testTag("delete_confirm_button")
            ) {
                Text("Xóa")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("delete_cancel_button")
            ) {
                Text("Hủy")
            }
        },
        modifier = Modifier.testTag("delete_confirm_dialog")
    )
}

@Composable
fun ClipboardPreviewPopup(
    card: ClipboardCard,
    anchorY: Float,
    isMaskingEnabled: Boolean,
    isSensitiveRevealed: Boolean,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onToggleRevealSensitive: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var visible by remember { mutableStateOf(false) }
    var dismissing by remember { mutableStateOf(false) }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val previewHeight = 336.dp
    val popupHeightPx = with(density) { (previewHeight + 116.dp).toPx() }
    val marginPx = with(density) { 12.dp.toPx() }
    val spaceBelow = screenHeightPx - anchorY
    val canOpenBelow = spaceBelow >= popupHeightPx + marginPx
    val canOpenAbove = anchorY >= popupHeightPx + marginPx
    val opensBelow = if (anchorY <= 0f) {
        true
    } else if (canOpenBelow) {
        true
    } else if (canOpenAbove) {
        false
    } else {
        spaceBelow >= anchorY
    }
    val rawVerticalOffset = if (opensBelow) {
        anchorY + with(density) { 20.dp.toPx() }
    } else {
        anchorY - popupHeightPx - with(density) { 20.dp.toPx() }
    }
    val verticalOffset = rawVerticalOffset
        .coerceIn(marginPx, (screenHeightPx - popupHeightPx - marginPx).coerceAtLeast(marginPx))
        .toInt()
    val popupWidth = minOf(360.dp, (configuration.screenWidthDp - 24).dp)
    val isMasked = card.isSensitive && isMaskingEnabled && !isSensitiveRevealed

    fun dismissAnimated() {
        if (dismissing) return
        dismissing = true
        visible = false
        scope.launch {
            delay(150)
            onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        visible = true
    }

    Popup(
        alignment = androidx.compose.ui.Alignment.TopCenter,
        offset = androidx.compose.ui.unit.IntOffset(0, verticalOffset),
        onDismissRequest = ::dismissAnimated,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(160)) + scaleIn(initialScale = 0.96f, animationSpec = tween(160)),
            exit = fadeOut(animationSpec = tween(140)) + scaleOut(targetScale = 0.96f, animationSpec = tween(140))
        ) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .width(popupWidth)
                    .testTag("clipboard_preview_popup")
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Clipboard preview",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = card.sourceApp ?: card.contentType.name,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                        IconButton(onClick = ::dismissAnimated, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Clear, contentDescription = "Close preview")
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(previewHeight)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        if (isMasked) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "••••••••••••••••",
                                    style = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFFB7791F))
                                )
                                TextButton(onClick = onToggleRevealSensitive) {
                                    Text("Reveal sensitive content")
                                }
                            }
                        } else {
                            Text(
                                text = card.content,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 19.sp
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = RelativeTimeFormatter.format(card.createdAtMillis),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        if (card.pinned) {
                            Text(
                                text = "Pinned",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (MaterialTheme.colorScheme.background.red < 0.5f) PinnedGreenDark else PinnedGreenLight,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                        IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy content")
                        }
                    }
                }
            }
        }
    }
}
