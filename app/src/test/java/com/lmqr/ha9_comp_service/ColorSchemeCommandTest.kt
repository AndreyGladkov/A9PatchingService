package com.lmqr.ha9_comp_service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColorSchemeCommandTest {
    @Test
    fun systemThemeSendsNoCommand() {
        assertNull(ColorSchemeCommand.of(ColorSchemeCommand.SYSTEM_THEME, "333333"))
    }

    @Test
    fun missingPreferenceSendsNoCommand() {
        assertNull(ColorSchemeCommand.of(null, "333333"))
    }

    @Test
    fun chosenStyleSendsThemeCommand() {
        assertEquals("theme 5 333333", ColorSchemeCommand.of("5", "333333"))
    }
}
