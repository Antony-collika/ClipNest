package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.local.AppDatabase
import com.example.data.local.FileManager
import com.example.data.local.SettingsDataStore
import com.example.data.repository.ClipboardRepositoryImpl
import com.example.data.repository.VaultBackupCodec
import com.example.service.CaptureNotificationManager
import com.example.ui.editor.EditorScreen
import com.example.ui.editor.EditorViewModel
import com.example.ui.editor.EditorViewModelFactory
import com.example.ui.localization.withAppLanguage
import com.example.ui.navigation.Screen
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import com.example.ui.settings.SettingsViewModelFactory
import com.example.ui.theme.ClipboardManagerTheme
import com.example.ui.vault.VaultScreen
import com.example.ui.vault.VaultViewModel
import com.example.ui.vault.VaultViewModelFactory
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("InvalidFragmentVersionForActivityResult")
class MainActivity : ComponentActivity() {

    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var fileManager: FileManager
    private lateinit var database: AppDatabase
    private lateinit var repository: ClipboardRepositoryImpl

    @SuppressLint("InvalidFragmentVersionForActivityResult")
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) CaptureNotificationManager.showCaptureNotification(applicationContext)
    }

    private var pendingFolderSelection: ((Uri) -> Unit)? = null
    private var pendingOpenFileSelection: ((Uri) -> Unit)? = null
    private val incomingOpenUri = MutableStateFlow<Uri?>(null)
    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val callback = pendingFolderSelection
        pendingFolderSelection = null
        if (uri != null) callback?.invoke(uri)
    }

    private val openFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val callback = pendingOpenFileSelection
            pendingOpenFileSelection = null
            callback?.invoke(uri)
        }
    }

    private val backupFileLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) writeVaultBackup(uri)
    }

    private val restoreFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) readVaultBackup(uri)
    }

    private val vaultViewModel: VaultViewModel by viewModels {
        VaultViewModelFactory(repository, settingsDataStore, fileManager, applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        settingsDataStore = SettingsDataStore(applicationContext)
        fileManager = FileManager(applicationContext)
        database = AppDatabase.getInstance(applicationContext)
        repository = ClipboardRepositoryImpl(database.clipboardDao())

        CaptureNotificationManager.createNotificationChannel(applicationContext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            CaptureNotificationManager.showCaptureNotification(applicationContext)
        }

        handleIntent(intent)

        setContent {
            val userSettings by settingsDataStore.userSettingsFlow.collectAsStateWithLifecycle(
                initialValue = com.example.data.local.UserSettings()
            )
            androidx.compose.runtime.LaunchedEffect(userSettings.notificationEnabled) {
                if (userSettings.notificationEnabled) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    ) {
                        CaptureNotificationManager.showCaptureNotification(applicationContext, userSettings.language)
                    }
                } else {
                    CaptureNotificationManager.dismissCaptureNotification(applicationContext)
                }
            }

            val localizedContext = LocalContext.current.withAppLanguage(userSettings.language)
            val incomingUri by incomingOpenUri.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalContext provides localizedContext) {
                ClipboardManagerTheme(
                    themeMode = userSettings.themeMode,
                    themePreset = userSettings.themePreset
                ) {
                    MainAppContent(
                        vaultViewModel = vaultViewModel,
                        editorViewModelFactory = EditorViewModelFactory(fileManager, settingsDataStore, applicationContext),
                        settingsViewModelFactory = SettingsViewModelFactory(settingsDataStore, repository, fileManager, applicationContext),
                        onRequestFolder = ::requestFolderSelection,
                        onRequestOpenFile = ::requestOpenFile,
                        onShareText = ::shareTextExternally,
                        incomingOpenUri = incomingUri,
                        onIncomingOpenUriHandled = { incomingOpenUri.value = null },
                        onRequestBackup = ::requestBackupFile,
                        onRequestRestore = ::requestRestoreFile
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun requestFolderSelection(onSelected: (Uri) -> Unit) {
        pendingFolderSelection = onSelected
        folderPickerLauncher.launch(null)
    }

    private fun requestOpenFile(onSelected: (Uri) -> Unit) {
        pendingOpenFileSelection = onSelected
        openFileLauncher.launch(arrayOf("text/plain", "text/markdown", "text/*", "application/octet-stream"))
    }

    private fun requestBackupFile() {
        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        backupFileLauncher.launch("XBoard_Backup_$date.json")
    }

    private fun requestRestoreFile() {
        restoreFileLauncher.launch(arrayOf("application/json", "text/json", "*/*"))
    }

    private fun writeVaultBackup(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val json = VaultBackupCodec.encode(repository.getAllCards())
                contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(json.toByteArray(Charsets.UTF_8))
                } ?: error("Unable to open backup destination")
            }
            val language = settingsDataStore.userSettingsFlow.first().language
            val message = applicationContext.withAppLanguage(language).getString(
                if (result.isSuccess) com.example.R.string.backup_saved else com.example.R.string.backup_failed
            )
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun readVaultBackup(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val json = contentResolver.openInputStream(uri)?.use { input ->
                    input.readBytes().toString(Charsets.UTF_8)
                } ?: error("Unable to open backup source")
                val backup = VaultBackupCodec.decode(json)
                repository.mergeBackupCards(backup.cards)
            }
            val language = settingsDataStore.userSettingsFlow.first().language
            val message = applicationContext.withAppLanguage(language).getString(
                when {
                    result.isSuccess &&
                        result.getOrNull()?.imported == 0 &&
                        result.getOrNull()?.skippedDuplicates == 0 -> com.example.R.string.restore_empty
                    result.isSuccess -> com.example.R.string.restore_complete
                    result.exceptionOrNull() is IllegalArgumentException ||
                        result.exceptionOrNull() is com.squareup.moshi.JsonDataException ||
                        result.exceptionOrNull() is com.squareup.moshi.JsonEncodingException -> com.example.R.string.invalid_backup_file
                    else -> com.example.R.string.restore_failed
                },
                result.getOrNull()?.imported ?: 0,
                result.getOrNull()?.skippedDuplicates ?: 0
            )
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun shareTextExternally(text: String, chooserTitle: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooserIntent = Intent.createChooser(sendIntent, chooserTitle)
        runCatching {
            startActivity(chooserIntent)
        }.onFailure {
            Toast.makeText(
                this,
                getString(com.example.R.string.share_unavailable),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    @SuppressLint("WrongConstant")
    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(CaptureNotificationManager.EXTRA_OPEN_CAPTURE, false) == true) {
            vaultViewModel.openInAppCapture()
        }
        if (intent?.action == Intent.ACTION_VIEW || intent?.action == Intent.ACTION_EDIT) {
            val clipUri = intent.clipData?.getItemAt(0)?.uri
            val uri = intent.data ?: clipUri
            Log.d(
                "XBoard.OpenWith",
                "action=${intent.action}, type=${intent.type}, data=${intent.data}, " +
                    "clipData=$clipUri, flags=0x${intent.flags.toString(16)}"
            )
            if (uri != null) {
                val grantedFlags = intent.flags and
                    (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                if (grantedFlags != 0 &&
                    intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0
                ) {
                    runCatching { contentResolver.takePersistableUriPermission(uri, grantedFlags) }
                        .onFailure { error ->
                            Log.d("XBoard.OpenWith", "Persistable permission unavailable for $uri", error)
                        }
                }
                incomingOpenUri.value = uri
            } else {
                Log.w("XBoard.OpenWith", "Open intent did not contain a data or ClipData URI")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(
    vaultViewModel: VaultViewModel,
    editorViewModelFactory: ViewModelProvider.Factory,
    settingsViewModelFactory: ViewModelProvider.Factory,
    onRequestFolder: (((Uri) -> Unit) -> Unit),
    onRequestOpenFile: ((Uri) -> Unit) -> Unit,
    onShareText: (String, String) -> Unit,
    incomingOpenUri: Uri?,
    onIncomingOpenUriHandled: () -> Unit,
    onRequestBackup: () -> Unit,
    onRequestRestore: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val context = LocalContext.current
    val vaultState by vaultViewModel.uiState.collectAsStateWithLifecycle()
    val editorViewModel: EditorViewModel = viewModel(factory = editorViewModelFactory)
    val editorSearchOpen = editorViewModel.isSearchOpen.collectAsStateWithLifecycle().value
    val editorSearchQuery = editorViewModel.searchQuery.collectAsStateWithLifecycle().value
    val editorSearchMatchCount = editorViewModel.searchMatchCount.collectAsStateWithLifecycle().value
    val editorActiveSearchMatch = editorViewModel.activeSearchMatch.collectAsStateWithLifecycle().value
    val editorUiState by editorViewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = 0,
        pageCount = { 2 }
    )
    val scope = rememberCoroutineScope()
    val isSettings = currentRoute == Screen.Settings.route
    val isEditorTab = !isSettings && pagerState.currentPage == 1
    val selectedTab = pagerState.currentPage.coerceIn(0, 1)
    val selectedCards = vaultState.cards.filter { vaultState.selectedIds.contains(it.id) }
    val visibleSelectedCount = selectedCards.size
    val allSelected = vaultState.cards.isNotEmpty() && visibleSelectedCount == vaultState.cards.size

    LaunchedEffect(incomingOpenUri) {
        incomingOpenUri?.let { uri ->
            editorViewModel.openExternalDocument(uri, context.contentResolver)
            pagerState.animateScrollToPage(1)
            onIncomingOpenUriHandled()
        }
    }

    fun openExternalFile() {
        onRequestOpenFile { uri ->
            editorViewModel.openExternalDocument(uri, context.contentResolver)
            scope.launch { pagerState.animateScrollToPage(1) }
        }
    }

    fun openEditorFromVault() {
        val navigate = {
            vaultViewModel.copySelectedCardsThenOpenEditor(context) {
                scope.launch { pagerState.animateScrollToPage(1) }
            }
        }
        if (editorUiState.externalDocumentUri != null) {
            editorViewModel.returnToInternalEditor(context.contentResolver, onComplete = navigate)
        } else {
            navigate()
        }
    }

    Scaffold(
        topBar = {
                MainTopBar(
                    title = when {
                        isSettings -> stringResource(com.example.R.string.settings)
                        selectedTab == 0 -> stringResource(com.example.R.string.vault)
                        else -> if (editorUiState.externalDocumentUri != null) editorUiState.documentName else stringResource(com.example.R.string.editor)
                    },
                    isVault = !isSettings && selectedTab == 0,
                    isEditor = !isSettings && selectedTab == 1,
                    selectedCount = if (isSettings) 0 else vaultState.selectedIds.size,
                    allSelected = allSelected,
                    allSelectedPinned = !isSettings && selectedCards.isNotEmpty() && selectedCards.all { it.pinned },
                    isSettings = isSettings,
                    showPinnedFirst = vaultState.userSettings.showPinnedFirst,
                    isSearchOpen = if (isEditorTab) editorSearchOpen else vaultState.isSearchOpen,
                    searchQuery = if (isEditorTab) editorSearchQuery else vaultState.searchQuery,
                    searchPlaceholder = if (isEditorTab) stringResource(com.example.R.string.search_editor) else stringResource(com.example.R.string.search_vault),
                    editorDocumentName = if (editorUiState.externalDocumentUri != null) editorUiState.documentName else stringResource(com.example.R.string.editor),
                    editorSearchMatchCount = editorSearchMatchCount,
                    editorActiveSearchMatch = editorActiveSearchMatch,
                    onPreviousSearchMatch = editorViewModel::previousSearchMatch,
                    onNextSearchMatch = editorViewModel::nextSearchMatch,
                    onSearchOpen = if (isEditorTab) editorViewModel::openSearch else vaultViewModel::openSearch,
                    onSearchClose = if (isEditorTab) editorViewModel::closeSearch else vaultViewModel::closeSearch,
                    onSearchQueryChange = if (isEditorTab) editorViewModel::setSearchQuery else vaultViewModel::setSearchQuery,
                    onToggleSelectAll = {
                        if (allSelected) vaultViewModel.clearSelection() else vaultViewModel.selectAll()
                    },
                    onShareSelected = { vaultViewModel.shareSelected() },
                    onSaveFile = vaultViewModel::openExportDialog,
                    onEditorSave = { editorViewModel.onSaveClicked(context.contentResolver) },
                    onOpenFile = ::openExternalFile,
                    onReturnToEditor = { editorViewModel.returnToInternalEditor(context.contentResolver) },
                    isExternalDocument = editorUiState.externalDocumentUri != null,
                    onOpenEditor = ::openEditorFromVault,
                    onToggleShowPinnedFirst = vaultViewModel::toggleShowPinnedFirst,
                    onPinSelected = vaultViewModel::togglePinSelected,
                    onCopySelected = { vaultViewModel.copySelectedCards(context) },
                    onDeleteSelected = vaultViewModel::requestDeleteSelected,
                    onOpenSettings = {
                        navController.navigate(Screen.Settings.route) { launchSingleTop = true }
                    },
                    onTabSelected = { page ->
                        scope.launch {
                            pagerState.animateScrollToPage(page, animationSpec = tween(durationMillis = 180))
                        }
                    }
                )
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Vault.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Vault.route) {
                androidx.compose.foundation.pager.HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 0,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("main_content_pager")
                ) { page ->
                    when (page) {
                        0 -> VaultScreen(
                            viewModel = vaultViewModel,
                            onOpenEditor = ::openEditorFromVault,
                            onShareText = onShareText,
                            onRequestExportFolder = {
                                onRequestFolder { uri ->
                                    vaultViewModel.setExportFolder(uri, context.contentResolver)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        1 -> EditorScreen(
                            viewModel = editorViewModel,
                            onRequestSaveFolder = {
                                onRequestFolder { uri ->
                                    editorViewModel.setDefaultSaveFolder(uri, context.contentResolver)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            composable(Screen.Settings.route) {
                val settingsViewModel: SettingsViewModel = viewModel(factory = settingsViewModelFactory)
                        SettingsScreen(
                            viewModel = settingsViewModel,
                            onRequestSaveFolder = {
                                onRequestFolder { uri ->
                                    settingsViewModel.setDefaultSaveFolder(uri, context.contentResolver)
                                }
                            },
                            onRequestBackup = onRequestBackup,
                            onRequestRestore = onRequestRestore
                        )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar(
    title: String,
    isVault: Boolean,
    isEditor: Boolean,
    selectedCount: Int,
    allSelected: Boolean,
    allSelectedPinned: Boolean,
    isSettings: Boolean,
    showPinnedFirst: Boolean,
    isSearchOpen: Boolean,
    searchQuery: String,
    searchPlaceholder: String,
    editorDocumentName: String,
    editorSearchMatchCount: Int,
    editorActiveSearchMatch: Int,
    onPreviousSearchMatch: () -> Unit,
    onNextSearchMatch: () -> Unit,
    onSearchOpen: () -> Unit,
    onSearchClose: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleSelectAll: () -> Unit,
    onShareSelected: () -> Unit,
    onSaveFile: () -> Unit,
    onEditorSave: () -> Unit,
    onOpenFile: () -> Unit,
    onReturnToEditor: () -> Unit,
    isExternalDocument: Boolean,
    onOpenEditor: () -> Unit,
    onToggleShowPinnedFirst: () -> Unit,
    onPinSelected: () -> Unit,
    onCopySelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onOpenSettings: () -> Unit,
    onTabSelected: (Int) -> Unit
) {
    var overflowExpanded by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(isSearchOpen) {
        if (isSearchOpen) {
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(52.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterStart
            ) {
                when {
                    isSearchOpen -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChange,
                                placeholder = { Text(searchPlaceholder) },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(searchFocusRequester)
                                    .testTag("main_search_input")
                            )
                            if (isEditor) {
                                Text(
                                    text = if (editorSearchMatchCount == 0) "0/0" else "${editorActiveSearchMatch + 1}/$editorSearchMatchCount",
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    modifier = Modifier.testTag("editor_search_match_count")
                                )
                                IconButton(
                                    onClick = onPreviousSearchMatch,
                                    modifier = Modifier.size(36.dp).testTag("editor_search_previous")
                                ) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(com.example.R.string.previous_match))
                                }
                                IconButton(
                                    onClick = onNextSearchMatch,
                                    modifier = Modifier.size(36.dp).testTag("editor_search_next")
                                ) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(com.example.R.string.next_match))
                                }
                            }
                        }
                    }
                    isVault && selectedCount > 0 -> {
                        MainTabSlot(
                            selected = true,
                            onClick = { onTabSelected(0) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("main_tab_vault")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                VaultSelectionCheckbox(
                                    checked = allSelected,
                                    onClick = onToggleSelectAll,
                                    modifier = Modifier.testTag("vault_select_all_checkbox")
                                )
                                Text(
                                    text = stringResource(com.example.R.string.selected_count, selectedCount),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    modifier = Modifier.testTag("vault_selected_count_text")
                                )
                            }
                        }
                    }
                    isVault || isEditor -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MainTabSlot(
                                selected = isVault,
                                onClick = { onTabSelected(0) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("main_tab_vault")
                            ) {
                                Text(
                                    stringResource(com.example.R.string.vault),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                            MainTabSlot(
                                selected = isEditor,
                                onClick = { onTabSelected(1) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("main_tab_editor")
                            ) {
                                Text(
                                    editorDocumentName,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    else -> {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .testTag("main_title")
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                if (isVault && selectedCount > 0 && !isSearchOpen) {
                    IconButton(
                        onClick = onPinSelected,
                        modifier = Modifier.size(36.dp).testTag("vault_action_pin_direct")
                    ) {
                        Icon(
                            imageVector = if (allSelectedPinned) Icons.Outlined.PushPin else Icons.Default.PushPin,
                            contentDescription = stringResource(if (allSelectedPinned) com.example.R.string.unpin_selected else com.example.R.string.pin_selected),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(
                        onClick = onCopySelected,
                        modifier = Modifier.size(36.dp).testTag("vault_action_copy")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = stringResource(com.example.R.string.copy_selected), modifier = Modifier.size(22.dp))
                    }
                    IconButton(
                        onClick = onDeleteSelected,
                        modifier = Modifier.size(36.dp).testTag("vault_action_delete")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(com.example.R.string.delete_selected), modifier = Modifier.size(22.dp))
                    }
                }

                if (!isSettings) {
                    if (isSearchOpen) {
                        IconButton(
                            onClick = onSearchClose,
                            modifier = Modifier.size(36.dp).testTag("main_close_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(com.example.R.string.close_search),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onSearchOpen,
                            modifier = Modifier.size(36.dp).testTag("main_search_button")
                        ) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(com.example.R.string.search), modifier = Modifier.size(22.dp))
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { overflowExpanded = true },
                            modifier = Modifier.size(36.dp).testTag("main_overflow_button")
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(com.example.R.string.more_options), modifier = Modifier.size(22.dp))
                        }
                        DropdownMenu(
                            expanded = overflowExpanded,
                            onDismissRequest = { overflowExpanded = false },
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp,
                            shadowElevation = 2.dp,
                            modifier = Modifier.testTag("main_overflow_menu")
                        ) {
                    if (isVault) {
                        DropdownMenuItem(
                            text = { Text(if (allSelected) stringResource(com.example.R.string.clear_selection) else stringResource(com.example.R.string.select_all)) },
                            onClick = {
                                overflowExpanded = false
                                onToggleSelectAll()
                            },
                            modifier = Modifier.testTag("main_menu_select_all")
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(com.example.R.string.share)) },
                            enabled = selectedCount > 0,
                            onClick = {
                                overflowExpanded = false
                                onShareSelected()
                            },
                            modifier = Modifier.testTag("main_menu_share")
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(com.example.R.string.save_file)) },
                            enabled = selectedCount > 0,
                            onClick = {
                                overflowExpanded = false
                                onSaveFile()
                            },
                            modifier = Modifier.testTag("main_menu_save_file")
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(com.example.R.string.open_editor)) },
                            enabled = selectedCount > 0,
                            onClick = {
                                overflowExpanded = false
                                onOpenEditor()
                            },
                            modifier = Modifier.testTag("main_menu_open_editor")
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(com.example.R.string.show_pinned_first)) },
                            trailingIcon = {
                                if (showPinnedFirst) {
                                    Icon(Icons.Default.Check, contentDescription = stringResource(com.example.R.string.active))
                                }
                            },
                            onClick = {
                                overflowExpanded = false
                                onToggleShowPinnedFirst()
                            },
                            modifier = Modifier.testTag("main_menu_show_pinned_first")
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(com.example.R.string.settings)) },
                            onClick = {
                                overflowExpanded = false
                                onOpenSettings()
                            },
                            modifier = Modifier.testTag("main_menu_settings")
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text(stringResource(com.example.R.string.open_file)) },
                            onClick = {
                                overflowExpanded = false
                                onOpenFile()
                            },
                            modifier = Modifier.testTag("editor_menu_open_file")
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(com.example.R.string.return_to_editor)) },
                            enabled = isExternalDocument,
                            onClick = {
                                overflowExpanded = false
                                onReturnToEditor()
                            },
                            modifier = Modifier.testTag("editor_menu_return_to_editor")
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(com.example.R.string.save_file)) },
                            onClick = {
                                overflowExpanded = false
                                onEditorSave()
                            },
                            modifier = Modifier.testTag("editor_menu_save_file")
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(com.example.R.string.settings)) },
                            onClick = {
                                overflowExpanded = false
                                onOpenSettings()
                            },
                            modifier = Modifier.testTag("main_menu_settings")
                        )
                    }
                }
            }
        }
    }
}
}
}

@Composable
private fun MainTabSlot(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                )
        )
    }
}

@Composable
private fun VaultSelectionCheckbox(
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Checkbox(
        checked = checked,
        onCheckedChange = { onClick() },
        colors = CheckboxDefaults.colors(
            checkedColor = MaterialTheme.colorScheme.primary,
            uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
            checkmarkColor = MaterialTheme.colorScheme.onPrimary
        ),
        modifier = modifier.size(32.dp)
    )
}
