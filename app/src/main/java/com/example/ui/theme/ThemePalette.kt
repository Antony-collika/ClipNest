package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.data.local.ThemePreset

data class ThemeSemanticColors(
    val pinned: Color,
    val sensitive: Color
)

data class ThemePalette(
    val light: ColorScheme,
    val dark: ColorScheme,
    val lightSemantic: ThemeSemanticColors,
    val darkSemantic: ThemeSemanticColors
) {
    fun semanticColors(isDark: Boolean): ThemeSemanticColors =
        if (isDark) darkSemantic else lightSemantic
}

val EmeraldThemePalette = ThemePalette(
    light = lightColorScheme(
        primary = EmeraldPrimaryLight,
        onPrimary = OnEmeraldPrimaryLight,
        primaryContainer = EmeraldContainerLight,
        onPrimaryContainer = OnEmeraldContainerLight,
        secondary = EmeraldSecondaryLight,
        onSecondary = OnEmeraldSecondaryLight,
        secondaryContainer = EmeraldSecondaryContainerLight,
        onSecondaryContainer = OnEmeraldSecondaryContainerLight,
        tertiary = EmeraldTertiaryLight,
        onTertiary = OnEmeraldTertiaryLight,
        tertiaryContainer = EmeraldTertiaryContainerLight,
        onTertiaryContainer = OnEmeraldTertiaryContainerLight,
        background = BackgroundLight,
        onBackground = OnBackgroundLight,
        surface = SurfaceLight,
        onSurface = OnSurfaceLight,
        surfaceVariant = SurfaceVariantLight,
        onSurfaceVariant = OnSurfaceVariantLight,
        outline = OutlineLight,
        outlineVariant = OutlineVariantLight
    ),
    dark = darkColorScheme(
        primary = EmeraldPrimaryDark,
        onPrimary = OnEmeraldPrimaryDark,
        primaryContainer = EmeraldContainerDark,
        onPrimaryContainer = OnEmeraldContainerDark,
        secondary = EmeraldSecondaryDark,
        onSecondary = OnEmeraldSecondaryDark,
        secondaryContainer = EmeraldSecondaryContainerDark,
        onSecondaryContainer = OnEmeraldSecondaryContainerDark,
        tertiary = EmeraldTertiaryDark,
        onTertiary = OnEmeraldTertiaryDark,
        tertiaryContainer = EmeraldTertiaryContainerDark,
        onTertiaryContainer = OnEmeraldTertiaryContainerDark,
        background = BackgroundDark,
        onBackground = OnBackgroundDark,
        surface = SurfaceDark,
        onSurface = OnSurfaceDark,
        surfaceVariant = SurfaceVariantDark,
        onSurfaceVariant = OnSurfaceVariantDark,
        outline = OutlineDark,
        outlineVariant = OutlineVariantDark
    ),
    lightSemantic = ThemeSemanticColors(PinnedGreenLight, SensitiveAmberLight),
    darkSemantic = ThemeSemanticColors(PinnedGreenDark, SensitiveAmberDark)
)

