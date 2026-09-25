package com.clipnest.ui.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import android.graphics.Rect
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
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
// Lưới an toàn cho shimmer: nếu WebView không báo "đã load xong" trong
// khoảng thời gian này, ta tự coi như xong để tránh shimmer bị kẹt mãi mãi.
private const val MARKDOWN_PREVIEW_READY_TIMEOUT_MS = 5_000L

private class CaretSuggestionPopupPositionProvider(
    initialCaretRectInParent: Rect,
    private val gapPx: Int,
    private val edgePx: Int
) : PopupPositionProvider {
    private var caretRectInParent: Rect = Rect(initialCaretRectInParent)

    fun updateCaretRect(rect: Rect) {
        caretRectInParent = Rect(rect)
    }

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val caretLeft = anchorBounds.left + caretRectInParent.left
        val caretTop = anchorBounds.top + caretRectInParent.top
        val caretBottom = anchorBounds.top + caretRectInParent.bottom
        val maxX = (windowSize.width - popupContentSize.width - edgePx).coerceAtLeast(edgePx)
        val x = caretLeft.coerceIn(edgePx, maxX)
        val spaceBelow = windowSize.height - caretBottom - edgePx
        val spaceAbove = caretTop - edgePx
        val fitsBelow = spaceBelow >= popupContentSize.height + gapPx
        val y = if (fitsBelow || spaceBelow >= spaceAbove) {
            caretBottom + gapPx
        } else {
            caretTop - popupContentSize.height - gapPx
        }
        val maxY = (windowSize.height - popupContentSize.height - edgePx).coerceAtLeast(edgePx)
        return IntOffset(x, y.coerceIn(edgePx, maxY))
    }
}
private val PREVIEW_HEADER_HEIGHT = 48.dp

