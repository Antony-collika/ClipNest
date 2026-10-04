package com.clipnest.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.clipnest.data.local.ThemePreset

// Semantic colors represent note states rather than visual surfaces.
data class ThemeSemanticColors(
    val pinned: Color,
    val sensitive: Color
)

// Note-specific colors expose the visual tokens consumed by note-related UI.
data class ThemeNoteColors(
    val card: Color,
    val selectedCard: Color,
    val pinnedSurface: Color,
    val tagSurface: Color,
    val tagContent: Color,
    val noteLabel: Color,
    val headerSurface: Color,
    val headerContent: Color,
    val dockSurface: Color,
    val wavePrimary: Color,
    val waveSecondary: Color,
    val breadcrumb: Color,
    val sectionText: Color,
    val noteTitle: Color,
    val notePreview: Color,
    val noteActionIcon: Color,
    val emptyStateText: Color,
    val searchSurface: Color,
    val searchContent: Color,
    val searchHint: Color,
    val popupSurface: Color,
    val popupContent: Color,
    val popupSecondary: Color,
    val menuSurface: Color,
    val menuContent: Color,
    val dockIcon: Color
)

// A complete visual palette for one app theme.
// Keep theme-specific color decisions here so UI screens do not hard-code HEX colors.
data class ThemePalette(
    // Material color system: app background, text, surfaces, primary/secondary accents, and outlines.
    val colorScheme: ColorScheme,
    // Semantic states: pinned and sensitive content.
    val semanticColors: ThemeSemanticColors,
    // Decorative wave layers, from the upper layer to the bottom layer.
    val waveTop: Color,
    val waveMid: Color,
    val waveBottom: Color,
    // Floating dock: background, icon color, and divider.
    val dockBg: Color,
    val dockIconColor: Color,
    val dockDivider: Color,
    // Floating action button: background and icon color.
    val fabBg: Color,
    val fabIconColor: Color,
    // Text hierarchy outside cards.
    val breadcrumb: Color,
    val sectionText: Color,
    // Note screen colors are independently configurable and do not derive from other palette colors.
    val noteCardBg: Color,
    val noteSelectedCardBg: Color,
    val noteTitleColor: Color,
    val notePreviewColor: Color,
    val noteLabelColor: Color,
    val noteActionIconColor: Color
) {
    // Maps the palette into the colors used by note UI components.
    // Some values currently derive from Material ColorScheme tokens.
    fun noteColors(): ThemeNoteColors {
        return ThemeNoteColors(
            // Note card surface.
            card = noteCardBg,
            // Selected note card surface.
            selectedCard = noteSelectedCardBg,
            // Subtle background used to indicate a pinned note.
            pinnedSurface = semanticColors.pinned.copy(alpha = 0.10f),
            // Label/tag background and content color.
            tagSurface = colorScheme.secondaryContainer.copy(alpha = 0.88f),
            tagContent = colorScheme.onSecondaryContainer,
            noteLabel = noteLabelColor,
            // Top bar surface and the content drawn on it.
            headerSurface = colorScheme.primary,
            headerContent = colorScheme.onPrimary,
            // Dock and decorative wave colors exposed to note UI.
            dockSurface = dockBg,
            wavePrimary = waveTop,
            waveSecondary = waveMid,
            breadcrumb = breadcrumb,
            sectionText = sectionText,
            noteTitle = noteTitleColor,
            notePreview = notePreviewColor,
            noteActionIcon = noteActionIconColor,
            emptyStateText = colorScheme.onSurfaceVariant,
            searchSurface = colorScheme.surfaceVariant,
            searchContent = colorScheme.onSurface,
            searchHint = colorScheme.onSurfaceVariant,
            popupSurface = colorScheme.surface,
            popupContent = colorScheme.onSurface,
            popupSecondary = colorScheme.onSurfaceVariant,
            menuSurface = colorScheme.surface,
            menuContent = colorScheme.onSurface,
            dockIcon = dockIconColor
        )
    }
}

