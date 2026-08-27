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

val LightThemePalette = ThemePalette(
    light = lightColorScheme(
        primary = Color(0xFF111111), onPrimary = Color.White,
        primaryContainer = Color(0xFFE5E7EB), onPrimaryContainer = Color(0xFF111111),
        secondary = Color(0xFF6B7280), onSecondary = Color.White,
        secondaryContainer = Color(0xFFE5E7EB), onSecondaryContainer = Color(0xFF111111),
        tertiary = Color(0xFF374151), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE5E7EB), onTertiaryContainer = Color(0xFF111111),
        background = Color(0xFFFFFFFF), onBackground = Color(0xFF111111),
        surface = Color(0xFFFFFFFF), onSurface = Color(0xFF111111),
        surfaceVariant = Color(0xFFF3F4F6), onSurfaceVariant = Color(0xFF6B7280),
        outline = Color(0xFF9CA3AF), outlineVariant = Color(0xFFE5E7EB)
    ),
    dark = darkColorScheme(
        primary = Color(0xFF111111), onPrimary = Color.White,
        primaryContainer = Color(0xFFE5E7EB), onPrimaryContainer = Color(0xFF111111),
        secondary = Color(0xFF6B7280), onSecondary = Color.White,
        secondaryContainer = Color(0xFFE5E7EB), onSecondaryContainer = Color(0xFF111111),
        tertiary = Color(0xFF374151), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE5E7EB), onTertiaryContainer = Color(0xFF111111),
        background = Color(0xFFFFFFFF), onBackground = Color(0xFF111111),
        surface = Color(0xFFFFFFFF), onSurface = Color(0xFF111111),
        surfaceVariant = Color(0xFFF3F4F6), onSurfaceVariant = Color(0xFF6B7280),
        outline = Color(0xFF9CA3AF), outlineVariant = Color(0xFFE5E7EB)
    ),
    lightSemantic = ThemeSemanticColors(Color(0xFF111111), Color(0xFFD97706)),
    darkSemantic = ThemeSemanticColors(Color(0xFF111111), Color(0xFFD97706))
)

val DarkThemePalette = ThemePalette(
    light = lightColorScheme(
        primary = Color(0xFF18181B), onPrimary = Color.White,
        primaryContainer = Color(0xFFE4E4E7), onPrimaryContainer = Color(0xFF18181B),
        secondary = Color(0xFF71717A), onSecondary = Color.White,
        secondaryContainer = Color(0xFFE4E4E7), onSecondaryContainer = Color(0xFF18181B),
        tertiary = Color(0xFF52525B), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE4E4E7), onTertiaryContainer = Color(0xFF18181B),
        background = Color(0xFFF4F4F5), onBackground = Color(0xFF18181B),
        surface = Color(0xFFF4F4F5), onSurface = Color(0xFF18181B),
        surfaceVariant = Color(0xFFE4E4E7), onSurfaceVariant = Color(0xFF71717A),
        outline = Color(0xFFA1A1AA), outlineVariant = Color(0xFFD4D4D8)
    ),
    dark = darkColorScheme(
        primary = Color(0xFFF4F4F5), onPrimary = Color(0xFF18181B),
        primaryContainer = Color(0xFF3F3F46), onPrimaryContainer = Color(0xFFF4F4F5),
        secondary = Color(0xFFA1A1AA), onSecondary = Color(0xFF18181B),
        secondaryContainer = Color(0xFF27272A), onSecondaryContainer = Color(0xFFE4E4E7),
        tertiary = Color(0xFFD4D4D8), onTertiary = Color(0xFF27272A),
        tertiaryContainer = Color(0xFF3F3F46), onTertiaryContainer = Color(0xFFF4F4F5),
        background = Color(0xFF18181B), onBackground = Color(0xFFF4F4F5),
        surface = Color(0xFF202023), onSurface = Color(0xFFF4F4F5),
        surfaceVariant = Color(0xFF3F3F46), onSurfaceVariant = Color(0xFFA1A1AA),
        outline = Color(0xFF71717A), outlineVariant = Color(0xFF3F3F46)
    ),
    lightSemantic = ThemeSemanticColors(Color(0xFF27272A), Color(0xFFB45309)),
    darkSemantic = ThemeSemanticColors(Color(0xFF38BDF8), Color(0xFFFBBF24))
)