@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    editorTextSize: EditorTextSize = EditorTextSize.DEFAULT,
    viewerTextSize: ViewerTextSize = ViewerTextSize.DEFAULT,
    onRequestSaveFolder: () -> Unit,
    onRequestOpenFile: () -> Unit,
    onRequestExternalSaveAs: (String, String) -> Unit,
    onExit: (String?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val openWithDiagnostic by viewModel.openWithDiagnostic.collectAsStateWithLifecycle()
    val pendingEncryptedOpen by viewModel.pendingEncryptedOpen.collectAsStateWithLifecycle()
    val topicSuggestionSession by viewModel.topicSuggestionSession.collectAsStateWithLifecycle()
    val topicSuggestions by viewModel.topicSuggestions.collectAsStateWithLifecycle()
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    val editorTextColor = MaterialTheme.colorScheme.onBackground.toArgb()
    val previewBackground = if (isDark) Color(0xFF2B2B2B) else Color(0xFFF6F6F6)
    val previewTextColor = if (isDark) Color(0xFFF4F4F4) else Color(0xFF171717)
    val previewMutedColor = if (isDark) Color(0xFFCACACA) else Color(0xFF5E5E5E)
    val previewColors = remember(isDark) {
        MarkdownPreviewColors.from(previewBackground, previewTextColor, previewMutedColor, if (isDark) Color(0xFF3A3A3A) else Color(0xFFE8E8E8), if (isDark) Color(0xFF777777) else Color(0xFF8A8A8A), if (isDark) Color(0xFF555555) else Color(0xFFC7C7C7), previewTextColor, if (isDark) Color(0xFF3A3A3A) else Color(0xFFE8E8E8), isDark)
    }
    var previewHtml by remember { mutableStateOf("") }
    var tocHeadings by remember { mutableStateOf<List<MarkdownHeading>>(emptyList()) }
    var tocIndexing by remember { mutableStateOf(false) }
    var nativeEditorView by remember { mutableStateOf<NativeEditorView?>(null) }
    var caretInTitle by remember { mutableStateOf(false) }
    // Keep the last valid caret anchor. Native editor layout can briefly emit
    // transient callbacks while a character is inserted; the suggestion popup
    // must not be removed just because one callback has no usable rect.
    var topicSuggestionCaretRect by remember { mutableStateOf<Rect?>(null) }

    LaunchedEffect(uiState.externalDocumentUri, uiState.documentName, uiState.externalDocumentFileCount) {
        if (uiState.externalDocumentUri != null) {
            withFrameNanos { }
            nativeEditorView?.requestFocus()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is EditorEvent.ShowToast -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                is EditorEvent.RequestSaveFolder -> onRequestSaveFolder()
                is EditorEvent.RequestExternalSaveAs -> onRequestExternalSaveAs(event.suggestedFileName, event.mimeType)
            }
        }
    }

    LaunchedEffect(uiState.documentRevision, uiState.showMarkdownPreview, previewColors, viewerTextSize) {
        if (!uiState.showMarkdownPreview) return@LaunchedEffect
        tocIndexing = true
        tocHeadings = emptyList()
        val size = viewModel.currentDocumentText().length
        val debounce = when { size >= PREVIEW_LARGE_DOCUMENT_THRESHOLD -> PREVIEW_HUGE_DEBOUNCE_MS; size >= PREVIEW_SMALL_DOCUMENT_THRESHOLD -> PREVIEW_LARGE_DEBOUNCE_MS; else -> PREVIEW_SMALL_DEBOUNCE_MS }
        delay(debounce)
        if (!viewModel.uiState.value.showMarkdownPreview) return@LaunchedEffect
        val snapshot = viewModel.currentDocumentSnapshot()
        val isCsvDocument = uiState.documentName.endsWith(".csv", ignoreCase = true)
        val rendered = withContext(Dispatchers.Default) { if (isCsvDocument) CsvPreviewRenderer.render(snapshot.text, previewColors, viewerTextSize.px) else MarkdownPreviewRenderer.render(snapshot.text, previewColors, viewerTextSize.px) }
        if (snapshot.isCurrent(viewModel.uiState.value.documentRevision) && viewModel.uiState.value.showMarkdownPreview) {
            previewHtml = rendered
            if (isCsvDocument) { tocHeadings = emptyList(); tocIndexing = false } else launch {
                val headings = withContext(Dispatchers.Default) { MarkdownPreviewRenderer.extractHeadings(snapshot.text) }
                val current = viewModel.uiState.value
                if (current.showMarkdownPreview && current.documentRevision == snapshot.revision) { tocHeadings = headings; tocIndexing = false }
            }
        }
    }

    LaunchedEffect(uiState.showMarkdownPreview) { viewModel.hideNativeKeyboard() }

    Box(modifier.fillMaxSize().imePadding()) {
        Column(Modifier.fillMaxSize()) {
            EditorNoteBreadcrumbBar(
                    mode = uiState.mode,
                    origin = uiState.noteOrigin,
                    onExit = {
                        val returnKey = uiState.noteOrigin?.returnKey
                        viewModel.returnToFreeEditor { onExit(returnKey) }
                    },
                    onSave = viewModel::saveCurrentNoteNow,
                    onSaveToNote = { viewModel.createNoteAndEnterNoteMode(viewModel.currentDocumentText(), title = viewModel.currentDocumentTitle()) }
                )
            EditorToolbox(
                isMarkdownToolsExpanded = uiState.isMarkdownToolsExpanded,
                isPreviewVisible = uiState.showMarkdownPreview,
                caretInTitle = caretInTitle,
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
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                AndroidView(
                    factory = {
                        NativeEditorView(it).apply {
                            setEditorTextSize(editorTextSize)
                            setEditorTextColor(editorTextColor)
                            nativeEditorView = this
                            viewModel.bindNativeEditor(this)
                            caretInTitle = isCaretInTitle()
                            setSectionChangeListener { inTitle -> caretInTitle = inTitle }
                            setCaretRectChangeListener { rect ->
                        // Never clear the anchor during transient editor layout.
                        // Popup lifetime is controlled by the suggestion session, not
                        // by a single caret-layout callback.
                        topicSuggestionCaretRect = Rect(rect)
                    }
                        }
                    },
                    update = {
                        it.setEditorTextSize(editorTextSize)
                        it.setEditorTextColor(editorTextColor)
                        nativeEditorView = it
                        if (uiState.showMarkdownPreview) it.hideKeyboardAndClearFocus()
                        viewModel.bindNativeEditor(it)
                        caretInTitle = it.isCaretInTitle()
                        it.setSectionChangeListener { inTitle -> caretInTitle = inTitle }
                    },
                    modifier = Modifier.fillMaxSize().testTag("editor_text_input")
                )
                val session = topicSuggestionSession
                val caretRect = topicSuggestionCaretRect
                if (uiState.mode == EditorMode.NOTE && session != null && caretRect != null) {
                    val query = session.query
                    val hasExactMatch = topicSuggestions.any { it.name.equals(query, ignoreCase = true) }
                    val density = LocalDensity.current
                    val gapPx = with(density) { 4.dp.roundToPx() }
                    val edgePx = with(density) { 8.dp.roundToPx() }
                    val positionProvider = remember(gapPx, edgePx) {
                        CaretSuggestionPopupPositionProvider(caretRect, gapPx, edgePx)
                    }
                    // Move the existing popup anchor as the caret moves. The provider
                    // identity stays stable for the lifetime of the suggestion session.
                    positionProvider.updateCaretRect(caretRect)
                    Popup(
                        popupPositionProvider = positionProvider,
                        onDismissRequest = viewModel::dismissTopicSuggestions,
                        properties = PopupProperties(
                            focusable = false,
                            dismissOnClickOutside = true
                        )
                    ) {
                        Surface(
                            modifier = Modifier
                                .widthIn(min = 200.dp, max = 320.dp)
                                .heightIn(max = 280.dp)
                                .shadow(8.dp, RoundedCornerShape(10.dp)),
                            shape = RoundedCornerShape(10.dp),
                            tonalElevation = 3.dp
                        ) {
                            Column(Modifier.verticalScroll(rememberScrollState())) {
                                topicSuggestions.forEach { topic ->
                                    DropdownMenuItem(
                                        text = { Text("#" + topic.name) },
                                        onClick = { viewModel.selectExistingTopic(topic) }
                                    )
                                }
                                if (query.isNotBlank() && !hasExactMatch) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(com.clipnest.R.string.create_topic, query)) },
                                        onClick = viewModel::createTopicFromSuggestion
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (uiState.showEmptyNoteExitDialog) {
        AlertDialog(
            onDismissRequest = viewModel::cancelEmptyNoteExit,
            title = { Text(stringResource(com.clipnest.R.string.empty_note_title)) },
            text = { Text(stringResource(com.clipnest.R.string.empty_note_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::saveEmptyNoteAndExit) {
                    Text(stringResource(com.clipnest.R.string.save))
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = viewModel::discardEmptyNoteAndExit) {
                        Text(stringResource(com.clipnest.R.string.discard_note))
                    }
                    TextButton(onClick = viewModel::cancelEmptyNoteExit) {
                        Text(stringResource(com.clipnest.R.string.cancel))
                    }
                }
            }
        )
    }

    if (uiState.showMarkdownPreview) MarkdownPreviewDialog(html = previewHtml, headings = tocHeadings, tocIndexing = tocIndexing, backgroundColor = previewBackground, contentColor = previewTextColor, onDismiss = viewModel::toggleMarkdownPreview)
    if (uiState.showSaveNewFileDialog) SaveNewFileDialog(defaultFolderUri = uiState.defaultSaveFolderUri, initialFileName = uiState.title.ifBlank { uiState.documentName }, onChooseFolder = onRequestSaveFolder, onDismiss = viewModel::dismissSaveNewFileDialog, onConfirm = { fileName, format -> viewModel.confirmSaveToNewFile(fileName, format, context.contentResolver) })
    if (uiState.showExternalUnsavedChangesDialog) AlertDialog(onDismissRequest = viewModel::cancelExternalExit, title = { Text("Unsaved changes") }, text = { Text("This external file has unsaved changes. What would you like to do?") }, confirmButton = { Column(horizontalAlignment = Alignment.End) { Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { TextButton(onClick = { viewModel.chooseExternalSave(context.contentResolver) }) { Text("Save") }; TextButton(onClick = viewModel::chooseExternalSaveAs) { Text("Save as") }; TextButton(onClick = { viewModel.chooseExternalNoSave(context.contentResolver) }) { Text("Don't save") } }; TextButton(onClick = viewModel::cancelExternalExit) { Text("Cancel") } } }, dismissButton = {})
    openWithDiagnostic?.let { AlertDialog(onDismissRequest = viewModel::dismissOpenWithDiagnostic, title = { Text(stringResource(com.clipnest.R.string.open_with_fallback_title)) }, text = { Text(stringResource(com.clipnest.R.string.open_with_fallback_message)) }, confirmButton = { TextButton(onClick = viewModel::dismissOpenWithDiagnostic) { Text(stringResource(com.clipnest.R.string.close)) } }, dismissButton = { TextButton(onClick = { viewModel.dismissOpenWithDiagnostic(); onRequestOpenFile() }) { Text(stringResource(com.clipnest.R.string.open_with_fallback_open_file)) } }) }
    pendingEncryptedOpen?.let { pending -> DecryptOpenDialog(displayName = pending.displayName, isError = pending.error, onDismiss = viewModel::dismissPendingEncryptedOpen, onConfirm = { password -> viewModel.confirmDecryptAndOpen(password, context.contentResolver) }) }
}

@Composable
private fun EditorToolbox(
    isMarkdownToolsExpanded: Boolean,
    caretInTitle: Boolean,
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
    val contentToolsEnabled = !caretInTitle
    val disabledTint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f), modifier = Modifier.fillMaxWidth().testTag("editor_toolbox")) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(1.dp), verticalAlignment = Alignment.CenterVertically) {
            EditorToolButton("editor_action_cut", "Cut", Icons.Default.ContentCut, onCut)
            EditorToolButton("editor_action_copy", stringResource(com.clipnest.R.string.copy_selected), Icons.Default.ContentCopy, onCopy)
            EditorToolButton("editor_action_paste", stringResource(com.clipnest.R.string.paste), Icons.Default.ContentPaste, onPaste)
            EditorToolButton("editor_action_select_all", stringResource(com.clipnest.R.string.select_all), Icons.Default.SelectAll, onSelectAll)
            EditorToolButton("editor_action_delete", stringResource(com.clipnest.R.string.delete_selected), Icons.Default.Delete, onDelete)
            EditorToolButton("editor_action_undo", stringResource(com.clipnest.R.string.undo), Icons.AutoMirrored.Filled.Undo, onUndo, enabled = contentToolsEnabled, tint = if (contentToolsEnabled) MaterialTheme.colorScheme.primary else disabledTint)
            EditorToolButton("editor_action_redo", stringResource(com.clipnest.R.string.redo), Icons.AutoMirrored.Filled.Redo, onRedo, enabled = contentToolsEnabled, tint = if (contentToolsEnabled) MaterialTheme.colorScheme.primary else disabledTint)
            EditorToolButton("markdown_action_view", if (isPreviewVisible) stringResource(com.clipnest.R.string.hide_markdown_preview) else stringResource(com.clipnest.R.string.show_markdown_preview), if (isPreviewVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, onTogglePreview, enabled = contentToolsEnabled, tint = if (contentToolsEnabled) MaterialTheme.colorScheme.primary else disabledTint)
            MarkdownTextButton("markdown_action_h1", "H1", stringResource(com.clipnest.R.string.markdown_h1), enabled = contentToolsEnabled) { onHeading(1) }
            MarkdownTextButton("markdown_action_h2", "H2", stringResource(com.clipnest.R.string.markdown_h2), enabled = contentToolsEnabled) { onHeading(2) }
            MarkdownTextButton("markdown_action_h3", "H3", stringResource(com.clipnest.R.string.markdown_h3), enabled = contentToolsEnabled) { onHeading(3) }
            MarkdownTextButton("markdown_action_bold", "B", stringResource(com.clipnest.R.string.markdown_bold), bold = true, enabled = contentToolsEnabled, onClick = onBold)
            MarkdownTextButton("markdown_action_italic", "I", stringResource(com.clipnest.R.string.markdown_italic), italic = true, enabled = contentToolsEnabled, onClick = onItalic)
            MarkdownTextButton("markdown_action_quote", "❝", stringResource(com.clipnest.R.string.markdown_quote), enabled = contentToolsEnabled, onClick = onQuote)
            MarkdownTextButton("markdown_action_code", "</>", stringResource(com.clipnest.R.string.markdown_code), enabled = contentToolsEnabled, onClick = onCode)
            MarkdownTextButton("markdown_action_bullets", "•", stringResource(com.clipnest.R.string.markdown_bullets), enabled = contentToolsEnabled, onClick = onBullets)
            MarkdownTextButton("markdown_action_numbers", "1.", stringResource(com.clipnest.R.string.markdown_numbers), enabled = contentToolsEnabled, onClick = onNumbers)
            MarkdownTextButton("markdown_action_rule", "—", stringResource(com.clipnest.R.string.markdown_horizontal_rule), enabled = contentToolsEnabled, onClick = onHorizontalRule)
        }
    }
}

@Composable
private fun MarkdownTextButton(tag: String, label: String, description: String, bold: Boolean = false, italic: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val contentColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    Surface(color = Color.Transparent, contentColor = contentColor, shape = RoundedCornerShape(6.dp), modifier = Modifier.size(48.dp).testTag(tag).semantics { role = Role.Button; contentDescription = description }) {
        Box(Modifier.fillMaxSize().clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelMedium.copy(color = contentColor, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal))
        }
    }
}

