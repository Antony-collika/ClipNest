package com.clipnest.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.clipnest.data.local.ThemePreset

/**
 * Central source of all ClipNest theme colors.
 * ColorScheme retains generic Material 3 roles; component-specific colors are
 * explicit ThemePalette properties so their HEX values are visible here.
 */
data class ThemePalette(
    val colorScheme: ColorScheme,
    // Shared transparency for Note/Vault card backgrounds.
    val cardBackgroundAlpha: Float,
    // Shared overlay background for Note/Vault screens.
    val screenOverlayBackground: Color,
    // Shared transparency for the Note/Vault screen overlay.
    val screenOverlayBackgroundAlpha: Float,

    // ============================================================
    // NOTE SCREEN
    // ============================================================
    // Background of an individual note card.
    val noteCardBackground: Color,
    // Color used for note card selected background.
    val noteCardSelectedBackground: Color,
    // Color used for note card pinned background.
    val noteCardPinnedBackground: Color,
    // Color used for note tag background.
    val noteTagBackground: Color,
    // Color used for note tag text.
    val noteTagText: Color,
    // Color used for note header background.
    val noteHeaderBackground: Color,
    // Color used for note header content.
    val noteHeaderContent: Color,
    // Color used for note breadcrumb background.
    val noteBreadcrumbBackground: Color,
    // Color used for note breadcrumb text.
    val noteBreadcrumbText: Color,
    // Color used for note breadcrumb divider.
    val noteBreadcrumbDivider: Color,
    // Color used for note section title.
    val noteSectionTitle: Color,
    // Color used for note title.
    val noteTitle: Color,
    // Color used for note preview text.
    val notePreviewText: Color,
    // Color used for note action icon.
    val noteActionIcon: Color,
    // Color used for note empty state text.
    val noteEmptyStateText: Color,
    // Color used for note search background.
    val noteSearchBackground: Color,
    // Color used for note search content.
    val noteSearchContent: Color,
    // Color used for note search hint.
    val noteSearchHint: Color,
    // Color used for note popup background.
    val notePopupBackground: Color,
    // Color used for note popup content.
    val notePopupContent: Color,
    // Color used for note popup secondary text.
    val notePopupSecondaryText: Color,
    // Color used for note popup divider.
    val notePopupDivider: Color,
    // Color used for note view mode background.
    val noteViewModeBackground: Color,
    // Color used for note view mode selected background.
    val noteViewModeSelectedBackground: Color,
    // Color used for note view mode selected icon.
    val noteViewModeSelectedIcon: Color,
    // Color used for note view mode unselected icon.
    val noteViewModeUnselectedIcon: Color,
    // Color used for note section chevron.
    val noteSectionChevron: Color,
    // Color used for note list divider.
    val noteListDivider: Color,

    // ============================================================
    // SHARED NAVIGATION
    // ============================================================
    // Background of the shared bottom navigation dock.
    val dockBackground: Color,
    // Color used for dock icon.
    val dockIcon: Color,
    // Color used for dock divider.
    val dockDivider: Color,
    // Color used for fab background.
    val fabBackground: Color,
    // Color used for fab icon.
    val fabIcon: Color,
    // Color used for main tab text.
    val mainTabText: Color,
    // Color used for main tab selected indicator.
    val mainTabSelectedIndicator: Color,

    // ============================================================
    // SEMANTIC STATES
    // ============================================================
    // Color of the pinned state indicator.
    val pinnedIndicator: Color,
    // Color used for sensitive indicator.
    val sensitiveIndicator: Color,

    // ============================================================
    // EDITOR
    // ============================================================
    // Text color of the native editor.
    val editorText: Color,
    // Color used for editor markdown preview background.
    val editorMarkdownPreviewBackground: Color,
    // Color used for editor markdown preview text.
    val editorMarkdownPreviewText: Color,
    // Color used for editor markdown preview muted text.
    val editorMarkdownPreviewMutedText: Color,
    // Color used for editor markdown preview surface variant.
    val editorMarkdownPreviewSurfaceVariant: Color,
    // Color used for editor markdown preview outline.
    val editorMarkdownPreviewOutline: Color,
    // Color used for editor markdown preview outline variant.
    val editorMarkdownPreviewOutlineVariant: Color,
    // Color used for editor markdown preview primary.
    val editorMarkdownPreviewPrimary: Color,
    // Color used for editor markdown preview code background.
    val editorMarkdownPreviewCodeBackground: Color,
    // CSS color-scheme mode for the Markdown preview renderer; not a component Color.
    val editorMarkdownPreviewColorScheme: String,
    // Color used for editor toolbar background.
    val editorToolbarBackground: Color,
    // Color used for editor toolbar icon.
    val editorToolbarIcon: Color,
    // Color used for editor toolbar disabled icon.
    val editorToolbarDisabledIcon: Color,
    // Color used for editor markdown preview divider.
    val editorMarkdownPreviewDivider: Color,
    // Color used for editor markdown preview shimmer base.
    val editorMarkdownPreviewShimmerBase: Color,
    // Color used for editor markdown preview shimmer highlight.
    val editorMarkdownPreviewShimmerHighlight: Color,

    // ============================================================
    // VAULT
    // ============================================================
    // Background of an individual vault clipboard card.
    val vaultClipboardCardBackground: Color,
    // Color used for vault clipboard card selected background.
    val vaultClipboardCardSelectedBackground: Color,
    // Color used for vault clipboard card pressed background.
    val vaultClipboardCardPressedBackground: Color,
    // Color used for vault clipboard card dragging background.
    val vaultClipboardCardDraggingBackground: Color,
    // Color used for vault clipboard preview text.
    val vaultClipboardPreviewText: Color,
    // Color used for vault clipboard meta text.
    val vaultClipboardMetaText: Color,
    // Color used for vault action icon.
    val vaultActionIcon: Color,
    // Color used for vault checkbox checked.
    val vaultCheckboxChecked: Color,
    // Color used for vault checkbox unchecked.
    val vaultCheckboxUnchecked: Color,
    // Color used for vault drag handle.
    val vaultDragHandle: Color,
    // Color used for vault drag handle active.
    val vaultDragHandleActive: Color,
    // Color used for vault empty state icon.
    val vaultEmptyStateIcon: Color,
    // Color used for vault group divider.
    val vaultGroupDivider: Color,
    // Color used for vault popup divider.
    val vaultPopupDivider: Color,

    // ============================================================
    // SHARE DIALOG
    // ============================================================
    // Background of an unselected share option.
    val shareOptionBackground: Color,
    // Color used for share option selected background.
    val shareOptionSelectedBackground: Color,
    // Color used for share option selected border.
    val shareOptionSelectedBorder: Color,
    // Color used for share option accent.
    val shareOptionAccent: Color,
    // Color used for share option unselected accent.
    val shareOptionUnselectedAccent: Color,
    // Color used for share option text.
    val shareOptionText: Color,
    // Color used for share option hint.
    val shareOptionHint: Color,

    // ============================================================
    // SETTINGS / COMMON DIALOGS
    // ============================================================
    // Title text color of a settings group.
    val settingsGroupTitle: Color,
    // Color used for settings group icon.
    val settingsGroupIcon: Color,
    // Color used for settings privacy card background.
    val settingsPrivacyCardBackground: Color,
    // Background of the first-run education callout.
    val firstRunCalloutBackground: Color
)

