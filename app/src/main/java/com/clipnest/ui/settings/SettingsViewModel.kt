package com.clipnest.ui.settings

import android.content.Context
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.clipnest.ai.AiProviderType
import com.clipnest.ai.GeminiModelCatalog
import com.clipnest.data.local.AppLanguage
import com.clipnest.data.local.EditorTextSize
import com.clipnest.data.local.FileManager
import com.clipnest.data.local.RetentionPolicy
import com.clipnest.data.local.SettingsDataStore
import com.clipnest.data.local.ThemePreset
import com.clipnest.data.local.ViewerTextSize
import com.clipnest.data.local.UserSettings
import com.clipnest.data.repository.ClipboardRepository
import com.clipnest.security.SecureApiKeyStore
import com.clipnest.service.CaptureNotificationManager
import com.clipnest.ui.localization.withAppLanguage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val userSettings: UserSettings = UserSettings()
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

    private val secureApiKeyStore = SecureApiKeyStore(appContext)
    private val _eventFlow = MutableSharedFlow<SettingsEvent>()
    val eventFlow: SharedFlow<SettingsEvent> = _eventFlow.asSharedFlow()
    private val _hasGeminiApiKey = kotlinx.coroutines.flow.MutableStateFlow(secureApiKeyStore.hasGeminiApiKey())
    val hasGeminiApiKey: StateFlow<Boolean> = _hasGeminiApiKey.asStateFlow()

    val userSettings: StateFlow<UserSettings> = settingsDataStore.userSettingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserSettings()
    )

    fun setLanguage(language: AppLanguage) { viewModelScope.launch { settingsDataStore.setLanguage(language) } }
    fun setThemePreset(themePreset: ThemePreset) { viewModelScope.launch { settingsDataStore.setThemePreset(themePreset) } }
    fun setEditorTextSize(size: EditorTextSize) { viewModelScope.launch { settingsDataStore.setEditorTextSize(size) } }
    fun setViewerTextSize(size: ViewerTextSize) { viewModelScope.launch { settingsDataStore.setViewerTextSize(size) } }
    fun setShowPinnedFirst(enabled: Boolean) { viewModelScope.launch { settingsDataStore.setShowPinnedFirst(enabled) } }
    fun setSensitivePreviewMasked(masked: Boolean) { viewModelScope.launch { settingsDataStore.setSensitivePreviewMasked(masked) } }

    fun setAiProvider(provider: AiProviderType) { viewModelScope.launch { settingsDataStore.setAiProvider(provider.name) } }
    fun setGeminiModel(modelId: String) {
        if (GeminiModelCatalog.find(modelId) == null) return
        viewModelScope.launch { settingsDataStore.setGeminiModelId(modelId) }
    }
    fun setVercelModel(modelId: String) {
        if (GeminiModelCatalog.find(modelId) == null) return
        viewModelScope.launch { settingsDataStore.setVercelModelId(modelId) }
    }
    fun setAiPrompt(prompt: String) {
        viewModelScope.launch { settingsDataStore.setAiPrompt(prompt) }
    }
    fun saveGeminiApiKey(apiKey: String) {
        runCatching { secureApiKeyStore.saveGeminiApiKey(apiKey.trim()) }
            .onFailure { error -> viewModelScope.launch { _eventFlow.emit(SettingsEvent.ShowToast(error.message ?: "Could not save API key")) } }
            .onSuccess {
                _hasGeminiApiKey.value = true
                viewModelScope.launch { _eventFlow.emit(SettingsEvent.ShowToast("Gemini API key saved")) }
            }
    }
    fun deleteGeminiApiKey() {
        secureApiKeyStore.deleteGeminiApiKey()
        _hasGeminiApiKey.value = false
        viewModelScope.launch { _eventFlow.emit(SettingsEvent.ShowToast("Gemini API key deleted")) }
    }
    fun geminiApiKeyConfigured(): Boolean = _hasGeminiApiKey.value

    fun setNotificationEnabled(enabled: Boolean, context: Context) {
        viewModelScope.launch {
            settingsDataStore.setNotificationEnabled(enabled)
            if (enabled) {
                val language = settingsDataStore.userSettingsFlow.first().language
                CaptureNotificationManager.showCaptureNotification(context, language)
            } else CaptureNotificationManager.dismissCaptureNotification(context)
        }
    }

    fun setDefaultSaveFolder(uri: Uri, contentResolver: ContentResolver) {
        runCatching { contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        viewModelScope.launch { settingsDataStore.setDefaultSaveFolderUri(uri.toString()) }
    }
    fun clearDefaultSaveFolder() { viewModelScope.launch { settingsDataStore.setDefaultSaveFolderUri(null) } }
    fun setRetentionPolicy(policy: RetentionPolicy) {
        viewModelScope.launch {
            settingsDataStore.setRetentionPolicy(policy)
            val deleted = repository.cleanupOldCards(policy)
            if (deleted > 0) {
                val language = settingsDataStore.userSettingsFlow.first().language
                _eventFlow.emit(SettingsEvent.ShowToast(appContext.withAppLanguage(language).getString(com.clipnest.R.string.cleaned_old_cards, deleted)))
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
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) return SettingsViewModel(settingsDataStore, repository, fileManager, appContext) as T
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
