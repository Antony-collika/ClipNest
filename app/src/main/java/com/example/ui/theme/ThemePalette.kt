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

val NordThemePalette = ThemePalette(
    light = lightColorScheme(
        primary = Color(0xFF5E81AC), onPrimary = Color.White,
        primaryContainer = Color(0xFFD8DEE9), onPrimaryContainer = Color(0xFF2E3440),
        secondary = Color(0xFF4C566A), onSecondary = Color.White,
        secondaryContainer = Color(0xFFE5E9F0), onSecondaryContainer = Color(0xFF2E3440),
        tertiary = Color(0xFF8FBCBB), onTertiary = Color(0xFF163235),
        tertiaryContainer = Color(0xFFD8EEEE), onTertiaryContainer = Color(0xFF163235),
        background = Color(0xFFECEFF4), onBackground = Color(0xFF2E3440),
        surface = Color(0xFFECEFF4), onSurface = Color(0xFF2E3440),
        surfaceVariant = Color(0xFFE5E9F0), onSurfaceVariant = Color(0xFF4C566A),
        outline = Color(0xFF7B8799), outlineVariant = Color(0xFFD8DEE9)
    ),
    dark = darkColorScheme(
        primary = Color(0xFF88C0D0), onPrimary = Color(0xFF20333A),
        primaryContainer = Color(0xFF4C566A), onPrimaryContainer = Color(0xFFECEFF4),
        secondary = Color(0xFFD8DEE9), onSecondary = Color(0xFF2E3440),
        secondaryContainer = Color(0xFF434C5E), onSecondaryContainer = Color(0xFFE5E9F0),
        tertiary = Color(0xFFA3BE8C), onTertiary = Color(0xFF26351E),
        tertiaryContainer = Color(0xFF4A6741), onTertiaryContainer = Color(0xFFE5F2D9),
        background = Color(0xFF2E3440), onBackground = Color(0xFFECEFF4),
        surface = Color(0xFF3B4252), onSurface = Color(0xFFECEFF4),
        surfaceVariant = Color(0xFF434C5E), onSurfaceVariant = Color(0xFFD8DEE9),
        outline = Color(0xFF9AA5B5), outlineVariant = Color(0xFF4C566A)
    ),
    lightSemantic = ThemeSemanticColors(Color(0xFF2E7D5B), Color(0xFFB45309)),
    darkSemantic = ThemeSemanticColors(Color(0xFFA3BE8C), Color(0xFFEBCB8B))
)

val SolarizedThemePalette = ThemePalette(
    light = lightColorScheme(
        primary = Color(0xFF268BD2), onPrimary = Color.White,
        primaryContainer = Color(0xFFB9DDF3), onPrimaryContainer = Color(0xFF073A5A),
        secondary = Color(0xFF2AA198), onSecondary = Color.White,
        secondaryContainer = Color(0xFFB9E4DF), onSecondaryContainer = Color(0xFF063B38),
        tertiary = Color(0xFFB58900), onTertiary = Color(0xFF332700),
        tertiaryContainer = Color(0xFFF4E4AA), onTertiaryContainer = Color(0xFF332700),
        background = Color(0xFFFDF6E3), onBackground = Color(0xFF586E75),
        surface = Color(0xFFFDF6E3), onSurface = Color(0xFF586E75),
        surfaceVariant = Color(0xFFEEE8D5), onSurfaceVariant = Color(0xFF657B83),
        outline = Color(0xFF93A1A1), outlineVariant = Color(0xFFD9D2BD)
    ),
    dark = darkColorScheme(
        primary = Color(0xFF268BD2), onPrimary = Color.White,
        primaryContainer = Color(0xFF075985), onPrimaryContainer = Color(0xFFD2ECFF),
        secondary = Color(0xFF2AA198), onSecondary = Color(0xFF002B36),
        secondaryContainer = Color(0xFF12665F), onSecondaryContainer = Color(0xFFC7F2ED),
        tertiary = Color(0xFFB58900), onTertiary = Color(0xFF1F1B00),
        tertiaryContainer = Color(0xFF6D5300), onTertiaryContainer = Color(0xFFFFE9A6),
        background = Color(0xFF002B36), onBackground = Color(0xFF839496),
        surface = Color(0xFF073642), onSurface = Color(0xFF839496),
        surfaceVariant = Color(0xFF0B4654), onSurfaceVariant = Color(0xFF93A1A1),
        outline = Color(0xFF839496), outlineVariant = Color(0xFF315A63)
    ),
    lightSemantic = ThemeSemanticColors(Color(0xFF268BD2), Color(0xFFB58900)),
    darkSemantic = ThemeSemanticColors(Color(0xFF66B8F0), Color(0xFFEACB6E))
)

