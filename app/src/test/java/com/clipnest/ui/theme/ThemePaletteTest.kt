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
    fun forestIsTheDefaultReferencePaletteAndOtherPresetsDiffer() {
        assertEquals(ForestThemePalette, themePaletteFor(ThemePreset.FOREST))

        ThemePreset.entries
            .filterNot { it == ThemePreset.FOREST }
            .forEach { preset ->
                assertNotEquals(ForestThemePalette, themePaletteFor(preset))
            }
    }
}
