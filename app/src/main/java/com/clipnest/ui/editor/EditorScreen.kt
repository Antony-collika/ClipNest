package com.clipnest.ui.editor

import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.material3.minimumInteractiveComponentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clipnest.data.local.EditorTextSize
import com.clipnest.data.local.ViewerTextSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val PREVIEW_RENDER_DEBOUNCE_MS = 140L
private val PREVIEW_POPUP_HEIGHT = 620.dp
private val PREVIEW_POPUP_WIDTH_FRACTION = 0.92f
private val PREVIEW_HEADER_HEIGHT = 48.dp
private val PREVIEW_FOOTER_HEIGHT = 36.dp

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
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    val previewBackground = if (isDark) Color(0xFF2B2B2B) else Color(0xFFF6F6F6)
    val previewTextColor = if (isDark) Color(0xFFF4F4F4) else Color(0xFF171717)
    val previewMutedColor = if (isDark) Color(0xFFCACACA) else Color(0xFF5E5E5E)
    val previewColors = remember(isDark) {
        MarkdownPreviewColors.from(
            background = previewBackground,
            onSurface = previewTextColor,
            onSurfaceVariant = previewMutedColor,
            surfaceVariant = if (isDark) Color(0xFF3A3A3A) else Color(0xFFE8E8E8),
            outline = if (isDark) Color(0xFF777777) else Color(0xFF8A8A8A),
            outlineVariant = if (isDark) Color(0xFF555555) else Color(0xFFC7C7C7),
            primary = previewTextColor,
            codeBackground = if (isDark) Color(0xFF3A3A3A) else Color(0xFFE8E8E8),
            isDark = isDark
        )
    }
    var previewHtml by remember { mutableStateOf("") }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.onPauseOrExit()
            }
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

    LaunchedEffect(uiState.content.text, previewColors, viewerTextSize) {
        delay(PREVIEW_RENDER_DEBOUNCE_MS)
        previewHtml = withContext(Dispatchers.Default) {
            MarkdownPreviewRenderer.render(uiState.content.text, previewColors, viewerTextSize.px)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("editor_content_area")
        ) {
            EditorTextInput(
                value = uiState.content,
                onValueChange = viewModel::onContentChange,
                editorTextSize = editorTextSize,
                modifier = Modifier.fillMaxSize()
            )
        }

        EditorToolbox(
            isPreviewVisible = uiState.showMarkdownPreview,
            onPaste = { viewModel.pasteFromClipboard(context) },
            onCopy = { viewModel.copySelectedText(context) },
            onSelectAll = viewModel::selectAll,
            onDelete = viewModel::deleteSelectedText,
            onUndo = viewModel::undo,
            onRedo = viewModel::redo,
            onTogglePreview = viewModel::toggleMarkdownPreview,
            onMoveCursorLeft = viewModel::moveCursorLeft,
            onMoveCursorRight = viewModel::moveCursorRight,
            onHeading = viewModel::insertMarkdownHeading,
            onBold = viewModel::toggleMarkdownStrong,
            onItalic = viewModel::toggleMarkdownEmphasis,
            onQuote = viewModel::insertMarkdownQuote,
            onCode = viewModel::insertMarkdownCodeBlock,
            onBullets = viewModel::insertMarkdownBullets,
            onNumbers = viewModel::insertMarkdownNumbers,
            onHorizontalRule = viewModel::insertMarkdownHorizontalRule
        )
    }

    if (uiState.showMarkdownPreview) {
        EditorMarkdownPreviewPopup(
            html = previewHtml,
            backgroundColor = previewBackground,
            contentColor = previewTextColor,
            onDismissRequest = viewModel::toggleMarkdownPreview
        )
    }

    openWithDiagnostic?.let {
        AlertDialog(
            onDismissRequest = viewModel::dismissOpenWithDiagnostic,
            title = { Text(stringResource(com.clipnest.R.string.open_with_fallback_title)) },
            text = { Text(stringResource(com.clipnest.R.string.open_with_fallback_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissOpenWithDiagnostic) {
                    Text(stringResource(com.clipnest.R.string.close))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissOpenWithDiagnostic()
                        onRequestOpenFile()
                    }
                ) {
                    Text(stringResource(com.clipnest.R.string.open_with_fallback_open_file))
                }
            }
        )
    }

    if (uiState.showSaveNewFileDialog) {
        SaveNewFileDialog(
            defaultFolderUri = uiState.defaultSaveFolderUri,
            initialFileName = uiState.documentName,
            onChooseFolder = onRequestSaveFolder,
            onDismiss = viewModel::dismissSaveNewFileDialog,
            onConfirm = { fileName, format ->
                viewModel.confirmSaveToNewFile(fileName, format, context.contentResolver)
            }
        )
    }
}

