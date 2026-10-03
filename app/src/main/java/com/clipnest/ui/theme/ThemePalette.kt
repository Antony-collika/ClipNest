package com.clipnest.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.clipnest.data.local.ThemePreset

data class ThemeSemanticColors(
    val pinned: Color,
    val sensitive: Color
)

data class ThemeNoteColors(
    val card: Color,
    val selectedCard: Color,
    val pinnedSurface: Color,
    val tagSurface: Color,
    val tagContent: Color,
    val headerSurface: Color,
    val dockSurface: Color,
    val wavePrimary: Color,
    val waveSecondary: Color
)

data class ThemePalette(
    val light: ColorScheme,
    val dark: ColorScheme,
    val lightSemantic: ThemeSemanticColors,
    val darkSemantic: ThemeSemanticColors,
    // Wave & dock colors sourced directly from themes.md spec
    val waveTop: Color,
    val waveMid: Color,
    val waveBottom: Color,
    val dockBg: Color,
    val dockIconColor: Color,
    val fabBg: Color,
    val fabIconColor: Color
) {
    fun semanticColors(isDark: Boolean): ThemeSemanticColors =
        if (isDark) darkSemantic else lightSemantic

    fun noteColors(isDark: Boolean): ThemeNoteColors {
        val scheme = if (isDark) dark else light
        val semantic = semanticColors(isDark)
        return ThemeNoteColors(
            card = scheme.surfaceVariant,
            selectedCard = scheme.primaryContainer.copy(alpha = 0.72f),
            pinnedSurface = semantic.pinned.copy(alpha = 0.10f),
            tagSurface = scheme.secondaryContainer.copy(alpha = 0.88f),
            tagContent = scheme.onSecondaryContainer,
            headerSurface = scheme.surfaceVariant.copy(alpha = 0.46f),
            dockSurface = dockBg,
            wavePrimary = waveTop,
            waveSecondary = waveMid
        )
    }
}

// ─────────────────────────────────────────────
// 1. FOREST (Default) — Ấm áp, tự nhiên, cổ điển
// ─────────────────────────────────────────────
val ForestThemePalette = ThemePalette(
    light = lightColorScheme(
        primary = Color(0xFF1C3B2B), onPrimary = Color.White,
        primaryContainer = Color(0xFFD2E4D9), onPrimaryContainer = Color(0xFF1C3B2B),
        secondary = Color(0xFF5A6660), onSecondary = Color.White,
        secondaryContainer = Color(0xFFDBE8E0), onSecondaryContainer = Color(0xFF1C3B2B),
        tertiary = Color(0xFF2A523C), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFCFE5D6), onTertiaryContainer = Color(0xFF1C3B2B),
        background = Color(0xFFF3ECE1), onBackground = Color(0xFF1A231E),
        surface = Color(0xFFF3ECE1), onSurface = Color(0xFF1A231E),
        surfaceVariant = Color(0xFFE8DED0), onSurfaceVariant = Color(0xFF5A6660),
        outline = Color(0xFF6F7A73), outlineVariant = Color(0xFFDBE8E0)
    ),
    dark = darkColorScheme(
        primary = Color(0xFF4ADE80), onPrimary = Color(0xFF1C3B2B),
        primaryContainer = Color(0xFF142E21), onPrimaryContainer = Color(0xFFD2E4D9),
        secondary = Color(0xFFB0C4B8), onSecondary = Color(0xFF1A231E),
        secondaryContainer = Color(0xFF2A3D32), onSecondaryContainer = Color(0xFFDBE8E0),
        tertiary = Color(0xFF6DBD8A), onTertiary = Color(0xFF0B1A13),
        tertiaryContainer = Color(0xFF142E21), onTertiaryContainer = Color(0xFFCFE5D6),
        background = Color(0xFF0B1A13), onBackground = Color(0xFFE0EBE3),
        surface = Color(0xFF142E21), onSurface = Color(0xFFE0EBE3),
        surfaceVariant = Color(0xFF1C3B2B), onSurfaceVariant = Color(0xFFB0C4B8),
        outline = Color(0xFF6F7A73), outlineVariant = Color(0xFF2A3D32)
    ),
    lightSemantic = ThemeSemanticColors(Color(0xFF1C3B2B), Color(0xFFD97706)),
    darkSemantic = ThemeSemanticColors(Color(0xFF4ADE80), Color(0xFFFBBF24)),
    waveTop = Color(0xFF2A523C),
    waveMid = Color(0xFF142E21),
    waveBottom = Color(0xFF0B1A13),
    dockBg = Color(0xFFFFFFFF),
    dockIconColor = Color(0xFF2B332E),
    fabBg = Color(0xFF1C3B2B),
    fabIconColor = Color(0xFFFFFFFF)
)