val MidnightBlueThemePalette = ThemePalette(
    light = lightColorScheme(
        primary = Color(0xFF0F172A), onPrimary = Color.White,
        primaryContainer = Color(0xFFDCE7F5), onPrimaryContainer = Color(0xFF0F172A),
        secondary = Color(0xFF334155), onSecondary = Color.White,
        secondaryContainer = Color(0xFFE2E8F0), onSecondaryContainer = Color(0xFF0F172A),
        tertiary = Color(0xFF0284C7), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFBAE6FD), onTertiaryContainer = Color(0xFF082F49),
        background = Color(0xFFF8FAFC), onBackground = Color(0xFF0F172A),
        surface = Color(0xFFF8FAFC), onSurface = Color(0xFF0F172A),
        surfaceVariant = Color(0xFFE2E8F0), onSurfaceVariant = Color(0xFF334155),
        outline = Color(0xFF64748B), outlineVariant = Color(0xFFCBD5E1)
    ),
    dark = darkColorScheme(
        primary = Color(0xFF38BDF8), onPrimary = Color(0xFF082F49),
        primaryContainer = Color(0xFF164E63), onPrimaryContainer = Color(0xFFBAE6FD),
        secondary = Color(0xFFCBD5E1), onSecondary = Color(0xFF0F172A),
        secondaryContainer = Color(0xFF1E293B), onSecondaryContainer = Color(0xFFE2E8F0),
        tertiary = Color(0xFF67E8F9), onTertiary = Color(0xFF083344),
        tertiaryContainer = Color(0xFF155E75), onTertiaryContainer = Color(0xFFCFFAFE),
        background = Color(0xFF0F172A), onBackground = Color(0xFFF8FAFC),
        surface = Color(0xFF172554), onSurface = Color(0xFFF8FAFC),
        surfaceVariant = Color(0xFF1E293B), onSurfaceVariant = Color(0xFFCBD5E1),
        outline = Color(0xFF64748B), outlineVariant = Color(0xFF334155)
    ),
    lightSemantic = ThemeSemanticColors(Color(0xFF0369A1), Color(0xFFB45309)),
    darkSemantic = ThemeSemanticColors(Color(0xFF38BDF8), Color(0xFFFBBF24))
)

val ForestThemePalette = ThemePalette(
    light = lightColorScheme(
        primary = Color(0xFF4F8A63), onPrimary = Color.White,
        primaryContainer = Color(0xFFCFE8D6), onPrimaryContainer = Color(0xFF17211B),
        secondary = Color(0xFF526A60), onSecondary = Color.White,
        secondaryContainer = Color(0xFFDCE9DF), onSecondaryContainer = Color(0xFF17211B),
        tertiary = Color(0xFF356B4A), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFC6E3CF), onTertiaryContainer = Color(0xFF143020),
        background = Color(0xFFF5F7F2), onBackground = Color(0xFF17211B),
        surface = Color(0xFFF5F7F2), onSurface = Color(0xFF17211B),
        surfaceVariant = Color(0xFFE7EEE8), onSurfaceVariant = Color(0xFF526A60),
        outline = Color(0xFF71877A), outlineVariant = Color(0xFFCCDACE)
    ),
    dark = darkColorScheme(
        primary = Color(0xFF4F8A63), onPrimary = Color.White,
        primaryContainer = Color(0xFF2F6845), onPrimaryContainer = Color(0xFFD8F3DE),
        secondary = Color(0xFFB9CEBE), onSecondary = Color(0xFF1D3224),
        secondaryContainer = Color(0xFF354D3C), onSecondaryContainer = Color(0xFFD5E9DA),
        tertiary = Color(0xFF8DC69D), onTertiary = Color(0xFF12321D),
        tertiaryContainer = Color(0xFF2F6845), onTertiaryContainer = Color(0xFFD8F3DE),
        background = Color(0xFF17211B), onBackground = Color(0xFFE5EFE7),
        surface = Color(0xFF203027), onSurface = Color(0xFFE5EFE7),
        surfaceVariant = Color(0xFF354D3C), onSurfaceVariant = Color(0xFFB9CEBE),
        outline = Color(0xFF829B89), outlineVariant = Color(0xFF4C6653)
    ),
    lightSemantic = ThemeSemanticColors(Color(0xFF2F6845), Color(0xFFB45309)),
    darkSemantic = ThemeSemanticColors(Color(0xFF8DC69D), Color(0xFFFBBF24))
)

