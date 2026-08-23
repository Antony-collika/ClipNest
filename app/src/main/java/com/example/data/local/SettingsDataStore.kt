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

enum class NoteDocType(val fileName: String, val displayName: String) {
    NOTE("Note.md", "Note"),
    DRAFT("Draft.md", "Draft")
}

data class UserSettings(
    val showPinnedFirst: Boolean = false,
    val isSensitivePreviewMasked: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val notificationEnabled: Boolean = true,
    val firstRunEducationShown: Boolean = false,
    val retentionPolicy: RetentionPolicy = RetentionPolicy.NEVER,
    val lastActiveNoteFile: NoteDocType = NoteDocType.DRAFT
)

class SettingsDataStore(private val context: Context) {

    private object PreferencesKeys {
        val SHOW_PINNED_FIRST = booleanPreferencesKey("show_pinned_first")
        val SENSITIVE_PREVIEW_MASKED = booleanPreferencesKey("sensitive_preview_masked")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val NOTIFICATION_ENABLED = booleanPreferencesKey("notification_enabled")
        val FIRST_RUN_EDUCATION_SHOWN = booleanPreferencesKey("first_run_education_shown")
        val RETENTION_POLICY = stringPreferencesKey("retention_policy")
        val LAST_ACTIVE_NOTE_FILE = stringPreferencesKey("last_active_note_file")
    }

    val userSettingsFlow: Flow<UserSettings> = context.dataStore.data.map { preferences ->
        val showPinnedFirst = preferences[PreferencesKeys.SHOW_PINNED_FIRST] ?: false
        val isSensitiveMasked = preferences[PreferencesKeys.SENSITIVE_PREVIEW_MASKED] ?: true
        val themeModeStr = preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        val themeMode = try {
            ThemeMode.valueOf(themeModeStr)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }
        val notificationEnabled = preferences[PreferencesKeys.NOTIFICATION_ENABLED] ?: true
        val firstRunShown = preferences[PreferencesKeys.FIRST_RUN_EDUCATION_SHOWN] ?: false
        val retentionStr = preferences[PreferencesKeys.RETENTION_POLICY] ?: RetentionPolicy.NEVER.name
        val retention = try {
            RetentionPolicy.valueOf(retentionStr)
        } catch (_: Exception) {
            RetentionPolicy.NEVER
        }
        val lastNoteStr = preferences[PreferencesKeys.LAST_ACTIVE_NOTE_FILE] ?: NoteDocType.DRAFT.name
        val lastNote = try {
            NoteDocType.valueOf(lastNoteStr)
        } catch (_: Exception) {
            NoteDocType.DRAFT
        }

        UserSettings(
            showPinnedFirst = showPinnedFirst,
            isSensitivePreviewMasked = isSensitiveMasked,
            themeMode = themeMode,
            notificationEnabled = notificationEnabled,
            firstRunEducationShown = firstRunShown,
            retentionPolicy = retention,
            lastActiveNoteFile = lastNote
        )
    }

    suspend fun setShowPinnedFirst(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOW_PINNED_FIRST] = enabled
        }
    }

    suspend fun setSensitivePreviewMasked(masked: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SENSITIVE_PREVIEW_MASKED] = masked
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode.name
        }
    }

    suspend fun setNotificationEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.NOTIFICATION_ENABLED] = enabled
        }
    }

    suspend fun setFirstRunEducationShown(shown: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.FIRST_RUN_EDUCATION_SHOWN] = shown
        }
    }

    suspend fun setRetentionPolicy(policy: RetentionPolicy) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.RETENTION_POLICY] = policy.name
        }
    }

    suspend fun setLastActiveNoteFile(docType: NoteDocType) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_ACTIVE_NOTE_FILE] = docType.name
        }
    }
}
