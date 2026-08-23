package com.example.ui.editor

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.NoteDocType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

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
                is EditorEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box {
                        Surface(
                            onClick = { viewModel.setDropdownExpanded(true) },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.testTag("editor_title_dropdown_trigger")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = if (uiState.activeDocType == NoteDocType.NOTE) Icons.Default.Description else Icons.Default.EditNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = uiState.activeDocType.displayName,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.testTag("editor_active_file_label")
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Switch file",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = uiState.isDropdownExpanded,
                            onDismissRequest = { viewModel.setDropdownExpanded(false) },
                            modifier = Modifier.testTag("editor_file_dropdown_menu")
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Note (Note.md)")
                                        if (uiState.activeDocType == NoteDocType.NOTE) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Description, contentDescription = null)
                                },
                                onClick = { viewModel.switchDocument(NoteDocType.NOTE) },
                                modifier = Modifier.testTag("editor_menu_select_note")
                            )

                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Draft (Draft.md)")
                                        if (uiState.activeDocType == NoteDocType.DRAFT) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.EditNote, contentDescription = null)
                                },
                                onClick = { viewModel.switchDocument(NoteDocType.DRAFT) },
                                modifier = Modifier.testTag("editor_menu_select_draft")
                            )
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = viewModel::onSaveClicked,
                            modifier = Modifier.testTag("editor_save_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Save document",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Draft Save Menu: Save Draft vs Save to new file
                        DropdownMenu(
                            expanded = uiState.showSaveMenu,
                            onDismissRequest = viewModel::dismissSaveMenu,
                            modifier = Modifier.testTag("editor_draft_save_menu")
                        ) {
                            DropdownMenuItem(
                                text = { Text("Save Draft") },
                                leadingIcon = {
                                    Icon(Icons.Default.Save, contentDescription = null)
                                },
                                onClick = viewModel::saveDraftDirectly,
                                modifier = Modifier.testTag("editor_menu_save_draft")
                            )

                            DropdownMenuItem(
                                text = { Text("Save to new file") },
                                leadingIcon = {
                                    Icon(Icons.Default.Description, contentDescription = null)
                                },
                                onClick = viewModel::openSaveNewFileDialog,
                                modifier = Modifier.testTag("editor_menu_save_to_new_file")
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            // Editor Toolbar with PASTE ACTION AT THE BEGINNING
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // PASTE ACTION: Always visible at the start of the editor action bar
                    FilledTonalButton(
                        onClick = { viewModel.pasteFromClipboard(context) },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("editor_action_paste")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Paste",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Paste",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Markdown helpers
                    IconButton(
                        onClick = { viewModel.insertMarkdownHelper("# ") },
                        modifier = Modifier.size(36.dp).testTag("editor_tool_h1")
                    ) {
                        Icon(Icons.Default.Title, contentDescription = "Heading", modifier = Modifier.size(18.dp))
                    }

                    IconButton(
                        onClick = { viewModel.insertMarkdownHelper("**", "**") },
                        modifier = Modifier.size(36.dp).testTag("editor_tool_bold")
                    ) {
                        Icon(Icons.Default.FormatBold, contentDescription = "Bold", modifier = Modifier.size(18.dp))
                    }

                    IconButton(
                        onClick = { viewModel.insertMarkdownHelper("*", "*") },
                        modifier = Modifier.size(36.dp).testTag("editor_tool_italic")
                    ) {
                        Icon(Icons.Default.FormatItalic, contentDescription = "Italic", modifier = Modifier.size(18.dp))
                    }

                    IconButton(
                        onClick = { viewModel.insertMarkdownHelper("- ") },
                        modifier = Modifier.size(36.dp).testTag("editor_tool_bullet")
                    ) {
                        Icon(Icons.Default.FormatListBulleted, contentDescription = "Bullet list", modifier = Modifier.size(18.dp))
                    }

                    IconButton(
                        onClick = { viewModel.insertMarkdownHelper("```\n", "\n```") },
                        modifier = Modifier.size(36.dp).testTag("editor_tool_code")
                    ) {
                        Text("</>", style = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp))
                    }

                    IconButton(
                        onClick = { viewModel.insertMarkdownHelper("- [ ] ") },
                        modifier = Modifier.size(36.dp).testTag("editor_tool_task")
                    ) {
                        Text("☑", style = TextStyle(fontSize = 16.sp))
                    }
                }
            }

            // Main Editor Multiline TextField
            TextField(
                value = uiState.content,
                onValueChange = viewModel::onContentChange,
                placeholder = {
                    Text(
                        text = "Write or paste Markdown content here...",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    )
                },
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("editor_text_input")
            )
        }
    }

    if (uiState.showSaveNewFileDialog) {
        SaveNewFileDialog(
            onDismiss = viewModel::dismissSaveNewFileDialog,
            onConfirm = viewModel::saveToNewFile
        )
    }
}
