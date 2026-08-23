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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import kotlinx.coroutines.launch
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
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

    private val editorViewModel: EditorViewModel by viewModels {
        EditorViewModelFactory(fileManager, settingsDataStore)
    }

    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModelFactory(settingsDataStore, repository, fileManager)
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
                    editorViewModel = editorViewModel,
                    settingsViewModel = settingsViewModel
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
    editorViewModel: EditorViewModel,
    settingsViewModel: SettingsViewModel
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val vaultState by vaultViewModel.uiState.collectAsStateWithLifecycle()

    val isMainTab = currentRoute == Screen.Vault.route || currentRoute == Screen.Note.route
    val selectedTab = if (currentRoute == Screen.Note.route) 1 else 0

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

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
                    selected = currentRoute == Screen.Settings.route,
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
                        title = if (currentRoute == Screen.Settings.route) "Settings" else "Clipboard Manager",
                        isSearchOpen = vaultState.isSearchOpen && currentRoute == Screen.Vault.route,
                        searchQuery = vaultState.searchQuery,
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onSearchOpen = vaultViewModel::openSearch,
                        onSearchClose = vaultViewModel::closeSearch,
                        onSearchQueryChange = vaultViewModel::setSearchQuery
                    )
                    if (isMainTab) {
                        MainTabRow(
                            selectedTab = selectedTab,
                            onTabSelected = { tab ->
                                val route = if (tab == 0) Screen.Vault.route else Screen.Note.route
                                navController.navigate(route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
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
                    VaultScreen(viewModel = vaultViewModel)
                }
                composable(Screen.Note.route) {
                    EditorScreen(viewModel = editorViewModel)
                }
                composable(Screen.Settings.route) {
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
    isSearchOpen: Boolean,
    searchQuery: String,
    onMenuClick: () -> Unit,
    onSearchOpen: () -> Unit,
    onSearchClose: () -> Unit,
    onSearchQueryChange: (String) -> Unit
) {
    var overflowExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        navigationIcon = {
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.testTag("main_navigation_drawer_button")
            ) {
                Icon(Icons.Default.Menu, contentDescription = "Settings")
            }
        },
        title = {
            if (isSearchOpen) {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Search vault...") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("main_search_input")
                )
            } else {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.testTag("main_title")
                )
            }
        },
        actions = {
            if (isSearchOpen) {
                IconButton(onClick = onSearchClose, modifier = Modifier.testTag("main_close_search_button")) {
                    Text("×", style = MaterialTheme.typography.headlineSmall)
                }
            } else {
                IconButton(onClick = onSearchOpen, modifier = Modifier.testTag("main_search_button")) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
            }
            androidx.compose.foundation.layout.Box {
                IconButton(
                    onClick = { overflowExpanded = true },
                    modifier = Modifier.testTag("main_overflow_button")
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(
                    expanded = overflowExpanded,
                    onDismissRequest = { overflowExpanded = false },
                    modifier = Modifier.testTag("main_overflow_menu")
                ) {
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        onClick = {
                            overflowExpanded = false
                            onMenuClick()
                        }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun MainTabRow(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    TabRow(
        selectedTabIndex = selectedTab,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        indicator = { positions ->
            TabRowDefaults.SecondaryIndicator(
                Modifier.tabIndicatorOffset(positions[selectedTab]),
                color = MaterialTheme.colorScheme.primary,
                height = 3.dp
            )
        },
        divider = {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        },
        modifier = Modifier.testTag("main_top_tab_row")
    ) {
        Tab(
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            text = { Text("Kho", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) },
            modifier = Modifier.testTag("main_tab_kho")
        )
        Tab(
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            text = { Text("Soạn thảo", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) },
            modifier = Modifier.testTag("main_tab_soan_thao")
        )
    }
}
