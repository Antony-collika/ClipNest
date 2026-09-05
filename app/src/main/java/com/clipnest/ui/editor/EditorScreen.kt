package com.clipnest.ui.editor

import android.view.View
import android.view.ViewConfiguration
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clipnest.data.local.EditorTextSize
import com.clipnest.data.local.ViewerTextSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

private const val MAX_PREVIEW_FRACTION = 1.0f
private const val PREVIEW_RENDER_DEBOUNCE_MS = 140L
private val PREVIEW_HANDLE_HEIGHT = 48.dp
private val PREVIEW_HEADER_HEIGHT = 48.dp

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

            EditorWithPreviewOverlay(
                uiState = uiState,
                previewHtml = previewHtml,
                onPreviewFractionChange = viewModel::setPreviewSplitFraction,
                previewBackground = previewBackground,
                previewTextColor = previewTextColor,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (uiState.isMarkdownToolsExpanded) {
            ExpandedMarkdownToolbox(
                isPreviewVisible = uiState.showMarkdownPreview,
                onHeading = viewModel::insertMarkdownHeading,
                onBold = viewModel::toggleMarkdownStrong,
                onItalic = viewModel::toggleMarkdownEmphasis,
                onQuote = viewModel::insertMarkdownQuote,
                onCode = viewModel::insertMarkdownCodeBlock,
                onBullets = viewModel::insertMarkdownBullets,
                onNumbers = viewModel::insertMarkdownNumbers,
                onHorizontalRule = viewModel::insertMarkdownHorizontalRule,
                onTogglePreview = viewModel::toggleMarkdownPreview
            )
        }

        EditorToolbox(
            isMarkdownToolsExpanded = uiState.isMarkdownToolsExpanded,
            onPaste = { viewModel.pasteFromClipboard(context) },
            onCopy = { viewModel.copySelectedText(context) },
            onSelectAll = viewModel::selectAll,
            onDelete = viewModel::deleteSelectedText,
            onUndo = viewModel::undo,
            onRedo = viewModel::redo,
            onMoveCursorLeft = viewModel::moveCursorLeft,
            onMoveCursorRight = viewModel::moveCursorRight,
            onToggleExpanded = viewModel::toggleMarkdownTools
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
private fun EditorWithPreviewOverlay(
    uiState: EditorUiState,
    previewHtml: String,
    onPreviewFractionChange: (Float) -> Unit,
    previewBackground: Color,
    previewTextColor: Color,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.testTag("editor_split_view")) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val totalHeightPx = with(density) { maxHeight.toPx() }
        val minPreviewFraction = if (totalHeightPx > 0f) {
            with(density) { (PREVIEW_HEADER_HEIGHT + 1.dp).toPx() } / totalHeightPx
        } else 0f
        var dragging by remember { mutableStateOf(false) }
        var dragFraction by remember(uiState.showMarkdownPreview, uiState.previewSplitFraction) {
            mutableStateOf(uiState.previewSplitFraction)
        }
        val latestFraction by rememberUpdatedState(dragFraction)
        val previewDragState = rememberDraggableState { delta ->
            if (totalHeightPx > 0f && dragging) {
                dragFraction = (latestFraction - delta / totalHeightPx)
                    .coerceIn(minPreviewFraction.coerceAtMost(MAX_PREVIEW_FRACTION), MAX_PREVIEW_FRACTION)
            }
        }
        val fraction = dragFraction.coerceIn(minPreviewFraction.coerceAtMost(MAX_PREVIEW_FRACTION), MAX_PREVIEW_FRACTION)
        val previewHeight = if (uiState.showMarkdownPreview) maxHeight * fraction else 0.dp

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(previewHeight)
        ) {
            MarkdownPreviewPane(
                html = previewHtml,
                backgroundColor = previewBackground,
                contentColor = previewTextColor,
                dragState = previewDragState,
                onDragStarted = { dragging = true },
                onDragStopped = {
                    onPreviewFractionChange(latestFraction.coerceIn(minPreviewFraction.coerceAtMost(MAX_PREVIEW_FRACTION), MAX_PREVIEW_FRACTION))
                    dragging = false
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun MarkdownPreviewPane(
    html: String,
    backgroundColor: Color,
    contentColor: Color,
    dragState: androidx.compose.foundation.gestures.DraggableState,
    onDragStarted: () -> Unit,
    onDragStopped: () -> Unit,
    modifier: Modifier = Modifier
) {
    val previewSurfaceColor = backgroundColor.toArgb()
    val shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
    Surface(
        color = backgroundColor,
        contentColor = contentColor,
        shape = shape,
        shadowElevation = 8.dp,
        modifier = modifier
            .clip(shape)
            .shadow(8.dp, shape)
            .testTag("markdown_preview_pane")
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(PREVIEW_HEADER_HEIGHT)
                    .background(backgroundColor)
                    .testTag("markdown_preview_header")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PREVIEW_HANDLE_HEIGHT)
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = dragState,
                            onDragStarted = { onDragStarted() },
                            onDragStopped = { onDragStopped() }
                        )
                        .testTag("markdown_preview_handle_hitbox"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(5.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFF8B8B8B))
                        )
                        Text(
                            text = stringResource(com.clipnest.R.string.preview_markdown),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = contentColor,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            androidx.compose.material3.HorizontalDivider(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = PREVIEW_HEADER_HEIGHT),
                color = contentColor.copy(alpha = 0.18f)
            )
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
                        setBackgroundColor(previewSurfaceColor)
                        isVerticalScrollBarEnabled = true
                        isHorizontalScrollBarEnabled = false
                        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
                        var downX = 0f
                        var downY = 0f
                        var locked = false
                        setOnTouchListener { view, event ->
                            when (event.actionMasked) {
                                android.view.MotionEvent.ACTION_DOWN -> {
                                    downX = event.x
                                    downY = event.y
                                    locked = false
                                }
                                android.view.MotionEvent.ACTION_MOVE -> {
                                    val dx = event.x - downX
                                    val dy = event.y - downY
                                    if (!locked && maxOf(abs(dx), abs(dy)) > touchSlop) {
                                        locked = true
                                        view.parent?.requestDisallowInterceptTouchEvent(abs(dy) >= abs(dx))
                                    }
                                }
                                android.view.MotionEvent.ACTION_UP,
                                android.view.MotionEvent.ACTION_CANCEL -> {
                                    view.parent?.requestDisallowInterceptTouchEvent(false)
                                    locked = false
                                }
                            }
                            false
                        }
                        webViewClient = WebViewClient()
                    }
                },
                update = { webView ->
                    webView.setBackgroundColor(previewSurfaceColor)
                    if (html.isNotBlank() && webView.tag != html) {
                        val previousScrollY = webView.scrollY
                        webView.tag = html
                        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                        webView.post { if (webView.tag == html) webView.scrollTo(0, previousScrollY) }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = PREVIEW_HEADER_HEIGHT + 1.dp)
                    .testTag("markdown_preview_content")
            )
        }
    }
}

