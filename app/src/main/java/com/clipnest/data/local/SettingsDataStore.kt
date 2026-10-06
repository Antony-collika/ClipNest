package com.clipnest.data.local

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
    FOREST,
    NORD,
    SNOW_SAPPHIRE,
    SAKURA,
    LAVENDER,
    LIGHT_BASIC,
    BASIC_DARK,
    DEEP_OCEAN,
    COFFEE,
    OBSIDIAN
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
    val themePreset: ThemePreset = ThemePreset.FOREST,
    val backgroundImageUri: String? = null,
    val backgroundColorHex: String? = null,
    val noteCardBackgroundHex: String? = null,
    val noteTitleTextHex: String? = null,
    val notePreviewTextHex: String? = null,
    val vaultCardBackgroundHex: String? = null,
    val vaultPreviewTextHex: String? = null,
    val vaultMetaTextHex: String? = null,
    val editorTextSize: EditorTextSize = EditorTextSize.DEFAULT,
    val viewerTextSize: ViewerTextSize = ViewerTextSize.DEFAULT,
    val notificationEnabled: Boolean = true,
    val firstRunEducationShown: Boolean = false,
    val retentionPolicy: RetentionPolicy = RetentionPolicy.NEVER,
    val defaultSaveFolderUri: String? = null,
    val aiProvider: String = "VERCEL",
    val geminiModelId: String = "gemini-3.5-flash-lite",
    val vercelModelId: String = "gemini-3.5-flash-lite",
    val aiPrompt: String = ""
)

class SettingsDataStore(private val context: Context) {

    private object PreferencesKeys {
        val LANGUAGE = stringPreferencesKey("language")
        val SHOW_PINNED_FIRST = booleanPreferencesKey("show_pinned_first")
        val SENSITIVE_PREVIEW_MASKED = booleanPreferencesKey("sensitive_preview_masked")
        val THEME_PRESET = stringPreferencesKey("theme_preset")
        val BACKGROUND_IMAGE_URI = stringPreferencesKey("background_image_uri")
        val BACKGROUND_COLOR_HEX = stringPreferencesKey("background_color_hex")
        val NOTE_CARD_BACKGROUND_HEX = stringPreferencesKey("note_card_background_hex")
        val NOTE_TITLE_TEXT_HEX = stringPreferencesKey("note_title_text_hex")
        val NOTE_PREVIEW_TEXT_HEX = stringPreferencesKey("note_preview_text_hex")
        val VAULT_CARD_BACKGROUND_HEX = stringPreferencesKey("vault_card_background_hex")
        val VAULT_PREVIEW_TEXT_HEX = stringPreferencesKey("vault_preview_text_hex")
        val VAULT_META_TEXT_HEX = stringPreferencesKey("vault_meta_text_hex")
        val EDITOR_TEXT_SIZE = stringPreferencesKey("editor_text_size")
        val VIEWER_TEXT_SIZE = stringPreferencesKey("viewer_text_size")
        val NOTIFICATION_ENABLED = booleanPreferencesKey("notification_enabled")
        val FIRST_RUN_EDUCATION_SHOWN = booleanPreferencesKey("first_run_education_shown")
        val RETENTION_POLICY = stringPreferencesKey("retention_policy")
        val DEFAULT_SAVE_FOLDER_URI = stringPreferencesKey("default_save_folder_uri")
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val GEMINI_MODEL_ID = stringPreferencesKey("gemini_model_id")
        val VERCEL_MODEL_ID = stringPreferencesKey("vercel_model_id")
        val AI_PROMPT = stringPreferencesKey("ai_prompt")
    }

