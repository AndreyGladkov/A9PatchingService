package com.lmqr.ha9_comp_service.offline

import android.content.SharedPreferences
import com.lmqr.ha9_comp_service.command_runners.CommandRunner
import com.lmqr.ha9_comp_service.command_runners.Commands

/**
 * Turns Wi-Fi and mobile data off, and leaving turns both on. It does not hold them off: turning
 * either back on by hand ends the mode.
 */
class OfflineMode(
    private val prefs: SharedPreferences,
    private val daemon: CommandRunner,
) {
    val isOn: Boolean get() = prefs.getBoolean(KEY_ON, false)

    fun toggle() = switchNetwork(on = isOn)

    fun onNetworkTurnedOn() {
        if (isOn) prefs.edit().putBoolean(KEY_ON, false).apply()
    }

    private fun switchNetwork(on: Boolean) {
        prefs.edit().putBoolean(KEY_ON, !on).apply()
        daemon.runCommands(arrayOf(Commands.WIFI(on), Commands.DATA(on)))
    }

    companion object {
        const val KEY_ON = "offline_mode"
        const val KEY_SUPPORTED = "offline_supported"
        private const val NETWORK_DAEMON_VERSION = 1

        fun isSupportedBy(daemonVersion: String?): Boolean =
            (daemonVersion?.trim()?.toIntOrNull() ?: 0) >= NETWORK_DAEMON_VERSION
    }
}