// ─────────────────────────────────────────────
// 2. NORD — Xám xanh Bắc Âu lạnh, tối giản
// ─────────────────────────────────────────────
val NordThemePalette = ThemePalette(
    light = lightColorScheme(
        primary = Color(0xFF2E3440), onPrimary = Color.White,
        primaryContainer = Color(0xFFD8DEE9), onPrimaryContainer = Color(0xFF2E3440),
        secondary = Color(0xFF4C566A), onSecondary = Color.White,
        secondaryContainer = Color(0xFFD8DEE9), onSecondaryContainer = Color(0xFF2E3440),
        tertiary = Color(0xFF5E81AC), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFD8DEE9), onTertiaryContainer = Color(0xFF2E3440),
        background = Color(0xFFECEFF4), onBackground = Color(0xFF2E3440),
        surface = Color(0xFFECEFF4), onSurface = Color(0xFF2E3440),
        surfaceVariant = Color(0xFFE5E9F0), onSurfaceVariant = Color(0xFF4C566A),
        outline = Color(0xFF7B8794), outlineVariant = Color(0xFFD8DEE9)
    ),
    dark = darkColorScheme(
        primary = Color(0xFF81A1C1), onPrimary = Color(0xFF2E3440),
        primaryContainer = Color(0xFF3B4252), onPrimaryContainer = Color(0xFFECEFF4),
        secondary = Color(0xFFD8DEE9), onSecondary = Color(0xFF2E3440),
        secondaryContainer = Color(0xFF434C5E), onSecondaryContainer = Color(0xFFE5E9F0),
        tertiary = Color(0xFF88C0D0), onTertiary = Color(0xFF2E3440),
        tertiaryContainer = Color(0xFF4C566A), onTertiaryContainer = Color(0xFFECEFF4),
        background = Color(0xFF2E3440), onBackground = Color(0xFFECEFF4),
        surface = Color(0xFF3B4252), onSurface = Color(0xFFECEFF4),
        surfaceVariant = Color(0xFF434C5E), onSurfaceVariant = Color(0xFFD8DEE9),
        outline = Color(0xFF7B8794), outlineVariant = Color(0xFF4C566A)
    ),
    lightSemantic = ThemeSemanticColors(Color(0xFF2E3440), Color(0xFFB45309)),
    darkSemantic = ThemeSemanticColors(Color(0xFF81A1C1), Color(0xFFEBCB8B)),
    waveTop = Color(0xFF81A1C1),
    waveMid = Color(0xFF5E81AC),
    waveBottom = Color(0xFF2E3440),
    dockBg = Color(0xFFFFFFFF),
    dockIconColor = Color(0xFF2E3440),
    fabBg = Color(0xFF5E81AC),
    fabIconColor = Color(0xFFFFFFFF)
)

