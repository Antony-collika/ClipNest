package com.clipnest.ui.theme

import com.clipnest.data.local.ThemePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ThemePaletteTest {

    @Test
    fun everyPresetProvidesDistinctLightAndDarkPrimaryColors() {
        val palettes = ThemePreset.entries.map(::themePaletteFor)
        val primaryColorPairs = palettes.map { it.light.primary to it.dark.primary }.toSet()

        assertEquals(ThemePreset.entries.size, primaryColorPairs.size)
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
