package com.lmqr.ha9_comp_service.offline

import android.content.SharedPreferences
import com.lmqr.ha9_comp_service.command_runners.CommandRunner
import com.lmqr.ha9_comp_service.command_runners.Commands

interface NetworkSwitches {
    val isWifiOn: Boolean
    val isDataOn: Boolean
}

/**
 * Turns Wi-Fi and mobile data off once and remembers how they were. It does not hold them off:
 * turning either back on by hand ends the mode, and leaving it brings back only what was on.
 */
class OfflineMode(
    private val prefs: SharedPreferences,
    private val network: NetworkSwitches,
    private val daemon: CommandRunner,
) {
    val isOn: Boolean get() = prefs.getBoolean(KEY_ON, false)

    fun toggle() = if (isOn) leave() else enter()

    fun onNetworkTurnedOn() {
        if (isOn) prefs.edit().putBoolean(KEY_ON, false).apply()
    }

    private fun enter() {
        val wifi = network.isWifiOn
        val data = network.isDataOn
        prefs.edit()
            .putBoolean(KEY_ON, true)
            .putBoolean(KEY_WIFI_BEFORE, wifi)
            .putBoolean(KEY_DATA_BEFORE, data)
            .apply()
        daemon.runCommands(switches(wifi, data, on = false))
    }

    private fun leave() {
        prefs.edit().putBoolean(KEY_ON, false).apply()
        daemon.runCommands(
            switches(prefs.getBoolean(KEY_WIFI_BEFORE, false), prefs.getBoolean(KEY_DATA_BEFORE, false), on = true)
        )
    }

    private fun switches(wifi: Boolean, data: Boolean, on: Boolean) = listOfNotNull(
        Commands.WIFI(on).takeIf { wifi },
        Commands.DATA(on).takeIf { data },
    ).toTypedArray()

    companion object {
        const val KEY_ON = "offline_mode"
        const val KEY_SUPPORTED = "offline_supported"
        private const val KEY_WIFI_BEFORE = "offline_wifi_before"
        private const val KEY_DATA_BEFORE = "offline_data_before"
        private const val NETWORK_DAEMON_VERSION = 1

        fun isSupportedBy(daemonVersion: String?): Boolean =
            (daemonVersion?.trim()?.toIntOrNull() ?: 0) >= NETWORK_DAEMON_VERSION
    }
}
