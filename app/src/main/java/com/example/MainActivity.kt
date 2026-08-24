package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import kotlinx.coroutines.launch
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
import com.example.service.CaptureNotificationManager
import com.example.ui.editor.EditorScreen
import com.example.ui.editor.EditorViewModel
import com.example.ui.editor.EditorViewModelFactory
import com.example.ui.navigation.Screen
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import com.example.ui.settings.SettingsViewModelFactory
import com.example.ui.theme.ClipboardManagerTheme
import com.example.ui.vault.VaultScreen
import com.example.ui.vault.VaultViewModel
import com.example.ui.vault.VaultViewModelFactory

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

    private val vaultViewModel: VaultViewModel by viewModels {
        VaultViewModelFactory(repository, settingsDataStore, fileManager)
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
                        CaptureNotificationManager.showCaptureNotification(applicationContext)
                    }
                } else {
                    CaptureNotificationManager.dismissCaptureNotification(applicationContext)
                }
            }

            ClipboardManagerTheme(themeMode = userSettings.themeMode) {
                MainAppContent(
                    vaultViewModel = vaultViewModel,
                    editorViewModelFactory = EditorViewModelFactory(fileManager, settingsDataStore),
                    settingsViewModelFactory = SettingsViewModelFactory(settingsDataStore, repository, fileManager)
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(CaptureNotificationManager.EXTRA_OPEN_CAPTURE, false) == true) {
            vaultViewModel.openInAppCapture()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(
    vaultViewModel: VaultViewModel,
    editorViewModelFactory: ViewModelProvider.Factory,
    settingsViewModelFactory: ViewModelProvider.Factory
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
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = 0,
        pageCount = { 2 }
    )
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val isSettings = currentRoute == Screen.Settings.route
    val isEditorTab = !isSettings && pagerState.currentPage == 1
    val isMainTab = !isSettings
    val selectedTab = pagerState.currentPage.coerceIn(0, 1)

    fun openEditorFromVault() {
        vaultViewModel.copySelectedCardsThenOpenEditor(context) {
            scope.launch { pagerState.animateScrollToPage(1) }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    text = "Clipboard Manager",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Settings") },
                    selected = isSettings,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate(Screen.Settings.route) { launchSingleTop = true }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp).testTag("drawer_settings_item")
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                Column {
                    MainTopBar(
                        title = if (isSettings) "Settings" else "Clipboard Manager",
                        isVault = !isSettings && selectedTab == 0,
                        isEditor = !isSettings && selectedTab == 1,
                        selectedCount = if (isSettings) 0 else vaultState.selectedIds.size,
                        allSelected = !isSettings && vaultState.cards.isNotEmpty() &&
                            vaultState.selectedIds.size == vaultState.cards.size,
                        showPinnedFirst = vaultState.userSettings.showPinnedFirst,
                        isSearchOpen = if (isEditorTab) editorSearchOpen else vaultState.isSearchOpen,
                        searchQuery = if (isEditorTab) editorSearchQuery else vaultState.searchQuery,
                        searchPlaceholder = if (isEditorTab) "Search editor..." else "Search vault...",
                        editorSearchMatchCount = editorSearchMatchCount,
                        editorActiveSearchMatch = editorActiveSearchMatch,
                        onPreviousSearchMatch = editorViewModel::previousSearchMatch,
                        onNextSearchMatch = editorViewModel::nextSearchMatch,
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onSearchOpen = if (isEditorTab) editorViewModel::openSearch else vaultViewModel::openSearch,
                        onSearchClose = if (isEditorTab) editorViewModel::closeSearch else vaultViewModel::closeSearch,
                        onSearchQueryChange = if (isEditorTab) editorViewModel::setSearchQuery else vaultViewModel::setSearchQuery,
                        onToggleSelectAll = {
                            if (vaultState.selectedIds.size == vaultState.cards.size && vaultState.cards.isNotEmpty()) {
                                vaultViewModel.clearSelection()
                            } else {
                                vaultViewModel.selectAll()
                            }
                        },
                        onShareSelected = { vaultViewModel.shareSelected(context) },
                        onSaveFile = vaultViewModel::openExportDialog,
                        onOpenEditor = ::openEditorFromVault,
                        onToggleShowPinnedFirst = vaultViewModel::toggleShowPinnedFirst
                    )
                    if (isMainTab) {
                        MainTabRow(
                            selectedTab = selectedTab,
                            onTabSelected = { target ->
                                scope.launch { pagerState.animateScrollToPage(target) }
                            }
                        )
                    }
                }
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
                        beyondViewportPageCount = 1,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("main_content_pager")
                    ) { page ->
                        when (page) {
                            0 -> VaultScreen(
                                viewModel = vaultViewModel,
                                onOpenEditor = ::openEditorFromVault,
                                modifier = Modifier.fillMaxSize()
                            )
                            1 -> EditorScreen(
                                viewModel = editorViewModel,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
                composable(Screen.Settings.route) {
                    val settingsViewModel: SettingsViewModel = viewModel(factory = settingsViewModelFactory)
                    SettingsScreen(viewModel = settingsViewModel)
                }
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
    showPinnedFirst: Boolean,
    isSearchOpen: Boolean,
    searchQuery: String,
    searchPlaceholder: String,
    editorSearchMatchCount: Int,
    editorActiveSearchMatch: Int,
    onPreviousSearchMatch: () -> Unit,
    onNextSearchMatch: () -> Unit,
    onMenuClick: () -> Unit,
    onSearchOpen: () -> Unit,
    onSearchClose: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleSelectAll: () -> Unit,
    onShareSelected: () -> Unit,
    onSaveFile: () -> Unit,
    onOpenEditor: () -> Unit,
    onToggleShowPinnedFirst: () -> Unit
) {
    var overflowExpanded by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(52.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.testTag("main_navigation_drawer_button")
            ) {
                Icon(Icons.Default.Menu, contentDescription = "Settings")
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterStart
            ) {
                if (isSearchOpen) {
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
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous match")
                            }
                            IconButton(
                                onClick = onNextSearchMatch,
                                modifier = Modifier.size(36.dp).testTag("editor_search_next")
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next match")
                            }
                        }
                    }
                } else {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.testTag("main_title")
                    )
                }
            }

            if (isSearchOpen) {
                IconButton(
                    onClick = onSearchClose,
                    modifier = Modifier.testTag("main_close_search_button")
                ) {
                    Text("×", style = MaterialTheme.typography.headlineSmall)
                }
            } else {
                IconButton(
                    onClick = onSearchOpen,
                    modifier = Modifier.testTag("main_search_button")
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
            }

            Box {
                IconButton(
                    onClick = { overflowExpanded = true },
                    modifier = Modifier.testTag("main_overflow_button")
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
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
                            text = { Text(if (allSelected) "Clear selection" else "Select all") },
                            onClick = {
                                overflowExpanded = false
                                onToggleSelectAll()
                            },
                            modifier = Modifier.testTag("main_menu_select_all")
                        )
                        DropdownMenuItem(
                            text = { Text("Share") },
                            enabled = selectedCount > 0,
                            onClick = {
                                overflowExpanded = false
                                onShareSelected()
                            },
                            modifier = Modifier.testTag("main_menu_share")
                        )
                        DropdownMenuItem(
                            text = { Text("Save file") },
                            enabled = selectedCount > 0,
                            onClick = {
                                overflowExpanded = false
                                onSaveFile()
                            },
                            modifier = Modifier.testTag("main_menu_save_file")
                        )
                        DropdownMenuItem(
                            text = { Text("Open editor") },
                            enabled = selectedCount > 0,
                            onClick = {
                                overflowExpanded = false
                                onOpenEditor()
                            },
                            modifier = Modifier.testTag("main_menu_open_editor")
                        )
                        DropdownMenuItem(
                            text = { Text("Show pinned first") },
                            trailingIcon = {
                                if (showPinnedFirst) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Active"
                                    )
                                }
                            },
                            onClick = {
                                overflowExpanded = false
                                onToggleShowPinnedFirst()
                            },
                            modifier = Modifier.testTag("main_menu_show_pinned_first")
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            onClick = {
                                overflowExpanded = false
                                onMenuClick()
                            },
                            modifier = Modifier.testTag("main_menu_settings")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MainTabRow(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(MaterialTheme.colorScheme.surface)
            .selectableGroup()
            .testTag("main_top_tab_row"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompactMainTab(
            label = "Kho",
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            tag = "main_tab_kho"
        )
        CompactMainTab(
            label = "Soạn thảo",
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            tag = "main_tab_soan_thao"
        )
    }
}

@Composable
private fun RowScope.CompactMainTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab
            )
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(3.dp)
                .background(if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
        )
    }
}
