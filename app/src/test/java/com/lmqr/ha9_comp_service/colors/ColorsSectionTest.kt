package com.lmqr.ha9_comp_service.colors

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorsSectionTest {
    @Test
    fun collapsedByDefaultWhenTheToggleIsReachable() {
        assertFalse(isColorsSectionShown(expanded = false, staticAodRowShown = true))
    }

    @Test
    fun followsTheToggle() {
        assertTrue(isColorsSectionShown(expanded = true, staticAodRowShown = true))
    }

    @Test
    fun staysOpenWhenTheStaticAodRowAndItsToggleAreHidden() {
        assertTrue(isColorsSectionShown(expanded = false, staticAodRowShown = false))
    }
}