val SoftPaperCreamThemePalette = ThemePalette(
    light = lightColorScheme(
        primary = Color(0xFFD97706), onPrimary = Color.White,
        primaryContainer = Color(0xFFFFE1B3), onPrimaryContainer = Color(0xFF4B2600),
        secondary = Color(0xFF787774), onSecondary = Color.White,
        secondaryContainer = Color(0xFFE8E4DC), onSecondaryContainer = Color(0xFF292826),
        tertiary = Color(0xFF7C5C2E), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFF1DDBB), onTertiaryContainer = Color(0xFF2C1B08),
        background = Color(0xFFFBF9F5), onBackground = Color(0xFF2C2C2A),
        surface = Color(0xFFFBF9F5), onSurface = Color(0xFF2C2C2A),
        surfaceVariant = Color(0xFFF1EEE8), onSurfaceVariant = Color(0xFF787774),
        outline = Color(0xFF8D8A83), outlineVariant = Color(0xFFE2DED5)
    ),
    dark = darkColorScheme(
        primary = Color(0xFFFFB95E), onPrimary = Color(0xFF472600),
        primaryContainer = Color(0xFF8A4D00), onPrimaryContainer = Color(0xFFFFDDB2),
        secondary = Color(0xFFC8C5BD), onSecondary = Color(0xFF30302D),
        secondaryContainer = Color(0xFF4A4944), onSecondaryContainer = Color(0xFFE5E2D9),
        tertiary = Color(0xFFD6B887), onTertiary = Color(0xFF3A2A13),
        tertiaryContainer = Color(0xFF5C4523), onTertiaryContainer = Color(0xFFF6DDB3),
        background = Color(0xFF2C2C2A), onBackground = Color(0xFFF1EEE8),
        surface = Color(0xFF383835), onSurface = Color(0xFFF1EEE8),
        surfaceVariant = Color(0xFF4A4944), onSurfaceVariant = Color(0xFFC8C5BD),
        outline = Color(0xFFA9A69E), outlineVariant = Color(0xFF5C5B55)
    ),
    lightSemantic = ThemeSemanticColors(Color(0xFF2F7A55), Color(0xFFD97706)),
    darkSemantic = ThemeSemanticColors(Color(0xFF8FD0A7), Color(0xFFFFB95E))
)

val MidnightOledThemePalette = ThemePalette(
    light = lightColorScheme(
        primary = Color(0xFF4F46E5), onPrimary = Color.White,
        primaryContainer = Color(0xFFE0E7FF), onPrimaryContainer = Color(0xFF1E1B4B),
        secondary = Color(0xFF64748B), onSecondary = Color.White,
        secondaryContainer = Color(0xFFE2E8F0), onSecondaryContainer = Color(0xFF1E293B),
        tertiary = Color(0xFF7C3AED), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFEDE9FE), onTertiaryContainer = Color(0xFF2E1065),
        background = Color(0xFFF8FAFC), onBackground = Color(0xFF1E293B),
        surface = Color(0xFFF8FAFC), onSurface = Color(0xFF1E293B),
        surfaceVariant = Color(0xFFF1F5F9), onSurfaceVariant = Color(0xFF64748B),
        outline = Color(0xFF94A3B8), outlineVariant = Color(0xFFE2E8F0)
    ),
    dark = darkColorScheme(
        primary = Color(0xFF6366F1), onPrimary = Color.White,
        primaryContainer = Color(0xFF3730A3), onPrimaryContainer = Color(0xFFE0E7FF),
        secondary = Color(0xFF64748B), onSecondary = Color(0xFFE2E8F0),
        secondaryContainer = Color(0xFF1E293B), onSecondaryContainer = Color(0xFFCBD5E1),
        tertiary = Color(0xFFA78BFA), onTertiary = Color(0xFF2E1065),
        tertiaryContainer = Color(0xFF5B21B6), onTertiaryContainer = Color(0xFFEDE9FE),
        background = Color(0xFF000000), onBackground = Color(0xFFE8E8E8),
        surface = Color(0xFF121212), onSurface = Color(0xFFE8E8E8),
        surfaceVariant = Color(0xFF1E293B), onSurfaceVariant = Color(0xFF94A3B8),
        outline = Color(0xFF64748B), outlineVariant = Color(0xFF334155)
    ),
    lightSemantic = ThemeSemanticColors(Color(0xFF4338CA), Color(0xFFB45309)),
    darkSemantic = ThemeSemanticColors(Color(0xFFA5B4FC), Color(0xFFFBBF24))
)