val OceanThemePalette = ThemePalette(
    light = lightColorScheme(
        primary = Color(0xFF1B5E92),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFC9E6FF),
        onPrimaryContainer = Color(0xFF001D32),
        secondary = Color(0xFF4D6475),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD0E5F6),
        onSecondaryContainer = Color(0xFF081E2D),
        tertiary = Color(0xFF00696C),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFF9DF0F0),
        onTertiaryContainer = Color(0xFF002021),
        background = Color(0xFFF7FAFE),
        onBackground = Color(0xFF181C20),
        surface = Color(0xFFF7FAFE),
        onSurface = Color(0xFF181C20),
        surfaceVariant = Color(0xFFDEE3EA),
        onSurfaceVariant = Color(0xFF41474D),
        outline = Color(0xFF71787F),
        outlineVariant = Color(0xFFC1C7CF)
    ),
    dark = darkColorScheme(
        primary = Color(0xFF9ACBFF),
        onPrimary = Color(0xFF003354),
        primaryContainer = Color(0xFF004B72),
        onPrimaryContainer = Color(0xFFC9E6FF),
        secondary = Color(0xFFB4C9DA),
        onSecondary = Color(0xFF1E333F),
        secondaryContainer = Color(0xFF354A57),
        onSecondaryContainer = Color(0xFFD0E5F6),
        tertiary = Color(0xFF80D4D6),
        onTertiary = Color(0xFF003739),
        tertiaryContainer = Color(0xFF004F51),
        onTertiaryContainer = Color(0xFF9DF0F0),
        background = Color(0xFF101417),
        onBackground = Color(0xFFE0E5E9),
        surface = Color(0xFF101417),
        onSurface = Color(0xFFE0E5E9),
        surfaceVariant = Color(0xFF41474D),
        onSurfaceVariant = Color(0xFFC1C7CF),
        outline = Color(0xFF8B9299),
        outlineVariant = Color(0xFF41474D)
    ),
    lightSemantic = ThemeSemanticColors(Color(0xFF1D4ED8), Color(0xFFB45309)),
    darkSemantic = ThemeSemanticColors(Color(0xFF93C5FD), Color(0xFFFBBF24))
)

val VioletThemePalette = ThemePalette(
    light = lightColorScheme(
        primary = Color(0xFF6750A4),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFEADDFF),
        onPrimaryContainer = Color(0xFF21005D),
        secondary = Color(0xFF625B71),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE8DEF8),
        onSecondaryContainer = Color(0xFF1D192B),
        tertiary = Color(0xFF7D5260),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFD8E4),
        onTertiaryContainer = Color(0xFF31111D),
        background = Color(0xFFFFFBFE),
        onBackground = Color(0xFF1D1B20),
        surface = Color(0xFFFFFBFE),
        onSurface = Color(0xFF1D1B20),
        surfaceVariant = Color(0xFFE7E0EC),
        onSurfaceVariant = Color(0xFF49454F),
        outline = Color(0xFF79747E),
        outlineVariant = Color(0xFFCAC4D0)
    ),
    dark = darkColorScheme(
        primary = Color(0xFFD0BCFF),
        onPrimary = Color(0xFF381E72),
        primaryContainer = Color(0xFF4F378B),
        onPrimaryContainer = Color(0xFFEADDFF),
        secondary = Color(0xFFCCC2DC),
        onSecondary = Color(0xFF332D41),
        secondaryContainer = Color(0xFF4A4458),
        onSecondaryContainer = Color(0xFFE8DEF8),
        tertiary = Color(0xFFEFB8C8),
        onTertiary = Color(0xFF492532),
        tertiaryContainer = Color(0xFF633B48),
        onTertiaryContainer = Color(0xFFFFD8E4),
        background = Color(0xFF141218),
        onBackground = Color(0xFFE6E0E9),
        surface = Color(0xFF141218),
        onSurface = Color(0xFFE6E0E9),
        surfaceVariant = Color(0xFF49454F),
        onSurfaceVariant = Color(0xFFCAC4D0),
        outline = Color(0xFF938F99),
        outlineVariant = Color(0xFF49454F)
    ),
    lightSemantic = ThemeSemanticColors(Color(0xFF6B21A8), Color(0xFFB45309)),
    darkSemantic = ThemeSemanticColors(Color(0xFFD8B4FE), Color(0xFFFBBF24))
)