val ForestThemePalette = ThemePalette(
    colorScheme = lightColorScheme(

        primary = Color(0xFF16301F), onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFCFE3D5), onPrimaryContainer = Color(0xFF16301F),
        secondary = Color(0xFF55635B), onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD6E5DB), onSecondaryContainer = Color(0xFF16301F),
        tertiary = Color(0xFF234A32), onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFC9E1D2), onTertiaryContainer = Color(0xFF16301F),
        background = Color(0xFFF1E9DA), onBackground = Color(0xFF17201B),
        surface = Color(0xFFF1E9DA), onSurface = Color(0xFF17201B),
        surfaceVariant = Color(0xFFE5DAC7), onSurfaceVariant = Color(0xFF55635B),
        outline = Color(0xFF6C776F), outlineVariant = Color(0xFFD6E5DB)
    ),

    cardBackgroundAlpha = 0.5f,
    screenOverlayBackground = Color(0xFFF1E9DA),
    screenOverlayBackgroundAlpha = 0.88f,

    // NOTE SCREEN
    noteCardBackground = Color(0xFFFDFAF3),
    noteCardSelectedBackground = Color(0xFFDCEBE1),
    noteCardPinnedBackground = Color(0xFFE4F0E8),
    noteTagBackground = Color(0xFFD3E8DC),
    noteTagText = Color(0xFF16301F),
    noteHeaderBackground = Color(0xFF16301F),
    noteHeaderContent = Color(0xFFFFFFFF),
    noteBreadcrumbBackground = Color(0xFFE4F0E8),
    noteBreadcrumbText = Color(0xFF234A32),
    noteBreadcrumbDivider = Color(0xFF234A32),
    noteSectionTitle = Color(0xFF55635B),
    noteTitle = Color(0xFF17201B),
    notePreviewText = Color(0xFF55635B),
    noteActionIcon = Color(0xFF2A312C),
    noteEmptyStateText = Color(0xFF55635B),
    noteSearchBackground = Color(0xFFE5DAC7),
    noteSearchContent = Color(0xFF17201B),
    noteSearchHint = Color(0xFF55635B),
    notePopupBackground = Color(0xFFF1E9DA),
    notePopupContent = Color(0xFF17201B),
    notePopupSecondaryText = Color(0xFF55635B),
    notePopupDivider = Color(0xFFD6E5DB),
    noteViewModeBackground = Color(0xFFE5DAC7),
    noteViewModeSelectedBackground = Color(0xFFFDFAF3),
    noteViewModeSelectedIcon = Color(0xFF16301F),
    noteViewModeUnselectedIcon = Color(0xFF55635B),
    noteSectionChevron = Color(0xFF17201B),
    noteListDivider = Color(0xFFD6E5DB),

    // SHARED NAVIGATION
    dockBackground = Color(0xFFFDFAF3),
    dockIcon = Color(0xFF2A312C),
    dockDivider = Color(0xFFD6E5DB),
    fabBackground = Color(0xFF16301F),
    fabIcon = Color(0xFFFFFFFF),
    mainTabText = Color(0xFFF1E9DA),
    mainTabSelectedIndicator = Color(0xFFB7D5BF),

    // SEMANTIC STATES
    pinnedIndicator = Color(0xFF16301F),
    sensitiveIndicator = Color(0xFFB45309),

    // EDITOR
    editorText = Color(0xFF17201B),
    editorMarkdownPreviewBackground = Color(0xFFFDFAF3),
    editorMarkdownPreviewText = Color(0xFF17201B),
    editorMarkdownPreviewMutedText = Color(0xFF55635B),
    editorMarkdownPreviewSurfaceVariant = Color(0xFFE5DAC7),
    editorMarkdownPreviewOutline = Color(0xFF8A948C),
    editorMarkdownPreviewOutlineVariant = Color(0xFFCFDDD3),
    editorMarkdownPreviewPrimary = Color(0xFF16301F),
    editorMarkdownPreviewCodeBackground = Color(0xFFE5DAC7),
    editorMarkdownPreviewColorScheme = "light",
    editorToolbarBackground = Color(0xFFEDE4D2),
    editorToolbarIcon = Color(0xFF16301F),
    editorToolbarDisabledIcon = Color(0xFFA9B3AB),
    editorMarkdownPreviewDivider = Color(0xFFD6E5DB),
    editorMarkdownPreviewShimmerBase = Color(0xFFE0EADF),
    editorMarkdownPreviewShimmerHighlight = Color(0xFFCFE3D5),

    // VAULT
    vaultClipboardCardBackground = Color(0xFFFDFAF3),
    vaultClipboardCardSelectedBackground = Color(0xFFDCEBE1),
    vaultClipboardCardPressedBackground = Color(0xFFE4F0E8),
    vaultClipboardCardDraggingBackground = Color(0xFFCFE3D5),
    vaultClipboardPreviewText = Color(0xFF17201B),
    vaultClipboardMetaText = Color(0xFF55635B),
    vaultActionIcon = Color(0xFF55635B),
    vaultCheckboxChecked = Color(0xFF16301F),
    vaultCheckboxUnchecked = Color(0xFF55635B),
    vaultDragHandle = Color(0xFF55635B),
    vaultDragHandleActive = Color(0xFF17201B),
    vaultEmptyStateIcon = Color(0xFF234A32),
    vaultGroupDivider = Color(0xFFD6E5DB),
    vaultPopupDivider = Color(0xFFD6E5DB),

    // SHARE DIALOG
    shareOptionBackground = Color(0xFFEDE4D2),
    shareOptionSelectedBackground = Color(0xFFDCEBE1),
    shareOptionSelectedBorder = Color(0xFF16301F),
    shareOptionAccent = Color(0xFF16301F),
    shareOptionUnselectedAccent = Color(0xFF55635B),
    shareOptionText = Color(0xFF17201B),
    shareOptionHint = Color(0xFF55635B),

    // SETTINGS / COMMON DIALOGS
    settingsGroupTitle = Color(0xFF17201B),
    settingsGroupIcon = Color(0xFF16301F),
    settingsPrivacyCardBackground = Color(0xFFE4F0E8),
    firstRunCalloutBackground = Color(0xFFEDE4D2)
)
val NordThemePalette = ThemePalette(
    colorScheme = lightColorScheme(

        primary = Color(0xFF2E3440), onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD8DEE9), onPrimaryContainer = Color(0xFF2E3440),
        secondary = Color(0xFF4C566A), onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD8DEE9), onSecondaryContainer = Color(0xFF2E3440),
        tertiary = Color(0xFF5E81AC), onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFD8DEE9), onTertiaryContainer = Color(0xFF2E3440),
        background = Color(0xFFECEFF4), onBackground = Color(0xFF2E3440),
        surface = Color(0xFFECEFF4), onSurface = Color(0xFF2E3440),
        surfaceVariant = Color(0xFFE5E9F0), onSurfaceVariant = Color(0xFF4C566A),
        outline = Color(0xFF7B8794), outlineVariant = Color(0xFFD8DEE9)
    ),

    cardBackgroundAlpha = 0.5f,
    screenOverlayBackground = Color(0xFFECEFF4),
    screenOverlayBackgroundAlpha = 0.88f,

    // NOTE SCREEN
    noteCardBackground = Color(0xFFFFFFFF),
    noteCardSelectedBackground = Color(0xFFFFDEE3),
    noteCardPinnedBackground = Color(0xFFFFEAEB),
    noteTagBackground = Color(0xFFFFDDE2),
    noteTagText = Color(0xFF2E3440),
    noteHeaderBackground = Color(0xFF2E3440),
    noteHeaderContent = Color(0xFFFFFFFF),
    noteBreadcrumbBackground = Color(0xFFECEFF4),
    noteBreadcrumbText = Color(0xFF5E81AC),
    noteBreadcrumbDivider = Color(0xFFFF465B),
    noteSectionTitle = Color(0xFF4C566A),
    noteTitle = Color(0xFF2E3440),
    notePreviewText = Color(0xFF4C566A),
    noteActionIcon = Color(0xFF2E3440),
    noteEmptyStateText = Color(0xFF4C566A),
    noteSearchBackground = Color(0xFFE5E9F0),
    noteSearchContent = Color(0xFF2E3440),
    noteSearchHint = Color(0xFF4C566A),
    notePopupBackground = Color(0xFFECEFF4),
    notePopupContent = Color(0xFF2E3440),
    notePopupSecondaryText = Color(0xFF4C566A),
    notePopupDivider = Color(0xFFFFE2E7),
    noteViewModeBackground = Color(0xFFE5E9F0),
    noteViewModeSelectedBackground = Color(0xFFECEFF4),
    noteViewModeSelectedIcon = Color(0xFF2E3440),
    noteViewModeUnselectedIcon = Color(0xFF4C566A),
    noteSectionChevron = Color(0xFF2E3440),
    noteListDivider = Color(0xFFD8DEE9),

    // SHARED NAVIGATION
    dockBackground = Color(0xFFFFFFFF),
    dockIcon = Color(0xFF2E3440),
    dockDivider = Color(0xFFD8DEE9),
    fabBackground = Color(0xFF5E81AC),
    fabIcon = Color(0xFFFFFFFF),
    mainTabText = Color(0xFFF6F4EA),
    mainTabSelectedIndicator = Color(0xFFDDE8B5),

    // SEMANTIC STATES
    pinnedIndicator = Color(0xFF2E3440),
    sensitiveIndicator = Color(0xFFB45309),

    // EDITOR
    editorText = Color(0xFF2E3440),
    editorMarkdownPreviewBackground = Color(0xFFF6F6F6),
    editorMarkdownPreviewText = Color(0xFF171717),
    editorMarkdownPreviewMutedText = Color(0xFF5E5E5E),
    editorMarkdownPreviewSurfaceVariant = Color(0xFFE8E8E8),
    editorMarkdownPreviewOutline = Color(0xFF8A8A8A),
    editorMarkdownPreviewOutlineVariant = Color(0xFFC7C7C7),
    editorMarkdownPreviewPrimary = Color(0xFF171717),
    editorMarkdownPreviewCodeBackground = Color(0xFFE8E8E8),
    editorMarkdownPreviewColorScheme = "light",
    editorToolbarBackground = Color(0xFFFFEAED),
    editorToolbarIcon = Color(0xFF2E3440),
    editorToolbarDisabledIcon = Color(0xFFFFB6AA),
    editorMarkdownPreviewDivider = Color(0xFFFFCECE),
    editorMarkdownPreviewShimmerBase = Color(0xFFFFE0E0),
    editorMarkdownPreviewShimmerHighlight = Color(0xFFFFC9C9),

    // VAULT
    vaultClipboardCardBackground = Color(0xFFECEFF4),
    vaultClipboardCardSelectedBackground = Color(0xFFFFE5E9),
    vaultClipboardCardPressedBackground = Color(0xFFFFE8EB),
    vaultClipboardCardDraggingBackground = Color(0xFFD8DEE9),
    vaultClipboardPreviewText = Color(0xFF2E3440),
    vaultClipboardMetaText = Color(0xFF4C566A),
    vaultActionIcon = Color(0xFF4C566A),
    vaultCheckboxChecked = Color(0xFF2E3440),
    vaultCheckboxUnchecked = Color(0xFF4C566A),
    vaultDragHandle = Color(0xFF4C566A),
    vaultDragHandleActive = Color(0xFF2E3440),
    vaultEmptyStateIcon = Color(0xFFFF676C),
    vaultGroupDivider = Color(0xFFD8DEE9),
    vaultPopupDivider = Color(0xFFFFE2E7),

    // SHARE DIALOG
    shareOptionBackground = Color(0xFFFFE9ED),
    shareOptionSelectedBackground = Color(0xFFFFE3E7),
    shareOptionSelectedBorder = Color(0xFF2E3440),
    shareOptionAccent = Color(0xFF2E3440),
    shareOptionUnselectedAccent = Color(0xFF4C566A),
    shareOptionText = Color(0xFF2E3440),
    shareOptionHint = Color(0xFF4C566A),

    // SETTINGS / COMMON DIALOGS
    settingsGroupTitle = Color(0xFF2E3440),
    settingsGroupIcon = Color(0xFF2E3440),
    settingsPrivacyCardBackground = Color(0xFFFFE5E9),
    firstRunCalloutBackground = Color(0xFFFFE9EC)
)

