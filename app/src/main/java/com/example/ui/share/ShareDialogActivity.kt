package com.example.ui.share

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.lifecycleScope
import com.example.data.local.AppDatabase
import com.example.data.local.SettingsDataStore
import com.example.data.model.ContentType
import com.example.data.repository.CapturePayload
import com.example.data.repository.CaptureSource
import com.example.data.repository.ClipboardRepositoryImpl
import com.example.domain.TextNormalizer
import com.example.ui.localization.withAppLanguage
import com.example.ui.theme.ClipboardManagerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShareDialogActivity : ComponentActivity() {

    private var clipboardRead = false
    private var sharedText by mutableStateOf("")
    private var clipboardText by mutableStateOf("")
    private var clipboardReady by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)

        // The shared payload is safe to snapshot from the user-initiated Intent.
        // The system clipboard is intentionally not read here: on Android 10+
        // this activity may not yet own the focused window.
        sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent.clipData?.getItemAt(0)?.text?.toString()
            ?: ""

        lifecycleScope.launch {
            val settings = SettingsDataStore(applicationContext).userSettingsFlow.first()
            withContext(Dispatchers.Main) {
                setContent {
                    val localizedContext = LocalContext.current.withAppLanguage(settings.language)
                    CompositionLocalProvider(LocalContext provides localizedContext) {
                        ClipboardManagerTheme(
                            themeMode = settings.themeMode,
                            themePreset = settings.themePreset
                        ) {
                            if (clipboardReady) {
                                ShareDialogOverlay(
                                    sharedText = sharedText,
                                    clipboardText = clipboardText,
                                    onDismiss = { finishActivity() },
                                    onSave = { saveShared, saveClipboard ->
                                        saveSelections(saveShared, sharedText, saveClipboard, clipboardText)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !clipboardRead) {
            clipboardRead = true
            clipboardText = readCurrentClipboard()
            clipboardReady = true
        }
    }

    private fun readCurrentClipboard(): String {
        return try {
            val clipManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipManager.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(this)
                ?.toString()
                ?.trim()
                ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun saveSelections(
        saveShared: Boolean,
        sharedText: String,
        saveClipboard: Boolean,
        clipboardText: String
    ) {
        val shared = sharedText.takeIf { saveShared && it.isNotBlank() }
        val clipboard = clipboardText.takeIf { saveClipboard && it.isNotBlank() }
        val payloads = when {
            shared != null && clipboard != null && shared != clipboard -> {
                listOf(
                    CapturePayload(
                        content = TextNormalizer.combine(clipboard, shared),
                        sourceApp = CaptureSource.COMBINED,
                        contentType = ContentType.COMBINED
                    )
                )
            }
            shared != null -> listOf(
                CapturePayload(
                    content = shared,
                    sourceApp = CaptureSource.ANDROID_SHARE
                )
            )
            clipboard != null -> listOf(
                CapturePayload(
                    content = clipboard,
                    sourceApp = CaptureSource.SYSTEM_CLIPBOARD
                )
            )
            else -> emptyList()
        }

        if (payloads.isEmpty()) {
            finishActivity()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val repository = ClipboardRepositoryImpl(
                    AppDatabase.getInstance(applicationContext).clipboardDao()
                )
                val saved = repository.saveCards(payloads)
                withContext(Dispatchers.Main) {
                    val count = saved.size
                    Toast.makeText(
                        applicationContext,
                        if (count > 1) getString(com.example.R.string.saved_to_vault_count, count) else getString(com.example.R.string.saved_to_vault),
                        Toast.LENGTH_SHORT
                    ).show()
                    finishActivity()
                }
            } catch (error: Exception) {
                error.printStackTrace()
                withContext(Dispatchers.Main) { finishActivity() }
            }
        }
    }

    private fun finishActivity() {
        finish()
        overridePendingTransition(0, 0)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}

@Composable
fun ShareDialogOverlay(
    sharedText: String,
    clipboardText: String,
    onDismiss: () -> Unit,
    onSave: (saveShared: Boolean, saveClipboard: Boolean) -> Unit
) {
    val hasShared = sharedText.isNotBlank()
    val hasClipboard = clipboardText.isNotBlank() && clipboardText != sharedText

    var isSharedSelected by remember { mutableStateOf(hasShared) }
    var isClipboardSelected by remember { mutableStateOf(hasClipboard) }

    // If both exist, user can select both or either one
    val isAnySelected = (isSharedSelected && hasShared) || (isClipboardSelected && hasClipboard)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp)
                .testTag("share_dialog_surface")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon Badge
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
                    text = stringResource(com.example.R.string.save_to_vault),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    modifier = Modifier.testTag("share_dialog_title")
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Option 1: Clipboard hiện tại trên thiết bị
                if (hasClipboard) {
                    val isSelected = isClipboardSelected
                    val containerBg = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
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
                            Icon(
                                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                                contentDescription = if (isSelected) stringResource(com.example.R.string.selected) else stringResource(com.example.R.string.not_selected),
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(22.dp)
                            )

                            Spacer(modifier = Modifier.width(10.dp))

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
                                    text = stringResource(com.example.R.string.device_clipboard),
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

                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Option 2: Nội dung chia sẻ / URL
                if (hasShared) {
                    val isSelected = isSharedSelected
                    val containerBg = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
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
                            Icon(
                                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                                contentDescription = if (isSelected) stringResource(com.example.R.string.selected) else stringResource(com.example.R.string.not_selected),
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(22.dp)
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Icon(
                                imageVector = if (sharedText.startsWith("http")) Icons.Default.Link else Icons.Default.Description,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(
                                        if (sharedText.trim().startsWith("http://") || sharedText.trim().startsWith("https://")) {
                                            com.example.R.string.shared_url
                                        } else {
                                            com.example.R.string.shared_content
                                        }
                                    ),
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

                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Hint
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(com.example.R.string.share_select_hint),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Action Buttons: Cancel and Save only!
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                        Text(stringResource(com.example.R.string.cancel), fontSize = 14.sp)
                    }

                    Button(
                        onClick = { onSave(isSharedSelected, isClipboardSelected) },
                        enabled = isAnySelected,
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("share_dialog_save_button")
                    ) {
                        Text(stringResource(com.example.R.string.save), fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