// ─────────────────────────────────────────────
// 3. SNOW / SAPPHIRE — Tuyết trong + xanh sapphire
// ─────────────────────────────────────────────
val SnowSapphireThemePalette = ThemePalette(
    light = lightColorScheme(
        primary = Color(0xFF1E3A8A), onPrimary = Color.White,
        primaryContainer = Color(0xFFBAE6FD), onPrimaryContainer = Color(0xFF0C4A6E),
        secondary = Color(0xFF0369A1), onSecondary = Color.White,
        secondaryContainer = Color(0xFFBAE6FD), onSecondaryContainer = Color(0xFF0C4A6E),
        tertiary = Color(0xFF0284C7), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE0F2FE), onTertiaryContainer = Color(0xFF0C4A6E),
        background = Color(0xFFF0F9FF), onBackground = Color(0xFF0C4A6E),
        surface = Color(0xFFF0F9FF), onSurface = Color(0xFF0C4A6E),
        surfaceVariant = Color(0xFFE0F2FE), onSurfaceVariant = Color(0xFF0369A1),
        outline = Color(0xFF64748B), outlineVariant = Color(0xFFBAE6FD)
    ),
    dark = darkColorScheme(
        primary = Color(0xFF38BDF8), onPrimary = Color(0xFF0C4A6E),
        primaryContainer = Color(0xFF075985), onPrimaryContainer = Color(0xFFE0F2FE),
        secondary = Color(0xFF7DD3FC), onSecondary = Color(0xFF0C4A6E),
        secondaryContainer = Color(0xFF0369A1), onSecondaryContainer = Color(0xFFBAE6FD),
        tertiary = Color(0xFF38BDF8), onTertiary = Color(0xFF0C4A6E),
        tertiaryContainer = Color(0xFF1E3A8A), onTertiaryContainer = Color(0xFFE0F2FE),
        background = Color(0xFF0C2A45), onBackground = Color(0xFFE0F2FE),
        surface = Color(0xFF0F3558), onSurface = Color(0xFFE0F2FE),
        surfaceVariant = Color(0xFF1E4976), onSurfaceVariant = Color(0xFF7DD3FC),
        outline = Color(0xFF64748B), outlineVariant = Color(0xFF0369A1)
    ),
    lightSemantic = ThemeSemanticColors(Color(0xFF1E3A8A), Color(0xFFB45309)),
    darkSemantic = ThemeSemanticColors(Color(0xFF38BDF8), Color(0xFFFBBF24)),
    waveTop = Color(0xFF38BDF8),
    waveMid = Color(0xFF0284C7),
    waveBottom = Color(0xFF1E3A8A),
    dockBg = Color(0xFFFFFFFF),
    dockIconColor = Color(0xFF1E3A8A),
    fabBg = Color(0xFF0284C7),
    fabIconColor = Color(0xFFFFFFFF)
)

// ─────────────────────────────────────────────
// 4. SAKURA — Hoa anh đào nhẹ nhàng, tinh tế
// ─────────────────────────────────────────────
val SakuraThemePalette = ThemePalette(
    light = lightColorScheme(
        primary = Color(0xFF831843), onPrimary = Color.White,
        primaryContainer = Color(0xFFFBCFE8), onPrimaryContainer = Color(0xFF500724),
        secondary = Color(0xFF9D174D), onSecondary = Color.White,
        secondaryContainer = Color(0xFFFBCFE8), onSecondaryContainer = Color(0xFF500724),
        tertiary = Color(0xFFDB2777), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFCE7F3), onTertiaryContainer = Color(0xFF500724),
        background = Color(0xFFFDF2F8), onBackground = Color(0xFF500724),
        surface = Color(0xFFFDF2F8), onSurface = Color(0xFF500724),
        surfaceVariant = Color(0xFFFCE7F3), onSurfaceVariant = Color(0xFF9D174D),
        outline = Color(0xFF9CA3AF), outlineVariant = Color(0xFFFBCFE8)
    ),
    dark = darkColorScheme(
        primary = Color(0xFFF472B6), onPrimary = Color(0xFF500724),
        primaryContainer = Color(0xFF9D174D), onPrimaryContainer = Color(0xFFFCE7F3),
        secondary = Color(0xFFFBCFE8), onSecondary = Color(0xFF500724),
        secondaryContainer = Color(0xFF831843), onSecondaryContainer = Color(0xFFFBCFE8),
        tertiary = Color(0xFFF472B6), onTertiary = Color(0xFF500724),
        tertiaryContainer = Color(0xFF831843), onTertiaryContainer = Color(0xFFFCE7F3),
        background = Color(0xFF3A0A1E), onBackground = Color(0xFFFCE7F3),
        surface = Color(0xFF4A1028), onSurface = Color(0xFFFCE7F3),
        surfaceVariant = Color(0xFF5A1530), onSurfaceVariant = Color(0xFFFBCFE8),
        outline = Color(0xFF9CA3AF), outlineVariant = Color(0xFF831843)
    ),
    lightSemantic = ThemeSemanticColors(Color(0xFF831843), Color(0xFFD97706)),
    darkSemantic = ThemeSemanticColors(Color(0xFFF472B6), Color(0xFFFBBF24)),
    waveTop = Color(0xFFF472B6),
    waveMid = Color(0xFFDB2777),
    waveBottom = Color(0xFF831843),
    dockBg = Color(0xFFFFFFFF),
    dockIconColor = Color(0xFF831843),
    fabBg = Color(0xFFDB2777),
    fabIconColor = Color(0xFFFFFFFF)
)

