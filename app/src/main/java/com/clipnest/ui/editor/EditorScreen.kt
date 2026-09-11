package com.clipnest.ui.editor

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clipnest.data.local.EditorTextSize
import com.clipnest.data.local.ViewerTextSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREVIEW_SMALL_DOCUMENT_THRESHOLD = 100_000
private const val PREVIEW_LARGE_DOCUMENT_THRESHOLD = 500_000
private const val PREVIEW_SMALL_DEBOUNCE_MS = 140L
private const val PREVIEW_LARGE_DEBOUNCE_MS = 360L
private const val PREVIEW_HUGE_DEBOUNCE_MS = 650L
private val PREVIEW_HEADER_HEIGHT = 48.dp

@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    editorTextSize: EditorTextSize = EditorTextSize.DEFAULT,
    viewerTextSize: ViewerTextSize = ViewerTextSize.DEFAULT,
    onRequestSaveFolder: () -> Unit,
    onRequestOpenFile: () -> Unit,
    onExit: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val openWithDiagnostic by viewModel.openWithDiagnostic.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    val editorTextColor = MaterialTheme.colorScheme.onBackground.toArgb()
    val previewBackground = if (isDark) Color(0xFF2B2B2B) else Color(0xFFF6F6F6)
    val previewTextColor = if (isDark) Color(0xFFF4F4F4) else Color(0xFF171717)
    val previewMutedColor = if (isDark) Color(0xFFCACACA) else Color(0xFF5E5E5E)
    val previewColors = remember(isDark) {
        MarkdownPreviewColors.from(
            previewBackground,
            previewTextColor,
            previewMutedColor,
            if (isDark) Color(0xFF3A3A3A) else Color(0xFFE8E8E8),
            if (isDark) Color(0xFF777777) else Color(0xFF8A8A8A),
            if (isDark) Color(0xFF555555) else Color(0xFFC7C7C7),
            previewTextColor,
            if (isDark) Color(0xFF3A3A3A) else Color(0xFFE8E8E8),
            isDark
        )
    }
    var previewHtml by remember { mutableStateOf("") }
    var tocHeadings by remember { mutableStateOf<List<MarkdownHeading>>(emptyList()) }
    var tocIndexing by remember { mutableStateOf(false) }
    var editorVisible by remember { mutableStateOf(true) }

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
                is EditorEvent.RequestSaveFolder -> onRequestSaveFolder()
            }
        }
    }

    LaunchedEffect(uiState.documentRevision, uiState.showMarkdownPreview, previewColors, viewerTextSize) {
        if (!uiState.showMarkdownPreview) return@LaunchedEffect
        tocIndexing = true
        tocHeadings = emptyList()
        val size = viewModel.currentDocumentText().length
        val debounce = when {
            size >= PREVIEW_LARGE_DOCUMENT_THRESHOLD -> PREVIEW_HUGE_DEBOUNCE_MS
            size >= PREVIEW_SMALL_DOCUMENT_THRESHOLD -> PREVIEW_LARGE_DEBOUNCE_MS
            else -> PREVIEW_SMALL_DEBOUNCE_MS
        }
        delay(debounce)
        if (!viewModel.uiState.value.showMarkdownPreview) return@LaunchedEffect
        val snapshot = viewModel.currentDocumentSnapshot()
        val rendered = withContext(Dispatchers.Default) {
            MarkdownPreviewRenderer.render(snapshot.text, previewColors, viewerTextSize.px)
        }
        if (snapshot.isCurrent(viewModel.uiState.value.documentRevision) && viewModel.uiState.value.showMarkdownPreview) {
            previewHtml = rendered
            launch {
                val headings = withContext(Dispatchers.Default) {
                    MarkdownPreviewRenderer.extractHeadings(snapshot.text)
                }
                val current = viewModel.uiState.value
                if (current.showMarkdownPreview && current.documentRevision == snapshot.revision) {
                    tocHeadings = headings
                    tocIndexing = false
                }
            }
        }
    }

    LaunchedEffect(uiState.showMarkdownPreview) {
        if (uiState.showMarkdownPreview) viewModel.hideNativeKeyboard()
        else viewModel.hideNativeKeyboard()
    }

    Box(
        modifier.fillMaxSize()
            .imePadding()
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                val windowWidth = context.resources.displayMetrics.widthPixels.toFloat()
                val visible = bounds.right > 0f && bounds.left < windowWidth
                if (visible != editorVisible) editorVisible = visible
                if (!visible) viewModel.hideNativeKeyboard()
            }
    ) {
        Column(Modifier.fillMaxSize()) {
            EditorNoteBreadcrumbBar(
                mode = uiState.mode,
                origin = uiState.noteOrigin,
                onExit = { viewModel.flushPendingSaveAndExit(context.contentResolver, onExit) }
            )
            EditorToolbox(
                isMarkdownToolsExpanded = uiState.isMarkdownToolsExpanded,
                isPreviewVisible = uiState.showMarkdownPreview,
                onPaste = { viewModel.pasteFromClipboard(context) },
                onCopy = { viewModel.copySelectedText(context) },
                onCut = { viewModel.cutSelectedText(context) },
                onSelectAll = viewModel::selectAll,
                onDelete = viewModel::deleteSelectedText,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
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
            EditorNoteTitleField(
                value = uiState.title,
                onValueChange = viewModel::onTitleChange,
                editorTextSize = editorTextSize,
                textColor = MaterialTheme.colorScheme.onSurface,
                hintColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            )
            EditorNoteTitleDivider()
            AndroidView(
                factory = {
                    NativeEditorView(it).apply {
                        setEditorTextSize(editorTextSize)
                        setEditorTextColor(editorTextColor)
                        viewModel.bindNativeEditor(this)
                    }
                },
                update = {
                    it.setEditorTextSize(editorTextSize)
                    it.setEditorTextColor(editorTextColor)
                    if (uiState.showMarkdownPreview || !editorVisible) it.hideKeyboardAndClearFocus()
                    viewModel.bindNativeEditor(it)
                },
                modifier = Modifier.fillMaxWidth().weight(1f).testTag("editor_text_input")
            )
        }
    }

    if (uiState.showMarkdownPreview) {
        MarkdownPreviewDialog(
            html = previewHtml,
            headings = tocHeadings,
            tocIndexing = tocIndexing,
            backgroundColor = previewBackground,
            contentColor = previewTextColor,
            onDismiss = viewModel::toggleMarkdownPreview
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

    openWithDiagnostic?.let {
        AlertDialog(
            onDismissRequest = viewModel::dismissOpenWithDiagnostic,
            title = { Text(stringResource(com.clipnest.R.string.open_with_fallback_title)) },
            text = { Text(stringResource(com.clipnest.R.string.open_with_fallback_message)) },
            confirmButton = { TextButton(onClick = viewModel::dismissOpenWithDiagnostic) { Text(stringResource(com.clipnest.R.string.close)) } },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissOpenWithDiagnostic(); onRequestOpenFile() }) {
                    Text(stringResource(com.clipnest.R.string.open_with_fallback_open_file))
                }
            }
        )
    }
}

