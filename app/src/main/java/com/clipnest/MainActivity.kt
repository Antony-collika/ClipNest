package com.clipnest

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.clipnest.data.local.EditorTextSize
import com.clipnest.data.local.ViewerTextSize
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
import androidx.compose.material3.CircularProgressIndicator
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
import com.clipnest.data.local.AppDatabase
import com.clipnest.data.local.FileManager
import com.clipnest.data.local.SettingsDataStore
import com.clipnest.data.repository.ClipboardRepositoryImpl
import com.clipnest.data.repository.AiRepository
import com.clipnest.data.repository.AskAiCoordinator
import com.clipnest.data.ai.AiApi
import com.clipnest.data.repository.VaultBackupCodec
import com.clipnest.service.CaptureNotificationManager
import com.clipnest.ui.editor.EditorScreen
import com.clipnest.ui.editor.EditorViewModel
import com.clipnest.ui.editor.EditorViewModelFactory
import com.clipnest.ui.localization.withAppLanguage
import com.clipnest.ui.navigation.Screen
import com.clipnest.ui.settings.SettingsScreen
import com.clipnest.ui.settings.SettingsViewModel
import com.clipnest.ui.settings.SettingsViewModelFactory
import com.clipnest.ui.theme.ClipNestTheme
import com.clipnest.ui.vault.BackupPasswordDialog
import com.clipnest.ui.vault.VaultScreen
import com.clipnest.ui.vault.VaultViewModel
import com.clipnest.ui.vault.VaultViewModelFactory
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class BackupPasswordAction { EXPORT, IMPORT }

@SuppressLint("InvalidFragmentVersionForActivityResult")
class MainActivity : ComponentActivity() {
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var fileManager: FileManager
    private lateinit var database: AppDatabase
    private lateinit var repository: ClipboardRepositoryImpl