// ─────────────────────────────────────────────
// 1. FOREST (Default) — Warm, natural, classic
// ─────────────────────────────────────────────
val ForestThemePalette = ThemePalette(
    colorScheme = lightColorScheme(
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
    semanticColors = ThemeSemanticColors(Color(0xFF1C3B2B), Color(0xFFD97706)),
    waveTop = Color(0xFF2A523C),
    waveMid = Color(0xFF0F2219),
    waveBottom = Color(0xFF070E0A),
    dockBg = Color(0xFFFFFFFF),
    dockIconColor = Color(0xFF2B332E),
    fabBg = Color(0xFF1C3B2B),
    fabIconColor = Color(0xFFFFFFFF),
    breadcrumb = Color(0xFF2A523C),
    sectionText = Color(0xFF5A6660),
    noteCardBg = Color(0xFFE8DED0),
    noteSelectedCardBg = Color(0xFFD2E4D9),
    noteTitleColor = Color(0xFF1A231E),
    notePreviewColor = Color(0xFF5A6660),
    noteLabelColor = Color(0xFF1C3B2B),
    noteActionIconColor = Color(0xFF2B332E),
    dockDivider = Color(0xFFDBE8E0)
)

// ─────────────────────────────────────────────
// 2. NORD — Cool Scandinavian gray-blue, minimal
// ─────────────────────────────────────────────
val NordThemePalette = ThemePalette(
    colorScheme = lightColorScheme(
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
    semanticColors = ThemeSemanticColors(Color(0xFF2E3440), Color(0xFFB45309)),
    waveTop = Color(0xFF88C0D0),
    waveMid = Color(0xFF5E81AC),
    waveBottom = Color(0xFF2E3440),
    dockBg = Color(0xFFFFFFFF),
    dockIconColor = Color(0xFF2E3440),
    fabBg = Color(0xFF5E81AC),
    fabIconColor = Color(0xFFFFFFFF),
    breadcrumb = Color(0xFF5E81AC),
    sectionText = Color(0xFF4C566A),
    noteCardBg = Color(0xFFE5E9F0),
    noteSelectedCardBg = Color(0xFFD8DEE9),
    noteTitleColor = Color(0xFF2E3440),
    notePreviewColor = Color(0xFF4C566A),
    noteLabelColor = Color(0xFF2E3440),
    noteActionIconColor = Color(0xFF2E3440),
    dockDivider = Color(0xFFD8DEE9)
)

// ─────────────────────────────────────────────
// 3. SNOW / SAPPHIRE — Clear snow + sapphire blue
// ─────────────────────────────────────────────
val SnowSapphireThemePalette = ThemePalette(
    colorScheme = lightColorScheme(
        primary = Color(0xFF1E3A8A), onPrimary = Color.White,
        primaryContainer = Color(0xFFBAE6FD), onPrimaryContainer = Color(0xFF0C4A6E),
        secondary = Color(0xFF0369A1), onSecondary = Color.White,
        secondaryContainer = Color(0xFFBAE6FD), onSecondaryContainer = Color(0xFF0C4A6E),
        tertiary = Color(0xFF0284C7), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE0F2FE), onTertiaryContainer = Color(0xFF0C4A6E),
        background = Color(0xFFF0F9FF), onBackground = Color(0xFF0C4A6E),
        surface = Color(0xFFF0F9FF), onSurface = Color(0xFF0C4A6E),
        surfaceVariant = Color.White, onSurfaceVariant = Color(0xFF0369A1),
        outline = Color(0xFF64748B), outlineVariant = Color(0xFFBAE6FD)
    ),
    semanticColors = ThemeSemanticColors(Color(0xFF1E3A8A), Color(0xFFB45309)),
    waveTop = Color(0xFF38BDF8),
    waveMid = Color(0xFF0284C7),
    waveBottom = Color(0xFF1E3A8A),
    dockBg = Color(0xFFFFFFFF),
    dockIconColor = Color(0xFF1E3A8A),
    fabBg = Color(0xFF0284C7),
    fabIconColor = Color(0xFFFFFFFF),
    breadcrumb = Color(0xFF0284C7),
    sectionText = Color(0xFF0369A1),
    noteCardBg = Color(0xFFFFFFFF),
    noteSelectedCardBg = Color(0xFFBAE6FD),
    noteTitleColor = Color(0xFF0C4A6E),
    notePreviewColor = Color(0xFF0369A1),
    noteLabelColor = Color(0xFF0C4A6E),
    noteActionIconColor = Color(0xFF1E3A8A),
    dockDivider = Color(0xFFBAE6FD)
)

// ─────────────────────────────────────────────
// 4. SAKURA — Soft, refined cherry blossom
// ─────────────────────────────────────────────
val SakuraThemePalette = ThemePalette(
    colorScheme = lightColorScheme(
        primary = Color(0xFF9D174D), onPrimary = Color.White,
        primaryContainer = Color(0xFFFBCFE8), onPrimaryContainer = Color(0xFF500724),
        secondary = Color(0xFF831843), onSecondary = Color.White,
        secondaryContainer = Color(0xFFFBCFE8), onSecondaryContainer = Color(0xFF500724),
        tertiary = Color(0xFFDB2777), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFCE7F3), onTertiaryContainer = Color(0xFF500724),
        background = Color(0xFFFDF2F8), onBackground = Color(0xFF500724),
        surface = Color(0xFFFDF2F8), onSurface = Color(0xFF500724),
        surfaceVariant = Color(0xFFFCE7F3), onSurfaceVariant = Color(0xFF9D174D),
        outline = Color(0xFF9CA3AF), outlineVariant = Color(0xFFFBCFE8)
    ),
    semanticColors = ThemeSemanticColors(Color(0xFF831843), Color(0xFFD97706)),
    waveTop = Color(0xFFF472B6),
    waveMid = Color(0xFFDB2777),
    waveBottom = Color(0xFF831843),
    dockBg = Color(0xFFFFFFFF),
    dockIconColor = Color(0xFF831843),
    fabBg = Color(0xFFDB2777),
    fabIconColor = Color(0xFFFFFFFF),
    breadcrumb = Color(0xFFDB2777),
    sectionText = Color(0xFF9D174D),
    noteCardBg = Color(0xFFFCE7F3),
    noteSelectedCardBg = Color(0xFFFBCFE8),
    noteTitleColor = Color(0xFF500724),
    notePreviewColor = Color(0xFF9D174D),
    noteLabelColor = Color(0xFF500724),
    noteActionIconColor = Color(0xFF831843),
    dockDivider = Color(0xFFFBCFE8)
)