@Composable
private fun EditorToolbox(
    isMarkdownToolsExpanded: Boolean,
    isPreviewVisible: Boolean,
    onPaste: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
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
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f), modifier = Modifier.fillMaxWidth().testTag("editor_toolbox")) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(1.dp), verticalAlignment = Alignment.CenterVertically) {
            EditorToolButton("editor_action_cut", "Cut", Icons.Default.ContentCut, onCut)
            EditorToolButton("editor_action_copy", stringResource(com.clipnest.R.string.copy_selected), Icons.Default.ContentCopy, onCopy)
            EditorToolButton("editor_action_paste", stringResource(com.clipnest.R.string.paste), Icons.Default.ContentPaste, onPaste)
            EditorToolButton("editor_action_select_all", stringResource(com.clipnest.R.string.select_all), Icons.Default.SelectAll, onSelectAll)
            EditorToolButton("editor_action_delete", stringResource(com.clipnest.R.string.delete_selected), Icons.Default.Delete, onDelete)
            EditorToolButton("editor_action_undo", stringResource(com.clipnest.R.string.undo), Icons.AutoMirrored.Filled.Undo, onUndo)
            EditorToolButton("editor_action_redo", stringResource(com.clipnest.R.string.redo), Icons.AutoMirrored.Filled.Redo, onRedo)
            EditorToolButton("markdown_action_view", if (isPreviewVisible) stringResource(com.clipnest.R.string.hide_markdown_preview) else stringResource(com.clipnest.R.string.show_markdown_preview), if (isPreviewVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, onTogglePreview)
            MarkdownTextButton("markdown_action_h1", "H1", stringResource(com.clipnest.R.string.markdown_h1)) { onHeading(1) }
            MarkdownTextButton("markdown_action_h2", "H2", stringResource(com.clipnest.R.string.markdown_h2)) { onHeading(2) }
            MarkdownTextButton("markdown_action_h3", "H3", stringResource(com.clipnest.R.string.markdown_h3)) { onHeading(3) }
            MarkdownTextButton("markdown_action_bold", "B", stringResource(com.clipnest.R.string.markdown_bold), bold = true, onClick = onBold)
            MarkdownTextButton("markdown_action_italic", "I", stringResource(com.clipnest.R.string.markdown_italic), italic = true, onClick = onItalic)
            MarkdownTextButton("markdown_action_quote", "❝", stringResource(com.clipnest.R.string.markdown_quote), onClick = onQuote)
            MarkdownTextButton("markdown_action_code", "</>", stringResource(com.clipnest.R.string.markdown_code), onClick = onCode)
            MarkdownTextButton("markdown_action_bullets", "•", stringResource(com.clipnest.R.string.markdown_bullets), onClick = onBullets)
            MarkdownTextButton("markdown_action_numbers", "1.", stringResource(com.clipnest.R.string.markdown_numbers), onClick = onNumbers)
            MarkdownTextButton("markdown_action_rule", "—", stringResource(com.clipnest.R.string.markdown_horizontal_rule), onClick = onHorizontalRule)
        }
    }
}

