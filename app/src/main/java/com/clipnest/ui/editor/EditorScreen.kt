package com.clipnest.ui.editor

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clipnest.data.local.EditorTextSize
import com.clipnest.data.local.ViewerTextSize

private const val NOTE_TAG_QUERY_MAX_LENGTH = 40

@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    editorTextSize: EditorTextSize = EditorTextSize.DEFAULT,
    viewerTextSize: ViewerTextSize = ViewerTextSize.DEFAULT,
    onRequestSaveFolder: () -> Unit,
    onRequestOpenFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val openWithDiagnostic by viewModel.openWithDiagnostic.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var title by rememberSaveable { mutableStateOf("") }
    var tagQuery by remember { mutableStateOf<String?>(null) }
    var tagStart by remember { mutableStateOf(-1) }
    var tagEnd by remember { mutableStateOf(-1) }
    var editorRef by remember { mutableStateOf<HighlightingEditText?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) viewModel.onPauseOrExit()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onPauseOrExit()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is EditorEvent.ShowToast -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                EditorEvent.RequestSaveFolder -> onRequestSaveFolder()
            }
        }
    }

    val suggestions = remember(uiState.content.text, tagQuery) {
        val query = tagQuery ?: return@remember emptyList<String>()
        Regex("(?<!\\S)#([\\p{L}\\p{N}_-]+)")
            .findAll(uiState.content.text)
            .map { it.groupValues[1] }
            .filter { it.startsWith(query, ignoreCase = true) }
            .distinctBy { it.lowercase() }
            .take(5)
            .toList()
    }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            Column(Modifier.fillMaxSize()) {
                NoteTitleField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 18.dp, vertical = 10.dp),
                    textColor = MaterialTheme.colorScheme.onSurface,
                    hintColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    textSize = (editorTextSize.sp + 4).coerceAtMost(32)
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
                )
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    AndroidView(
                        factory = { ctx ->
                            HighlightingEditText(ctx).apply {
                                setTextColor(MaterialTheme.colorScheme.onSurface.toArgb())
                                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, editorTextSize.sp.toFloat())
                                setLineSpacing(0f, editorTextSize.lineHeightSp.toFloat() / editorTextSize.sp.toFloat())
                                tagColor = MaterialTheme.colorScheme.primary.toArgb()
                                hint = ctx.getString(com.clipnest.R.string.note_content_placeholder)
                                setHintTextColor(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f).toArgb())
                                setPadding(dp(18), dp(14), dp(18), dp(18))
                                setFullText(uiState.content.text, uiState.content.selection.start, uiState.content.selection.end)
                                viewModel.setEditorInstance(this)
                                editorRef = this
                            }
                        },
                        update = { editor ->
                            editorRef = editor
                            editor.setTextColor(MaterialTheme.colorScheme.onSurface.toArgb())
                            editor.setHintTextColor(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f).toArgb())
                            editor.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, editorTextSize.sp.toFloat())
                            editor.setLineSpacing(0f, editorTextSize.lineHeightSp.toFloat() / editorTextSize.sp.toFloat())
                            editor.tagColor = MaterialTheme.colorScheme.primary.toArgb()
                            viewModel.setEditorInstance(editor)
                            if (!editor.isContentInitialized()) {
                                editor.setFullText(uiState.content.text, uiState.content.selection.start, uiState.content.selection.end)
                            } else {
                                editor.setEditorSelectionIfNeeded(uiState.content.selection.start, uiState.content.selection.end)
                            }
                            editor.onTextChange = { change ->
                                viewModel.onTextChange(change)
                                val token = findActiveTag(editor.getFullText(), editor.selectionEnd.coerceIn(0, editor.length()))
                                if (token != null && token.second.length <= NOTE_TAG_QUERY_MAX_LENGTH) {
                                    tagStart = token.first
                                    tagEnd = token.first + token.second.length + 1
                                    tagQuery = token.second
                                } else tagQuery = null
                            }
                            editor.onSelectionChange = { _, end ->
                                val token = findActiveTag(editor.getFullText(), end.coerceIn(0, editor.length()))
                                if (token != null && token.second.length <= NOTE_TAG_QUERY_MAX_LENGTH) {
                                    tagStart = token.first
                                    tagEnd = token.first + token.second.length + 1
                                    tagQuery = token.second
                                } else tagQuery = null
                            }
                        },
                        modifier = Modifier.fillMaxSize().testTag("note_content_editor")
                    )
                    DropdownMenu(
                        expanded = tagQuery != null,
                        onDismissRequest = { tagQuery = null },
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Text(
                            text = stringResource(com.clipnest.R.string.note_label_suggestions),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        suggestions.forEach { label ->
                            DropdownMenuItem(
                                text = { Text("#$label") },
                                onClick = {
                                    replaceActiveTag(editorRef, tagStart, tagEnd, label, viewModel)
                                    tagQuery = null
                                }
                            )
                        }
                        val query = tagQuery.orEmpty()
                        if (query.isNotBlank() && suggestions.none { it.equals(query, ignoreCase = true) }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(com.clipnest.R.string.note_label_create, query)) },
                                onClick = { tagQuery = null }
                            )
                        }
                    }
                }
            }
        }
        NoteEditorToolbar(
            onPaste = { viewModel.pasteFromClipboard(context) },
            onCopy = { viewModel.copySelectedText(context) },
            onSelectAll = viewModel::selectAll,
            onDelete = viewModel::deleteSelectedText,
            onUndo = viewModel::undo,
            onRedo = viewModel::redo
        )
    }

    openWithDiagnostic?.let {
        AlertDialog(
            onDismissRequest = viewModel::dismissOpenWithDiagnostic,
            title = { Text(stringResource(com.clipnest.R.string.open_with_fallback_title)) },
            text = { Text(stringResource(com.clipnest.R.string.open_with_fallback_message)) },
            confirmButton = { TextButton(onClick = viewModel::dismissOpenWithDiagnostic) { Text(stringResource(com.clipnest.R.string.close)) } },
            dismissButton = { TextButton(onClick = { viewModel.dismissOpenWithDiagnostic(); onRequestOpenFile() }) { Text(stringResource(com.clipnest.R.string.open_with_fallback_open_file)) } }
        )
    }
    if (uiState.showSaveNewFileDialog) {
        SaveNewFileDialog(
            defaultFolderUri = uiState.defaultSaveFolderUri,
            initialFileName = uiState.documentName,
            onChooseFolder = onRequestSaveFolder,
            onDismiss = viewModel::dismissSaveNewFileDialog,
            onConfirm = { fileName, format -> viewModel.confirmSaveToNewFile(fileName, format, context.contentResolver) }
        )
    }
}