@Composable
private fun EditorMarkdownPreviewPopup(
    html: String,
    backgroundColor: Color,
    contentColor: Color,
    onDismissRequest: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val previewSurfaceColor = backgroundColor.toArgb()
    val shape = RoundedCornerShape(18.dp)
    val popupHeightPx = with(density) { PREVIEW_POPUP_HEIGHT.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val maxTravelPx = ((screenHeightPx - popupHeightPx) / 2f).coerceAtLeast(0f)
    var dragOffsetY by remember { mutableStateOf(0f) }
    val clampedOffsetY = dragOffsetY.coerceIn(-maxTravelPx, maxTravelPx)
    val popupDragModifier = Modifier.pointerInput(Unit) {
        detectDragGestures { change, dragAmount ->
            change.consume()
            dragOffsetY = (dragOffsetY + dragAmount.y).coerceIn(-maxTravelPx, maxTravelPx)
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = backgroundColor,
            contentColor = contentColor,
            shape = shape,
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth(PREVIEW_POPUP_WIDTH_FRACTION)
                .height(PREVIEW_POPUP_HEIGHT)
                .offset { IntOffset(0, clampedOffsetY.roundToInt()) }
                .testTag("markdown_preview_popup")
        ) {
            Column {
                Row(
                    modifier = popupDragModifier
                        .fillMaxWidth()
                        .height(PREVIEW_HEADER_HEIGHT)
                        .background(backgroundColor)
                        .padding(start = 16.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(com.clipnest.R.string.preview_markdown),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = contentColor,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .size(40.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("markdown_preview_close")
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = stringResource(com.clipnest.R.string.close),
                            tint = contentColor
                        )
                    }
                }

                HorizontalDivider(color = contentColor.copy(alpha = 0.18f))

                MarkdownPreviewWebView(
                    html = html,
                    backgroundColor = previewSurfaceColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("markdown_preview_content")
                )

                HorizontalDivider(color = contentColor.copy(alpha = 0.18f))

                Box(
                    modifier = popupDragModifier
                        .fillMaxWidth()
                        .height(PREVIEW_FOOTER_HEIGHT)
                        .background(backgroundColor)
                        .testTag("markdown_preview_footer")
                )
            }
        }
    }
}

@Composable
private fun MarkdownPreviewWebView(
    html: String,
    backgroundColor: Int,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = false
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                setBackgroundColor(backgroundColor)
                isVerticalScrollBarEnabled = true
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                webViewClient = WebViewClient()
            }
        },
        update = { webView ->
            webView.setBackgroundColor(backgroundColor)
            if (html.isNotBlank() && webView.tag != html) {
                val previousScrollY = webView.scrollY
                webView.tag = html
                webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                webView.post { if (webView.tag == html) webView.scrollTo(0, previousScrollY) }
            }
        },
        modifier = modifier
    )
}

