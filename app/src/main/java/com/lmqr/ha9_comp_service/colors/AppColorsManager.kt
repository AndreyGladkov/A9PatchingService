package com.lmqr.ha9_comp_service.colors

import android.content.SharedPreferences
import com.lmqr.ha9_comp_service.command_runners.CommandRunner

class AppColorsManager(
    private val sharedPreferences: SharedPreferences,
    private val commandRunner: CommandRunner,
) {
    private var currentPackage = ""
    private var appliedLevels: PanelLevels? = null

    val isRecolorEnabled
        get() = sharedPreferences.appBoolean(ColorKeys.ENABLED, currentPackage, ColorDefaults.ENABLED)

    val lineThreshold
        get() = sharedPreferences.appInt(
            ColorKeys.LINE_THRESHOLD, currentPackage, ColorDefaults.LINE_THRESHOLD
        )

    val panelLevels
        get() = sharedPreferences.panelLevelsFor(currentPackage)

    fun onAppChange(packageName: String) {
        if (packageName != currentPackage) {
            currentPackage = packageName
            applyPanelLevels()
        }
    }

    fun toggleRecolor(): Boolean {
        val enabled = !isRecolorEnabled
        sharedPreferences.edit()
            .putBoolean(ColorKeys.forApp(ColorKeys.ENABLED, currentPackage), enabled)
            .apply()
        return enabled
    }

    fun saveAppValue(key: String, value: Int) {
        sharedPreferences.edit().putInt(ColorKeys.forApp(key, currentPackage), value).apply()
        applyPanelLevels()
    }

    fun previewPanelLevels(levels: PanelLevels) = applyPanelLevels(levels)

    fun resetApp() {
        val editor = sharedPreferences.edit()
        ColorKeys.perApp.forEach { editor.remove(ColorKeys.forApp(it, currentPackage)) }
        editor.apply()
        applyPanelLevels()
    }

    fun applyPanelLevels(levels: PanelLevels = panelLevels) {
        if (levels != appliedLevels) {
            commandRunner.runCommands(levels.commands())
            appliedLevels = levels
        }
    }
}
