package com.lmqr.ha9_comp_service.colors

import com.lmqr.ha9_comp_service.RefreshMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorSettingsTest {
    private val app = "org.telegram.messenger"

    @Test
    fun `defaults apply when nothing is stored`() {
        val config = FakeSharedPreferences().recolorConfigFor(app)!!

        assertEquals(178, config.lineThreshold)
        assertEquals(127, config.fillDarkCutoff)
        assertEquals(229, config.fillLightCutoff)
        assertEquals(204, config.lightGray)
        assertEquals(FillLevels.TWO, config.fillLevels)
    }

    @Test
    fun `app override wins over global value`() {
        val prefs = FakeSharedPreferences(
            ColorKeys.LINE_THRESHOLD to 50,
            ColorKeys.forApp(ColorKeys.LINE_THRESHOLD, app) to 90,
        )

        assertEquals(229, prefs.recolorConfigFor(app)!!.lineThreshold)
        assertEquals(127, prefs.recolorConfigFor("other.app")!!.lineThreshold)
    }

    @Test
    fun `recolor can be disabled per app`() {
        val prefs = FakeSharedPreferences(ColorKeys.forApp(ColorKeys.ENABLED, app) to false)

        assertNull(prefs.recolorConfigFor(app))
        assertEquals(178, prefs.recolorConfigFor("other.app")!!.lineThreshold)
    }

    @Test
    fun `recolor can be disabled globally and enabled per app`() {
        val prefs = FakeSharedPreferences(
            ColorKeys.ENABLED to false,
            ColorKeys.forApp(ColorKeys.ENABLED, app) to true,
        )

        assertEquals(178, prefs.recolorConfigFor(app)!!.lineThreshold)
        assertNull(prefs.recolorConfigFor("other.app"))
    }

    @Test
    fun `clear refresh mode gives three fill levels`() {
        val prefs = FakeSharedPreferences("package:$app" to RefreshMode.CLEAR.mode)

        assertEquals(FillLevels.THREE, prefs.recolorConfigFor(app)!!.fillLevels)
    }

    @Test
    fun `default refresh mode decides fill levels for apps without their own`() {
        val prefs = FakeSharedPreferences("refresh_setting" to "0")

        assertEquals(FillLevels.THREE, prefs.recolorConfigFor(app)!!.fillLevels)
    }

    @Test
    fun `disabled per app refresh ignores app refresh mode`() {
        val prefs = FakeSharedPreferences(
            "disable_perapprefresh" to true,
            "package:$app" to RefreshMode.CLEAR.mode,
        )

        assertEquals(FillLevels.TWO, prefs.recolorConfigFor(app)!!.fillLevels)
    }

    @Test
    fun `light grey is kept between fill cutoffs`() {
        val prefs = FakeSharedPreferences(
            ColorKeys.FILL_DARK to 40,
            ColorKeys.FILL_LIGHT to 60,
            ColorKeys.LIGHT_GRAY to 95,
        )

        assertEquals(153, prefs.recolorConfigFor(app)!!.lightGray)
    }

    @Test
    fun `light cutoff never drops below dark cutoff`() {
        val prefs = FakeSharedPreferences(ColorKeys.FILL_DARK to 80, ColorKeys.FILL_LIGHT to 20)
        val config = prefs.recolorConfigFor(app)!!

        assertEquals(config.fillDarkCutoff, config.fillLightCutoff)
    }

    @Test
    fun `panel levels resolve per app`() {
        val prefs = FakeSharedPreferences(
            ColorKeys.EPD_CONTRAST to 10,
            ColorKeys.forApp(ColorKeys.EPD_BLACK, app) to 55,
        )

        assertEquals(PanelLevels(10, 55, 20), prefs.panelLevelsFor(app))
        assertEquals(PanelLevels(10, 30, 20), prefs.panelLevelsFor("other.app"))
    }

    @Test
    fun `recolor follows its own keys and refresh mode keys`() {
        listOf(
            ColorKeys.forApp(ColorKeys.ENABLED, app),
            ColorKeys.forApp(ColorKeys.LINE_THRESHOLD, app),
            ColorKeys.FILL_LIGHT,
            "package:$app",
            "refresh_setting",
            "disable_perapprefresh",
        ).forEach { assertTrue(it, ColorKeys.affectsRecolor(it)) }
    }

    @Test
    fun `recolor ignores panel and unrelated keys`() {
        listOf(
            ColorKeys.EPD_CONTRAST,
            ColorKeys.forApp(ColorKeys.EPD_BLACK, app),
            "static-aod:package:$app",
            "isReader:static-aod:package:$app",
            "override_max_brightness",
        ).forEach { assertFalse(it, ColorKeys.affectsRecolor(it)) }
    }

    @Test
    fun `panel commands use daemon protocol`() {
        assertEquals(listOf("sco10", "stb55", "stw20"), PanelLevels(10, 55, 20).commands().toList())
    }
}
