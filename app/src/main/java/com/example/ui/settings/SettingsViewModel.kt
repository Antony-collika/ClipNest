package com.example.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.FileManager
import com.example.data.local.RetentionPolicy
import com.example.data.local.SettingsDataStore
import com.example.data.local.ThemeMode
import com.example.data.local.UserSettings
import com.example.data.repository.ClipboardRepository
import com.example.service.CaptureNotificationManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    private val fileManager: FileManager
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
        refreshExportedFiles()
    }

    fun refreshExportedFiles() {
        _exportedFiles.value = fileManager.listExportedFiles()
    }

    fun setThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch {
            settingsDataStore.setThemeMode(themeMode)
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
                CaptureNotificationManager.showCaptureNotification(context)
            } else {
                CaptureNotificationManager.dismissCaptureNotification(context)
            }
        }
    }

    fun setRetentionPolicy(policy: RetentionPolicy) {
        viewModelScope.launch {
            settingsDataStore.setRetentionPolicy(policy)
            val deleted = repository.cleanupOldCards(policy)
            if (deleted > 0) {
                _eventFlow.emit(SettingsEvent.ShowToast("Cleaned up $deleted old cards"))
            }
        }
    }
}

class SettingsViewModelFactory(
    private val settingsDataStore: SettingsDataStore,
    private val repository: ClipboardRepository,
    private val fileManager: FileManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(settingsDataStore, repository, fileManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
