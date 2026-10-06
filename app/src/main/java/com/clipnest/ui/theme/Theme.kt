package com.clipnest.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.clipnest.data.local.ThemePreset
import com.clipnest.data.local.UserSettings

@Composable
fun ClipNestTheme(
    themePreset: ThemePreset = ThemePreset.FOREST,
    userSettings: UserSettings? = null,
    content: @Composable () -> Unit
) {
    val palette = themePaletteFor(themePreset, userSettings)
    CompositionLocalProvider(LocalThemePalette provides palette) {
        MaterialTheme(
            colorScheme = palette.colorScheme,
            typography = Typography,
            content = content
        )
    }
}

fun themePaletteFor(preset: ThemePreset, userSettings: UserSettings? = null): ThemePalette {
    val base = when (preset) {
        ThemePreset.FOREST -> ForestThemePalette
        ThemePreset.NORD -> NordThemePalette
        ThemePreset.SNOW_SAPPHIRE -> SnowSapphireThemePalette
        ThemePreset.SAKURA -> SakuraThemePalette
        ThemePreset.LAVENDER -> LavenderThemePalette
        ThemePreset.LIGHT_BASIC -> LightBasicThemePalette
        ThemePreset.BASIC_DARK -> BasicDarkThemePalette
        ThemePreset.DEEP_OCEAN -> DeepOceanThemePalette
        ThemePreset.COFFEE -> CoffeeThemePalette
        ThemePreset.OBSIDIAN -> ObsidianThemePalette
    }
    if (userSettings == null) return base

    fun parse(value: String?): Color? = value?.let {
        runCatching {
            val normalized = it.trim().removePrefix("#")
            if (normalized.length == 6 || normalized.length == 8) {
                Color(android.graphics.Color.parseColor("#$normalized"))
            } else null
        }.getOrNull()
    }

    val background = parse(userSettings.backgroundColorHex) ?: base.screenOverlayBackground
    return base.copy(
        colorScheme = base.colorScheme.copy(background = background, surface = background),
        screenOverlayBackground = background,
        noteCardBackground = parse(userSettings.noteCardBackgroundHex) ?: base.noteCardBackground,
        noteTitle = parse(userSettings.noteTitleTextHex) ?: base.noteTitle,
        notePreviewText = parse(userSettings.notePreviewTextHex) ?: base.notePreviewText,
        vaultClipboardCardBackground = parse(userSettings.vaultCardBackgroundHex) ?: base.vaultClipboardCardBackground,
        vaultClipboardPreviewText = parse(userSettings.vaultPreviewTextHex) ?: base.vaultClipboardPreviewText,
        vaultClipboardMetaText = parse(userSettings.vaultMetaTextHex) ?: base.vaultClipboardMetaText
    )
}
