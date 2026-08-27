package com.example.ui.settings

import android.content.Context
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppLanguage
import com.example.data.local.EditorTextSize
import com.example.data.local.FileManager
import com.example.data.local.RetentionPolicy
import com.example.data.local.SettingsDataStore
import com.example.data.local.ThemePreset
import com.example.data.local.ViewerTextSize
import com.example.data.local.UserSettings
import com.example.data.repository.ClipboardRepository
import com.example.service.CaptureNotificationManager
import com.example.ui.localization.withAppLanguage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File

data class SettingsUiState(
    val userSettings: UserSettings = UserSettings(),
    val exportedFiles: List<File> = emptyList()
)

sealed class SettingsEvent {
    data class ShowToast(val message: String) : SettingsEvent()
}

class SettingsViewModel(
    private val settingsDataStore: SettingsDataStore,
    private val repository: ClipboardRepository,
    private val fileManager: FileManager,
    private val appContext: Context
) : ViewModel() {

    private val _eventFlow = MutableSharedFlow<SettingsEvent>()
    val eventFlow: SharedFlow<SettingsEvent> = _eventFlow.asSharedFlow()

    private val _exportedFiles = MutableStateFlow<List<File>>(emptyList())
    val exportedFiles: StateFlow<List<File>> = _exportedFiles.asStateFlow()

    val userSettings: StateFlow<UserSettings> = settingsDataStore.userSettingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserSettings()
    )

    init {
        // Let Settings render its primary content first; exported files are a
        // secondary section and can be refreshed after the first frame.
        viewModelScope.launch {
            yield()
            refreshExportedFiles()
        }
    }

    fun refreshExportedFiles() {
        viewModelScope.launch {
            val files = withContext(Dispatchers.IO) {
                fileManager.listExportedFiles()
            }
            _exportedFiles.value = files
        }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            settingsDataStore.setLanguage(language)
        }
    }

    fun setThemePreset(themePreset: ThemePreset) {
        viewModelScope.launch {
            settingsDataStore.setThemePreset(themePreset)
        }
    }

    fun setEditorTextSize(size: EditorTextSize) {
        viewModelScope.launch {
            settingsDataStore.setEditorTextSize(size)
        }
    }

    fun setViewerTextSize(size: ViewerTextSize) {
        viewModelScope.launch {
            settingsDataStore.setViewerTextSize(size)
        }
    }

    fun setShowPinnedFirst(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setShowPinnedFirst(enabled)
        }
    }

    fun setSensitivePreviewMasked(masked: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setSensitivePreviewMasked(masked)
        }
    }

    fun setNotificationEnabled(enabled: Boolean, context: Context) {
        viewModelScope.launch {
            settingsDataStore.setNotificationEnabled(enabled)
            if (enabled) {
                val language = settingsDataStore.userSettingsFlow.first().language
                CaptureNotificationManager.showCaptureNotification(context, language)
            } else {
                CaptureNotificationManager.dismissCaptureNotification(context)
            }
        }
    }

    fun setDefaultSaveFolder(uri: Uri, contentResolver: ContentResolver) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        viewModelScope.launch { settingsDataStore.setDefaultSaveFolderUri(uri.toString()) }
    }

    fun clearDefaultSaveFolder() {
        viewModelScope.launch { settingsDataStore.setDefaultSaveFolderUri(null) }
    }

    fun setRetentionPolicy(policy: RetentionPolicy) {
        viewModelScope.launch {
            settingsDataStore.setRetentionPolicy(policy)
            val deleted = repository.cleanupOldCards(policy)
            if (deleted > 0) {
                val language = settingsDataStore.userSettingsFlow.first().language
                _eventFlow.emit(
                    SettingsEvent.ShowToast(
                        appContext.withAppLanguage(language).getString(
                            com.example.R.string.cleaned_old_cards,
                            deleted
                        )
                    )
                )
            }
        }
    }
}

class SettingsViewModelFactory(
    private val settingsDataStore: SettingsDataStore,
    private val repository: ClipboardRepository,
    private val fileManager: FileManager,
    private val appContext: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(settingsDataStore, repository, fileManager, appContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
