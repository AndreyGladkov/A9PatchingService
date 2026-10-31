package com.lmqr.ha9_comp_service.colors

import com.lmqr.ha9_comp_service.command_runners.CommandRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppColorsManagerTest {
    private val reader = "org.koreader.launcher"
    private val browser = "org.mozilla.firefox"

    private class RecordingRunner : CommandRunner {
        val sent = mutableListOf<String>()
        override fun runCommands(cmds: Array<String>) {
            sent += cmds
        }
        override fun onDestroy() = Unit
    }

    private val runner = RecordingRunner()
    private val prefs = FakeSharedPreferences(
        ColorKeys.forApp(ColorKeys.EPD_CONTRAST, reader) to 40,
    )
    private val manager = AppColorsManager(prefs, runner)

    @Test
    fun `nothing is sent to the panel before the first app is known`() {
        assertEquals(emptyList<String>(), runner.sent)
    }

    @Test
    fun `switching apps sends levels only when they differ`() {
        manager.onAppChange(browser)
        assertEquals(listOf("sco0", "stb30", "stw20"), runner.sent)

        runner.sent.clear()
        manager.onAppChange("org.other.app")
        assertEquals(emptyList<String>(), runner.sent)

        manager.onAppChange(reader)
        assertEquals(listOf("sco40", "stb30", "stw20"), runner.sent)
    }

    @Test
    fun `saving one panel value keeps the others following global values`() {
        manager.onAppChange(browser)
        manager.saveAppValue(ColorKeys.EPD_CONTRAST, 10)
        prefs.edit().putInt(ColorKeys.EPD_BLACK, 50).apply()

        assertEquals(PanelLevels(10, 50, 20), manager.panelLevels)
        assertFalse(prefs.contains(ColorKeys.forApp(ColorKeys.EPD_BLACK, browser)))
    }

    @Test
    fun `saved panel value is sent to the panel`() {
        manager.onAppChange(browser)
        runner.sent.clear()

        manager.saveAppValue(ColorKeys.EPD_WHITE, 60)

        assertEquals(listOf("sco0", "stb30", "stw60"), runner.sent)
    }

    @Test
    fun `preview applies without saving`() {
        manager.onAppChange(browser)
        runner.sent.clear()

        manager.previewPanelLevels(PanelLevels(5, 6, 7))

        assertEquals(listOf("sco5", "stb6", "stw7"), runner.sent)
        assertEquals(PanelLevels(0, 30, 20), manager.panelLevels)
    }

    @Test
    fun `toggle recolor flips only the current app`() {
        manager.onAppChange(browser)

        assertFalse(manager.toggleRecolor())
        assertFalse(manager.isRecolorEnabled)
        assertTrue(prefs.recolorConfigFor(reader) != null)
    }

    @Test
    fun `reset drops app overrides and restores global levels`() {
        manager.onAppChange(reader)
        manager.saveAppValue(ColorKeys.LINE_THRESHOLD, 20)
        manager.toggleRecolor()
        runner.sent.clear()

        manager.resetApp()

        assertTrue(manager.isRecolorEnabled)
        assertEquals(ColorDefaults.LINE_THRESHOLD, manager.lineThreshold)
        assertEquals(listOf("sco0", "stb30", "stw20"), runner.sent)
    }
}