// ─────────────────────────────────────────────
// 5. LAVENDER — Tím hoa oải hương, thư thái
// ─────────────────────────────────────────────
val LavenderThemePalette = ThemePalette(
    light = lightColorScheme(
        primary = Color(0xFF4C1D95), onPrimary = Color.White,
        primaryContainer = Color(0xFFDDD6FE), onPrimaryContainer = Color(0xFF2E1065),
        secondary = Color(0xFF6D28D9), onSecondary = Color.White,
        secondaryContainer = Color(0xFFDDD6FE), onSecondaryContainer = Color(0xFF2E1065),
        tertiary = Color(0xFF7C3AED), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFEDE9FE), onTertiaryContainer = Color(0xFF2E1065),
        background = Color(0xFFF5F3FF), onBackground = Color(0xFF2E1065),
        surface = Color(0xFFF5F3FF), onSurface = Color(0xFF2E1065),
        surfaceVariant = Color(0xFFEDE9FE), onSurfaceVariant = Color(0xFF6D28D9),
        outline = Color(0xFF9CA3AF), outlineVariant = Color(0xFFDDD6FE)
    ),
    dark = darkColorScheme(
        primary = Color(0xFFA78BFA), onPrimary = Color(0xFF2E1065),
        primaryContainer = Color(0xFF7C3AED), onPrimaryContainer = Color(0xFFEDE9FE),
        secondary = Color(0xFFDDD6FE), onSecondary = Color(0xFF2E1065),
        secondaryContainer = Color(0xFF4C1D95), onSecondaryContainer = Color(0xFFDDD6FE),
        tertiary = Color(0xFFC4B5FD), onTertiary = Color(0xFF2E1065),
        tertiaryContainer = Color(0xFF4C1D95), onTertiaryContainer = Color(0xFFEDE9FE),
        background = Color(0xFF1A0A3D), onBackground = Color(0xFFEDE9FE),
        surface = Color(0xFF250F50), onSurface = Color(0xFFEDE9FE),
        surfaceVariant = Color(0xFF3A1870), onSurfaceVariant = Color(0xFFDDD6FE),
        outline = Color(0xFF9CA3AF), outlineVariant = Color(0xFF4C1D95)
    ),
    lightSemantic = ThemeSemanticColors(Color(0xFF4C1D95), Color(0xFFD97706)),
    darkSemantic = ThemeSemanticColors(Color(0xFFA78BFA), Color(0xFFFBBF24)),
    waveTop = Color(0xFFA78BFA),
    waveMid = Color(0xFF7C3AED),
    waveBottom = Color(0xFF4C1D95),
    dockBg = Color(0xFFFFFFFF),
    dockIconColor = Color(0xFF4C1D95),
    fabBg = Color(0xFF7C3AED),
    fabIconColor = Color(0xFFFFFFFF)
)

