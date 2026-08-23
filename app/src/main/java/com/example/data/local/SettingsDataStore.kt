package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class RetentionPolicy(val days: Int, val label: String) {
    NEVER(0, "Never auto-delete"),
    DAYS_7(7, "After 7 days"),
    DAYS_30(30, "After 30 days"),
    DAYS_90(90, "After 90 days")
}

data class UserSettings(
    val showPinnedFirst: Boolean = false,
    val isSensitivePreviewMasked: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val notificationEnabled: Boolean = true,
    val firstRunEducationShown: Boolean = false,
    val retentionPolicy: RetentionPolicy = RetentionPolicy.NEVER,
    val defaultSaveFolderUri: String? = null
)

class SettingsDataStore(private val context: Context) {

    private object PreferencesKeys {
        val SHOW_PINNED_FIRST = booleanPreferencesKey("show_pinned_first")
        val SENSITIVE_PREVIEW_MASKED = booleanPreferencesKey("sensitive_preview_masked")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val NOTIFICATION_ENABLED = booleanPreferencesKey("notification_enabled")
        val FIRST_RUN_EDUCATION_SHOWN = booleanPreferencesKey("first_run_education_shown")
        val RETENTION_POLICY = stringPreferencesKey("retention_policy")
        val DEFAULT_SAVE_FOLDER_URI = stringPreferencesKey("default_save_folder_uri")
    }

    val userSettingsFlow: Flow<UserSettings> = context.dataStore.data.map { preferences ->
        val themeMode = runCatching {
            ThemeMode.valueOf(preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM)
        val retention = runCatching {
            RetentionPolicy.valueOf(preferences[PreferencesKeys.RETENTION_POLICY] ?: RetentionPolicy.NEVER.name)
        }.getOrDefault(RetentionPolicy.NEVER)

        UserSettings(
            showPinnedFirst = preferences[PreferencesKeys.SHOW_PINNED_FIRST] ?: false,
            isSensitivePreviewMasked = preferences[PreferencesKeys.SENSITIVE_PREVIEW_MASKED] ?: true,
            themeMode = themeMode,
            notificationEnabled = preferences[PreferencesKeys.NOTIFICATION_ENABLED] ?: true,
            firstRunEducationShown = preferences[PreferencesKeys.FIRST_RUN_EDUCATION_SHOWN] ?: false,
            retentionPolicy = retention,
            defaultSaveFolderUri = preferences[PreferencesKeys.DEFAULT_SAVE_FOLDER_URI]
        )
    }

    suspend fun setShowPinnedFirst(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.SHOW_PINNED_FIRST] = enabled }
    }

    suspend fun setSensitivePreviewMasked(masked: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.SENSITIVE_PREVIEW_MASKED] = masked }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[PreferencesKeys.THEME_MODE] = mode.name }
    }

    suspend fun setNotificationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.NOTIFICATION_ENABLED] = enabled }
    }

    suspend fun setFirstRunEducationShown(shown: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.FIRST_RUN_EDUCATION_SHOWN] = shown }
    }

    suspend fun setRetentionPolicy(policy: RetentionPolicy) {
        context.dataStore.edit { it[PreferencesKeys.RETENTION_POLICY] = policy.name }
    }

    suspend fun setDefaultSaveFolderUri(uri: String?) {
        context.dataStore.edit { preferences ->
            if (uri.isNullOrBlank()) {
                preferences.remove(PreferencesKeys.DEFAULT_SAVE_FOLDER_URI)
            } else {
                preferences[PreferencesKeys.DEFAULT_SAVE_FOLDER_URI] = uri
            }
        }
    }
}