@Composable
private fun MarkdownTextButton(tag: String, label: String, description: String, bold: Boolean = false, italic: Boolean = false, onClick: () -> Unit) {
    Surface(color = Color.Transparent, contentColor = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(6.dp), modifier = Modifier.size(48.dp).testTag(tag).semantics { role = Role.Button; contentDescription = description }) {
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }, contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal))
        }
    }
}

@Composable
private fun EditorToolButton(tag: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, tint: Color = MaterialTheme.colorScheme.primary) {
    IconButton(onClick, Modifier.size(48.dp).testTag(tag).semantics { role = Role.Button; contentDescription = description }) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun MarkdownPreviewDialog(
    html: String,
    headings: List<MarkdownHeading>,
    tocIndexing: Boolean,
    backgroundColor: Color,
    contentColor: Color,
    onDismiss: () -> Unit
) {
    val surfaceColor = backgroundColor.toArgb()
    val shape = RoundedCornerShape(18.dp)
    val visibility = remember { MutableTransitionState(true) }
    var ready by remember(html) { mutableStateOf(false) }
    var showToc by remember { mutableStateOf(false) }
    var pendingHeadingIndex by remember { mutableStateOf<Int?>(null) }
    fun dismiss() { if (visibility.targetState) visibility.targetState = false }
    LaunchedEffect(visibility.currentState, visibility.targetState) { if (!visibility.currentState && !visibility.targetState) onDismiss() }
    Dialog(onDismissRequest = ::dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true)) {
        AnimatedVisibility(visibleState = visibility, enter = fadeIn(tween(280)) + slideInVertically(tween(280), initialOffsetY = { -it / 6 }), exit = fadeOut(tween(220)) + slideOutVertically(tween(220), targetOffsetY = { -it / 6 })) {
            Surface(modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.88f).widthIn(max = 720.dp).shadow(24.dp, shape).clip(shape).testTag("markdown_preview_dialog"), shape = shape, color = backgroundColor, contentColor = contentColor) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().height(PREVIEW_HEADER_HEIGHT).padding(start = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (showToc) {
                            IconButton(onClick = { showToc = false }, Modifier.size(44.dp).testTag("markdown_preview_toc_back")) {
                                Icon(Icons.Default.ArrowBack, "Back", tint = contentColor)
                            }
                            Text("Table of Contents", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                        } else {
                            IconButton(onClick = { showToc = true }, Modifier.size(44.dp).testTag("markdown_preview_toc")) {
                                Icon(Icons.Default.FormatListBulleted, "Table of contents", tint = contentColor)
                            }
                            Text(stringResource(com.clipnest.R.string.preview_markdown), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                        }
                        IconButton(::dismiss, Modifier.size(44.dp).testTag("markdown_preview_close")) { Icon(Icons.Default.Close, stringResource(com.clipnest.R.string.close), tint = contentColor) }
                    }
                    HorizontalDivider(color = contentColor.copy(alpha = 0.18f))
                    Box(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
                        if (showToc) {
                            if (tocIndexing) {
                                Text("🔹Loading . . .", modifier = Modifier.fillMaxWidth().padding(20.dp), style = MaterialTheme.typography.bodyLarge)
                            } else if (headings.isNotEmpty()) {
                                LazyColumn(Modifier.fillMaxSize().testTag("markdown_toc_list")) {
                                    items(headings, key = { it.index }) { heading ->
                                        Text(
                                            heading.title,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    pendingHeadingIndex = heading.index
                                                    showToc = false
                                                }
                                                .padding(start = ((heading.level - 1) * 18).dp, top = 10.dp, bottom = 10.dp, end = 8.dp),
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                            }
                        } else {
                            if (html.isNotBlank()) MarkdownPreviewWebView(html, surfaceColor, pendingHeadingIndex) { ready = true }
                            MarkdownPreviewLoadingOverlay(!ready, backgroundColor, contentColor)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownPreviewLoadingOverlay(visible: Boolean, backgroundColor: Color, contentColor: Color) {
    AnimatedVisibility(visible, enter = fadeIn(tween(120)), exit = fadeOut(tween(180))) {
        Box(Modifier.fillMaxSize().background(backgroundColor)) { MarkdownPreviewShimmer(backgroundColor, contentColor) }
    }
}

@Composable
private fun MarkdownPreviewShimmer(backgroundColor: Color, contentColor: Color) {
    val transition = rememberInfiniteTransition(label = "markdown_preview_shimmer")
    val progress by transition.animateFloat(-1f, 2f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "markdown_preview_shimmer_progress")
    val base = contentColor.copy(alpha = .10f); val highlight = contentColor.copy(alpha = .20f)
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        MarkdownShimmerLine(progress, base, highlight, .72f); MarkdownShimmerLine(progress, base, highlight, .92f); MarkdownShimmerLine(progress, base, highlight, .58f); Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun MarkdownShimmerLine(progress: Float, baseColor: Color, highlightColor: Color, widthFraction: Float) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().height(12.dp)) {
        val widthPx = with(density) { maxWidth.toPx() }
        val brush = Brush.linearGradient(listOf(baseColor, highlightColor, baseColor), Offset(widthPx * (progress - .5f), 0f), Offset(widthPx * progress, 0f))
        Box(Modifier.width(maxWidth * widthFraction).fillMaxSize().clip(RoundedCornerShape(6.dp)).background(brush))
    }
}