// ─────────────────────────────────────────────
// 6. LIGHT BASIC — Trắng đen truyền thống, tối giản
// ─────────────────────────────────────────────
val LightBasicThemePalette = ThemePalette(
    light = lightColorScheme(
        primary = Color(0xFF374151), onPrimary = Color.White,
        primaryContainer = Color(0xFFE5E7EB), onPrimaryContainer = Color(0xFF111827),
        secondary = Color(0xFF4B5563), onSecondary = Color.White,
        secondaryContainer = Color(0xFFE5E7EB), onSecondaryContainer = Color(0xFF111827),
        tertiary = Color(0xFF6B7280), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFF3F4F6), onTertiaryContainer = Color(0xFF111827),
        background = Color(0xFFFFFFFF), onBackground = Color(0xFF111827),
        surface = Color(0xFFFFFFFF), onSurface = Color(0xFF111827),
        surfaceVariant = Color(0xFFF3F4F6), onSurfaceVariant = Color(0xFF4B5563),
        outline = Color(0xFF9CA3AF), outlineVariant = Color(0xFFE5E7EB)
    ),
    dark = darkColorScheme(
        primary = Color(0xFF9CA3AF), onPrimary = Color(0xFF111827),
        primaryContainer = Color(0xFF374151), onPrimaryContainer = Color(0xFFF3F4F6),
        secondary = Color(0xFFE5E7EB), onSecondary = Color(0xFF111827),
        secondaryContainer = Color(0xFF374151), onSecondaryContainer = Color(0xFFE5E7EB),
        tertiary = Color(0xFFD1D5DB), onTertiary = Color(0xFF111827),
        tertiaryContainer = Color(0xFF4B5563), onTertiaryContainer = Color(0xFFF3F4F6),
        background = Color(0xFF111827), onBackground = Color(0xFFF9FAFB),
        surface = Color(0xFF1F2937), onSurface = Color(0xFFF9FAFB),
        surfaceVariant = Color(0xFF374151), onSurfaceVariant = Color(0xFFD1D5DB),
        outline = Color(0xFF6B7280), outlineVariant = Color(0xFF374151)
    ),
    lightSemantic = ThemeSemanticColors(Color(0xFF374151), Color(0xFFD97706)),
    darkSemantic = ThemeSemanticColors(Color(0xFF9CA3AF), Color(0xFFFBBF24)),
    waveTop = Color(0xFF9CA3AF),
    waveMid = Color(0xFF6B7280),
    waveBottom = Color(0xFF374151),
    dockBg = Color(0xFFFFFFFF),
    dockIconColor = Color(0xFF374151),
    fabBg = Color(0xFF374151),
    fabIconColor = Color(0xFFFFFFFF)
)

// ─────────────────────────────────────────────
// 7. BASIC DARK — Xám đen truyền thống, tối giản
// ─────────────────────────────────────────────
val BasicDarkThemePalette = ThemePalette(
    light = lightColorScheme(
        primary = Color(0xFF374151), onPrimary = Color.White,
        primaryContainer = Color(0xFFE5E7EB), onPrimaryContainer = Color(0xFF111827),
        secondary = Color(0xFF4B5563), onSecondary = Color.White,
        secondaryContainer = Color(0xFFE5E7EB), onSecondaryContainer = Color(0xFF111827),
        tertiary = Color(0xFF6B7280), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFF3F4F6), onTertiaryContainer = Color(0xFF111827),
        background = Color(0xFFFFFFFF), onBackground = Color(0xFF111827),
        surface = Color(0xFFFFFFFF), onSurface = Color(0xFF111827),
        surfaceVariant = Color(0xFFF3F4F6), onSurfaceVariant = Color(0xFF4B5563),
        outline = Color(0xFF9CA3AF), outlineVariant = Color(0xFFE5E7EB)
    ),
    dark = darkColorScheme(
        primary = Color(0xFFF9FAFB), onPrimary = Color(0xFF111827),
        primaryContainer = Color(0xFF374151), onPrimaryContainer = Color(0xFFF9FAFB),
        secondary = Color(0xFFCBD5E1), onSecondary = Color(0xFF111827),
        secondaryContainer = Color(0xFF374151), onSecondaryContainer = Color(0xFFF9FAFB),
        tertiary = Color(0xFF94A3B8), onTertiary = Color(0xFF111827),
        tertiaryContainer = Color(0xFF273244), onTertiaryContainer = Color(0xFFF9FAFB),
        background = Color(0xFF111827), onBackground = Color(0xFFF9FAFB),
        surface = Color(0xFF2D3A4D), onSurface = Color(0xFFF9FAFB),
        surfaceVariant = Color(0xFF374151), onSurfaceVariant = Color(0xFFCBD5E1),
        outline = Color(0xFF6B7280), outlineVariant = Color(0xFF374151)
    ),
    lightSemantic = ThemeSemanticColors(Color(0xFF374151), Color(0xFFD97706)),
    darkSemantic = ThemeSemanticColors(Color(0xFFF9FAFB), Color(0xFFFBBF24)),
    waveTop = Color(0xFF4B5563),
    waveMid = Color(0xFF374151),
    waveBottom = Color(0xFF111827),
    dockBg = Color(0xFFF9FAFB),
    dockIconColor = Color(0xFF111827),
    fabBg = Color(0xFFF9FAFB),
    fabIconColor = Color(0xFF111827)
)