val SnowSapphireThemePalette = ThemePalette(
    colorScheme = lightColorScheme(

        primary = Color(0xFF1E3A8A), onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFCFE3F7), onPrimaryContainer = Color(0xFF0C2B5C),
        secondary = Color(0xFF2C5282), onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFDCEAF7), onSecondaryContainer = Color(0xFF0C2B5C),
        tertiary = Color(0xFF3B82C4), onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFE0EFFB), onTertiaryContainer = Color(0xFF0C2B5C),
        background = Color(0xFFEAF3FB), onBackground = Color(0xFF0C2B5C),
        surface = Color(0xFFEAF3FB), onSurface = Color(0xFF0C2B5C),
        surfaceVariant = Color(0xFFD6E6F5), onSurfaceVariant = Color(0xFF2C5282),
        outline = Color(0xFF6A8AAE), outlineVariant = Color(0xFFCFE3F7)
    ),

    cardBackgroundAlpha = 0.5f,
    screenOverlayBackground = Color(0xFFEAF3FB),
    screenOverlayBackgroundAlpha = 0.88f,

    // NOTE SCREEN
    noteCardBackground = Color(0xFFF4F9FE),
    noteCardSelectedBackground = Color(0xFFCFE3F7),
    noteCardPinnedBackground = Color(0xFFDCEAF7),
    noteTagBackground = Color(0xFFC9E0F5),
    noteTagText = Color(0xFF0C2B5C),
    noteHeaderBackground = Color(0xFF1E3A8A),
    noteHeaderContent = Color(0xFFFFFFFF),
    noteBreadcrumbBackground = Color(0xFFDCEAF7),
    noteBreadcrumbText = Color(0xFF2C5282),
    noteBreadcrumbDivider = Color(0xFF2C5282),
    noteSectionTitle = Color(0xFF2C5282),
    noteTitle = Color(0xFF0C2B5C),
    notePreviewText = Color(0xFF2C5282),
    noteActionIcon = Color(0xFF1E3A8A),
    noteEmptyStateText = Color(0xFF2C5282),
    noteSearchBackground = Color(0xFFD6E6F5),
    noteSearchContent = Color(0xFF0C2B5C),
    noteSearchHint = Color(0xFF2C5282),
    notePopupBackground = Color(0xFFEAF3FB),
    notePopupContent = Color(0xFF0C2B5C),
    notePopupSecondaryText = Color(0xFF2C5282),
    notePopupDivider = Color(0xFFCFE3F7),
    noteViewModeBackground = Color(0xFFD6E6F5),
    noteViewModeSelectedBackground = Color(0xFFF4F9FE),
    noteViewModeSelectedIcon = Color(0xFF1E3A8A),
    noteViewModeUnselectedIcon = Color(0xFF2C5282),
    noteSectionChevron = Color(0xFF0C2B5C),
    noteListDivider = Color(0xFFCFE3F7),

    // SHARED NAVIGATION
    dockBackground = Color(0xFFF4F9FE),
    dockIcon = Color(0xFF1E3A8A),
    dockDivider = Color(0xFFCFE3F7),
    fabBackground = Color(0xFF1E3A8A),
    fabIcon = Color(0xFFFFFFFF),
    mainTabText = Color(0xFFEAF3FB),
    mainTabSelectedIndicator = Color(0xFFA9C9EC),

    // SEMANTIC STATES
    pinnedIndicator = Color(0xFF1E3A8A),
    sensitiveIndicator = Color(0xFFB45309),

    // EDITOR
    editorText = Color(0xFF0C2B5C),
    editorMarkdownPreviewBackground = Color(0xFFF4F9FE),
    editorMarkdownPreviewText = Color(0xFF0C2B5C),
    editorMarkdownPreviewMutedText = Color(0xFF2C5282),
    editorMarkdownPreviewSurfaceVariant = Color(0xFFD6E6F5),
    editorMarkdownPreviewOutline = Color(0xFF7B98BC),
    editorMarkdownPreviewOutlineVariant = Color(0xFFCFE3F7),
    editorMarkdownPreviewPrimary = Color(0xFF1E3A8A),
    editorMarkdownPreviewCodeBackground = Color(0xFFD6E6F5),
    editorMarkdownPreviewColorScheme = "light",
    editorToolbarBackground = Color(0xFFE2EEF9),
    editorToolbarIcon = Color(0xFF1E3A8A),
    editorToolbarDisabledIcon = Color(0xFF9FB4CE),
    editorMarkdownPreviewDivider = Color(0xFFCFE3F7),
    editorMarkdownPreviewShimmerBase = Color(0xFFDCEAF7),
    editorMarkdownPreviewShimmerHighlight = Color(0xFFCFE3F7),

    // VAULT
    vaultClipboardCardBackground = Color(0xFFF4F9FE),
    vaultClipboardCardSelectedBackground = Color(0xFFCFE3F7),
    vaultClipboardCardPressedBackground = Color(0xFFDCEAF7),
    vaultClipboardCardDraggingBackground = Color(0xFFB9D6F0),
    vaultClipboardPreviewText = Color(0xFF0C2B5C),
    vaultClipboardMetaText = Color(0xFF2C5282),
    vaultActionIcon = Color(0xFF2C5282),
    vaultCheckboxChecked = Color(0xFF1E3A8A),
    vaultCheckboxUnchecked = Color(0xFF2C5282),
    vaultDragHandle = Color(0xFF2C5282),
    vaultDragHandleActive = Color(0xFF0C2B5C),
    vaultEmptyStateIcon = Color(0xFF2C5282),
    vaultGroupDivider = Color(0xFFCFE3F7),
    vaultPopupDivider = Color(0xFFCFE3F7),

    // SHARE DIALOG
    shareOptionBackground = Color(0xFFE2EEF9),
    shareOptionSelectedBackground = Color(0xFFCFE3F7),
    shareOptionSelectedBorder = Color(0xFF1E3A8A),
    shareOptionAccent = Color(0xFF1E3A8A),
    shareOptionUnselectedAccent = Color(0xFF2C5282),
    shareOptionText = Color(0xFF0C2B5C),
    shareOptionHint = Color(0xFF2C5282),

    // SETTINGS / COMMON DIALOGS
    settingsGroupTitle = Color(0xFF0C2B5C),
    settingsGroupIcon = Color(0xFF1E3A8A),
    settingsPrivacyCardBackground = Color(0xFFDCEAF7),
    firstRunCalloutBackground = Color(0xFFE2EEF9)
)

