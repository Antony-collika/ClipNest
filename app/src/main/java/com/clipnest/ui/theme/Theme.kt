package com.clipnest.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.clipnest.data.local.ThemePreset

@Composable
fun ClipNestTheme(
    themePreset: ThemePreset = ThemePreset.FOREST,
    content: @Composable () -> Unit
) {
    val palette = themePaletteFor(themePreset)
    val colorScheme = palette.colorScheme
    CompositionLocalProvider(LocalThemePalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
