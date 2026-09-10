package com.clipnest.ui.theme

import com.clipnest.data.local.ThemePreset
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
    fun lightIsTheDefaultReferencePaletteAndOtherPresetsDiffer() {
        assertEquals(LightThemePalette, themePaletteFor(ThemePreset.LIGHT))
        assertNotEquals(LightThemePalette, themePaletteFor(ThemePreset.DARK))
        assertNotEquals(LightThemePalette, themePaletteFor(ThemePreset.MIDNIGHT_BLUE))
        assertNotEquals(LightThemePalette, themePaletteFor(ThemePreset.FOREST))
        assertNotEquals(LightThemePalette, themePaletteFor(ThemePreset.LAVENDER))
    }
}
