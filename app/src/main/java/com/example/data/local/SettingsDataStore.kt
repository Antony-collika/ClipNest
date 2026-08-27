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

enum class ThemePreset {
    LIGHT,
    DARK,
    MIDNIGHT_BLUE,
    FOREST,
    LAVENDER,
    NORD,
    SOLARIZED,
    SOFT_PAPER_CREAM,
    MIDNIGHT_OLED,
    SAGE_SLATE
}

enum class EditorTextSize(val sp: Int, val lineHeightSp: Int) {
    VERY_SMALL(12, 15),
    SMALL(13, 16),
    DEFAULT(14, 17),
    LARGE(16, 20),
    VERY_LARGE(18, 22),
    HUGE(20, 25)
}

enum class ViewerTextSize(val px: Int) {
    VERY_SMALL(14),
    SMALL(15),
    DEFAULT(16),
    LARGE(18),
    VERY_LARGE(20),
    HUGE(22)
}

enum class AppLanguage {
    ENGLISH,
    VIETNAMESE
}

enum class RetentionPolicy(val days: Int, val label: String) {
    NEVER(0, "Never auto-delete"),
    DAYS_7(7, "After 7 days"),
    DAYS_30(30, "After 30 days"),
    DAYS_90(90, "After 90 days")
}

data class UserSettings(
    val language: AppLanguage = AppLanguage.ENGLISH,
    val showPinnedFirst: Boolean = false,
    val isSensitivePreviewMasked: Boolean = true,
    val themePreset: ThemePreset = ThemePreset.LIGHT,
    val editorTextSize: EditorTextSize = EditorTextSize.DEFAULT,
    val viewerTextSize: ViewerTextSize = ViewerTextSize.DEFAULT,
    val notificationEnabled: Boolean = true,
    val firstRunEducationShown: Boolean = false,
    val retentionPolicy: RetentionPolicy = RetentionPolicy.NEVER,
    val defaultSaveFolderUri: String? = null
)

class SettingsDataStore(private val context: Context) {

    private object PreferencesKeys {
        val LANGUAGE = stringPreferencesKey("language")
        val SHOW_PINNED_FIRST = booleanPreferencesKey("show_pinned_first")
        val SENSITIVE_PREVIEW_MASKED = booleanPreferencesKey("sensitive_preview_masked")
        val THEME_PRESET = stringPreferencesKey("theme_preset")
        val EDITOR_TEXT_SIZE = stringPreferencesKey("editor_text_size")
        val VIEWER_TEXT_SIZE = stringPreferencesKey("viewer_text_size")
        val NOTIFICATION_ENABLED = booleanPreferencesKey("notification_enabled")
        val FIRST_RUN_EDUCATION_SHOWN = booleanPreferencesKey("first_run_education_shown")
        val RETENTION_POLICY = stringPreferencesKey("retention_policy")
        val DEFAULT_SAVE_FOLDER_URI = stringPreferencesKey("default_save_folder_uri")
    }

    val userSettingsFlow: Flow<UserSettings> = context.dataStore.data.map { preferences ->
        val language = runCatching {
            AppLanguage.valueOf(preferences[PreferencesKeys.LANGUAGE] ?: AppLanguage.ENGLISH.name)
        }.getOrDefault(AppLanguage.ENGLISH)
        val themePreset = runCatching {
            ThemePreset.valueOf(preferences[PreferencesKeys.THEME_PRESET] ?: ThemePreset.LIGHT.name)
        }.getOrDefault(ThemePreset.LIGHT)
        val editorTextSize = runCatching {
            EditorTextSize.valueOf(preferences[PreferencesKeys.EDITOR_TEXT_SIZE] ?: EditorTextSize.DEFAULT.name)
        }.getOrDefault(EditorTextSize.DEFAULT)
        val viewerTextSize = runCatching {
            ViewerTextSize.valueOf(preferences[PreferencesKeys.VIEWER_TEXT_SIZE] ?: ViewerTextSize.DEFAULT.name)
        }.getOrDefault(ViewerTextSize.DEFAULT)
        val retention = runCatching {
            RetentionPolicy.valueOf(preferences[PreferencesKeys.RETENTION_POLICY] ?: RetentionPolicy.NEVER.name)
        }.getOrDefault(RetentionPolicy.NEVER)

        UserSettings(
            language = language,
            showPinnedFirst = preferences[PreferencesKeys.SHOW_PINNED_FIRST] ?: false,
            isSensitivePreviewMasked = preferences[PreferencesKeys.SENSITIVE_PREVIEW_MASKED] ?: true,
            themePreset = themePreset,
            editorTextSize = editorTextSize,
            viewerTextSize = viewerTextSize,
            notificationEnabled = preferences[PreferencesKeys.NOTIFICATION_ENABLED] ?: true,
            firstRunEducationShown = preferences[PreferencesKeys.FIRST_RUN_EDUCATION_SHOWN] ?: false,
            retentionPolicy = retention,
            defaultSaveFolderUri = preferences[PreferencesKeys.DEFAULT_SAVE_FOLDER_URI]
        )
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[PreferencesKeys.LANGUAGE] = language.name }
    }

    suspend fun setShowPinnedFirst(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.SHOW_PINNED_FIRST] = enabled }
    }

    suspend fun setSensitivePreviewMasked(masked: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.SENSITIVE_PREVIEW_MASKED] = masked }
    }

    suspend fun setThemePreset(preset: ThemePreset) {
        context.dataStore.edit { it[PreferencesKeys.THEME_PRESET] = preset.name }
    }

    suspend fun setEditorTextSize(size: EditorTextSize) {
        context.dataStore.edit { it[PreferencesKeys.EDITOR_TEXT_SIZE] = size.name }
    }

    suspend fun setViewerTextSize(size: ViewerTextSize) {
        context.dataStore.edit { it[PreferencesKeys.VIEWER_TEXT_SIZE] = size.name }
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