    val userSettingsFlow: Flow<UserSettings> = context.dataStore.data.map { preferences ->
        val language = runCatching {
            AppLanguage.valueOf(preferences[PreferencesKeys.LANGUAGE] ?: AppLanguage.ENGLISH.name)
        }.getOrDefault(AppLanguage.ENGLISH)
        val themePreset = runCatching {
            ThemePreset.valueOf(preferences[PreferencesKeys.THEME_PRESET] ?: ThemePreset.FOREST.name)
        }.getOrDefault(ThemePreset.FOREST)
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
            backgroundImageUri = preferences[PreferencesKeys.BACKGROUND_IMAGE_URI],
            backgroundColorHex = preferences[PreferencesKeys.BACKGROUND_COLOR_HEX],
            noteCardBackgroundHex = preferences[PreferencesKeys.NOTE_CARD_BACKGROUND_HEX],
            noteTitleTextHex = preferences[PreferencesKeys.NOTE_TITLE_TEXT_HEX],
            notePreviewTextHex = preferences[PreferencesKeys.NOTE_PREVIEW_TEXT_HEX],
            vaultCardBackgroundHex = preferences[PreferencesKeys.VAULT_CARD_BACKGROUND_HEX],
            vaultPreviewTextHex = preferences[PreferencesKeys.VAULT_PREVIEW_TEXT_HEX],
            vaultMetaTextHex = preferences[PreferencesKeys.VAULT_META_TEXT_HEX],
            editorTextSize = editorTextSize,
            viewerTextSize = viewerTextSize,
            notificationEnabled = preferences[PreferencesKeys.NOTIFICATION_ENABLED] ?: true,
            firstRunEducationShown = preferences[PreferencesKeys.FIRST_RUN_EDUCATION_SHOWN] ?: false,
            retentionPolicy = retention,
            defaultSaveFolderUri = preferences[PreferencesKeys.DEFAULT_SAVE_FOLDER_URI],
            aiProvider = preferences[PreferencesKeys.AI_PROVIDER] ?: "VERCEL",
            geminiModelId = preferences[PreferencesKeys.GEMINI_MODEL_ID] ?: "gemini-3.5-flash-lite",
            vercelModelId = preferences[PreferencesKeys.VERCEL_MODEL_ID] ?: "gemini-3.5-flash-lite",
            aiPrompt = preferences[PreferencesKeys.AI_PROMPT] ?: ""
        )
    }

    suspend fun setLanguage(language: AppLanguage) { context.dataStore.edit { it[PreferencesKeys.LANGUAGE] = language.name } }
    suspend fun setShowPinnedFirst(enabled: Boolean) { context.dataStore.edit { it[PreferencesKeys.SHOW_PINNED_FIRST] = enabled } }
    suspend fun setSensitivePreviewMasked(masked: Boolean) { context.dataStore.edit { it[PreferencesKeys.SENSITIVE_PREVIEW_MASKED] = masked } }
    suspend fun setThemePreset(preset: ThemePreset) { context.dataStore.edit { it[PreferencesKeys.THEME_PRESET] = preset.name } }

    suspend fun setBackgroundImageUri(uri: String?) = setOptionalString(PreferencesKeys.BACKGROUND_IMAGE_URI, uri)
    suspend fun setBackgroundColorHex(value: String?) = setOptionalString(PreferencesKeys.BACKGROUND_COLOR_HEX, value)
    suspend fun setNoteCardBackgroundHex(value: String?) = setOptionalString(PreferencesKeys.NOTE_CARD_BACKGROUND_HEX, value)
    suspend fun setNoteTitleTextHex(value: String?) = setOptionalString(PreferencesKeys.NOTE_TITLE_TEXT_HEX, value)
    suspend fun setNotePreviewTextHex(value: String?) = setOptionalString(PreferencesKeys.NOTE_PREVIEW_TEXT_HEX, value)
    suspend fun setVaultCardBackgroundHex(value: String?) = setOptionalString(PreferencesKeys.VAULT_CARD_BACKGROUND_HEX, value)
    suspend fun setVaultPreviewTextHex(value: String?) = setOptionalString(PreferencesKeys.VAULT_PREVIEW_TEXT_HEX, value)
    suspend fun setVaultMetaTextHex(value: String?) = setOptionalString(PreferencesKeys.VAULT_META_TEXT_HEX, value)

    private suspend fun setOptionalString(key: Preferences.Key<String>, value: String?) {
        context.dataStore.edit { preferences ->
            if (value.isNullOrBlank()) preferences.remove(key) else preferences[key] = value.trim()
        }
    }
    suspend fun setEditorTextSize(size: EditorTextSize) { context.dataStore.edit { it[PreferencesKeys.EDITOR_TEXT_SIZE] = size.name } }
    suspend fun setViewerTextSize(size: ViewerTextSize) { context.dataStore.edit { it[PreferencesKeys.VIEWER_TEXT_SIZE] = size.name } }
    suspend fun setNotificationEnabled(enabled: Boolean) { context.dataStore.edit { it[PreferencesKeys.NOTIFICATION_ENABLED] = enabled } }
    suspend fun setFirstRunEducationShown(shown: Boolean) { context.dataStore.edit { it[PreferencesKeys.FIRST_RUN_EDUCATION_SHOWN] = shown } }
    suspend fun setRetentionPolicy(policy: RetentionPolicy) { context.dataStore.edit { it[PreferencesKeys.RETENTION_POLICY] = policy.name } }
    suspend fun setAiProvider(provider: String) { context.dataStore.edit { it[PreferencesKeys.AI_PROVIDER] = provider } }
    suspend fun setGeminiModelId(modelId: String) { context.dataStore.edit { it[PreferencesKeys.GEMINI_MODEL_ID] = modelId } }
    suspend fun setVercelModelId(modelId: String) { context.dataStore.edit { it[PreferencesKeys.VERCEL_MODEL_ID] = modelId } }
    suspend fun setAiPrompt(prompt: String) { context.dataStore.edit { it[PreferencesKeys.AI_PROMPT] = prompt } }

    suspend fun setDefaultSaveFolderUri(uri: String?) {
        context.dataStore.edit { preferences ->
            if (uri.isNullOrBlank()) preferences.remove(PreferencesKeys.DEFAULT_SAVE_FOLDER_URI)
            else preferences[PreferencesKeys.DEFAULT_SAVE_FOLDER_URI] = uri
        }
    }
}