@Composable
private fun EditorToolButton(tag: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, enabled: Boolean = true, tint: Color = MaterialTheme.colorScheme.primary) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp).testTag(tag).semantics { role = Role.Button; contentDescription = description }) { Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun MarkdownPreviewDialog(html: String, headings: List<MarkdownHeading>, tocIndexing: Boolean, backgroundColor: Color, contentColor: Color, onDismiss: () -> Unit) {
    val surfaceColor = backgroundColor.toArgb()
    val shape = RoundedCornerShape(18.dp)
    val visibility = remember { MutableTransitionState(true) }
    // Thay vì một cờ true/false đơn thuần, ta lưu lại CHÍNH nội dung html mà
    // WebView đã thực sự báo là load xong. Nhờ vậy, nếu tín hiệu "đã xong"
    // đến muộn (của một html cũ, đã bị thay bằng html mới hơn) thì nó sẽ
    // không còn khớp với `html` hiện tại nữa và sẽ tự động bị bỏ qua, thay vì
    // âm thầm được hiểu nhầm là "html hiện tại cũng đã xong".
    var readyHtml by remember { mutableStateOf<String?>(null) }
    val ready = readyHtml == html
    var showToc by remember { mutableStateOf(false) }
    var pendingHeadingIndex by remember { mutableStateOf<Int?>(null) }
    // Lưới an toàn: nếu vì lý do gì đó (ví dụ WebView gặp trục trặc) mà
    // tín hiệu "đã xong" không bao giờ tới, shimmer sẽ không bị kẹt vĩnh
    // viễn - sau một khoảng thời gian hợp lý, ta tự coi như đã xong.
    LaunchedEffect(html) {
        delay(MARKDOWN_PREVIEW_READY_TIMEOUT_MS)
        if (readyHtml != html) readyHtml = html
    }
    fun dismiss() { if (visibility.targetState) visibility.targetState = false }
    LaunchedEffect(visibility.currentState, visibility.targetState) { if (!visibility.currentState && !visibility.targetState) onDismiss() }
    Dialog(onDismissRequest = ::dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true)) {
        AnimatedVisibility(visibleState = visibility, enter = fadeIn(tween(280)) + slideInVertically(tween(280), initialOffsetY = { -it / 6 }), exit = fadeOut(tween(220)) + slideOutVertically(tween(220), targetOffsetY = { -it / 6 })) {
            Surface(modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.88f).widthIn(max = 720.dp).shadow(24.dp, shape).clip(shape).testTag("markdown_preview_dialog"), shape = shape, color = backgroundColor, contentColor = contentColor) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().height(PREVIEW_HEADER_HEIGHT).padding(start = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (showToc) {
                            IconButton(onClick = { showToc = false }, Modifier.size(44.dp).testTag("markdown_preview_toc_back")) { Icon(Icons.Default.ArrowBack, "Back", tint = contentColor) }
                            Text("Table of Contents", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                        } else {
                            IconButton(onClick = { showToc = true }, Modifier.size(44.dp).testTag("markdown_preview_toc")) { Icon(Icons.Default.FormatListBulleted, "Table of contents", tint = contentColor) }
                            Text(stringResource(com.clipnest.R.string.preview_markdown), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                        }
                        IconButton(::dismiss, Modifier.size(44.dp).testTag("markdown_preview_close")) { Icon(Icons.Default.Close, stringResource(com.clipnest.R.string.close), tint = contentColor) }
                    }
                    HorizontalDivider(color = contentColor.copy(alpha = 0.18f))
                    Box(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
                        if (showToc) {
                            if (tocIndexing) Text("🔹Loading . . .", modifier = Modifier.fillMaxWidth().padding(20.dp), style = MaterialTheme.typography.bodyLarge)
                            else if (headings.isNotEmpty()) {
                                LazyColumn(Modifier.fillMaxSize().testTag("markdown_toc_list")) {
                                    items(headings, key = { it.index }) { heading -> Text(heading.title, modifier = Modifier.fillMaxWidth().clickable { pendingHeadingIndex = heading.index; showToc = false }.padding(start = ((heading.level - 1) * 18).dp, top = 10.dp, bottom = 10.dp, end = 8.dp), style = MaterialTheme.typography.bodyLarge) }
                                }
                            }
                        } else {
                            if (html.isNotBlank()) MarkdownPreviewWebView(html, surfaceColor, pendingHeadingIndex) { loadedHtml -> readyHtml = loadedHtml }
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
    AnimatedVisibility(visible, enter = fadeIn(tween(120)), exit = fadeOut(tween(180))) { Box(Modifier.fillMaxSize().background(backgroundColor)) { MarkdownPreviewShimmer(backgroundColor, contentColor) } }
}

@Composable
private fun MarkdownPreviewShimmer(backgroundColor: Color, contentColor: Color) {
    val transition = rememberInfiniteTransition(label = "markdown_preview_shimmer")
    val progress by transition.animateFloat(-1f, 2f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "markdown_preview_shimmer_progress")
    val base = contentColor.copy(alpha = .10f); val highlight = contentColor.copy(alpha = .20f)
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { MarkdownShimmerLine(progress, base, highlight, .72f); MarkdownShimmerLine(progress, base, highlight, .92f); MarkdownShimmerLine(progress, base, highlight, .58f); Spacer(Modifier.weight(1f)) }
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
