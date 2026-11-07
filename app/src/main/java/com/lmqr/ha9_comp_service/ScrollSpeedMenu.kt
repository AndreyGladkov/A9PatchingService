package com.lmqr.ha9_comp_service

import android.content.SharedPreferences
import android.graphics.Typeface
import com.lmqr.ha9_comp_service.databinding.FloatingMenuLayoutBinding

const val SCROLL_SPEED_BOOST = "scroll_speed_boost"

fun FloatingMenuLayoutBinding.bindScrollSpeed(prefs: SharedPreferences) {
    scrollSpeedToggle.setOnClickListener {
        prefs.edit().putBoolean(SCROLL_SPEED_BOOST, !prefs.getBoolean(SCROLL_SPEED_BOOST, false)).apply()
        showScrollSpeed(prefs)
    }
    showScrollSpeed(prefs)
}

fun FloatingMenuLayoutBinding.showScrollSpeed(prefs: SharedPreferences) {
    val enabled = prefs.getBoolean(SCROLL_SPEED_BOOST, false)
    scrollSpeedToggle.setText(if (enabled) R.string.scroll_speed_on else R.string.scroll_speed_off)
    scrollSpeedToggle.setTypeface(null, if (enabled) Typeface.BOLD else Typeface.NORMAL)
}
