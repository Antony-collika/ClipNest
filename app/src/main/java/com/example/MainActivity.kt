package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.local.AppDatabase
import com.example.data.local.FileManager
import com.example.data.local.NoteDocType
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

        handleIntent(intent)

        setContent {
            val userSettings by settingsDataStore.userSettingsFlow.collectAsStateWithLifecycle(
                initialValue = com.example.data.local.UserSettings()
            )

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
        if (intent == null) return

        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                ?: intent.clipData?.getItemAt(0)?.text?.toString()
            if (!sharedText.isNullOrBlank()) {
                vaultViewModel.handleIncomingShare(sharedText)
            }
        } else if (intent.getBooleanExtra(CaptureNotificationManager.EXTRA_OPEN_CAPTURE, false)) {
            vaultViewModel.openInAppCapture()
        }
    }
}

@Composable
fun MainAppContent(
    vaultViewModel: VaultViewModel,
    editorViewModel: EditorViewModel,
    settingsViewModel: SettingsViewModel
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("bottom_nav_bar")
            ) {
                Screen.items.forEach { screen ->
                    val selected = currentRoute == screen.route
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = screen.title
                            )
                        },
                        label = { Text(screen.title) },
                        selected = selected,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        modifier = Modifier.testTag("bottom_nav_${screen.route}")
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
                VaultScreen(
                    viewModel = vaultViewModel,
                    onNavigateToEditor = { docType ->
                        editorViewModel.switchDocument(docType)
                        navController.navigate(Screen.Note.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            composable(Screen.Note.route) {
                EditorScreen(
                    viewModel = editorViewModel
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = settingsViewModel
                )
            }
        }
    }
}