    @SuppressLint("InvalidFragmentVersionForActivityResult")
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) CaptureNotificationManager.showCaptureNotification(applicationContext)
    }
    private var pendingFolderSelection: ((Uri) -> Unit)? = null
    private var pendingOpenFileSelection: ((Uri) -> Unit)? = null
    private var pendingBackupPassword: String? = null
    private val backupPasswordRequest = mutableStateOf<BackupPasswordAction?>(null)
    private val incomingOpenRequest = MutableStateFlow<IncomingOpenRequest?>(null)
    private val folderPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val callback = pendingFolderSelection
        pendingFolderSelection = null
        if (uri != null) callback?.invoke(uri)
    }
    private val openFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val callback = pendingOpenFileSelection
            pendingOpenFileSelection = null
            callback?.invoke(uri)
        }
    }
    private val backupFileLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val password = pendingBackupPassword
        pendingBackupPassword = null
        if (uri != null && password != null) writeVaultBackup(uri, password)
    }
    private val restoreFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val password = pendingBackupPassword
        pendingBackupPassword = null
        if (uri != null && password != null) readVaultBackup(uri, password)
    }
    private val vaultViewModel: VaultViewModel by viewModels { VaultViewModelFactory(repository, settingsDataStore, fileManager, applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        settingsDataStore = SettingsDataStore(applicationContext)
        fileManager = FileManager(applicationContext)
        database = AppDatabase.getInstance(applicationContext)
        repository = ClipboardRepositoryImpl(database.clipboardDao())
        CaptureNotificationManager.createNotificationChannel(applicationContext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else CaptureNotificationManager.showCaptureNotification(applicationContext)
        handleIntent(intent)
        setContent {
            val userSettings by settingsDataStore.userSettingsFlow.collectAsStateWithLifecycle(initialValue = com.clipnest.data.local.UserSettings())
            androidx.compose.runtime.LaunchedEffect(userSettings.notificationEnabled) {
                if (userSettings.notificationEnabled) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) CaptureNotificationManager.showCaptureNotification(applicationContext, userSettings.language)
                } else CaptureNotificationManager.dismissCaptureNotification(applicationContext)
            }
            val localizedContext = LocalContext.current.withAppLanguage(userSettings.language)
            val incomingRequest by incomingOpenRequest.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalContext provides localizedContext) {
                ClipNestTheme(themePreset = userSettings.themePreset) {
                    MainAppContent(
                        vaultViewModel = vaultViewModel,
                        editorViewModelFactory = EditorViewModelFactory(fileManager, settingsDataStore, applicationContext),
                        settingsViewModelFactory = SettingsViewModelFactory(settingsDataStore, repository, fileManager, applicationContext),
                        editorTextSize = userSettings.editorTextSize,
                        viewerTextSize = userSettings.viewerTextSize,
                        onRequestFolder = ::requestFolderSelection,
                        onRequestOpenFile = ::requestOpenFile,
                        onShareText = ::shareTextExternally,
                        incomingOpenRequest = incomingRequest,
                        onIncomingOpenRequestHandled = { incomingOpenRequest.value = null },
                        onRequestBackup = ::requestBackupFile,
                        onRequestRestore = ::requestRestoreFile
                    )
                    backupPasswordRequest.value?.let { action ->
                        BackupPasswordDialog(
                            action = action,
                            onDismiss = { backupPasswordRequest.value = null },
                            onConfirm = { password ->
                                backupPasswordRequest.value = null
                                pendingBackupPassword = password
                                if (action == BackupPasswordAction.EXPORT) launchBackupFilePicker() else launchRestoreFilePicker()
                            }
                        )
                    }
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleIntent(intent) }
    private fun requestFolderSelection(onSelected: (Uri) -> Unit) { pendingFolderSelection = onSelected; folderPickerLauncher.launch(null) }
    private fun requestOpenFile(onSelected: (Uri) -> Unit) { pendingOpenFileSelection = onSelected; openFileLauncher.launch(arrayOf("text/plain", "text/markdown", "text/x-markdown", "text/plain+md", "text/*", "application/octet-stream")) }
    private fun requestBackupFile() { backupPasswordRequest.value = BackupPasswordAction.EXPORT }
    private fun requestRestoreFile() { backupPasswordRequest.value = BackupPasswordAction.IMPORT }
    private fun launchBackupFilePicker() { val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()); backupFileLauncher.launch("ClipNest_Backup_$date.clipnest.json") }
    private fun launchRestoreFilePicker() { restoreFileLauncher.launch(arrayOf("application/json", "text/json", "*/*")) }
    private fun writeVaultBackup(uri: Uri, password: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching { val json = VaultBackupCodec.encodeEncrypted(repository.getAllCards(), password); contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) } ?: error("Unable to open backup destination") }
            val language = settingsDataStore.userSettingsFlow.first().language
            val message = applicationContext.withAppLanguage(language).getString(if (result.isSuccess) com.clipnest.R.string.backup_saved else com.clipnest.R.string.backup_failed)
            withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show() }
        }
    }
    private fun readVaultBackup(uri: Uri, password: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching { val json = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("Unable to open backup source"); val backup = VaultBackupCodec.decodeEncrypted(json, password); repository.mergeBackupCards(backup.cards) }
            val language = settingsDataStore.userSettingsFlow.first().language
            val message = applicationContext.withAppLanguage(language).getString(
                when {
                    result.isSuccess && result.getOrNull()?.imported == 0 && result.getOrNull()?.skippedDuplicates == 0 -> com.clipnest.R.string.restore_empty
                    result.isSuccess -> com.clipnest.R.string.restore_complete
                    result.exceptionOrNull() is IllegalArgumentException || result.exceptionOrNull() is com.squareup.moshi.JsonDataException || result.exceptionOrNull() is com.squareup.moshi.JsonEncodingException -> com.clipnest.R.string.invalid_backup_file
                    else -> com.clipnest.R.string.restore_failed
                }, result.getOrNull()?.imported ?: 0, result.getOrNull()?.skippedDuplicates ?: 0)
            withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show() }
        }
    }
    private fun shareTextExternally(text: String, chooserTitle: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
        runCatching { startActivity(Intent.createChooser(sendIntent, chooserTitle)) }.onFailure { Toast.makeText(this, getString(com.clipnest.R.string.share_unavailable), Toast.LENGTH_SHORT).show() }
    }
    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(CaptureNotificationManager.EXTRA_OPEN_CAPTURE, false) == true) vaultViewModel.openInAppCapture()
        val action = intent?.action ?: return
        val isOpenAction = action == Intent.ACTION_VIEW || action == Intent.ACTION_EDIT || action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE
        if (!isOpenAction) return
        val candidates = resolveIncomingDocumentUris(intent)
        if (action == Intent.ACTION_SEND_MULTIPLE && candidates.isEmpty()) { Log.w("ClipNest.OpenWith", "Multiple-send intent did not contain document URIs"); return }
        val resolvedUri = candidates.firstOrNull()
        val uri = resolvedUri?.uri
        Log.d("ClipNest.OpenWith", "action=$action, type=${intent.type}, data=${intent.data}, clipData=${intent.clipData?.itemCount}, uri=$uri, source=${resolvedUri?.source}, sourceItemCount=${resolvedUri?.itemCount}, candidateSources=${candidates.joinToString(",") { it.source.name }}, flags=0x${intent.flags.toString(16)}")
        if (uri == null) { Log.w("ClipNest.OpenWith", "Open intent did not contain a supported document URI"); return }
        val grantedFlags = intent.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        if (uri.scheme.equals(ContentResolver.SCHEME_CONTENT, ignoreCase = true) && grantedFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0) Log.w("ClipNest.OpenWith", "Content URI has no explicit read grant: $uri")
        if (grantedFlags != 0 && intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0) runCatching { contentResolver.takePersistableUriPermission(uri, grantedFlags) }.onFailure { error -> Log.d("ClipNest.OpenWith", "Persistable permission unavailable for $uri", error) }
        val streamInfo = inspectIncomingExtraStream(intent)
        val openContext = ExternalDocumentOpenContext(action = action, mimeType = intent.type, source = resolvedUri?.source ?: IncomingUriSource.DATA, clipDataItemCount = intent.clipData?.itemCount ?: 0, payloadItemCount = streamInfo.documentUriCount, extraStreamPresent = streamInfo.present, extraStreamValueType = streamInfo.valueType, flags = intent.flags, hasReadGrant = grantedFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0, hasPersistableGrant = intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0)
        incomingOpenRequest.value = IncomingOpenRequest(uri, openContext, candidates)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(
    vaultViewModel: VaultViewModel,
    editorViewModelFactory: ViewModelProvider.Factory,
    settingsViewModelFactory: ViewModelProvider.Factory,
    editorTextSize: EditorTextSize,
    viewerTextSize: ViewerTextSize,
    onRequestFolder: (((Uri) -> Unit) -> Unit),
    onRequestOpenFile: ((Uri) -> Unit) -> Unit,
    onShareText: (String, String) -> Unit,
    incomingOpenRequest: IncomingOpenRequest?,
    onIncomingOpenRequestHandled: () -> Unit,
    onRequestBackup: () -> Unit,
    onRequestRestore: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val context = LocalContext.current
    val askAiCoordinator = remember { AskAiCoordinator(aiRepository = AiRepository(AiApi(BuildConfig.AI_API_BASE_URL)), clipboardRepository = ClipboardRepositoryImpl(AppDatabase.getInstance(context).clipboardDao())) }
    var askAiInProgress by remember { mutableStateOf(false) }
    val vaultState by vaultViewModel.uiState.collectAsStateWithLifecycle()
    val editorViewModel: EditorViewModel = viewModel(factory = editorViewModelFactory)
    val editorSearchOpen = editorViewModel.isSearchOpen.collectAsStateWithLifecycle().value
    val editorSearchQuery = editorViewModel.searchQuery.collectAsStateWithLifecycle().value
    val editorReplaceQuery = editorViewModel.replaceQuery.collectAsStateWithLifecycle().value
    val editorSearchMatchCount = editorViewModel.searchMatchCount.collectAsStateWithLifecycle().value
    val editorActiveSearchMatch = editorViewModel.activeSearchMatch.collectAsStateWithLifecycle().value
    val editorUiState by editorViewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = 0, pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val isSettings = currentRoute == Screen.Settings.route
    val isEditorTab = !isSettings && pagerState.currentPage == 1
    val selectedTab = pagerState.currentPage.coerceIn(0, 1)
    val selectedCards = vaultState.cards.filter { vaultState.selectedIds.contains(it.id) }
    val visibleSelectedCount = selectedCards.size
    val allSelected = vaultState.cards.isNotEmpty() && visibleSelectedCount == vaultState.cards.size
    LaunchedEffect(incomingOpenRequest) {
        incomingOpenRequest?.let { request ->
            editorViewModel.openExternalDocument(request.uri, context.contentResolver, request.openContext, request.candidates)
            pagerState.animateScrollToPage(1)
            onIncomingOpenRequestHandled()
        }
    }
    fun openExternalFile() { onRequestOpenFile { uri -> editorViewModel.openExternalDocument(uri, context.contentResolver); scope.launch { pagerState.animateScrollToPage(1) } } }
    fun openEditorFromVault() {
        val navigate = { vaultViewModel.copySelectedCardsThenOpenEditor(context) { scope.launch { pagerState.animateScrollToPage(1) } } }
        if (editorUiState.externalDocumentUri != null) editorViewModel.returnToInternalEditor(context.contentResolver, onComplete = navigate) else navigate()
    }
    Scaffold(
        topBar = {
            MainTopBar(
                title = when { isSettings -> stringResource(com.clipnest.R.string.settings); selectedTab == 0 -> stringResource(com.clipnest.R.string.vault); else -> if (editorUiState.externalDocumentUri != null) editorUiState.documentName else stringResource(com.clipnest.R.string.editor) },
                isVault = !isSettings && selectedTab == 0,
                isEditor = !isSettings && selectedTab == 1,
                selectedCount = if (isSettings) 0 else vaultState.selectedIds.size,
                allSelected = allSelected,
                allSelectedPinned = !isSettings && selectedCards.isNotEmpty() && selectedCards.all { it.pinned },
                isSettings = isSettings,
                showPinnedFirst = vaultState.userSettings.showPinnedFirst,
                isSearchOpen = if (isEditorTab) editorSearchOpen else vaultState.isSearchOpen,
                searchQuery = if (isEditorTab) editorSearchQuery else vaultState.searchQuery,
                searchPlaceholder = if (isEditorTab) stringResource(com.clipnest.R.string.search_editor) else stringResource(com.clipnest.R.string.search_vault),
                editorDocumentName = if (editorUiState.externalDocumentUri != null) editorUiState.documentName else stringResource(com.clipnest.R.string.editor),
                editorSearchMatchCount = editorSearchMatchCount,
                editorActiveSearchMatch = editorActiveSearchMatch,
                editorReplaceQuery = editorReplaceQuery,
                onPreviousSearchMatch = editorViewModel::previousSearchMatch,
                onNextSearchMatch = editorViewModel::nextSearchMatch,
                onReplaceQueryChange = editorViewModel::setReplaceQuery,
                onReplaceCurrentMatch = editorViewModel::replaceCurrentMatch,
                onReplaceAllMatches = editorViewModel::replaceAllMatches,
                onSearchOpen = if (isEditorTab) editorViewModel::openSearchPublic else vaultViewModel::openSearch,
                onSearchClose = if (isEditorTab) editorViewModel::closeSearch else vaultViewModel::closeSearch,
                onSearchQueryChange = if (isEditorTab) editorViewModel::setSearchQuery else vaultViewModel::setSearchQuery,
                onToggleSelectAll = { if (allSelected) vaultViewModel.clearSelection() else vaultViewModel.selectAll() },
                onShareSelected = { vaultViewModel.shareSelected() },
                onSaveFile = vaultViewModel::openExportDialog,
                onEditorSave = { editorViewModel.onSaveClicked(context.contentResolver) },
                onAskAi = {
                    if (!askAiInProgress) {
                        val content = editorUiState.content.text
                        if (content.isBlank()) Toast.makeText(context, context.getString(com.clipnest.R.string.ask_ai_empty), Toast.LENGTH_SHORT).show()
                        else {
                            askAiInProgress = true
                            Toast.makeText(context, context.getString(com.clipnest.R.string.ask_ai_sent), Toast.LENGTH_SHORT).show()
                            scope.launch {
                                val result = askAiCoordinator.askAndSave(content)
                                askAiInProgress = false
                                Toast.makeText(context, if (result.isSuccess) context.getString(com.clipnest.R.string.ask_ai_saved) else context.getString(com.clipnest.R.string.ask_ai_failed), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                isAskAiInProgress = askAiInProgress,
                onOpenFile = ::openExternalFile,
                onReturnToEditor = { editorViewModel.returnToInternalEditor(context.contentResolver) },
                isExternalDocument = editorUiState.externalDocumentUri != null,
                onOpenEditor = ::openEditorFromVault,
                onToggleShowPinnedFirst = vaultViewModel::toggleShowPinnedFirst,
                onPinSelected = vaultViewModel::togglePinSelected,
                onCopySelected = { vaultViewModel.copySelectedCards(context) },
                onDeleteSelected = vaultViewModel::requestDeleteSelected,
                onOpenSettings = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } },
                onTabSelected = { page -> scope.launch { pagerState.animateScrollToPage(page, animationSpec = tween(durationMillis = 180)) } }
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        NavHost(navController = navController, startDestination = Screen.Vault.route, modifier = Modifier.padding(innerPadding)) {
            composable(Screen.Vault.route) {
                androidx.compose.foundation.pager.HorizontalPager(state = pagerState, beyondViewportPageCount = 1, modifier = Modifier.fillMaxSize().testTag("main_content_pager")) { page ->
                    when (page) {
                        0 -> VaultScreen(
                            viewModel = vaultViewModel,
                            onOpenEditor = ::openEditorFromVault,
                            onShareText = onShareText,
                            onRequestExportFolder = { onRequestFolder { uri -> vaultViewModel.setExportFolder(uri, context.contentResolver) } },
                            modifier = Modifier.fillMaxSize()
                        )
                        1 -> EditorScreen(
                            viewModel = editorViewModel,
                            editorTextSize = editorTextSize,
                            viewerTextSize = viewerTextSize,
                            onRequestSaveFolder = { onRequestFolder { uri -> editorViewModel.setDefaultSaveFolder(uri, context.contentResolver) } },
                            onRequestOpenFile = ::openExternalFile,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            composable(Screen.Settings.route) {
                val settingsViewModel: SettingsViewModel = viewModel(factory = settingsViewModelFactory)
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onRequestSaveFolder = { onRequestFolder { uri -> settingsViewModel.setDefaultSaveFolder(uri, context.contentResolver) } },
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
    editorReplaceQuery: String,
    onPreviousSearchMatch: () -> Unit,
    onNextSearchMatch: () -> Unit,
    onReplaceQueryChange: (String) -> Unit,
    onReplaceCurrentMatch: () -> Unit,
    onReplaceAllMatches: () -> Unit,
    onSearchOpen: () -> Unit,
    onSearchClose: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleSelectAll: () -> Unit,
    onShareSelected: () -> Unit,
    onSaveFile: () -> Unit,
    onEditorSave: () -> Unit,
    onAskAi: () -> Unit,
    isAskAiInProgress: Boolean,
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
        if (isSearchOpen) { searchFocusRequester.requestFocus(); keyboardController?.show() }
    }
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().height(if (isSearchOpen && isEditor) 104.dp else 52.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                when {
                    isSearchOpen -> {
                        if (isEditor) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
                                        modifier = Modifier.weight(1f).focusRequester(searchFocusRequester).testTag("main_search_input")
                                    )
                                    Text(if (editorSearchMatchCount == 0) "0/0" else "${editorActiveSearchMatch + 1}/$editorSearchMatchCount", style = MaterialTheme.typography.labelMedium, maxLines = 1, modifier = Modifier.testTag("editor_search_match_count"))
                                    IconButton(onClick = onPreviousSearchMatch, modifier = Modifier.size(36.dp).testTag("editor_search_previous")) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(com.clipnest.R.string.previous_match)) }
                                    IconButton(onClick = onNextSearchMatch, modifier = Modifier.size(36.dp).testTag("editor_search_next")) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(com.clipnest.R.string.next_match)) }
                                }
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    TextField(
                                        value = editorReplaceQuery,
                                        onValueChange = onReplaceQueryChange,
                                        placeholder = { Text(stringResource(com.clipnest.R.string.replace_hint)) },
                                        singleLine = true,
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                                        ),
                                        modifier = Modifier.weight(1f).testTag("editor_replace_input")
                                    )
                                    TextButton(onClick = onReplaceCurrentMatch, modifier = Modifier.testTag("editor_replace_button")) { Text(stringResource(com.clipnest.R.string.replace)) }
                                    TextButton(onClick = onReplaceAllMatches, modifier = Modifier.testTag("editor_replace_all_button")) { Text(stringResource(com.clipnest.R.string.replace_all)) }
                                }
                            }
                        } else {
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
                                modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester).testTag("main_search_input")
                            )
                        }
                    }
                    isVault && selectedCount > 0 -> {
                        MainTabSlot(selected = true, onClick = { onTabSelected(0) }, modifier = Modifier.fillMaxWidth().testTag("main_tab_vault")) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                                VaultSelectionCheckbox(checked = allSelected, onClick = onToggleSelectAll, modifier = Modifier.testTag("vault_select_all_checkbox"))
                                Text(text = stringResource(com.clipnest.R.string.selected_count, selectedCount), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, modifier = Modifier.testTag("vault_selected_count_text"))
                            }
                        }
                    }
                    isVault || isEditor -> {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            MainTabSlot(selected = isVault, onClick = { onTabSelected(0) }, modifier = Modifier.weight(1f).testTag("main_tab_vault")) { Text(stringResource(com.clipnest.R.string.vault), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) }
                            MainTabSlot(selected = isEditor, onClick = { onTabSelected(1) }, modifier = Modifier.weight(1f).testTag("main_tab_editor")) { Text(editorDocumentName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1) }
                        }
                    }
                    else -> Text(text = title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(start = 16.dp).testTag("main_title"))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                if (isAskAiInProgress) CircularProgressIndicator(modifier = Modifier.size(22.dp).testTag("ask_ai_loading_indicator"), strokeWidth = 2.dp)
                if (isVault && selectedCount > 0 && !isSearchOpen) {
                    IconButton(onClick = onPinSelected, modifier = Modifier.size(36.dp).testTag("vault_action_pin_direct")) { Icon(if (allSelectedPinned) Icons.Outlined.PushPin else Icons.Default.PushPin, contentDescription = stringResource(if (allSelectedPinned) com.clipnest.R.string.unpin_selected else com.clipnest.R.string.pin_selected), modifier = Modifier.size(22.dp)) }
                    IconButton(onClick = onCopySelected, modifier = Modifier.size(36.dp).testTag("vault_action_copy")) { Icon(Icons.Default.ContentCopy, contentDescription = stringResource(com.clipnest.R.string.copy_selected), modifier = Modifier.size(22.dp)) }
                    IconButton(onClick = onDeleteSelected, modifier = Modifier.size(36.dp).testTag("vault_action_delete")) { Icon(Icons.Default.Delete, contentDescription = stringResource(com.clipnest.R.string.delete_selected), modifier = Modifier.size(22.dp)) }
                }
                if (!isSettings) {
                    if (isSearchOpen) {
                        IconButton(onClick = onSearchClose, modifier = Modifier.size(36.dp).testTag("main_close_search_button")) { Icon(Icons.Default.Close, contentDescription = stringResource(com.clipnest.R.string.close_search), modifier = Modifier.size(22.dp)) }
                    } else {
                        IconButton(onClick = onSearchOpen, modifier = Modifier.size(36.dp).testTag("main_search_button")) { Icon(Icons.Default.Search, contentDescription = stringResource(com.clipnest.R.string.search), modifier = Modifier.size(22.dp)) }
                    }
                    Box {
                        IconButton(onClick = { overflowExpanded = true }, modifier = Modifier.size(36.dp).testTag("main_overflow_button")) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(com.clipnest.R.string.more_options), modifier = Modifier.size(22.dp)) }
                        if (overflowExpanded) {
                            DropdownMenu(expanded = overflowExpanded, onDismissRequest = { overflowExpanded = false }, containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp, shadowElevation = 2.dp, modifier = Modifier.testTag("main_overflow_menu")) {
                                if (isVault) {
                                    DropdownMenuItem(text = { Text(if (allSelected) stringResource(com.clipnest.R.string.clear_selection) else stringResource(com.clipnest.R.string.select_all)) }, onClick = { overflowExpanded = false; onToggleSelectAll() }, modifier = Modifier.testTag("main_menu_select_all"))
                                    DropdownMenuItem(text = { Text(stringResource(com.clipnest.R.string.share)) }, enabled = selectedCount > 0, onClick = { overflowExpanded = false; onShareSelected() }, modifier = Modifier.testTag("main_menu_share"))
                                    DropdownMenuItem(text = { Text(stringResource(com.clipnest.R.string.save_file)) }, enabled = selectedCount > 0, onClick = { overflowExpanded = false; onSaveFile() }, modifier = Modifier.testTag("main_menu_save_file"))
                                    DropdownMenuItem(text = { Text(stringResource(com.clipnest.R.string.open_editor)) }, enabled = selectedCount > 0, onClick = { overflowExpanded = false; onOpenEditor() }, modifier = Modifier.testTag("main_menu_open_editor"))
                                    DropdownMenuItem(text = { Text(stringResource(com.clipnest.R.string.show_pinned_first)) }, trailingIcon = { if (showPinnedFirst) Icon(Icons.Default.Check, contentDescription = stringResource(com.clipnest.R.string.active)) }, onClick = { overflowExpanded = false; onToggleShowPinnedFirst() }, modifier = Modifier.testTag("main_menu_show_pinned_first"))
                                    DropdownMenuItem(text = { Text(stringResource(com.clipnest.R.string.settings)) }, onClick = { overflowExpanded = false; onOpenSettings() }, modifier = Modifier.testTag("main_menu_settings"))
                                } else {
                                    DropdownMenuItem(text = { Text(stringResource(com.clipnest.R.string.open_file)) }, onClick = { overflowExpanded = false; onOpenFile() }, modifier = Modifier.testTag("editor_menu_open_file"))
                                    DropdownMenuItem(text = { Text(stringResource(com.clipnest.R.string.return_to_editor)) }, enabled = isExternalDocument, onClick = { overflowExpanded = false; onReturnToEditor() }, modifier = Modifier.testTag("editor_menu_return_to_editor"))
                                    DropdownMenuItem(text = { Text(stringResource(com.clipnest.R.string.save_file)) }, onClick = { overflowExpanded = false; onEditorSave() }, modifier = Modifier.testTag("editor_menu_save_file"))
                                    DropdownMenuItem(text = { Text(stringResource(com.clipnest.R.string.ask_ai)) }, onClick = { overflowExpanded = false; onAskAi() }, modifier = Modifier.testTag("editor_menu_ask_ai"))
                                    DropdownMenuItem(text = { Text(stringResource(com.clipnest.R.string.settings)) }, onClick = { overflowExpanded = false; onOpenSettings() }, modifier = Modifier.testTag("main_menu_settings"))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainTabSlot(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier = modifier.fillMaxHeight().clickable(onClick = onClick).padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { content() }
        Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)))
    }
}

@Composable
private fun VaultSelectionCheckbox(checked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Checkbox(checked = checked, onCheckedChange = { onClick() }, colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary, uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant, checkmarkColor = MaterialTheme.colorScheme.onPrimary), modifier = modifier.size(32.dp))
}
