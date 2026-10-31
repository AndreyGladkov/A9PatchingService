package com.lmqr.ha9_comp_service.colors

import android.content.SharedPreferences
import com.lmqr.ha9_comp_service.RefreshMode
import com.lmqr.ha9_comp_service.command_runners.Commands
import com.lmqr.ha9_comp_service.refreshModeFor

object ColorKeys {
    const val ENABLED = "colors_enabled"
    const val LINE_THRESHOLD = "colors_line_threshold"
    const val FILL_DARK = "colors_fill_dark"
    const val FILL_LIGHT = "colors_fill_light"
    const val LIGHT_GRAY = "colors_light_gray"
    const val EPD_CONTRAST = "colors_epd_contrast"
    const val EPD_BLACK = "colors_epd_black"
    const val EPD_WHITE = "colors_epd_white"

    val perApp = listOf(ENABLED, LINE_THRESHOLD, EPD_CONTRAST, EPD_BLACK, EPD_WHITE)

    fun forApp(key: String, packageName: String) = "$key:package:$packageName"

    fun affectsRecolor(key: String) =
        (key.startsWith("colors_") && !key.startsWith("colors_epd_")) || key.startsWith("package:") ||
            key == "refresh_setting" || key == "disable_perapprefresh"
}

object ColorDefaults {
    const val ENABLED = true
    const val LINE_THRESHOLD = 70
    const val FILL_DARK = 50
    const val FILL_LIGHT = 90
    const val LIGHT_GRAY = 80

    // Values the A9 kernel driver reports after boot.
    const val EPD_CONTRAST = 0
    const val EPD_BLACK = 30
    const val EPD_WHITE = 20
}

data class PanelLevels(val contrast: Int, val black: Int, val white: Int) {
    fun commands() = arrayOf(
        Commands.CONTRAST(contrast),
        Commands.BLACK_THRESHOLD(black),
        Commands.WHITE_THRESHOLD(white),
    )
}

fun SharedPreferences.appInt(key: String, packageName: String, default: Int) =
    getInt(ColorKeys.forApp(key, packageName), getInt(key, default))

fun SharedPreferences.appBoolean(key: String, packageName: String, default: Boolean) =
    getBoolean(ColorKeys.forApp(key, packageName), getBoolean(key, default))

fun SharedPreferences.panelLevelsFor(packageName: String) = PanelLevels(
    contrast = appInt(ColorKeys.EPD_CONTRAST, packageName, ColorDefaults.EPD_CONTRAST),
    black = appInt(ColorKeys.EPD_BLACK, packageName, ColorDefaults.EPD_BLACK),
    white = appInt(ColorKeys.EPD_WHITE, packageName, ColorDefaults.EPD_WHITE),
)

fun SharedPreferences.recolorConfigFor(packageName: String): RecolorConfig? {
    if (!appBoolean(ColorKeys.ENABLED, packageName, ColorDefaults.ENABLED))
        return null

    val dark = percentToLuma(getInt(ColorKeys.FILL_DARK, ColorDefaults.FILL_DARK))
    val light = percentToLuma(getInt(ColorKeys.FILL_LIGHT, ColorDefaults.FILL_LIGHT))
        .coerceAtLeast(dark)
    return RecolorConfig(
        lineThreshold = percentToLuma(
            appInt(ColorKeys.LINE_THRESHOLD, packageName, ColorDefaults.LINE_THRESHOLD)
        ),
        fillLevels = fillLevelsFor(refreshModeFor(packageName)),
        fillDarkCutoff = dark,
        fillLightCutoff = light,
        lightGray = percentToLuma(getInt(ColorKeys.LIGHT_GRAY, ColorDefaults.LIGHT_GRAY))
            .coerceIn(dark, light),
    )
}

// Every mode except Clear dithers the frame to 1 bit, so an intermediate grey turns into
// a dot pattern on the glass.
fun fillLevelsFor(mode: RefreshMode) =
    if (mode == RefreshMode.CLEAR) FillLevels.THREE else FillLevels.TWO

fun percentToLuma(percent: Int) = percent.coerceIn(0, 100) * 255 / 100