val SakuraThemePalette = ThemePalette(
    colorScheme = lightColorScheme(

        primary = Color(0xFF9D174D), onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFBCFE8), onPrimaryContainer = Color(0xFF500724),
        secondary = Color(0xFF831843), onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFBCFE8), onSecondaryContainer = Color(0xFF500724),
        tertiary = Color(0xFFDB2777), onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFCE7F3), onTertiaryContainer = Color(0xFF500724),
        background = Color(0xFFFDF2F8), onBackground = Color(0xFF500724),
        surface = Color(0xFFFDF2F8), onSurface = Color(0xFF500724),
        surfaceVariant = Color(0xFFFCE7F3), onSurfaceVariant = Color(0xFF9D174D),
        outline = Color(0xFF9CA3AF), outlineVariant = Color(0xFFFBCFE8)
    ),

    cardBackgroundAlpha = 0.5f,
    screenOverlayBackground = Color(0xFFFDF2F8),
    screenOverlayBackgroundAlpha = 0.88f,

    // NOTE SCREEN
    noteCardBackground = Color(0xFFFFFFFF),
    noteCardSelectedBackground = Color(0xFFFFFCD9),
    noteCardPinnedBackground = Color(0xFFFFF3E8),
    noteTagBackground = Color(0xFFFFFBD5),
    noteTagText = Color(0xFF500724),
    noteHeaderBackground = Color(0xFF9D174D),
    noteHeaderContent = Color(0xFFFFFFFF),
    noteBreadcrumbBackground = Color(0xFFFCE7F3),
    noteBreadcrumbText = Color(0xFFDB2777),
    noteBreadcrumbDivider = Color(0xFFFFBC1F),
    noteSectionTitle = Color(0xFF9D174D),
    noteTitle = Color(0xFF500724),
    notePreviewText = Color(0xFF9D174D),
    noteActionIcon = Color(0xFF831843),
    noteEmptyStateText = Color(0xFF9D174D),
    noteSearchBackground = Color(0xFFFCE7F3),
    noteSearchContent = Color(0xFF500724),
    noteSearchHint = Color(0xFF9D174D),
    notePopupBackground = Color(0xFFFDF2F8),
    notePopupContent = Color(0xFF500724),
    notePopupSecondaryText = Color(0xFF9D174D),
    notePopupDivider = Color(0xFFFFFCE1),
    noteViewModeBackground = Color(0xFFFCE7F3),
    noteViewModeSelectedBackground = Color(0xFFFDF2F8),
    noteViewModeSelectedIcon = Color(0xFF9D174D),
    noteViewModeUnselectedIcon = Color(0xFF9D174D),
    noteSectionChevron = Color(0xFF500724),
    noteListDivider = Color(0xFFFBCFE8),

    // SHARED NAVIGATION
    dockBackground = Color(0xFFFFFFFF),
    dockIcon = Color(0xFF831843),
    dockDivider = Color(0xFFFBCFE8),
    fabBackground = Color(0xFFDB2777),
    fabIcon = Color(0xFFFFFFFF),
    mainTabText = Color(0xFFF6F4EA),
    mainTabSelectedIndicator = Color(0xFFDDE8B5),

    // SEMANTIC STATES
    pinnedIndicator = Color(0xFF831843),
    sensitiveIndicator = Color(0xFFD97706),

    // EDITOR
    editorText = Color(0xFF500724),
    editorMarkdownPreviewBackground = Color(0xFFF6F6F6),
    editorMarkdownPreviewText = Color(0xFF171717),
    editorMarkdownPreviewMutedText = Color(0xFF5E5E5E),
    editorMarkdownPreviewSurfaceVariant = Color(0xFFE8E8E8),
    editorMarkdownPreviewOutline = Color(0xFF8A8A8A),
    editorMarkdownPreviewOutlineVariant = Color(0xFFC7C7C7),
    editorMarkdownPreviewPrimary = Color(0xFF171717),
    editorMarkdownPreviewCodeBackground = Color(0xFFE8E8E8),
    editorMarkdownPreviewColorScheme = "light",
    editorToolbarBackground = Color(0xFFFFFDEF),
    editorToolbarIcon = Color(0xFF9D174D),
    editorToolbarDisabledIcon = Color(0xFFFFC2A7),
    editorMarkdownPreviewDivider = Color(0xFFFFCECE),
    editorMarkdownPreviewShimmerBase = Color(0xFFFFE0E0),
    editorMarkdownPreviewShimmerHighlight = Color(0xFFFFC9C9),

    // VAULT
    vaultClipboardCardBackground = Color(0xFFFDF2F8),
    vaultClipboardCardSelectedBackground = Color(0xFFFFFCE6),
    vaultClipboardCardPressedBackground = Color(0xFFFFFDEA),
    vaultClipboardCardDraggingBackground = Color(0xFFFBCFE8),
    vaultClipboardPreviewText = Color(0xFF500724),
    vaultClipboardMetaText = Color(0xFF9D174D),
    vaultActionIcon = Color(0xFF9D174D),
    vaultCheckboxChecked = Color(0xFF9D174D),
    vaultCheckboxUnchecked = Color(0xFF9D174D),
    vaultDragHandle = Color(0xFF9D174D),
    vaultDragHandleActive = Color(0xFF500724),
    vaultEmptyStateIcon = Color(0xFFFFBA59),
    vaultGroupDivider = Color(0xFFFBCFE8),
    vaultPopupDivider = Color(0xFFFFFCE1),

    // SHARE DIALOG
    shareOptionBackground = Color(0xFFFFFDEE),
    shareOptionSelectedBackground = Color(0xFFFFFCE2),
    shareOptionSelectedBorder = Color(0xFF9D174D),
    shareOptionAccent = Color(0xFF9D174D),
    shareOptionUnselectedAccent = Color(0xFF9D174D),
    shareOptionText = Color(0xFF500724),
    shareOptionHint = Color(0xFF9D174D),

    // SETTINGS / COMMON DIALOGS
    settingsGroupTitle = Color(0xFF500724),
    settingsGroupIcon = Color(0xFF9D174D),
    settingsPrivacyCardBackground = Color(0xFFFFFCE6),
    firstRunCalloutBackground = Color(0xFFFFFDED)
)

