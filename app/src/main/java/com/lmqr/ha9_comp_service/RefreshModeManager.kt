package com.lmqr.ha9_comp_service

import android.content.SharedPreferences
import com.lmqr.ha9_comp_service.command_runners.CommandRunner
import com.lmqr.ha9_comp_service.command_runners.Commands

enum class RefreshMode(val command: String, val mode: Int) {
    CLEAR(Commands.SPEED_CLEAR, 0),
    BALANCED(Commands.SPEED_BALANCED, 1),
    SMOOTH(Commands.SPEED_SMOOTH, 2),
    SPEED(Commands.SPEED_FAST, 3);

    companion object {
        fun fromInt(value: Int) = entries.firstOrNull { it.mode == value } ?: SMOOTH
    }
}

class RefreshModeManager(
    private val sharedPreferences: SharedPreferences,
    private val commandRunner: CommandRunner,
) {
    var currentMode = RefreshMode.SMOOTH
        private set(v) {
            field = v
            if (temporaryMode == null) applyMode()
        }

    /** Set by another app (e.g. a keyboard while it is shown) without becoming the app's saved mode. */
    private var temporaryMode: RefreshMode? = null
    init {
        currentMode = RefreshMode.fromInt(defaultRefreshMode())
    }
    private var currentClassifier = ""

    private fun defaultRefreshMode() = sharedPreferences.defaultRefreshMode()
    fun onAppChange(packageName: String) = packageName.run {
        if (this != currentClassifier) {
            currentClassifier = this
            val tempMode = sharedPreferences.refreshModeFor(this)
            if (tempMode != currentMode) {
                currentMode = tempMode
            }
        }
    }

    fun overrideTemporarily(refreshMode: RefreshMode) {
        temporaryMode = refreshMode
        applyMode()
    }

    fun clearTemporaryMode() {
        if (temporaryMode == null) return
        temporaryMode = null
        applyMode()
    }

    fun changeMode(refreshMode: RefreshMode) {
        val hadTemporaryMode = temporaryMode != null
        temporaryMode = null
        if (refreshMode == currentMode) {
            if (hadTemporaryMode) applyMode()
        } else {
            currentMode = refreshMode
            sharedPreferences.edit()
                .putInt(
                    currentClassifier.toSharedPreferencesKey(
                        !sharedPreferences.getBoolean(
                            "disable_perapprefresh",
                            false
                        )
                    ), refreshMode.mode
                ).apply()
        }
    }

    fun applyMode() {
        commandRunner.runCommands(
            arrayOf((temporaryMode ?: currentMode).command)
        )
    }
}

fun SharedPreferences.refreshModeFor(packageName: String) = RefreshMode.fromInt(
    getInt(
        packageName.toSharedPreferencesKey(!getBoolean("disable_perapprefresh", false)),
        defaultRefreshMode()
    )
)

private fun SharedPreferences.defaultRefreshMode() =
    getString("refresh_setting", "2")?.let(Integer::parseInt) ?: RefreshMode.SMOOTH.mode

private fun String.toSharedPreferencesKey(isPerAppEnabled: Boolean): String {
    if (isPerAppEnabled) {
        if (startsWith("package:"))
            return this
        return "package:$this"
    }
    return "package:None"
}