@Composable
private fun NoteTitleField(value: String, onValueChange: (String) -> Unit, modifier: Modifier, textColor: Color, hintColor: Color, textSize: Int) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        textStyle = TextStyle(color = textColor, fontSize = textSize.sp, fontWeight = FontWeight.SemiBold),
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(stringResource(com.clipnest.R.string.note_title_placeholder), color = hintColor, style = MaterialTheme.typography.titleLarge)
                inner()
            }
        }
    )
}

@Composable
private fun NoteEditorToolbar(onPaste: () -> Unit, onCopy: () -> Unit, onSelectAll: () -> Unit, onDelete: () -> Unit, onUndo: () -> Unit, onRedo: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            NoteToolButton("note_action_paste", Icons.Default.ContentPaste, onPaste)
            NoteToolButton("note_action_copy", Icons.Default.ContentCopy, onCopy)
            NoteToolButton("note_action_select_all", Icons.Default.SelectAll, onSelectAll)
            NoteToolButton("note_action_delete", Icons.Default.Delete, onDelete)
            NoteToolButton("note_action_undo", Icons.AutoMirrored.Filled.Undo, onUndo)
            NoteToolButton("note_action_redo", Icons.AutoMirrored.Filled.Redo, onRedo)
        }
    }
}

@Composable
private fun NoteToolButton(tag: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clickable(onClick = onClick).testTag(tag), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}

private fun findActiveTag(text: String, caret: Int): Pair<Int, String>? {
    if (caret <= 0 || caret > text.length) return null
    var start = caret - 1
    while (start >= 0 && (text[start].isLetterOrDigit() || text[start] == '_' || text[start] == '-')) start--
    if (start < 0 || text[start] != '#') return null
    if (start > 0 && !text[start - 1].isWhitespace()) return null
    val query = text.substring(start + 1, caret)
    if (query.any { !(it.isLetterOrDigit() || it == '_' || it == '-') }) return null
    return start to query
}

private fun replaceActiveTag(editor: HighlightingEditText?, start: Int, end: Int, label: String, viewModel: EditorViewModel) {
    if (editor == null || start < 0 || end <= start || end > editor.length()) return
    val replacement = "#$label"
    val old = editor.getFullText().substring(start, end)
    editor.applyDirectEdit(
        operation = { editable -> editable.replace(start, end, replacement) },
        affectedStart = start,
        affectedEnd = start + replacement.length
    )
    editor.setEditorSelectionIfNeeded(start + replacement.length, start + replacement.length)
    viewModel.onTextChange(TextChange(start, old.length, replacement.length, removedText = old, addedText = replacement))
}

private fun dp(value: Int): Int = value
