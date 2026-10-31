package com.lmqr.ha9_comp_service

import com.lmqr.ha9_comp_service.colors.FakeSharedPreferences
import com.lmqr.ha9_comp_service.command_runners.CommandRunner
import com.lmqr.ha9_comp_service.command_runners.Commands
import org.junit.Assert.assertEquals
import org.junit.Test

class RefreshModeManagerTest {
    private val reader = "org.koreader.launcher"

    private class RecordingRunner : CommandRunner {
        val sent = mutableListOf<String>()
        override fun runCommands(cmds: Array<String>) {
            sent += cmds
        }
        override fun onDestroy() = Unit
    }

    private val runner = RecordingRunner()
    private val prefs = FakeSharedPreferences("package:$reader" to RefreshMode.CLEAR.mode)
    private val manager = RefreshModeManager(prefs, runner).apply { onAppChange(reader) }

    @Test
    fun `temporary mode is applied without being saved for the app`() {
        runner.sent.clear()
        manager.overrideTemporarily(RefreshMode.SPEED)
        assertEquals(listOf(Commands.SPEED_FAST), runner.sent)
        assertEquals(RefreshMode.CLEAR, manager.currentMode)
        assertEquals(RefreshMode.CLEAR, prefs.refreshModeFor(reader))
    }

    @Test
    fun `clearing the temporary mode restores the app mode`() {
        manager.overrideTemporarily(RefreshMode.SPEED)
        runner.sent.clear()
        manager.clearTemporaryMode()
        assertEquals(listOf(Commands.SPEED_CLEAR), runner.sent)
    }

    @Test
    fun `clearing without a temporary mode sends nothing`() {
        runner.sent.clear()
        manager.clearTemporaryMode()
        assertEquals(emptyList<String>(), runner.sent)
    }

    @Test
    fun `switching apps keeps the temporary mode on the panel`() {
        manager.overrideTemporarily(RefreshMode.SPEED)
        runner.sent.clear()
        manager.onAppChange("org.mozilla.firefox")
        assertEquals(emptyList<String>(), runner.sent)
        manager.clearTemporaryMode()
        assertEquals(listOf(Commands.SPEED_SMOOTH), runner.sent)
    }

    @Test
    fun `choosing a mode on the panel ends the temporary mode`() {
        manager.overrideTemporarily(RefreshMode.SPEED)
        runner.sent.clear()
        manager.changeMode(RefreshMode.BALANCED)
        assertEquals(listOf(Commands.SPEED_BALANCED), runner.sent)
        runner.sent.clear()
        manager.clearTemporaryMode()
        assertEquals(emptyList<String>(), runner.sent)
    }

    @Test
    fun `choosing the saved mode on the panel also ends the temporary mode`() {
        manager.overrideTemporarily(RefreshMode.SPEED)
        runner.sent.clear()
        manager.changeMode(RefreshMode.CLEAR)
        assertEquals(listOf(Commands.SPEED_CLEAR), runner.sent)
    }
}