@Composable
private fun EditorTextInput(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    editorTextSize: EditorTextSize,
    modifier: Modifier = Modifier
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val tagColor = MaterialTheme.colorScheme.primary.toArgb()

    AndroidView(
        factory = { context ->
            HighlightingEditText(context).apply {
                setTextColor(textColor.toArgb())
                textSize = editorTextSize.sp.toFloat()
                setLineSpacing(0f, editorTextSize.lineHeightSp.toFloat() / editorTextSize.sp.toFloat())
                this.tagColor = tagColor
                hint = context.getString(com.clipnest.R.string.write_or_paste)
                setHintTextColor(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f).toArgb())
                onEditorTextChanged = { text, selectionStart, selectionEnd ->
                    onValueChange(TextFieldValue(text = text, selection = TextRange(selectionStart, selectionEnd)))
                }
            }
        },
        update = { editor ->
            editor.setTextColor(textColor.toArgb())
            editor.textSize = editorTextSize.sp.toFloat()
            editor.setLineSpacing(0f, editorTextSize.lineHeightSp.toFloat() / editorTextSize.sp.toFloat())
            editor.setHintTextColor(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f).toArgb())
            if (editor.tagColor != tagColor) editor.tagColor = tagColor

            editor.onEditorSelectionChanged = { selectionStart, selectionEnd ->
                val current = value
                if (current.selection.start != selectionStart || current.selection.end != selectionEnd) {
                    onValueChange(current.copy(selection = TextRange(selectionStart, selectionEnd)))
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
    isMarkdownToolsExpanded: Boolean,
    onPaste: () -> Unit,
    onCopy: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onMoveCursorLeft: () -> Unit,
    onMoveCursorRight: () -> Unit,
    onToggleExpanded: () -> Unit
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
            EditorToolButton("editor_action_cursor_left", stringResource(com.clipnest.R.string.move_cursor_left), Icons.AutoMirrored.Filled.KeyboardArrowLeft, onMoveCursorLeft, repeatOnHold = true)
            EditorToolButton("editor_action_cursor_right", stringResource(com.clipnest.R.string.move_cursor_right), Icons.AutoMirrored.Filled.KeyboardArrowRight, onMoveCursorRight, repeatOnHold = true)
            EditorToolButton(
                "editor_action_markdown_expand",
                if (isMarkdownToolsExpanded) stringResource(com.clipnest.R.string.hide_markdown_tools) else stringResource(com.clipnest.R.string.show_markdown_tools),
                if (isMarkdownToolsExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                onToggleExpanded
            )
        }
    }
}

@Composable
private fun ExpandedMarkdownToolbox(
    isPreviewVisible: Boolean,
    onHeading: (Int) -> Unit,
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onQuote: () -> Unit,
    onCode: () -> Unit,
    onBullets: () -> Unit,
    onNumbers: () -> Unit,
    onHorizontalRule: () -> Unit,
    onTogglePreview: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("markdown_expanded_toolbox")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
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
            MarkdownTextButton(
                tag = "markdown_action_view",
                label = "",
                contentDescription = if (isPreviewVisible) stringResource(com.clipnest.R.string.hide_markdown_preview) else stringResource(com.clipnest.R.string.show_markdown_preview),
                onClick = onTogglePreview,
                icon = if (isPreviewVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility
            )
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
    italic: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .testTag(tag)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
            }
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) },
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
        } else {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (italic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal
                )
            )
        }
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
                            coroutineScope {
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
