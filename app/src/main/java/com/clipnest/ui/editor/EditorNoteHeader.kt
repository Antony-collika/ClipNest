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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 1.5.dp)
            .testTag("note_breadcrumb_bar"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (mode == EditorMode.NOTE) {
                IconButton(
                    onClick = onExit,
                    modifier = Modifier.testTag("note_breadcrumb_back")
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = stringResource(com.clipnest.R.string.back),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text = when {
                    mode != EditorMode.NOTE -> stringResource(com.clipnest.R.string.editor_mode_label)
                    origin != null -> stringResource(com.clipnest.R.string.note_breadcrumb_with_origin, origin.label)
                    else -> stringResource(com.clipnest.R.string.note_breadcrumb_default)
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        TextButton(
            onClick = if (mode == EditorMode.NOTE) onSave else onSaveToNote,
            modifier = Modifier.testTag("note_breadcrumb_action")
        ) {
            Icon(
                if (mode == EditorMode.NOTE) Icons.Default.Save else Icons.Default.Save,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 4.dp)
            )
            Text(
                text = if (mode == EditorMode.NOTE)
                    stringResource(com.clipnest.R.string.save_file)
                else
                    stringResource(com.clipnest.R.string.save_to_note),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
