package com.clipnest.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import com.clipnest.ui.theme.LocalThemePalette
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Breadcrumb row above the toolbar. Its actions are mode-specific:
 *
 * - [EditorMode.PLAIN]: "Save to Note" creates a Note from the current free
 *   Editor content and stays on the Editor screen.
 * - [EditorMode.NOTE]: the back arrow leaves Note mode; the right-hand "Save"
 *   button saves the current Note immediately and stays in Note mode.
 */
@Composable
fun EditorNoteBreadcrumbBar(
    mode: EditorMode,
    origin: EditorNoteOrigin?,
    onExit: () -> Unit,
    onSave: () -> Unit,
    onSaveToNote: () -> Unit,
    modifier: Modifier = Modifier
) {
    val noteColors = LocalThemePalette.current.noteColors()
    Row(