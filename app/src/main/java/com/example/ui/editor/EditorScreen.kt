package com.example.ui.editor

import android.view.ViewConfiguration
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

private const val MAX_PREVIEW_FRACTION = 1.0f
private const val PREVIEW_RENDER_DEBOUNCE_MS = 140L
private val PREVIEW_HANDLE_ROW_HEIGHT = 20.dp
private val PREVIEW_HANDLE_TOUCH_HEIGHT = 48.dp
private val PREVIEW_TITLE_ROW_HEIGHT = 28.dp
private val PREVIEW_HEADER_HEIGHT =
    PREVIEW_HANDLE_ROW_HEIGHT + PREVIEW_TITLE_ROW_HEIGHT
private val PREVIEW_COLLAPSED_HEIGHT =
    PREVIEW_HEADER_HEIGHT + 1.dp

@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onRequestSaveFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.red < 0.5f
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

    LaunchedEffect(uiState.content.text, previewColors) {
        // Keep the latest HTML warm even while the pane is hidden, so tapping
        // View can reveal the preview without waiting for its first render.
        // The effect is cancelled by Compose when text/theme changes again.
        delay(PREVIEW_RENDER_DEBOUNCE_MS)
        previewHtml = withContext(Dispatchers.Default) {
            MarkdownPreviewRenderer.render(uiState.content.text, previewColors)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
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
            EditorTextInput(
                value = uiState.content,
                onValueChange = viewModel::onContentChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
        EditorWithPreviewOverlay(
            uiState = uiState,
            previewHtml = previewHtml,
            onPreviewFractionChange = viewModel::setPreviewSplitFraction,
            previewBackground = previewBackground,
            previewTextColor = previewTextColor,
            modifier = Modifier.fillMaxSize()
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
    BoxWithConstraints(
        modifier = modifier.testTag("editor_split_view")
    ) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val totalHeightPx = with(density) { maxHeight.toPx() }
        val minPreviewFraction = if (totalHeightPx > 0f) {
            (with(density) { PREVIEW_COLLAPSED_HEIGHT.toPx() } / totalHeightPx)
                .coerceAtMost(MAX_PREVIEW_FRACTION)
        } else {
            0f
        }
        val latestOnFractionChange by rememberUpdatedState(onPreviewFractionChange)
        var isDragging by remember { mutableStateOf(false) }
        var dragFraction by remember(uiState.showMarkdownPreview, uiState.previewSplitFraction) {
            mutableStateOf(uiState.previewSplitFraction)
        }

        // The ViewModel fraction is the settled value. During a gesture, keep a local
        // fraction so the pane follows every pointer delta without waiting for a
        // StateFlow round-trip. Keep this local value after release as well: the
        // StateFlow update is asynchronous and must not briefly restore the old height.
        val latestDragFraction by rememberUpdatedState(dragFraction)
        val latestIsDragging by rememberUpdatedState(isDragging)
        val previewDragState = rememberDraggableState { delta ->
            if (totalHeightPx > 0f && latestIsDragging) {
                dragFraction = (latestDragFraction - delta / totalHeightPx)
                    .coerceIn(minPreviewFraction, MAX_PREVIEW_FRACTION)
            }
        }
        val previewFraction = dragFraction.coerceIn(minPreviewFraction, MAX_PREVIEW_FRACTION)
        // Deliberately avoid height animation during resize. A direct layout value is
        // stable at both ends of the gesture; View toggle itself remains instantaneous
        // rather than handing off from an animation to a drag value.
        val previewHeight = if (uiState.showMarkdownPreview) {
            maxHeight * previewFraction
        } else {
            0.dp
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(previewHeight)
                .zIndex(2f)
                .testTag("markdown_preview_overlay")
        ) {
            MarkdownPreviewPane(
                html = previewHtml,
                backgroundColor = previewBackground,
                contentColor = previewTextColor,
                dragState = previewDragState,
                onDragStarted = {
                    isDragging = true
                },
                onDragStopped = {
                    latestOnFractionChange(latestDragFraction.coerceIn(minPreviewFraction, MAX_PREVIEW_FRACTION))
                    isDragging = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
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
    val handleDragModifier = Modifier.draggable(
        orientation = Orientation.Vertical,
        state = dragState,
        onDragStarted = { _ -> onDragStarted() },
        onDragStopped = { _ -> onDragStopped() }
    )
    val previewShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
    Surface(
        color = backgroundColor,
        contentColor = contentColor,
        shape = previewShape,
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, previewShape)
            .clip(previewShape)
            .testTag("markdown_preview_pane")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(PREVIEW_HEADER_HEIGHT)
                    .background(backgroundColor)
                    .zIndex(1f)
                    .testTag("markdown_preview_header")
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(PREVIEW_HANDLE_ROW_HEIGHT)
                            .testTag("markdown_preview_resize_band"),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .offset(y = 7.dp)
                                .width(40.dp)
                                .height(5.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFF8B8B8B))
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(PREVIEW_TITLE_ROW_HEIGHT)
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(com.example.R.string.preview_markdown),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                            color = contentColor
                        )
                    }
                }
                // The transparent hitbox is larger than the visible handle but stays
                // inside the measured header container, so it never adds layout height.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(PREVIEW_HANDLE_TOUCH_HEIGHT)
                        .then(handleDragModifier)
                        .testTag("markdown_preview_handle_hitbox")
                )
            }
            androidx.compose.material3.HorizontalDivider(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = PREVIEW_HEADER_HEIGHT)
                    .zIndex(1f),
                color = contentColor.copy(alpha = 0.18f),
                thickness = 1.dp
            )
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        setBackgroundColor(previewSurfaceColor)
                        settings.javaScriptEnabled = false
                        settings.domStorageEnabled = false
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        isVerticalScrollBarEnabled = true
                        isHorizontalScrollBarEnabled = false
                        isNestedScrollingEnabled = true
                        overScrollMode = android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS
                        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
                        var downX = 0f
                        var downY = 0f
                        var directionLocked = false
                        setOnTouchListener { view, event ->
                            when (event.actionMasked) {
                                android.view.MotionEvent.ACTION_DOWN -> {
                                    downX = event.x
                                    downY = event.y
                                    directionLocked = false
                                }
                                android.view.MotionEvent.ACTION_MOVE -> {
                                    val dx = event.x - downX
                                    val dy = event.y - downY
                                    if (!directionLocked && maxOf(abs(dx), abs(dy)) > touchSlop) {
                                        directionLocked = true
                                        // Keep vertical scrolling inside WebView; let the
                                        // HorizontalPager consume horizontal swipes.
                                        view.parent?.requestDisallowInterceptTouchEvent(abs(dy) >= abs(dx))
                                    }
                                }
                                android.view.MotionEvent.ACTION_UP,
                                android.view.MotionEvent.ACTION_CANCEL -> {
                                    view.parent?.requestDisallowInterceptTouchEvent(false)
                                    directionLocked = false
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
                        webView.loadDataWithBaseURL(
                            null,
                            html,
                            "text/html",
                            "UTF-8",
                            null
                        )
                        webView.post {
                            if (webView.tag == html) webView.scrollTo(0, previousScrollY)
                        }
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
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurface
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("editor_text_input"),
        decorationBox = { innerTextField ->
            if (value.text.isEmpty()) {
                Text(
                    text = stringResource(com.example.R.string.write_or_paste),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                )
            }
            innerTextField()
        }
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
                .padding(horizontal = 2.dp, vertical = 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EditorToolButton("editor_action_paste", stringResource(com.example.R.string.paste), Icons.Default.ContentPaste, onPaste)
            EditorToolButton("editor_action_copy", stringResource(com.example.R.string.copy_selected), Icons.Default.ContentCopy, onCopy)
            EditorToolButton("editor_action_select_all", stringResource(com.example.R.string.select_all), Icons.Default.SelectAll, onSelectAll)
            EditorToolButton("editor_action_delete", stringResource(com.example.R.string.delete_selected), Icons.Default.Delete, onDelete)
            EditorToolButton("editor_action_undo", stringResource(com.example.R.string.undo), Icons.AutoMirrored.Filled.Undo, onUndo, repeatOnHold = true)
            EditorToolButton("editor_action_redo", stringResource(com.example.R.string.redo), Icons.AutoMirrored.Filled.Redo, onRedo, repeatOnHold = true)
            EditorToolButton("editor_action_cursor_left", stringResource(com.example.R.string.move_cursor_left), Icons.AutoMirrored.Filled.KeyboardArrowLeft, onMoveCursorLeft, repeatOnHold = true)
            EditorToolButton("editor_action_cursor_right", stringResource(com.example.R.string.move_cursor_right), Icons.AutoMirrored.Filled.KeyboardArrowRight, onMoveCursorRight, repeatOnHold = true)
            EditorToolButton(
                tag = "editor_action_markdown_expand",
                contentDescription = if (isMarkdownToolsExpanded) stringResource(com.example.R.string.hide_markdown_tools) else stringResource(com.example.R.string.show_markdown_tools),
                icon = if (isMarkdownToolsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                onClick = onToggleExpanded
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
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("markdown_expanded_toolbox")
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 1.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MarkdownTextButton("markdown_action_h1", "H1", stringResource(com.example.R.string.markdown_h1), { onHeading(1) }, modifier = Modifier.weight(1f))
                MarkdownTextButton("markdown_action_h2", "H2", stringResource(com.example.R.string.markdown_h2), { onHeading(2) }, modifier = Modifier.weight(1f))
                MarkdownTextButton("markdown_action_h3", "H3", stringResource(com.example.R.string.markdown_h3), { onHeading(3) }, modifier = Modifier.weight(1f))
                MarkdownTextButton("markdown_action_bold", "B", stringResource(com.example.R.string.markdown_bold), onBold, bold = true, modifier = Modifier.weight(1f))
                MarkdownTextButton("markdown_action_italic", "I", stringResource(com.example.R.string.markdown_italic), onItalic, italic = true, modifier = Modifier.weight(1f))
                MarkdownTextButton("markdown_action_quote", "❝", stringResource(com.example.R.string.markdown_quote), onQuote, modifier = Modifier.weight(1f))
                MarkdownTextButton("markdown_action_code", "</>", stringResource(com.example.R.string.markdown_code), onCode, modifier = Modifier.weight(1f))
                MarkdownTextButton("markdown_action_bullets", "•", stringResource(com.example.R.string.markdown_bullets), onBullets, modifier = Modifier.weight(1f))
                MarkdownTextButton("markdown_action_numbers", "1.", stringResource(com.example.R.string.markdown_numbers), onNumbers, modifier = Modifier.weight(1f))
                MarkdownTextButton("markdown_action_rule", "—", stringResource(com.example.R.string.markdown_horizontal_rule), onHorizontalRule, modifier = Modifier.weight(1f))
                MarkdownTextButton(
                    tag = "markdown_action_view",
                    label = "",
                    icon = if (isPreviewVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (isPreviewVisible) stringResource(com.example.R.string.hide_markdown_preview) else stringResource(com.example.R.string.show_markdown_preview),
                    onClick = onTogglePreview,
                    inactiveContentColor = if (isPreviewVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                    modifier = Modifier.weight(1f)
                )
            }
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
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    inactiveContentColor: Color? = null
) {
    Surface(
        color = Color.Transparent,
        contentColor = inactiveContentColor ?: MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .then(modifier)
            .height(36.dp)
            .testTag(tag)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            },
        tonalElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (bold) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                        fontStyle = if (italic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal
                    )
                )
            }
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
