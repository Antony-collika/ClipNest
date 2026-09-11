package com.clipnest.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipnest.data.local.EditorTextSize

/**
 * Breadcrumb row above the toolbar. Always visible (Title/Divider/Content
 * below are also always visible) — only the label and the right-hand
 * action change with [mode]:
 *
 * - [EditorMode.PLAIN]: left side reads "Editor mode" (no back icon), right
 *   side reads "Save to Note" with a Save icon.
 * - [EditorMode.NOTE]: left side shows a back arrow plus "Taking note" (or
 *   "<origin>/New Note" when [origin] is set), right side reads "Done" with
 *   a Check icon.
 *
 * Both right-side actions call the same [onExit] — autosave already
 * persists on every change, so neither one "saves" anything the other
 * doesn't; "Save to Note" and "Done" just communicate different intent to
 * the user depending on which mode they're in.
 */
@Composable
fun EditorNoteBreadcrumbBar(
    mode: EditorMode,
    origin: EditorNoteOrigin?,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
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
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(
            onClick = onExit,
            modifier = Modifier.testTag("note_breadcrumb_action")
        ) {
            Icon(
                if (mode == EditorMode.NOTE) Icons.Default.Check else Icons.Default.Save,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 4.dp)
            )
            Text(
                text = if (mode == EditorMode.NOTE)
                    stringResource(com.clipnest.R.string.done)
                else
                    stringResource(com.clipnest.R.string.save_to_note),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Title field above the shared content editor. Always visible, in both
 * [EditorMode.PLAIN] and [EditorMode.NOTE] — same placeholder behavior in
 * either mode, no mode branching here. Font size tracks [editorTextSize]
 * (base size + 2sp, bold) instead of a hardcoded style, so it stays
 * proportional when the user changes the app-wide editor text size. Height
 * is intentionally NOT fixed — it wraps to content — so larger text sizes
 * don't get clipped.
 */
@Composable
fun EditorNoteTitleField(
    value: String,
    onValueChange: (String) -> Unit,
    editorTextSize: EditorTextSize,
    textColor: Color,
    hintColor: Color,
    modifier: Modifier = Modifier
) {
    val titleFontSize = (editorTextSize.sp + 2).sp
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .testTag("note_title_input"),
        singleLine = true,
        textStyle = TextStyle(color = textColor, fontSize = titleFontSize, fontWeight = FontWeight.Bold),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = stringResource(com.clipnest.R.string.note_title_placeholder),
                        color = hintColor,
                        fontSize = titleFontSize,
                        fontWeight = FontWeight.Bold
                    )
                }
                inner()
            }
        }
    )
}

/**
 * Thin divider between the title field and the content editor. Extracted so
 * [EditorScreen] doesn't need to know the exact styling, and so it matches
 * whatever divider treatment the rest of the note chrome uses.
 */
@Composable
fun EditorNoteTitleDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = 18.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    )
}
