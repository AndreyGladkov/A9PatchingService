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

    private class Network(override var isWifiOn: Boolean, override var isDataOn: Boolean) : NetworkSwitches

    private val runner = RecordingRunner()
    private val prefs = FakeSharedPreferences()

    private fun mode(wifi: Boolean, data: Boolean) = OfflineMode(prefs, Network(wifi, data), runner)

    @Test
    fun `entering turns off what is on`() {
        val mode = mode(wifi = true, data = true)
        mode.toggle()
        assertTrue(mode.isOn)
        assertEquals(listOf("wif0", "dat0"), runner.sent)
    }

    @Test
    fun `entering leaves alone what is already off`() {
        mode(wifi = true, data = false).toggle()
        assertEquals(listOf("wif0"), runner.sent)
    }

    @Test
    fun `leaving brings back only what was on`() {
        val network = Network(isWifiOn = false, isDataOn = true)
        val mode = OfflineMode(prefs, network, runner)
        mode.toggle()
        network.isDataOn = false
        runner.sent.clear()
        mode.toggle()
        assertFalse(mode.isOn)
        assertEquals(listOf("dat1"), runner.sent)
    }

    @Test
    fun `network turned on by hand ends the mode without touching anything`() {
        val mode = mode(wifi = true, data = true)
        mode.toggle()
        runner.sent.clear()
        mode.onNetworkTurnedOn()
        assertFalse(mode.isOn)
        assertEquals(emptyList<String>(), runner.sent)
    }

    @Test
    fun `network turned on outside the mode changes nothing`() {
        val mode = mode(wifi = true, data = false)
        mode.onNetworkTurnedOn()
        assertFalse(mode.isOn)
        mode.toggle()
        assertEquals(listOf("wif0"), runner.sent)
    }

    @Test
    fun `mode survives a restart of the service`() {
        mode(wifi = true, data = false).toggle()
        val restarted = mode(wifi = false, data = false)
        assertTrue(restarted.isOn)
        runner.sent.clear()
        restarted.toggle()
        assertEquals(listOf("wif1"), runner.sent)
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