val SunsetThemePalette = ThemePalette(
    light = lightColorScheme(
        primary = Color(0xFFB9472E),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFDAD1),
        onPrimaryContainer = Color(0xFF3B0903),
        secondary = Color(0xFF77574E),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFDBD0),
        onSecondaryContainer = Color(0xFF2C1510),
        tertiary = Color(0xFF6A5E2F),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFF1E3A6),
        onTertiaryContainer = Color(0xFF211B00),
        background = Color(0xFFFFF8F6),
        onBackground = Color(0xFF201A18),
        surface = Color(0xFFFFF8F6),
        onSurface = Color(0xFF201A18),
        surfaceVariant = Color(0xFFF5DED8),
        onSurfaceVariant = Color(0xFF57433E),
        outline = Color(0xFF89736D),
        outlineVariant = Color(0xFFD8C2BC)
    ),
    dark = darkColorScheme(
        primary = Color(0xFFFFB5A2),
        onPrimary = Color(0xFF5F1608),
        primaryContainer = Color(0xFF8F2F1D),
        onPrimaryContainer = Color(0xFFFFDAD1),
        secondary = Color(0xFFE7BDB1),
        onSecondary = Color(0xFF432A23),
        secondaryContainer = Color(0xFF5C4037),
        onSecondaryContainer = Color(0xFFFFDBD0),
        tertiary = Color(0xFFD4C98E),
        onTertiary = Color(0xFF373000),
        tertiaryContainer = Color(0xFF514719),
        onTertiaryContainer = Color(0xFFF1E3A6),
        background = Color(0xFF191110),
        onBackground = Color(0xFFF0DFDB),
        surface = Color(0xFF191110),
        onSurface = Color(0xFFF0DFDB),
        surfaceVariant = Color(0xFF57433E),
        onSurfaceVariant = Color(0xFFD8C2BC),
        outline = Color(0xFFA38C85),
        outlineVariant = Color(0xFF57433E)
    ),
    lightSemantic = ThemeSemanticColors(Color(0xFFB45309), Color(0xFFB45309)),
    darkSemantic = ThemeSemanticColors(Color(0xFFFFB09A), Color(0xFFFFCC80))
)

val GraphiteThemePalette = ThemePalette(
    light = lightColorScheme(
        primary = Color(0xFF00695C),
        onPrimary = Color.White,
        primaryContainer = Color(0xFF9CF0E2),
        onPrimaryContainer = Color(0xFF00201B),
        secondary = Color(0xFF53615E),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD6E5E0),
        onSecondaryContainer = Color(0xFF0F1A17),
        tertiary = Color(0xFF4B6078),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFD2E4FF),
        onTertiaryContainer = Color(0xFF061B2F),
        background = Color(0xFFF7F8F8),
        onBackground = Color(0xFF191C1C),
        surface = Color(0xFFF7F8F8),
        onSurface = Color(0xFF191C1C),
        surfaceVariant = Color(0xFFDDE5E2),
        onSurfaceVariant = Color(0xFF404947),
        outline = Color(0xFF707976),
        outlineVariant = Color(0xFFC0C9C5)
    ),
    dark = darkColorScheme(
        primary = Color(0xFF80D5C5),
        onPrimary = Color(0xFF003731),
        primaryContainer = Color(0xFF005048),
        onPrimaryContainer = Color(0xFF9CF0E2),
        secondary = Color(0xFFB9CCC7),
        onSecondary = Color(0xFF243330),
        secondaryContainer = Color(0xFF3A4A47),
        onSecondaryContainer = Color(0xFFD6E5E0),
        tertiary = Color(0xFFB4C8E5),
        onTertiary = Color(0xFF1C334A),
        tertiaryContainer = Color(0xFF334A62),
        onTertiaryContainer = Color(0xFFD2E4FF),
        background = Color(0xFF181A1B),
        onBackground = Color(0xFFE1E3E3),
        surface = Color(0xFF202223),
        onSurface = Color(0xFFE1E3E3),
        surfaceVariant = Color(0xFF414947),
        onSurfaceVariant = Color(0xFFC0C9C5),
        outline = Color(0xFF8A9390),
        outlineVariant = Color(0xFF414947)
    ),
    lightSemantic = ThemeSemanticColors(Color(0xFF087F6B), Color(0xFFB45309)),
    darkSemantic = ThemeSemanticColors(Color(0xFF99F6E4), Color(0xFFFFCC80))
)

fun themePaletteFor(preset: ThemePreset): ThemePalette = when (preset) {
    ThemePreset.EMERALD -> EmeraldThemePalette
    ThemePreset.OCEAN -> OceanThemePalette
    ThemePreset.VIOLET -> VioletThemePalette
    ThemePreset.SUNSET -> SunsetThemePalette
    ThemePreset.GRAPHITE -> GraphiteThemePalette
}

val LocalThemePalette = staticCompositionLocalOf { EmeraldThemePalette }