// ─────────────────────────────────────────────
// 8. DEEP OCEAN — Đại dương sâu, xanh navy huyền bí
// ─────────────────────────────────────────────
val DeepOceanThemePalette = ThemePalette(
    light = lightColorScheme(
        primary = Color(0xFF0A1929), onPrimary = Color.White,
        primaryContainer = Color(0xFF90CAF9), onPrimaryContainer = Color(0xFF0A1929),
        secondary = Color(0xFF0F2233), onSecondary = Color.White,
        secondaryContainer = Color(0xFF90CAF9), onSecondaryContainer = Color(0xFF0A1929),
        tertiary = Color(0xFF1E6FA8), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFBBDEFB), onTertiaryContainer = Color(0xFF0A1929),
        background = Color(0xFFE3F2FD), onBackground = Color(0xFF0A1929),
        surface = Color(0xFFE3F2FD), onSurface = Color(0xFF0A1929),
        surfaceVariant = Color(0xFFBBDEFB), onSurfaceVariant = Color(0xFF0F2233),
        outline = Color(0xFF64B5F6), outlineVariant = Color(0xFF90CAF9)
    ),
    dark = darkColorScheme(
        primary = Color(0xFF64B5F6), onPrimary = Color(0xFF0A1929),
        primaryContainer = Color(0xFF14507A), onPrimaryContainer = Color(0xFFE3F2FD),
        secondary = Color(0xFF90CAF9), onSecondary = Color(0xFF0A1929),
        secondaryContainer = Color(0xFF1E4976), onSecondaryContainer = Color(0xFFE3F2FD),
        tertiary = Color(0xFF38BDF8), onTertiary = Color(0xFF0A1929),
        tertiaryContainer = Color(0xFF1E6FA8), onTertiaryContainer = Color(0xFFE3F2FD),
        background = Color(0xFF0A1929), onBackground = Color(0xFFE3F2FD),
        surface = Color(0xFF16385A), onSurface = Color(0xFFE3F2FD),
        surfaceVariant = Color(0xFF1E4976), onSurfaceVariant = Color(0xFF90CAF9),
        outline = Color(0xFF64B5F6), outlineVariant = Color(0xFF1E4976)
    ),
    lightSemantic = ThemeSemanticColors(Color(0xFF0A1929), Color(0xFFD97706)),
    darkSemantic = ThemeSemanticColors(Color(0xFF38BDF8), Color(0xFFFBBF24)),
    waveTop = Color(0xFF1E6FA8),
    waveMid = Color(0xFF14507A),
    waveBottom = Color(0xFF0A1929),
    dockBg = Color(0xFFE3F2FD),
    dockIconColor = Color(0xFF0A1929),
    fabBg = Color(0xFF38BDF8),
    fabIconColor = Color(0xFF0A1929)
)

// ─────────────────────────────────────────────
// 9. COFFEE — Cà phê ấm nóng, hổ phách trầm lắng
// ─────────────────────────────────────────────
val CoffeeThemePalette = ThemePalette(
    light = lightColorScheme(
        primary = Color(0xFF2B1A12), onPrimary = Color.White,
        primaryContainer = Color(0xFFD4A574), onPrimaryContainer = Color(0xFF2B1A12),
        secondary = Color(0xFF3A2418), onSecondary = Color.White,
        secondaryContainer = Color(0xFFD4A574), onSecondaryContainer = Color(0xFF2B1A12),
        tertiary = Color(0xFFA0522D), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE8C9A0), onTertiaryContainer = Color(0xFF2B1A12),
        background = Color(0xFFF5E6D3), onBackground = Color(0xFF2B1A12),
        surface = Color(0xFFF5E6D3), onSurface = Color(0xFF2B1A12),
        surfaceVariant = Color(0xFFE8C9A0), onSurfaceVariant = Color(0xFF3A2418),
        outline = Color(0xFFB08968), outlineVariant = Color(0xFFD4A574)
    ),
    dark = darkColorScheme(
        primary = Color(0xFFE8A87C), onPrimary = Color(0xFF2B1A12),
        primaryContainer = Color(0xFF7A3B1F), onPrimaryContainer = Color(0xFFF5E6D3),
        secondary = Color(0xFFD4A574), onSecondary = Color(0xFF2B1A12),
        secondaryContainer = Color(0xFF6B4630), onSecondaryContainer = Color(0xFFF5E6D3),
        tertiary = Color(0xFFE8A87C), onTertiary = Color(0xFF2B1A12),
        tertiaryContainer = Color(0xFFA0522D), onTertiaryContainer = Color(0xFFF5E6D3),
        background = Color(0xFF2B1A12), onBackground = Color(0xFFF5E6D3),
        surface = Color(0xFF5A3826), onSurface = Color(0xFFF5E6D3),
        surfaceVariant = Color(0xFF6B4630), onSurfaceVariant = Color(0xFFD4A574),
        outline = Color(0xFFB08968), outlineVariant = Color(0xFF6B4630)
    ),
    lightSemantic = ThemeSemanticColors(Color(0xFF2B1A12), Color(0xFFD97706)),
    darkSemantic = ThemeSemanticColors(Color(0xFFE8A87C), Color(0xFFFBBF24)),
    waveTop = Color(0xFFA0522D),
    waveMid = Color(0xFF7A3B1F),
    waveBottom = Color(0xFF2B1A12),
    dockBg = Color(0xFFF5E6D3),
    dockIconColor = Color(0xFF2B1A12),
    fabBg = Color(0xFFE8A87C),
    fabIconColor = Color(0xFF2B1A12)
)