@Composable
private fun EditorTextInput(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    editorTextSize: EditorTextSize,
    modifier: Modifier = Modifier
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f).toArgb()
    val tagColor = MaterialTheme.colorScheme.primary.toArgb()

    AndroidView(
        factory = { context ->
            HighlightingEditText(context).apply {
                setTextColor(textColor.toArgb())
                textSize = editorTextSize.sp.toFloat()
                setLineSpacing(0f, editorTextSize.lineHeightSp.toFloat() / editorTextSize.sp.toFloat())
                this.tagColor = tagColor
                hint = context.getString(com.clipnest.R.string.write_or_paste)
                setHintTextColor(hintColor)
            }
        },
        update = { editor ->
            editor.setTextColor(textColor.toArgb())
            editor.textSize = editorTextSize.sp.toFloat()
            editor.setLineSpacing(0f, editorTextSize.lineHeightSp.toFloat() / editorTextSize.sp.toFloat())
            editor.setHintTextColor(hintColor)
            if (editor.tagColor != tagColor) editor.tagColor = tagColor

            editor.onEditorTextChanged = { text, selectionStart, selectionEnd ->
                onValueChange(
                    TextFieldValue(
                        text = text,
                        selection = TextRange(selectionStart, selectionEnd)
                    )
                )
            }

            editor.onEditorSelectionChanged = { selectionStart, selectionEnd ->
                val currentText = editor.text?.toString().orEmpty()
                if (currentText != value.text ||
                    value.selection.start != selectionStart ||
                    value.selection.end != selectionEnd
                ) {
                    onValueChange(
                        TextFieldValue(
                            text = currentText,
                            selection = TextRange(selectionStart, selectionEnd)
                        )
                    )
                }
            }

            if (editor.text?.toString() != value.text) {
                editor.setEditorText(value.text, value.selection.start, value.selection.end)
            } else {
                editor.setEditorSelectionIfNeeded(value.selection.start, value.selection.end)
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .testTag("editor_text_input")
    )
}

@Composable
private fun EditorToolbox(
    isPreviewVisible: Boolean,
    onPaste: () -> Unit,
    onCopy: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onTogglePreview: () -> Unit,
    onMoveCursorLeft: () -> Unit,
    onMoveCursorRight: () -> Unit,
    onHeading: (Int) -> Unit,
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onQuote: () -> Unit,
    onCode: () -> Unit,
    onBullets: () -> Unit,
    onNumbers: () -> Unit,
    onHorizontalRule: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("editor_toolbox")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EditorToolButton("editor_action_paste", stringResource(com.clipnest.R.string.paste), Icons.Default.ContentPaste, onPaste)
            EditorToolButton("editor_action_copy", stringResource(com.clipnest.R.string.copy_selected), Icons.Default.ContentCopy, onCopy)
            EditorToolButton("editor_action_select_all", stringResource(com.clipnest.R.string.select_all), Icons.Default.SelectAll, onSelectAll)
            EditorToolButton("editor_action_delete", stringResource(com.clipnest.R.string.delete_selected), Icons.Default.Delete, onDelete)
            EditorToolButton("editor_action_undo", stringResource(com.clipnest.R.string.undo), Icons.AutoMirrored.Filled.Undo, onUndo, repeatOnHold = true)
            EditorToolButton("editor_action_redo", stringResource(com.clipnest.R.string.redo), Icons.AutoMirrored.Filled.Redo, onRedo, repeatOnHold = true)
            EditorToolButton(
                "editor_action_preview",
                if (isPreviewVisible) stringResource(com.clipnest.R.string.hide_markdown_preview) else stringResource(com.clipnest.R.string.show_markdown_preview),
                if (isPreviewVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                onTogglePreview
            )
            EditorToolButton("editor_action_cursor_left", stringResource(com.clipnest.R.string.move_cursor_left), Icons.AutoMirrored.Filled.KeyboardArrowLeft, onMoveCursorLeft, repeatOnHold = true)
            EditorToolButton("editor_action_cursor_right", stringResource(com.clipnest.R.string.move_cursor_right), Icons.AutoMirrored.Filled.KeyboardArrowRight, onMoveCursorRight, repeatOnHold = true)
            MarkdownTextButton("markdown_action_h1", "H1", stringResource(com.clipnest.R.string.markdown_h1), { onHeading(1) })
            MarkdownTextButton("markdown_action_h2", "H2", stringResource(com.clipnest.R.string.markdown_h2), { onHeading(2) })
            MarkdownTextButton("markdown_action_h3", "H3", stringResource(com.clipnest.R.string.markdown_h3), { onHeading(3) })
            MarkdownTextButton("markdown_action_bold", "B", stringResource(com.clipnest.R.string.markdown_bold), onBold, bold = true)
            MarkdownTextButton("markdown_action_italic", "I", stringResource(com.clipnest.R.string.markdown_italic), onItalic, italic = true)
            MarkdownTextButton("markdown_action_quote", "❝", stringResource(com.clipnest.R.string.markdown_quote), onQuote)
            MarkdownTextButton("markdown_action_code", "</>", stringResource(com.clipnest.R.string.markdown_code), onCode)
            MarkdownTextButton("markdown_action_bullets", "•", stringResource(com.clipnest.R.string.markdown_bullets), onBullets)
            MarkdownTextButton("markdown_action_numbers", "1.", stringResource(com.clipnest.R.string.markdown_numbers), onNumbers)
            MarkdownTextButton("markdown_action_rule", "—", stringResource(com.clipnest.R.string.markdown_horizontal_rule), onHorizontalRule)
        }
    }
}

@Composable
private fun MarkdownTextButton(
    tag: String,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    bold: Boolean = false,
    italic: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .testTag(tag)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
            }
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (italic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal
            )
        )
    }
}

@Composable
private fun EditorToolButton(
    tag: String,
    contentDescription: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    repeatOnHold: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .testTag(tag)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
            }
            .pointerInput(repeatOnHold) {
                detectTapGestures(
                    onPress = {
                        if (!repeatOnHold) {
                            onClick()
                            tryAwaitRelease()
                        } else {
                            kotlinx.coroutines.coroutineScope {
                                val repeatJob = launch {
                                    onClick()
                                    delay(220)
                                    while (isActive) {
                                        onClick()
                                        delay(55)
                                    }
                                }
                                tryAwaitRelease()
                                repeatJob.cancel()
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
    }
}