val SageSlateThemePalette = ThemePalette(
    light = lightColorScheme(
        primary = Color(0xFF059669), onPrimary = Color.White,
        primaryContainer = Color(0xFFBCEAD8), onPrimaryContainer = Color(0xFF00382A),
        secondary = Color(0xFF526A60), onSecondary = Color.White,
        secondaryContainer = Color(0xFFD7E6DE), onSecondaryContainer = Color(0xFF10251C),
        tertiary = Color(0xFF64748B), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFDCE4ED), onTertiaryContainer = Color(0xFF1E293B),
        background = Color(0xFFF4F6F4), onBackground = Color(0xFF1A2E26),
        surface = Color(0xFFF4F6F4), onSurface = Color(0xFF1A2E26),
        surfaceVariant = Color(0xFFE5ECE7), onSurfaceVariant = Color(0xFF526A60),
        outline = Color(0xFF71877C), outlineVariant = Color(0xFFC9D8CE)
    ),
    dark = darkColorScheme(
        primary = Color(0xFF34D399), onPrimary = Color(0xFF00382A),
        primaryContainer = Color(0xFF047857), onPrimaryContainer = Color(0xFFB7F7DB),
        secondary = Color(0xFFB7CEC1), onSecondary = Color(0xFF20352B),
        secondaryContainer = Color(0xFF354B40), onSecondaryContainer = Color(0xFFD3E8DC),
        tertiary = Color(0xFFAABCCD), onTertiary = Color(0xFF243240),
        tertiaryContainer = Color(0xFF3B4F63), onTertiaryContainer = Color(0xFFDCE4ED),
        background = Color(0xFF17231E), onBackground = Color(0xFFE1ECE5),
        surface = Color(0xFF22312A), onSurface = Color(0xFFE1ECE5),
        surfaceVariant = Color(0xFF354B40), onSurfaceVariant = Color(0xFFB7CEC1),
        outline = Color(0xFF8EA699), outlineVariant = Color(0xFF4B6255)
    ),
    lightSemantic = ThemeSemanticColors(Color(0xFF047857), Color(0xFFB45309)),
    darkSemantic = ThemeSemanticColors(Color(0xFF6EE7B7), Color(0xFFFBBF24))
)

fun themePaletteFor(preset: ThemePreset): ThemePalette = when (preset) {
    ThemePreset.EMERALD -> EmeraldThemePalette
    ThemePreset.OCEAN -> OceanThemePalette
    ThemePreset.VIOLET -> VioletThemePalette
    ThemePreset.SUNSET -> SunsetThemePalette
    ThemePreset.GRAPHITE -> GraphiteThemePalette
    ThemePreset.NORD -> NordThemePalette
    ThemePreset.SOLARIZED -> SolarizedThemePalette
    ThemePreset.SOFT_PAPER_CREAM -> SoftPaperCreamThemePalette
    ThemePreset.MIDNIGHT_OLED -> MidnightOledThemePalette
    ThemePreset.SAGE_SLATE -> SageSlateThemePalette
}

val LocalThemePalette = staticCompositionLocalOf { EmeraldThemePalette }