// ─────────────────────────────────────────────
// 5. LAVENDER — Calm lavender purple
// ─────────────────────────────────────────────
val LavenderThemePalette = ThemePalette(
    colorScheme = lightColorScheme(
        primary = Color(0xFF6D28D9), onPrimary = Color.White,
        primaryContainer = Color(0xFFDDD6FE), onPrimaryContainer = Color(0xFF2E1065),
        secondary = Color(0xFF4C1D95), onSecondary = Color.White,
        secondaryContainer = Color(0xFFDDD6FE), onSecondaryContainer = Color(0xFF2E1065),
        tertiary = Color(0xFF7C3AED), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFEDE9FE), onTertiaryContainer = Color(0xFF2E1065),
        background = Color(0xFFF5F3FF), onBackground = Color(0xFF2E1065),
        surface = Color(0xFFF5F3FF), onSurface = Color(0xFF2E1065),
        surfaceVariant = Color(0xFFEDE9FE), onSurfaceVariant = Color(0xFF6D28D9),
        outline = Color(0xFF9CA3AF), outlineVariant = Color(0xFFDDD6FE)
    ),
    semanticColors = ThemeSemanticColors(Color(0xFF4C1D95), Color(0xFFD97706)),
    waveTop = Color(0xFFA78BFA),
    waveMid = Color(0xFF7C3AED),
    waveBottom = Color(0xFF4C1D95),
    dockBg = Color(0xFFFFFFFF),
    dockIconColor = Color(0xFF4C1D95),
    fabBg = Color(0xFF7C3AED),
    fabIconColor = Color(0xFFFFFFFF),
    breadcrumb = Color(0xFF7C3AED),
    sectionText = Color(0xFF6D28D9),
    noteCardBg = Color(0xFFEDE9FE),
    noteSelectedCardBg = Color(0xFFDDD6FE),
    noteTitleColor = Color(0xFF2E1065),
    notePreviewColor = Color(0xFF6D28D9),
    noteLabelColor = Color(0xFF2E1065),
    noteActionIconColor = Color(0xFF4C1D95),
    dockDivider = Color(0xFFDDD6FE)
)

