package com.example.ui.theme

import com.example.data.local.ThemePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ThemePaletteTest {

    @Test
    fun everyPresetProvidesDistinctLightPrimaryColor() {
        val palettes = ThemePreset.entries.map(::themePaletteFor)
        val lightPrimaries = palettes.map { it.light.primary }.toSet()

        assertEquals(ThemePreset.entries.size, lightPrimaries.size)
    }

    @Test
    fun emeraldRemainsTheDefaultPaletteAndOtherPresetsDiffer() {
        assertEquals(EmeraldThemePalette, themePaletteFor(ThemePreset.EMERALD))
        assertNotEquals(EmeraldThemePalette, themePaletteFor(ThemePreset.OCEAN))
        assertNotEquals(EmeraldThemePalette, themePaletteFor(ThemePreset.VIOLET))
        assertNotEquals(EmeraldThemePalette, themePaletteFor(ThemePreset.SUNSET))
        assertNotEquals(EmeraldThemePalette, themePaletteFor(ThemePreset.GRAPHITE))
    }
}