val LavenderThemePalette = ThemePalette(
    light = lightColorScheme(
        primary = Color(0xFF8B5CF6), onPrimary = Color.White,
        primaryContainer = Color(0xFFE9D5FF), onPrimaryContainer = Color(0xFF32106B),
        secondary = Color(0xFF6D5A88), onSecondary = Color.White,
        secondaryContainer = Color(0xFFEDE9FE), onSecondaryContainer = Color(0xFF2E2142),
        tertiary = Color(0xFF7C3AED), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFEDE9FE), onTertiaryContainer = Color(0xFF2E1065),
        background = Color(0xFFF5F3FF), onBackground = Color(0xFF211A2E),
        surface = Color(0xFFF5F3FF), onSurface = Color(0xFF211A2E),
        surfaceVariant = Color(0xFFEDE9FE), onSurfaceVariant = Color(0xFF6D5A88),
        outline = Color(0xFF8B7FA1), outlineVariant = Color(0xFFDCD5F0)
    ),
    dark = darkColorScheme(
        primary = Color(0xFFB58CFF), onPrimary = Color(0xFF32106B),
        primaryContainer = Color(0xFF6D28D9), onPrimaryContainer = Color(0xFFF1E8FF),
        secondary = Color(0xFFD0C1E6), onSecondary = Color(0xFF302241),
        secondaryContainer = Color(0xFF4C3B61), onSecondaryContainer = Color(0xFFE9DDF7),
        tertiary = Color(0xFFC4B5FD), onTertiary = Color(0xFF2E1065),
        tertiaryContainer = Color(0xFF6D28D9), onTertiaryContainer = Color(0xFFF1E8FF),
        background = Color(0xFF211A2E), onBackground = Color(0xFFF1EBF8),
        surface = Color(0xFF2B223B), onSurface = Color(0xFFF1EBF8),
        surfaceVariant = Color(0xFF4C3B61), onSurfaceVariant = Color(0xFFD0C1E6),
        outline = Color(0xFF9D8CB4), outlineVariant = Color(0xFF655278)
    ),
    lightSemantic = ThemeSemanticColors(Color(0xFF7C3AED), Color(0xFFB45309)),
    darkSemantic = ThemeSemanticColors(Color(0xFFC4B5FD), Color(0xFFFBBF24))
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
    ThemePreset.LIGHT -> LightThemePalette
    ThemePreset.DARK -> DarkThemePalette
    ThemePreset.MIDNIGHT_BLUE -> MidnightBlueThemePalette
    ThemePreset.FOREST -> ForestThemePalette
    ThemePreset.LAVENDER -> LavenderThemePalette
    ThemePreset.NORD -> NordThemePalette
    ThemePreset.SOLARIZED -> SolarizedThemePalette
    ThemePreset.SOFT_PAPER_CREAM -> SoftPaperCreamThemePalette
    ThemePreset.MIDNIGHT_OLED -> MidnightOledThemePalette
    ThemePreset.SAGE_SLATE -> SageSlateThemePalette
}

val LocalThemePalette = staticCompositionLocalOf { LightThemePalette }