// ─────────────────────────────────────────────
// 10. OBSIDIAN — Đá obsidian đen tím, huyền bí sang trọng
// ─────────────────────────────────────────────
val ObsidianThemePalette = ThemePalette(
    light = lightColorScheme(
        primary = Color(0xFF1A0F2E), onPrimary = Color.White,
        primaryContainer = Color(0xFFC4B5FD), onPrimaryContainer = Color(0xFF1A0F2E),
        secondary = Color(0xFF241438), onSecondary = Color.White,
        secondaryContainer = Color(0xFFC4B5FD), onSecondaryContainer = Color(0xFF1A0F2E),
        tertiary = Color(0xFF7C3AED), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFEDE9FE), onTertiaryContainer = Color(0xFF1A0F2E),
        background = Color(0xFFEDE9FE), onBackground = Color(0xFF1A0F2E),
        surface = Color(0xFFEDE9FE), onSurface = Color(0xFF1A0F2E),
        surfaceVariant = Color(0xFFDDD6FE), onSurfaceVariant = Color(0xFF241438),
        outline = Color(0xFFA78BFA), outlineVariant = Color(0xFFC4B5FD)
    ),
    dark = darkColorScheme(
        primary = Color(0xFFA78BFA), onPrimary = Color(0xFF1A0F2E),
        primaryContainer = Color(0xFF5B21B6), onPrimaryContainer = Color(0xFFEDE9FE),
        secondary = Color(0xFFC4B5FD), onSecondary = Color(0xFF1A0F2E),
        secondaryContainer = Color(0xFF4C2E6E), onSecondaryContainer = Color(0xFFEDE9FE),
        tertiary = Color(0xFF7C3AED), onTertiary = Color(0xFF1A0F2E),
        tertiaryContainer = Color(0xFF5B21B6), onTertiaryContainer = Color(0xFFEDE9FE),
        background = Color(0xFF1A0F2E), onBackground = Color(0xFFEDE9FE),
        surface = Color(0xFF3A2356), onSurface = Color(0xFFEDE9FE),
        surfaceVariant = Color(0xFF4C2E6E), onSurfaceVariant = Color(0xFFC4B5FD),
        outline = Color(0xFFA78BFA), outlineVariant = Color(0xFF4C2E6E)
    ),
    lightSemantic = ThemeSemanticColors(Color(0xFF1A0F2E), Color(0xFFD97706)),
    darkSemantic = ThemeSemanticColors(Color(0xFFA78BFA), Color(0xFFFBBF24)),
    waveTop = Color(0xFF7C3AED),
    waveMid = Color(0xFF5B21B6),
    waveBottom = Color(0xFF1A0F2E),
    dockBg = Color(0xFFEDE9FE),
    dockIconColor = Color(0xFF1A0F2E),
    fabBg = Color(0xFFA78BFA),
    fabIconColor = Color(0xFF1A0F2E)
)

fun themePaletteFor(preset: ThemePreset): ThemePalette = when (preset) {
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

val LocalThemePalette = staticCompositionLocalOf { ForestThemePalette }