val LavenderThemePalette = ThemePalette(
    colorScheme = lightColorScheme(

        primary = Color(0xFF6D28D9), onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFDDD6FE), onPrimaryContainer = Color(0xFF2E1065),
        secondary = Color(0xFF4C1D95), onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFDDD6FE), onSecondaryContainer = Color(0xFF2E1065),
        tertiary = Color(0xFF7C3AED), onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFEDE9FE), onTertiaryContainer = Color(0xFF2E1065),
        background = Color(0xFFF5F3FF), onBackground = Color(0xFF2E1065),
        surface = Color(0xFFF5F3FF), onSurface = Color(0xFF2E1065),
        surfaceVariant = Color(0xFFEDE9FE), onSurfaceVariant = Color(0xFF6D28D9),
        outline = Color(0xFF9CA3AF), outlineVariant = Color(0xFFDDD6FE)
    ),

    cardBackgroundAlpha = 0.5f,
    screenOverlayBackground = Color(0xFFF5F3FF),
    screenOverlayBackgroundAlpha = 0.88f,

    // NOTE SCREEN
    noteCardBackground = Color(0xFFFFFFFF),
    noteCardSelectedBackground = Color(0xFFFFE4DE),
    noteCardPinnedBackground = Color(0xFFFFEDE8),
    noteTagBackground = Color(0xFFFFE1DB),
    noteTagText = Color(0xFF2E1065),
    noteHeaderBackground = Color(0xFF6D28D9),
    noteHeaderContent = Color(0xFFFFFFFF),
    noteBreadcrumbBackground = Color(0xFFEDE9FE),
    noteBreadcrumbText = Color(0xFF7C3AED),
    noteBreadcrumbDivider = Color(0xFFFF7531),
    noteSectionTitle = Color(0xFF6D28D9),
    noteTitle = Color(0xFF2E1065),
    notePreviewText = Color(0xFF6D28D9),
    noteActionIcon = Color(0xFF4C1D95),
    noteEmptyStateText = Color(0xFF6D28D9),
    noteSearchBackground = Color(0xFFEDE9FE),
    noteSearchContent = Color(0xFF2E1065),
    noteSearchHint = Color(0xFF6D28D9),
    notePopupBackground = Color(0xFFF5F3FF),
    notePopupContent = Color(0xFF2E1065),
    notePopupSecondaryText = Color(0xFF6D28D9),
    notePopupDivider = Color(0xFFFFE9E5),
    noteViewModeBackground = Color(0xFFEDE9FE),
    noteViewModeSelectedBackground = Color(0xFFF5F3FF),
    noteViewModeSelectedIcon = Color(0xFF6D28D9),
    noteViewModeUnselectedIcon = Color(0xFF6D28D9),
    noteSectionChevron = Color(0xFF2E1065),
    noteListDivider = Color(0xFFDDD6FE),

    // SHARED NAVIGATION
    dockBackground = Color(0xFFFFFFFF),
    dockIcon = Color(0xFF4C1D95),
    dockDivider = Color(0xFFDDD6FE),
    fabBackground = Color(0xFF7C3AED),
    fabIcon = Color(0xFFFFFFFF),
    mainTabText = Color(0xFFF6F4EA),
    mainTabSelectedIndicator = Color(0xFFDDE8B5),

    // SEMANTIC STATES
    pinnedIndicator = Color(0xFF4C1D95),
    sensitiveIndicator = Color(0xFFD97706),

    // EDITOR
    editorText = Color(0xFF2E1065),
    editorMarkdownPreviewBackground = Color(0xFFF6F6F6),
    editorMarkdownPreviewText = Color(0xFF171717),
    editorMarkdownPreviewMutedText = Color(0xFF5E5E5E),
    editorMarkdownPreviewSurfaceVariant = Color(0xFFE8E8E8),
    editorMarkdownPreviewOutline = Color(0xFF8A8A8A),
    editorMarkdownPreviewOutlineVariant = Color(0xFFC7C7C7),
    editorMarkdownPreviewPrimary = Color(0xFF171717),
    editorMarkdownPreviewCodeBackground = Color(0xFFE8E8E8),
    editorMarkdownPreviewColorScheme = "light",
    editorToolbarBackground = Color(0xFFFFF3F0),
    editorToolbarIcon = Color(0xFF6D28D9),
    editorToolbarDisabledIcon = Color(0xFFFFB6A4),
    editorMarkdownPreviewDivider = Color(0xFFFFCECE),
    editorMarkdownPreviewShimmerBase = Color(0xFFFFE0E0),
    editorMarkdownPreviewShimmerHighlight = Color(0xFFFFC9C9),

    // VAULT
    vaultClipboardCardBackground = Color(0xFFF5F3FF),
    vaultClipboardCardSelectedBackground = Color(0xFFFFEDE9),
    vaultClipboardCardPressedBackground = Color(0xFFFFF0ED),
    vaultClipboardCardDraggingBackground = Color(0xFFDDD6FE),
    vaultClipboardPreviewText = Color(0xFF2E1065),
    vaultClipboardMetaText = Color(0xFF6D28D9),
    vaultActionIcon = Color(0xFF6D28D9),
    vaultCheckboxChecked = Color(0xFF6D28D9),
    vaultCheckboxUnchecked = Color(0xFF6D28D9),
    vaultDragHandle = Color(0xFF6D28D9),
    vaultDragHandleActive = Color(0xFF2E1065),
    vaultEmptyStateIcon = Color(0xFFFF9665),
    vaultGroupDivider = Color(0xFFDDD6FE),
    vaultPopupDivider = Color(0xFFFFE9E5),

    // SHARE DIALOG
    shareOptionBackground = Color(0xFFFFF2EF),
    shareOptionSelectedBackground = Color(0xFFFFEAE6),
    shareOptionSelectedBorder = Color(0xFF6D28D9),
    shareOptionAccent = Color(0xFF6D28D9),
    shareOptionUnselectedAccent = Color(0xFF6D28D9),
    shareOptionText = Color(0xFF2E1065),
    shareOptionHint = Color(0xFF6D28D9),

    // SETTINGS / COMMON DIALOGS
    settingsGroupTitle = Color(0xFF2E1065),
    settingsGroupIcon = Color(0xFF6D28D9),
    settingsPrivacyCardBackground = Color(0xFFFFEDE9),
    firstRunCalloutBackground = Color(0xFFFFF1EE)
)