// ─────────────────────────────────────────────
// 6. LIGHT BASIC — Traditional light, minimal
// ─────────────────────────────────────────────
val LightBasicThemePalette = ThemePalette(
    colorScheme = lightColorScheme(
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
    semanticColors = ThemeSemanticColors(Color(0xFF374151), Color(0xFFD97706)),
    waveTop = Color(0xFF9CA3AF),
    waveMid = Color(0xFF6B7280),
    waveBottom = Color(0xFF374151),
    dockBg = Color(0xFFFFFFFF),
    dockIconColor = Color(0xFF374151),
    fabBg = Color(0xFF374151),
    fabIconColor = Color(0xFFFFFFFF),
    breadcrumb = Color(0xFF6B7280),
    sectionText = Color(0xFF4B5563),
    noteCardBg = Color(0xFFF8FAFC),
    noteSelectedCardBg = Color(0xFFE5E7EB),
    noteTitleColor = Color(0xFF111827),
    notePreviewColor = Color(0xFF4B5563),
    noteLabelColor = Color(0xFF111827),
    noteActionIconColor = Color(0xFF374151),
    dockDivider = Color(0xFFE5E7EB)
)

// ─────────────────────────────────────────────
// 7. BASIC DARK — Traditional dark gray, minimal
// ─────────────────────────────────────────────
val BasicDarkThemePalette = ThemePalette(
    colorScheme = darkColorScheme(
        primary = Color(0xFF111827), onPrimary = Color(0xFFF9FAFB),
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
    semanticColors = ThemeSemanticColors(Color(0xFFF9FAFB), Color(0xFFFBBF24)),
    waveTop = Color(0xFF4B5563),
    waveMid = Color(0xFF374151),
    waveBottom = Color(0xFF111827),
    dockBg = Color(0xFFF9FAFB),
    dockIconColor = Color(0xFF111827),
    fabBg = Color(0xFFF9FAFB),
    fabIconColor = Color(0xFF111827),
    breadcrumb = Color(0xFF94A3B8),
    sectionText = Color(0xFFCBD5E1),
    noteCardBg = Color(0xFF374151),
    noteSelectedCardBg = Color(0xFF374151),
    noteTitleColor = Color(0xFFF9FAFB),
    notePreviewColor = Color(0xFFCBD5E1),
    noteLabelColor = Color(0xFFF9FAFB),
    noteActionIconColor = Color(0xFF111827),
    dockDivider = Color(0xFF6B7280)
)

// ─────────────────────────────────────────────
// 8. DEEP OCEAN — Deep navy ocean, mysterious
// ─────────────────────────────────────────────
val DeepOceanThemePalette = ThemePalette(
    colorScheme = darkColorScheme(
        primary = Color(0xFF0A1929), onPrimary = Color(0xFFE3F2FD),
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
    semanticColors = ThemeSemanticColors(Color(0xFF38BDF8), Color(0xFFFBBF24)),
    waveTop = Color(0xFF1565C0),
    waveMid = Color(0xFF0D47A1),
    waveBottom = Color(0xFF0A1929),
    dockBg = Color(0xFFE3F2FD),
    dockIconColor = Color(0xFF0A1929),
    fabBg = Color(0xFF38BDF8),
    fabIconColor = Color(0xFF0A1929),
    breadcrumb = Color(0xFF38BDF8),
    sectionText = Color(0xFF90CAF9),
    noteCardBg = Color(0xFF1E4976),
    noteSelectedCardBg = Color(0xFF14507A),
    noteTitleColor = Color(0xFFE3F2FD),
    notePreviewColor = Color(0xFF90CAF9),
    noteLabelColor = Color(0xFFE3F2FD),
    noteActionIconColor = Color(0xFF0A1929),
    dockDivider = Color(0xFF64B5F6)
)

// ─────────────────────────────────────────────
// 9. COFFEE — Warm coffee brown, amber accents
// ─────────────────────────────────────────────
val CoffeeThemePalette = ThemePalette(
    colorScheme = darkColorScheme(
        primary = Color(0xFF2B1A12), onPrimary = Color(0xFFF5E6D3),
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
    semanticColors = ThemeSemanticColors(Color(0xFFE8A87C), Color(0xFFFBBF24)),
    waveTop = Color(0xFFA0522D),
    waveMid = Color(0xFF7A3B1F),
    waveBottom = Color(0xFF2B1A12),
    dockBg = Color(0xFFF5E6D3),
    dockIconColor = Color(0xFF2B1A12),
    fabBg = Color(0xFFE8A87C),
    fabIconColor = Color(0xFF2B1A12),
    breadcrumb = Color(0xFFE8A87C),
    sectionText = Color(0xFFD4A574),
    noteCardBg = Color(0xFF6B4630),
    noteSelectedCardBg = Color(0xFF7A3B1F),
    noteTitleColor = Color(0xFFF5E6D3),
    notePreviewColor = Color(0xFFD4A574),
    noteLabelColor = Color(0xFFF5E6D3),
    noteActionIconColor = Color(0xFF2B1A12),
    dockDivider = Color(0xFFB08968)
)

// ─────────────────────────────────────────────
// 10. OBSIDIAN — Dark violet obsidian, mysterious and refined
// ─────────────────────────────────────────────
val ObsidianThemePalette = ThemePalette(
    colorScheme = darkColorScheme(
        primary = Color(0xFF1A0F2E), onPrimary = Color(0xFFEDE9FE),
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
    semanticColors = ThemeSemanticColors(Color(0xFFA78BFA), Color(0xFFFBBF24)),
    waveTop = Color(0xFF8B5CF6),
    waveMid = Color(0xFF5B21B6),
    waveBottom = Color(0xFF1A0F2E),
    dockBg = Color(0xFFEDE9FE),
    dockIconColor = Color(0xFF1A0F2E),
    fabBg = Color(0xFFA78BFA),
    fabIconColor = Color(0xFF1A0F2E),
    breadcrumb = Color(0xFFA78BFA),
    sectionText = Color(0xFFC4B5FD),
    noteCardBg = Color(0xFF4C2E6E),
    noteSelectedCardBg = Color(0xFF5B21B6),
    noteTitleColor = Color(0xFFEDE9FE),
    notePreviewColor = Color(0xFFC4B5FD),
    noteLabelColor = Color(0xFFEDE9FE),
    noteActionIconColor = Color(0xFF1A0F2E),
    dockDivider = Color(0xFFDDD6FE)
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