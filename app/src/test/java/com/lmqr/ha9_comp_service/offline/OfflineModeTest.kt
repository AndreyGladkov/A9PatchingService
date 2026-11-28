package com.lmqr.ha9_comp_service.offline

import com.lmqr.ha9_comp_service.colors.FakeSharedPreferences
import com.lmqr.ha9_comp_service.command_runners.CommandRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineModeTest {
    private class RecordingRunner : CommandRunner {
        val sent = mutableListOf<String>()
        override fun runCommands(cmds: Array<String>) {
            sent += cmds
        }
        override fun onDestroy() = Unit
    }

    private val runner = RecordingRunner()
    private val prefs = FakeSharedPreferences()
    private val mode = OfflineMode(prefs, runner)

    @Test
    fun `entering turns off Wi-Fi and mobile data`() {
        mode.toggle()
        assertTrue(mode.isOn)
        assertEquals(listOf("wif0", "dat0"), runner.sent)
    }

    @Test
    fun `leaving turns on Wi-Fi and mobile data`() {
        mode.toggle()
        runner.sent.clear()
        mode.toggle()
        assertFalse(mode.isOn)
        assertEquals(listOf("wif1", "dat1"), runner.sent)
    }

    @Test
    fun `network turned on by hand ends the mode without touching anything`() {
        mode.toggle()
        runner.sent.clear()
        mode.onNetworkTurnedOn()
        assertFalse(mode.isOn)
        assertEquals(emptyList<String>(), runner.sent)
    }

    @Test
    fun `after the mode ended by hand the next press turns everything off again`() {
        mode.toggle()
        mode.onNetworkTurnedOn()
        runner.sent.clear()
        mode.toggle()
        assertTrue(mode.isOn)
        assertEquals(listOf("wif0", "dat0"), runner.sent)
    }

    @Test
    fun `network turned on outside the mode changes nothing`() {
        mode.onNetworkTurnedOn()
        assertFalse(mode.isOn)
        assertEquals(emptyList<String>(), runner.sent)
    }

    @Test
    fun `mode survives a restart of the service`() {
        mode.toggle()
        val restarted = OfflineMode(prefs, runner)
        assertTrue(restarted.isOn)
        runner.sent.clear()
        restarted.toggle()
        assertEquals(listOf("wif1", "dat1"), runner.sent)
    }

    @Test
    fun `only a daemon answering version 1 or later can switch the network`() {
        assertFalse(OfflineMode.isSupportedBy(null))
        assertFalse(OfflineMode.isSupportedBy("0\n"))
        assertFalse(OfflineMode.isSupportedBy("x"))
        assertTrue(OfflineMode.isSupportedBy("1\n"))
        assertTrue(OfflineMode.isSupportedBy("2"))
    }
}