val LightBasicThemePalette = ThemePalette(
    colorScheme = lightColorScheme(

        primary = Color(0xFF374151), onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE5E7EB), onPrimaryContainer = Color(0xFF111827),
        secondary = Color(0xFF4B5563), onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE5E7EB), onSecondaryContainer = Color(0xFF111827),
        tertiary = Color(0xFF6B7280), onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFF3F4F6), onTertiaryContainer = Color(0xFF111827),
        background = Color(0xFFFFFFFF), onBackground = Color(0xFF111827),
        surface = Color(0xFFFFFFFF), onSurface = Color(0xFF111827),
        surfaceVariant = Color(0xFFF3F4F6), onSurfaceVariant = Color(0xFF4B5563),
        outline = Color(0xFF9CA3AF), outlineVariant = Color(0xFFE5E7EB)
    ),

    cardBackgroundAlpha = 0.5f,
    screenOverlayBackground = Color(0xFFFFFFFF),
    screenOverlayBackgroundAlpha = 0.88f,

    // NOTE SCREEN
    noteCardBackground = Color(0xFFF8FAFC),
    noteCardSelectedBackground = Color(0xFFFFECEE),
    noteCardPinnedBackground = Color(0xFFFFE5E8),
    noteTagBackground = Color(0xFFFFE7E9),
    noteTagText = Color(0xFF111827),
    noteHeaderBackground = Color(0xFF374151),
    noteHeaderContent = Color(0xFFFFFFFF),
    noteBreadcrumbBackground = Color(0xFFF3F4F6),
    noteBreadcrumbText = Color(0xFF6B7280),
    noteBreadcrumbDivider = Color(0xFFFF515A),
    noteSectionTitle = Color(0xFF4B5563),
    noteTitle = Color(0xFF111827),
    notePreviewText = Color(0xFF4B5563),
    noteActionIcon = Color(0xFF374151),
    noteEmptyStateText = Color(0xFF4B5563),
    noteSearchBackground = Color(0xFFF3F4F6),
    noteSearchContent = Color(0xFF111827),
    noteSearchHint = Color(0xFF4B5563),
    notePopupBackground = Color(0xFFFFFFFF),
    notePopupContent = Color(0xFF111827),
    notePopupSecondaryText = Color(0xFF4B5563),
    notePopupDivider = Color(0xFFFFF2F3),
    noteViewModeBackground = Color(0xFFF3F4F6),
    noteViewModeSelectedBackground = Color(0xFFFFFFFF),
    noteViewModeSelectedIcon = Color(0xFF374151),
    noteViewModeUnselectedIcon = Color(0xFF4B5563),
    noteSectionChevron = Color(0xFF111827),
    noteListDivider = Color(0xFFE5E7EB),

    // SHARED NAVIGATION
    dockBackground = Color(0xFFFFFFFF),
    dockIcon = Color(0xFF374151),
    dockDivider = Color(0xFFE5E7EB),
    fabBackground = Color(0xFF374151),
    fabIcon = Color(0xFFFFFFFF),
    mainTabText = Color(0xFFF6F4EA),
    mainTabSelectedIndicator = Color(0xFFDDE8B5),

    // SEMANTIC STATES
    pinnedIndicator = Color(0xFF374151),
    sensitiveIndicator = Color(0xFFD97706),

    // EDITOR
    editorText = Color(0xFF111827),
    editorMarkdownPreviewBackground = Color(0xFFF6F6F6),
    editorMarkdownPreviewText = Color(0xFF171717),
    editorMarkdownPreviewMutedText = Color(0xFF5E5E5E),
    editorMarkdownPreviewSurfaceVariant = Color(0xFFE8E8E8),
    editorMarkdownPreviewOutline = Color(0xFF8A8A8A),
    editorMarkdownPreviewOutlineVariant = Color(0xFFC7C7C7),
    editorMarkdownPreviewPrimary = Color(0xFF171717),
    editorMarkdownPreviewCodeBackground = Color(0xFFE8E8E8),
    editorMarkdownPreviewColorScheme = "light",
    editorToolbarBackground = Color(0xFFFFFCFC),
    editorToolbarIcon = Color(0xFF374151),
    editorToolbarDisabledIcon = Color(0xFFFFACAC),
    editorMarkdownPreviewDivider = Color(0xFFFFCECE),
    editorMarkdownPreviewShimmerBase = Color(0xFFFFE0E0),
    editorMarkdownPreviewShimmerHighlight = Color(0xFFFFC9C9),

    // VAULT
    vaultClipboardCardBackground = Color(0xFFFFFFFF),
    vaultClipboardCardSelectedBackground = Color(0xFFFFF6F7),
    vaultClipboardCardPressedBackground = Color(0xFFFFF9FA),
    vaultClipboardCardDraggingBackground = Color(0xFFE5E7EB),
    vaultClipboardPreviewText = Color(0xFF111827),
    vaultClipboardMetaText = Color(0xFF4B5563),
    vaultActionIcon = Color(0xFF4B5563),
    vaultCheckboxChecked = Color(0xFF374151),
    vaultCheckboxUnchecked = Color(0xFF4B5563),
    vaultDragHandle = Color(0xFF4B5563),
    vaultDragHandleActive = Color(0xFF111827),
    vaultEmptyStateIcon = Color(0xFFFF737A),
    vaultGroupDivider = Color(0xFFE5E7EB),
    vaultPopupDivider = Color(0xFFFFF2F3),

    // SHARE DIALOG
    shareOptionBackground = Color(0xFFFFFAFB),
    shareOptionSelectedBackground = Color(0xFFFFF3F4),
    shareOptionSelectedBorder = Color(0xFF374151),
    shareOptionAccent = Color(0xFF374151),
    shareOptionUnselectedAccent = Color(0xFF4B5563),
    shareOptionText = Color(0xFF111827),
    shareOptionHint = Color(0xFF4B5563),

    // SETTINGS / COMMON DIALOGS
    settingsGroupTitle = Color(0xFF111827),
    settingsGroupIcon = Color(0xFF374151),
    settingsPrivacyCardBackground = Color(0xFFFFF6F7),
    firstRunCalloutBackground = Color(0xFFFFF9FA)
)

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

    cardBackgroundAlpha = 0.5f,
    screenOverlayBackground = Color(0xFF2D3A4D),
    screenOverlayBackgroundAlpha = 0.88f,

    // NOTE SCREEN
    noteCardBackground = Color(0xFFFFFFFF),
    noteCardSelectedBackground = Color(0xFFFF2C36),
    noteCardPinnedBackground = Color(0xFFFFFEFF),
    noteTagBackground = Color(0xFFFF4F58),
    noteTagText = Color(0xFFF9FAFB),
    noteHeaderBackground = Color(0xFF111827),
    noteHeaderContent = Color(0xFFF9FAFB),
    noteBreadcrumbBackground = Color(0xFF1F2937),
    noteBreadcrumbText = Color(0xFF94A3B8),
    noteBreadcrumbDivider = Color(0xFFFF535E),
    noteSectionTitle = Color(0xFFCBD5E1),
    noteTitle = Color(0xFFF9FAFB),
    notePreviewText = Color(0xFFCBD5E1),
    noteActionIcon = Color(0xFF111827),
    noteEmptyStateText = Color(0xFFCBD5E1),
    noteSearchBackground = Color(0xFF374151),
    noteSearchContent = Color(0xFFF9FAFB),
    noteSearchHint = Color(0xFFCBD5E1),
    notePopupBackground = Color(0xFF2D3A4D),
    notePopupContent = Color(0xFFF9FAFB),
    notePopupSecondaryText = Color(0xFFCBD5E1),
    notePopupDivider = Color(0xFFFF4C56),
    noteViewModeBackground = Color(0xFF374151),
    noteViewModeSelectedBackground = Color(0xFF2D3A4D),
    noteViewModeSelectedIcon = Color(0xFF111827),
    noteViewModeUnselectedIcon = Color(0xFFCBD5E1),
    noteSectionChevron = Color(0xFFF9FAFB),
    noteListDivider = Color(0xFF374151),

    // SHARED NAVIGATION
    dockBackground = Color(0xFFF9FAFB),
    dockIcon = Color(0xFF111827),
    dockDivider = Color(0xFF6B7280),
    fabBackground = Color(0xFFF9FAFB),
    fabIcon = Color(0xFF111827),
    mainTabText = Color(0xFFF6F4EA),
    mainTabSelectedIndicator = Color(0xFFDDE8B5),

    // SEMANTIC STATES
    pinnedIndicator = Color(0xFFF9FAFB),
    sensitiveIndicator = Color(0xFFFBBF24),

    // EDITOR
    editorText = Color(0xFFF9FAFB),
    editorMarkdownPreviewBackground = Color(0xFF2B2B2B),
    editorMarkdownPreviewText = Color(0xFFF4F4F4),
    editorMarkdownPreviewMutedText = Color(0xFFCACACA),
    editorMarkdownPreviewSurfaceVariant = Color(0xFF3A3A3A),
    editorMarkdownPreviewOutline = Color(0xFF777777),
    editorMarkdownPreviewOutlineVariant = Color(0xFF555555),
    editorMarkdownPreviewPrimary = Color(0xFFF4F4F4),
    editorMarkdownPreviewCodeBackground = Color(0xFF3A3A3A),
    editorMarkdownPreviewColorScheme = "dark",
    editorToolbarBackground = Color(0xFFFF1C23),
    editorToolbarIcon = Color(0xFF111827),
    editorToolbarDisabledIcon = Color(0xFFFFFD6A),
    editorMarkdownPreviewDivider = Color(0xFFFF4F4F),
    editorMarkdownPreviewShimmerBase = Color(0xFFFF3F3F),
    editorMarkdownPreviewShimmerHighlight = Color(0xFFFF5353),

    // VAULT
    vaultClipboardCardBackground = Color(0xFF2D3A4D),
    vaultClipboardCardSelectedBackground = Color(0xFFFF313C),
    vaultClipboardCardPressedBackground = Color(0xFFFF2F3C),
    vaultClipboardCardDraggingBackground = Color(0xFF374151),
    vaultClipboardPreviewText = Color(0xFFF9FAFB),
    vaultClipboardMetaText = Color(0xFFCBD5E1),
    vaultActionIcon = Color(0xFFCBD5E1),
    vaultCheckboxChecked = Color(0xFF111827),
    vaultCheckboxUnchecked = Color(0xFFCBD5E1),
    vaultDragHandle = Color(0xFFCBD5E1),
    vaultDragHandleActive = Color(0xFFF9FAFB),
    vaultEmptyStateIcon = Color(0xFFFF1118),
    vaultGroupDivider = Color(0xFF374151),
    vaultPopupDivider = Color(0xFFFF323E),

    // SHARE DIALOG
    shareOptionBackground = Color(0xFFFF313D),
    shareOptionSelectedBackground = Color(0xFFFF323D),
    shareOptionSelectedBorder = Color(0xFF111827),
    shareOptionAccent = Color(0xFF111827),
    shareOptionUnselectedAccent = Color(0xFFCBD5E1),
    shareOptionText = Color(0xFFF9FAFB),
    shareOptionHint = Color(0xFFCBD5E1),

    // SETTINGS / COMMON DIALOGS
    settingsGroupTitle = Color(0xFFF9FAFB),
    settingsGroupIcon = Color(0xFF111827),
    settingsPrivacyCardBackground = Color(0xFFFF1E26),
    firstRunCalloutBackground = Color(0xFFFF242D)
)

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

    cardBackgroundAlpha = 0.5f,
    screenOverlayBackground = Color(0xFF16385A),
    screenOverlayBackgroundAlpha = 0.88f,

    // NOTE SCREEN
    noteCardBackground = Color(0xFFFFFFFF),
    noteCardSelectedBackground = Color(0xFFFF1141),
    noteCardPinnedBackground = Color(0xFFFFEBF8),
    noteTagBackground = Color(0xFFFF395F),
    noteTagText = Color(0xFFE3F2FD),
    noteHeaderBackground = Color(0xFF0A1929),
    noteHeaderContent = Color(0xFFE3F2FD),
    noteBreadcrumbBackground = Color(0xFF12304A),
    noteBreadcrumbText = Color(0xFF38BDF8),
    noteBreadcrumbDivider = Color(0xFFFF216B),
    noteSectionTitle = Color(0xFF90CAF9),
    noteTitle = Color(0xFFE3F2FD),
    notePreviewText = Color(0xFF90CAF9),
    noteActionIcon = Color(0xFF0A1929),
    noteEmptyStateText = Color(0xFF90CAF9),
    noteSearchBackground = Color(0xFF1E4976),
    noteSearchContent = Color(0xFFE3F2FD),
    noteSearchHint = Color(0xFF90CAF9),
    notePopupBackground = Color(0xFF16385A),
    notePopupContent = Color(0xFFE3F2FD),
    notePopupSecondaryText = Color(0xFF90CAF9),
    notePopupDivider = Color(0xFFFF3D77),
    noteViewModeBackground = Color(0xFF1E4976),
    noteViewModeSelectedBackground = Color(0xFF16385A),
    noteViewModeSelectedIcon = Color(0xFF0A1929),
    noteViewModeUnselectedIcon = Color(0xFF90CAF9),
    noteSectionChevron = Color(0xFFE3F2FD),
    noteListDivider = Color(0xFF1E4976),

    // SHARED NAVIGATION
    dockBackground = Color(0xFFE3F2FD),
    dockIcon = Color(0xFF0A1929),
    dockDivider = Color(0xFF64B5F6),
    fabBackground = Color(0xFF38BDF8),
    fabIcon = Color(0xFF0A1929),
    mainTabText = Color(0xFFF6F4EA),
    mainTabSelectedIndicator = Color(0xFFDDE8B5),

    // SEMANTIC STATES
    pinnedIndicator = Color(0xFF38BDF8),
    sensitiveIndicator = Color(0xFFFBBF24),

    // EDITOR
    editorText = Color(0xFFE3F2FD),
    editorMarkdownPreviewBackground = Color(0xFF2B2B2B),
    editorMarkdownPreviewText = Color(0xFFF4F4F4),
    editorMarkdownPreviewMutedText = Color(0xFFCACACA),
    editorMarkdownPreviewSurfaceVariant = Color(0xFF3A3A3A),
    editorMarkdownPreviewOutline = Color(0xFF777777),
    editorMarkdownPreviewOutlineVariant = Color(0xFF555555),
    editorMarkdownPreviewPrimary = Color(0xFFF4F4F4),
    editorMarkdownPreviewCodeBackground = Color(0xFF3A3A3A),
    editorMarkdownPreviewColorScheme = "dark",
    editorToolbarBackground = Color(0xFFFF1026),
    editorToolbarIcon = Color(0xFF0A1929),
    editorToolbarDisabledIcon = Color(0xFFFFF55F),
    editorMarkdownPreviewDivider = Color(0xFFFF4F4F),
    editorMarkdownPreviewShimmerBase = Color(0xFFFF3F3F),
    editorMarkdownPreviewShimmerHighlight = Color(0xFFFF5353),

    // VAULT
    vaultClipboardCardBackground = Color(0xFF16385A),
    vaultClipboardCardSelectedBackground = Color(0xFFFF1540),
    vaultClipboardCardPressedBackground = Color(0xFFFF163D),
    vaultClipboardCardDraggingBackground = Color(0xFF14507A),
    vaultClipboardPreviewText = Color(0xFFE3F2FD),
    vaultClipboardMetaText = Color(0xFF90CAF9),
    vaultActionIcon = Color(0xFF90CAF9),
    vaultCheckboxChecked = Color(0xFF0A1929),
    vaultCheckboxUnchecked = Color(0xFF90CAF9),
    vaultDragHandle = Color(0xFF90CAF9),
    vaultDragHandleActive = Color(0xFFE3F2FD),
    vaultEmptyStateIcon = Color(0xFFFF0A19),
    vaultGroupDivider = Color(0xFF1E4976),
    vaultPopupDivider = Color(0xFFFF1A41),

    // SHARE DIALOG
    shareOptionBackground = Color(0xFFFF193F),
    shareOptionSelectedBackground = Color(0xFFFF1543),
    shareOptionSelectedBorder = Color(0xFF0A1929),
    shareOptionAccent = Color(0xFF0A1929),
    shareOptionUnselectedAccent = Color(0xFF90CAF9),
    shareOptionText = Color(0xFFE3F2FD),
    shareOptionHint = Color(0xFF90CAF9),

    // SETTINGS / COMMON DIALOGS
    settingsGroupTitle = Color(0xFFE3F2FD),
    settingsGroupIcon = Color(0xFF0A1929),
    settingsPrivacyCardBackground = Color(0xFFFF0E2C),
    firstRunCalloutBackground = Color(0xFFFF1431)
)

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

    cardBackgroundAlpha = 0.5f,
    screenOverlayBackground = Color(0xFF5A3826),
    screenOverlayBackgroundAlpha = 0.88f,

    // NOTE SCREEN
    noteCardBackground = Color(0xFFFFFFFF),
    noteCardSelectedBackground = Color(0xFFFF6432),
    noteCardPinnedBackground = Color(0xFFFFFDF6),
    noteTagBackground = Color(0xFFFF7D5C),
    noteTagText = Color(0xFFF5E6D3),
    noteHeaderBackground = Color(0xFF2B1A12),
    noteHeaderContent = Color(0xFFF5E6D3),
    noteBreadcrumbBackground = Color(0xFF3A251B),
    noteBreadcrumbText = Color(0xFFE8A87C),
    noteBreadcrumbDivider = Color(0xFFFF8A61),
    noteSectionTitle = Color(0xFFD4A574),
    noteTitle = Color(0xFFF5E6D3),
    notePreviewText = Color(0xFFD4A574),
    noteActionIcon = Color(0xFF2B1A12),
    noteEmptyStateText = Color(0xFFD4A574),
    noteSearchBackground = Color(0xFF6B4630),
    noteSearchContent = Color(0xFFF5E6D3),
    noteSearchHint = Color(0xFFD4A574),
    notePopupBackground = Color(0xFF5A3826),
    notePopupContent = Color(0xFFF5E6D3),
    notePopupSecondaryText = Color(0xFFD4A574),
    notePopupDivider = Color(0xFFFF8561),
    noteViewModeBackground = Color(0xFF6B4630),
    noteViewModeSelectedBackground = Color(0xFF5A3826),
    noteViewModeSelectedIcon = Color(0xFF2B1A12),
    noteViewModeUnselectedIcon = Color(0xFFD4A574),
    noteSectionChevron = Color(0xFFF5E6D3),
    noteListDivider = Color(0xFF6B4630),

    // SHARED NAVIGATION
    dockBackground = Color(0xFFF5E6D3),
    dockIcon = Color(0xFF2B1A12),
    dockDivider = Color(0xFFB08968),
    fabBackground = Color(0xFFE8A87C),
    fabIcon = Color(0xFF2B1A12),
    mainTabText = Color(0xFFF6F4EA),
    mainTabSelectedIndicator = Color(0xFFDDE8B5),

    // SEMANTIC STATES
    pinnedIndicator = Color(0xFFE8A87C),
    sensitiveIndicator = Color(0xFFFBBF24),

    // EDITOR
    editorText = Color(0xFFF5E6D3),
    editorMarkdownPreviewBackground = Color(0xFF2B2B2B),
    editorMarkdownPreviewText = Color(0xFFF4F4F4),
    editorMarkdownPreviewMutedText = Color(0xFFCACACA),
    editorMarkdownPreviewSurfaceVariant = Color(0xFF3A3A3A),
    editorMarkdownPreviewOutline = Color(0xFF777777),
    editorMarkdownPreviewOutlineVariant = Color(0xFF555555),
    editorMarkdownPreviewPrimary = Color(0xFFF4F4F4),
    editorMarkdownPreviewCodeBackground = Color(0xFF3A3A3A),
    editorMarkdownPreviewColorScheme = "dark",
    editorToolbarBackground = Color(0xFFFF3D26),
    editorToolbarIcon = Color(0xFF2B1A12),
    editorToolbarDisabledIcon = Color(0xFFFFFC78),
    editorMarkdownPreviewDivider = Color(0xFFFF4F4F),
    editorMarkdownPreviewShimmerBase = Color(0xFFFF3F3F),
    editorMarkdownPreviewShimmerHighlight = Color(0xFFFF5353),

    // VAULT
    vaultClipboardCardBackground = Color(0xFF5A3826),
    vaultClipboardCardSelectedBackground = Color(0xFFFF6539),
    vaultClipboardCardPressedBackground = Color(0xFFFF6139),
    vaultClipboardCardDraggingBackground = Color(0xFF7A3B1F),
    vaultClipboardPreviewText = Color(0xFFF5E6D3),
    vaultClipboardMetaText = Color(0xFFD4A574),
    vaultActionIcon = Color(0xFFD4A574),
    vaultCheckboxChecked = Color(0xFF2B1A12),
    vaultCheckboxUnchecked = Color(0xFFD4A574),
    vaultDragHandle = Color(0xFFD4A574),
    vaultDragHandleActive = Color(0xFFF5E6D3),
    vaultEmptyStateIcon = Color(0xFFFF2B1A),
    vaultGroupDivider = Color(0xFF6B4630),
    vaultPopupDivider = Color(0xFFFF633F),

    // SHARE DIALOG
    shareOptionBackground = Color(0xFFFF613E),
    shareOptionSelectedBackground = Color(0xFFFF6839),
    shareOptionSelectedBorder = Color(0xFF2B1A12),
    shareOptionAccent = Color(0xFF2B1A12),
    shareOptionUnselectedAccent = Color(0xFFD4A574),
    shareOptionText = Color(0xFFF5E6D3),
    shareOptionHint = Color(0xFFD4A574),

    // SETTINGS / COMMON DIALOGS
    settingsGroupTitle = Color(0xFFF5E6D3),
    settingsGroupIcon = Color(0xFF2B1A12),
    settingsPrivacyCardBackground = Color(0xFFFF4726),
    firstRunCalloutBackground = Color(0xFFFF4B30)
)

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

    cardBackgroundAlpha = 0.5f,
    screenOverlayBackground = Color(0xFF3A2356),
    screenOverlayBackgroundAlpha = 0.88f,

    // NOTE SCREEN
    noteCardBackground = Color(0xFFFFFFFF),
    noteCardSelectedBackground = Color(0xFFFF491C),
    noteCardPinnedBackground = Color(0xFFFFF6F3),
    noteTagBackground = Color(0xFFFF6147),
    noteTagText = Color(0xFFEDE9FE),
    noteHeaderBackground = Color(0xFF1A0F2E),
    noteHeaderContent = Color(0xFFEDE9FE),
    noteBreadcrumbBackground = Color(0xFF2A1945),
    noteBreadcrumbText = Color(0xFFA78BFA),
    noteBreadcrumbDivider = Color(0xFFFF614D),
    noteSectionTitle = Color(0xFFC4B5FD),
    noteTitle = Color(0xFFEDE9FE),
    notePreviewText = Color(0xFFC4B5FD),
    noteActionIcon = Color(0xFF1A0F2E),
    noteEmptyStateText = Color(0xFFC4B5FD),
    noteSearchBackground = Color(0xFF4C2E6E),
    noteSearchContent = Color(0xFFEDE9FE),
    noteSearchHint = Color(0xFFC4B5FD),
    notePopupBackground = Color(0xFF3A2356),
    notePopupContent = Color(0xFFEDE9FE),
    notePopupSecondaryText = Color(0xFFC4B5FD),
    notePopupDivider = Color(0xFFFF8C7D),
    noteViewModeBackground = Color(0xFF4C2E6E),
    noteViewModeSelectedBackground = Color(0xFF3A2356),
    noteViewModeSelectedIcon = Color(0xFF1A0F2E),
    noteViewModeUnselectedIcon = Color(0xFFC4B5FD),
    noteSectionChevron = Color(0xFFEDE9FE),
    noteListDivider = Color(0xFF4C2E6E),

    // SHARED NAVIGATION
    dockBackground = Color(0xFFEDE9FE),
    dockIcon = Color(0xFF1A0F2E),
    dockDivider = Color(0xFFDDD6FE),
    fabBackground = Color(0xFFA78BFA),
    fabIcon = Color(0xFF1A0F2E),
    mainTabText = Color(0xFFF6F4EA),
    mainTabSelectedIndicator = Color(0xFFDDE8B5),

    // SEMANTIC STATES
    pinnedIndicator = Color(0xFFA78BFA),
    sensitiveIndicator = Color(0xFFFBBF24),

    // EDITOR
    editorText = Color(0xFFEDE9FE),
    editorMarkdownPreviewBackground = Color(0xFF2B2B2B),
    editorMarkdownPreviewText = Color(0xFFF4F4F4),
    editorMarkdownPreviewMutedText = Color(0xFFCACACA),
    editorMarkdownPreviewSurfaceVariant = Color(0xFF3A3A3A),
    editorMarkdownPreviewOutline = Color(0xFF777777),
    editorMarkdownPreviewOutlineVariant = Color(0xFF555555),
    editorMarkdownPreviewPrimary = Color(0xFFF4F4F4),
    editorMarkdownPreviewCodeBackground = Color(0xFF3A3A3A),
    editorMarkdownPreviewColorScheme = "dark",
    editorToolbarBackground = Color(0xFFFF2818),
    editorToolbarIcon = Color(0xFF1A0F2E),
    editorToolbarDisabledIcon = Color(0xFFFFF96C),
    editorMarkdownPreviewDivider = Color(0xFFFF4F4F),
    editorMarkdownPreviewShimmerBase = Color(0xFFFF3F3F),
    editorMarkdownPreviewShimmerHighlight = Color(0xFFFF5353),

    // VAULT
    vaultClipboardCardBackground = Color(0xFF3A2356),
    vaultClipboardCardSelectedBackground = Color(0xFFFF4622),
    vaultClipboardCardPressedBackground = Color(0xFFFF4123),
    vaultClipboardCardDraggingBackground = Color(0xFF5B21B6),
    vaultClipboardPreviewText = Color(0xFFEDE9FE),
    vaultClipboardMetaText = Color(0xFFC4B5FD),
    vaultActionIcon = Color(0xFFC4B5FD),
    vaultCheckboxChecked = Color(0xFF1A0F2E),
    vaultCheckboxUnchecked = Color(0xFFC4B5FD),
    vaultDragHandle = Color(0xFFC4B5FD),
    vaultDragHandleActive = Color(0xFFEDE9FE),
    vaultEmptyStateIcon = Color(0xFFFF1A0F),
    vaultGroupDivider = Color(0xFF4C2E6E),
    vaultPopupDivider = Color(0xFFFF4329),

    // SHARE DIALOG
    shareOptionBackground = Color(0xFFFF4127),
    shareOptionSelectedBackground = Color(0xFFFF4922),
    shareOptionSelectedBorder = Color(0xFF1A0F2E),
    shareOptionAccent = Color(0xFF1A0F2E),
    shareOptionUnselectedAccent = Color(0xFFC4B5FD),
    shareOptionText = Color(0xFFEDE9FE),
    shareOptionHint = Color(0xFFC4B5FD),

    // SETTINGS / COMMON DIALOGS
    settingsGroupTitle = Color(0xFFEDE9FE),
    settingsGroupIcon = Color(0xFF1A0F2E),
    settingsPrivacyCardBackground = Color(0xFFFF3115),
    firstRunCalloutBackground = Color(0xFFFF331F)
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
