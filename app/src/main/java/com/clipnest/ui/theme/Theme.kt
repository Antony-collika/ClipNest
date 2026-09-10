package com.clipnest.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.clipnest.data.local.ThemePreset

private val darkPresetThemes = setOf(
    ThemePreset.DARK,
    ThemePreset.MIDNIGHT_BLUE,
    ThemePreset.NORD,
    ThemePreset.MIDNIGHT_OLED
)

@Composable
fun ClipNestTheme(
    themePreset: ThemePreset = ThemePreset.LIGHT,
    content: @Composable () -> Unit
) {
    val palette = themePaletteFor(themePreset)
    val colorScheme = if (themePreset in darkPresetThemes) palette.dark else palette.light
    CompositionLocalProvider(LocalThemePalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
