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
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
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
import com.clipnest.data.model.Note
import com.clipnest.data.model.VaultBackupNote
import com.clipnest.service.CaptureNotificationManager
import com.clipnest.ui.editor.EditorScreen
import com.clipnest.ui.drawer.ClipNestDrawer
import com.clipnest.ui.note.NoteScreen
import com.clipnest.ui.editor.EditorViewModel
import com.clipnest.ui.editor.EditorViewModelFactory
import com.clipnest.ui.localization.withAppLanguage
import com.clipnest.ui.navigation.Screen
import com.clipnest.ui.settings.SettingsScreen
import com.clipnest.ui.trash.TrashScreen
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
import java.security.MessageDigest

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
    private var pendingExternalSaveAs: ((Uri?) -> Unit)? = null
    private val externalSaveAsLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val callback = pendingExternalSaveAs
        pendingExternalSaveAs = null
        callback?.invoke(uri)
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
                        editorViewModelFactory = EditorViewModelFactory(fileManager, settingsDataStore, applicationContext, database.noteDao(), database.topicDao()),
                        settingsViewModelFactory = SettingsViewModelFactory(settingsDataStore, repository, fileManager, applicationContext),
                        editorTextSize = userSettings.editorTextSize,
                        viewerTextSize = userSettings.viewerTextSize,
                        onRequestFolder = ::requestFolderSelection,
                        onRequestOpenFile = ::requestOpenFile,
                        onRequestExternalSaveAs = ::requestExternalSaveAs,
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
    private fun requestOpenFile(onSelected: (Uri) -> Unit) { pendingOpenFileSelection = onSelected; openFileLauncher.launch(arrayOf("*/*")) }
    private fun requestExternalSaveAs(suggestedFileName: String, mimeType: String, callback: (Uri?) -> Unit) {
        pendingExternalSaveAs = callback
        externalSaveAsLauncher.launch(suggestedFileName)
    }
    private fun requestBackupFile() { backupPasswordRequest.value = BackupPasswordAction.EXPORT }
    private fun requestRestoreFile() { backupPasswordRequest.value = BackupPasswordAction.IMPORT }
    private fun launchBackupFilePicker() { val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()); backupFileLauncher.launch("ClipNest_Backup_$date.clipnest.json") }
    private fun launchRestoreFilePicker() { restoreFileLauncher.launch(arrayOf("application/json", "text/json", "*/*")) }
    private fun writeVaultBackup(uri: Uri, password: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val cards = repository.getAllCards()
                val notes = loadNotesForBackup()
                val json = VaultBackupCodec.encodeEncrypted(cards, notes, password)
                contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                    ?: error("Unable to open backup destination")
            }
            val language = settingsDataStore.userSettingsFlow.first().language
            val message = applicationContext.withAppLanguage(language).getString(
                if (result.isSuccess) com.clipnest.R.string.backup_saved else com.clipnest.R.string.backup_failed
            )
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun loadNotesForBackup(): List<VaultBackupNote> {
        val noteDao = database.noteDao()
        val projections = noteDao.getAllNoteBackupProjections()
        val chunkSize = 262_144
        return projections.map { note ->
            val content = buildString {
                var start = 1
                while (true) {
                    val chunk = noteDao.getNoteContentChunk(note.id, start, chunkSize) ?: break
                    if (chunk.isEmpty()) break
                    append(chunk)
                    if (chunk.length < chunkSize) break
                    start += chunkSize
                }
            }
            VaultBackupNote(
                title = note.title,
                content = content,
                createdAtMillis = note.createdAtMillis,
                updatedAtMillis = note.updatedAtMillis,
                isPinned = note.isPinned,
                isArchived = note.isArchived,
                isDeleted = note.isDeleted,
                deletedAtMillis = note.deletedAtMillis,
                editSessionCount = note.editSessionCount,
                lastAuthoredAtMillis = note.lastAuthoredAtMillis
            )
        }
    }

    private fun readVaultBackup(uri: Uri, password: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val json = contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                } ?: error("Unable to open backup source")
                val backup = VaultBackupCodec.decodeEncrypted(json, password)
                val cardResult = repository.mergeBackupCards(backup.cards)
                val noteResult = restoreBackupNotes(backup.notes)
                cardResult.copy(
                    imported = cardResult.imported + noteResult.first,
                    skippedDuplicates = cardResult.skippedDuplicates + noteResult.second
                )
            }
            val language = settingsDataStore.userSettingsFlow.first().language
            val message = applicationContext.withAppLanguage(language).getString(
                when {
                    result.isSuccess && result.getOrNull()?.imported == 0 && result.getOrNull()?.skippedDuplicates == 0 -> com.clipnest.R.string.restore_empty
                    result.isSuccess -> com.clipnest.R.string.restore_complete
                    result.exceptionOrNull() is IllegalArgumentException || result.exceptionOrNull() is com.squareup.moshi.JsonDataException || result.exceptionOrNull() is com.squareup.moshi.JsonEncodingException -> com.clipnest.R.string.invalid_backup_file
                    else -> com.clipnest.R.string.restore_failed
                },
                result.getOrNull()?.imported ?: 0,
                result.getOrNull()?.skippedDuplicates ?: 0
            )
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun restoreBackupNotes(notes: List<VaultBackupNote>): Pair<Int, Int> {
        if (notes.isEmpty()) return 0 to 0
        val noteDao = database.noteDao()
        val chunkSize = 262_144
        val existingKeys = mutableSetOf<String>()

        noteDao.getAllNoteBackupProjections().forEach { note ->
            val content = buildString {
                var start = 1
                while (true) {
                    val chunk = noteDao.getNoteContentChunk(note.id, start, chunkSize) ?: break
                    if (chunk.isEmpty()) break
                    append(chunk)
                    if (chunk.length < chunkSize) break
                    start += chunkSize
                }
            }
            existingKeys += noteBackupKey(note.title, note.createdAtMillis, content)
        }

        var imported = 0
        var skippedDuplicates = 0
        notes.forEach { note ->
            val key = noteBackupKey(note.title, note.createdAtMillis, note.content)
            if (!existingKeys.add(key)) {
                skippedDuplicates++
                return@forEach
            }
            noteDao.insertNote(
                Note(
                    title = note.title,
                    content = note.content,
                    preview = note.content.take(320),
                    isPinned = note.isPinned,
                    isArchived = note.isArchived,
                    isDeleted = note.isDeleted,
                    deletedAtMillis = note.deletedAtMillis,
                    editSessionCount = note.editSessionCount,
                    lastAuthoredAtMillis = note.lastAuthoredAtMillis,
                    createdAtMillis = note.createdAtMillis,
                    updatedAtMillis = note.updatedAtMillis
                )
            )
            imported++
        }
        return imported to skippedDuplicates
    }

    private fun noteBackupKey(title: String, createdAtMillis: Long, content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(content.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        return "$title\u0000$createdAtMillis\u0000$hash"
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
    onRequestExternalSaveAs: (String, String, (Uri?) -> Unit) -> Unit,
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
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = 0, pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != 2) keyboardController?.hide()
    }
    val isSettings = currentRoute == Screen.Settings.route
    val isTrash = currentRoute == Screen.Trash.route
    val isNoteTab = !isSettings && pagerState.currentPage == 0
    val isVaultTab = !isSettings && pagerState.currentPage == 1
    val isEditorTab = !isSettings && pagerState.currentPage == 2
    val selectedTab = pagerState.currentPage.coerceIn(0, 2)
    val selectedCards = vaultState.cards.filter { vaultState.selectedIds.contains(it.id) }
    val visibleSelectedCount = selectedCards.size
    val allVaultSelected = vaultState.cards.isNotEmpty() && visibleSelectedCount == vaultState.cards.size
    var noteSelectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var noteSelectedTopicId by remember { mutableStateOf<Long?>(null) }
    var noteVisibleIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var noteSearchOpen by remember { mutableStateOf(false) }
    var noteSearchQuery by remember { mutableStateOf("") }
    val noteDao = remember { AppDatabase.getInstance(context).noteDao() }
    val allNoteSelected = noteVisibleIds.isNotEmpty() && noteSelectedIds.containsAll(noteVisibleIds)
    val notePinnedIds by remember(noteSelectedTopicId) {
        noteSelectedTopicId?.let {
            noteDao.observePinnedNoteIdsForTopic(it, com.clipnest.data.model.NoteTopicRole.USER_TAG)
        } ?: noteDao.observePinnedNoteIds()
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val allSelectedNotesPinned = noteSelectedIds.isNotEmpty() && noteSelectedIds.all { it in notePinnedIds }
    LaunchedEffect(incomingOpenRequest) {
        incomingOpenRequest?.let { request ->
            editorViewModel.openExternalDocument(request.uri, context.contentResolver, request.openContext, request.candidates)
            pagerState.animateScrollToPage(2)
            onIncomingOpenRequestHandled()
        }
    }
    fun openExternalFile() { onRequestOpenFile { uri -> editorViewModel.openExternalDocument(uri, context.contentResolver); scope.launch { pagerState.animateScrollToPage(2) } } }
    fun openEditorFromVault() {
        val navigate = {
            vaultViewModel.copySelectedCardsThenOpenEditor(context) { combinedText ->
                editorViewModel.createNoteAndEnterNoteMode(initialContent = combinedText)
                scope.launch { pagerState.animateScrollToPage(2, animationSpec = tween(durationMillis = 180)) }
            }
        }
        if (editorUiState.externalDocumentUri != null) editorViewModel.returnToInternalEditor(context.contentResolver, onComplete = navigate) else navigate()
    }

    fun createNoteFromVault() {
        val navigate: () -> Unit = {
            editorViewModel.createNoteAndEnterNoteMode()
            scope.launch { pagerState.animateScrollToPage(2, animationSpec = tween(durationMillis = 180)) }
        }
        if (editorUiState.externalDocumentUri != null) editorViewModel.returnToInternalEditor(context.contentResolver, onComplete = navigate) else navigate()
    }

    fun openNewNote(origin: com.clipnest.ui.editor.EditorNoteOrigin?, topicId: Long?) {
        editorViewModel.createNoteAndEnterNoteMode(origin = origin, topicId = topicId)
        scope.launch { pagerState.animateScrollToPage(2, animationSpec = tween(durationMillis = 180)) }
    }

    fun openExistingNote(noteId: Long, origin: com.clipnest.ui.editor.EditorNoteOrigin?) {
        editorViewModel.openNoteInEditor(noteId, origin)
        scope.launch { pagerState.animateScrollToPage(2, animationSpec = tween(durationMillis = 180)) }
    }

    fun toggleSelectedNotesPin() {
        if (noteSelectedIds.isEmpty()) return
        scope.launch {
            if (noteSelectedTopicId == null) {
                val anyUnpinned = noteSelectedIds.any { id -> !noteDao.isNotePinned(id) }
                val now = System.currentTimeMillis()
                noteSelectedIds.forEach { id ->
                    noteDao.setNotePinned(id, anyUnpinned, now)
                }
            } else {
                val role = com.clipnest.data.model.NoteTopicRole.USER_TAG
                val topicId = noteSelectedTopicId!!
                val idsForTopic = noteSelectedIds.filter { noteDao.hasTopicRelation(it, topicId, role) }
                if (idsForTopic.isNotEmpty()) {
                    val anyUnpinned = idsForTopic.any { id -> !noteDao.isTopicPinned(id, topicId, role) }
                    noteDao.setTopicPinnedForNotes(idsForTopic, topicId, role, anyUnpinned)
                }
            }
            noteSelectedIds = emptySet()
        }
    }

    fun deleteSelectedNotes() {
        val now = System.currentTimeMillis()
        scope.launch {
            noteSelectedIds.forEach { id -> noteDao.setDeleted(id, true, now, now) }
            noteSelectedIds = emptySet()
        }
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ClipNestDrawer(
                topicDao = AppDatabase.getInstance(context).topicDao(),
                onClose = { scope.launch { drawerState.close() } },
                onTrashClick = {
                    scope.launch { drawerState.close() }
                    navController.navigate(Screen.Trash.route) { launchSingleTop = true }
                }
            )
        }
    ) {
    Scaffold(
        topBar = {
            MainTopBar(
                title = when { isSettings -> stringResource(com.clipnest.R.string.settings); isTrash -> stringResource(com.clipnest.R.string.drawer_trash); isNoteTab -> stringResource(com.clipnest.R.string.note_tab); isVaultTab -> stringResource(com.clipnest.R.string.vault); else -> if (editorUiState.externalDocumentUri != null) editorUiState.documentName else stringResource(com.clipnest.R.string.editor) },
                isNote = isNoteTab,
                isTrash = isTrash,
                isVault = isVaultTab,
                isEditor = isEditorTab,
                selectedCount = when {
                    isSettings || isTrash -> 0
                    isNoteTab -> noteSelectedIds.size
                    else -> vaultState.selectedIds.size
                },
                allSelected = if (isNoteTab) allNoteSelected else allVaultSelected,
                allSelectedPinned = if (isNoteTab) allSelectedNotesPinned else !isSettings && selectedCards.isNotEmpty() && selectedCards.all { it.pinned },
                noteCanPin = noteSelectedIds.isNotEmpty(),
                isSettings = isSettings,
                isSearchOpen = if (isEditorTab) editorSearchOpen else if (isVaultTab) vaultState.isSearchOpen else false,
                searchQuery = if (isEditorTab) editorSearchQuery else if (isVaultTab) vaultState.searchQuery else if (isNoteTab) noteSearchQuery else "",
                searchPlaceholder = if (isEditorTab) stringResource(com.clipnest.R.string.search_editor) else stringResource(com.clipnest.R.string.search_notes),
                editorDocumentName = if (editorUiState.externalDocumentUri != null) editorUiState.documentName else stringResource(com.clipnest.R.string.editor),
                editorSearchMatchCount = editorSearchMatchCount,
                editorActiveSearchMatch = editorActiveSearchMatch,
                editorReplaceQuery = editorReplaceQuery,
                onPreviousSearchMatch = editorViewModel::previousSearchMatch,
                onNextSearchMatch = editorViewModel::nextSearchMatch,
                onReplaceQueryChange = editorViewModel::setReplaceQuery,
                onReplaceCurrentMatch = editorViewModel::replaceCurrentMatch,
                onReplaceAllMatches = editorViewModel::replaceAllMatches,
                onSearchOpen = when {
                    isEditorTab -> editorViewModel::openSearchPublic
                    isVaultTab -> vaultViewModel::openSearch
                    else -> { { noteSearchOpen = true } }
                },
                onSearchClose = when {
                    isEditorTab -> editorViewModel::closeSearch
                    isVaultTab -> vaultViewModel::closeSearch
                    else -> { { noteSearchOpen = false; noteSearchQuery = "" } }
                },
                onSearchQueryChange = when {
                    isEditorTab -> editorViewModel::setSearchQuery
                    isVaultTab -> vaultViewModel::setSearchQuery
                    else -> { query: String -> noteSearchQuery = query }
                },
                onToggleSelectAll = {
                    if (isNoteTab) {
                        noteSelectedIds = if (allNoteSelected) emptySet() else noteVisibleIds
                    } else if (allVaultSelected) {
                        vaultViewModel.clearSelection()
                    } else {
                        vaultViewModel.selectAll()
                    }
                },
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
                onReturnToEditor = {
                    if (editorUiState.mode == com.clipnest.ui.editor.EditorMode.NOTE) editorViewModel.returnToFreeEditor()
                    else editorViewModel.returnToInternalEditor(context.contentResolver)
                },
                isExternalDocument = editorUiState.externalDocumentUri != null || editorUiState.mode == com.clipnest.ui.editor.EditorMode.NOTE,
                onOpenEditor = ::openEditorFromVault,
                onCreateNoteFromVault = ::createNoteFromVault,
                onPinSelected = if (isNoteTab) ::toggleSelectedNotesPin else vaultViewModel::togglePinSelected,
                onCopySelected = { vaultViewModel.copySelectedCards(context) },
                onDeleteSelected = if (isNoteTab) ::deleteSelectedNotes else vaultViewModel::requestDeleteSelected,
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
                        0 -> NoteScreen(
                            noteDao = noteDao,
                            topicDao = AppDatabase.getInstance(context).topicDao(),
                            onCreateNote = ::openNewNote,
                            onOpenNote = ::openExistingNote,
                            selectedNoteIds = noteSelectedIds,
                            onSelectionChanged = { noteSelectedIds = it },
                            onSelectedTopicIdChanged = { noteSelectedTopicId = it },
                            onVisibleNoteIdsChanged = { visible ->
                                noteVisibleIds = visible
                                noteSelectedIds = noteSelectedIds.intersect(visible)
                            },
                            isSearchOpen = noteSearchOpen,
                            searchQuery = noteSearchQuery,
                            onSearchQueryChange = { noteSearchQuery = it },
                            onCloseSearch = {
                                noteSearchOpen = false
                                noteSearchQuery = ""
                            },
                            onOpenSearch = {
                                noteSearchOpen = true
                                scope.launch { pagerState.animateScrollToPage(0) }
                            },
                            onOpenMenu = {
                                scope.launch { drawerState.open() }
                            },
                            onOpenSettings = {
                                navController.navigate(Screen.Settings.route) { launchSingleTop = true }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        1 -> VaultScreen(
                            viewModel = vaultViewModel,
                            onOpenEditor = ::openEditorFromVault,
                            onShareText = onShareText,
                            onRequestExportFolder = { onRequestFolder { uri -> vaultViewModel.setExportFolder(uri, context.contentResolver) } },
                            modifier = Modifier.fillMaxSize()
                        )
                        2 -> EditorScreen(
                            viewModel = editorViewModel,
                            editorTextSize = editorTextSize,
                            viewerTextSize = viewerTextSize,
                            onRequestSaveFolder = { onRequestFolder { uri -> editorViewModel.setDefaultSaveFolder(uri, context.contentResolver) } },
                            onRequestOpenFile = ::openExternalFile,
                            onRequestExternalSaveAs = { name, mime -> onRequestExternalSaveAs(name, mime) { uri -> editorViewModel.completeExternalSaveAs(uri, context.contentResolver) } },
                            onExit = { returnKey ->
                                if (returnKey?.startsWith("topic:") == true) {
                                    scope.launch { pagerState.animateScrollToPage(0, animationSpec = tween(durationMillis = 180)) }
                                } else {
                                    scope.launch { pagerState.animateScrollToPage(2, animationSpec = tween(durationMillis = 180)) }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            composable(Screen.Trash.route) {
                TrashScreen(noteDao = noteDao, onBack = { navController.navigate(Screen.Vault.route) { launchSingleTop = true } }, modifier = Modifier.fillMaxSize())
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



}
    
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar(
    title: String,
    isNote: Boolean,
    isVault: Boolean,
    isEditor: Boolean,
    isTrash: Boolean,
    selectedCount: Int,
    allSelected: Boolean,
    allSelectedPinned: Boolean,
    noteCanPin: Boolean,
    isSettings: Boolean,
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
    onCreateNoteFromVault: () -> Unit,
    onPinSelected: () -> Unit,
    onCopySelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onOpenSettings: () -> Unit,
    onTabSelected: (Int) -> Unit
) {
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(isSearchOpen) {
        if (isSearchOpen) { searchFocusRequester.requestFocus(); keyboardController?.show() }
    }
    Surface(color = if (isNote) androidx.compose.ui.graphics.Color(0xFF174B39) else MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
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
                    (isNote || isVault) && selectedCount > 0 -> {
                        MainTabSlot(
                            selected = true,
                            onClick = { onTabSelected(if (isNote) 0 else 1) },
                            modifier = Modifier.fillMaxWidth().testTag(if (isNote) "main_tab_note" else "main_tab_vault")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                                VaultSelectionCheckbox(
                                    checked = allSelected,
                                    onClick = onToggleSelectAll,
                                    modifier = Modifier.testTag(if (isNote) "note_select_all_checkbox" else "vault_select_all_checkbox")
                                )
                                Text(
                                    text = stringResource(com.clipnest.R.string.selected_count, selectedCount),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (isNote) androidx.compose.ui.graphics.Color(0xFFF6F4EA) else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    modifier = Modifier.testTag(if (isNote) "note_selected_count_text" else "vault_selected_count_text")
                                )
                            }
                        }
                    }
                    isNote || isVault || isEditor -> {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            val tabTextColor = if (isNote) androidx.compose.ui.graphics.Color(0xFFF6F4EA) else MaterialTheme.colorScheme.onSurface
                            val tabAccentColor = if (isNote) androidx.compose.ui.graphics.Color(0xFFDDE8B5) else MaterialTheme.colorScheme.primary
                            MainTabSlot(selected = isNote, onClick = { onTabSelected(0) }, modifier = Modifier.weight(1f).testTag("main_tab_note"), selectedColor = tabAccentColor) { Text(stringResource(com.clipnest.R.string.note_tab), color = tabTextColor, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) }
                            MainTabSlot(selected = isVault, onClick = { onTabSelected(1) }, modifier = Modifier.weight(1f).testTag("main_tab_vault"), selectedColor = tabAccentColor) { Text(stringResource(com.clipnest.R.string.vault), color = tabTextColor, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) }
                            MainTabSlot(selected = isEditor, onClick = { onTabSelected(2) }, modifier = Modifier.weight(1f).testTag("main_tab_editor"), selectedColor = tabAccentColor) { Text(editorDocumentName, color = tabTextColor, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1) }
                        }
                    }
                    else -> Text(text = title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(start = 16.dp).testTag("main_title"))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                if (isAskAiInProgress) CircularProgressIndicator(modifier = Modifier.size(22.dp).testTag("ask_ai_loading_indicator"), strokeWidth = 2.dp)
                if ((isVault || isNote) && selectedCount > 0 && !isSearchOpen && !isTrash) {
                    IconButton(onClick = onPinSelected, enabled = !isNote || noteCanPin, modifier = Modifier.size(36.dp).testTag(if (isNote) "note_action_pin_direct" else "vault_action_pin_direct")) {
                        Icon(
                            if (allSelectedPinned) Icons.Outlined.PushPin else Icons.Default.PushPin,
                            contentDescription = stringResource(if (allSelectedPinned) com.clipnest.R.string.unpin_selected else com.clipnest.R.string.pin_selected),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    if (isVault) {
                        IconButton(onClick = onCopySelected, modifier = Modifier.size(36.dp).testTag("vault_action_copy")) {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(com.clipnest.R.string.copy_selected), modifier = Modifier.size(22.dp))
                        }
                    }
                    IconButton(onClick = onDeleteSelected, modifier = Modifier.size(36.dp).testTag(if (isNote) "note_action_delete" else "vault_action_delete")) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(com.clipnest.R.string.delete_selected), modifier = Modifier.size(22.dp))
                    }
                }
                if (isSearchOpen) {
                    IconButton(
                        onClick = onSearchClose,
                        modifier = Modifier.size(36.dp).testTag("main_close_search_button")
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(com.clipnest.R.string.close_search),
                            modifier = Modifier.size(22.dp)
                        )
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
    selectedColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier.fillMaxHeight().clickable(onClick = onClick).padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { content() }
        Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(if (selected) selectedColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)))
    }
}

@Composable
private fun VaultSelectionCheckbox(checked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Checkbox(checked = checked, onCheckedChange = { onClick() }, colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary, uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant, checkmarkColor = MaterialTheme.colorScheme.onPrimary), modifier = modifier.size(32.dp))
}
