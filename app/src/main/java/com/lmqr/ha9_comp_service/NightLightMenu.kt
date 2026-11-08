package com.lmqr.ha9_comp_service

import SystemSettingsManager
import SystemSettingsManager.NightLightMode
import android.content.Context
import android.view.View
import android.widget.SeekBar
import com.lmqr.ha9_comp_service.databinding.FloatingMenuLayoutBinding

fun FloatingMenuLayoutBinding.bindNightLight(context: Context) {
    warmthSeekbar.max = FRONT_LIGHT_TEMPERATURES.warmthSteps
    warmthSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            if (fromUser)
                SystemSettingsManager.setNightLightTemperature(
                    context,
                    FRONT_LIGHT_TEMPERATURES.temperatureOf(progress),
                )
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) {
        }

        override fun onStopTrackingTouch(seekBar: SeekBar) {
        }
    })
    buttonNight.setOnClickListener {
        showNightLightMode(SystemSettingsManager.setNextNightLightMode(context))
    }
    showNightLight(context)
}

fun FloatingMenuLayoutBinding.showNightLight(context: Context) {
    warmthSeekbar.progress =
        FRONT_LIGHT_TEMPERATURES.warmthOf(SystemSettingsManager.getNightLightTemperature(context))
    showNightLightMode(SystemSettingsManager.getNightLightMode(context))
}

// The services.jar patch sends all light to the white LEDs while Night Light is off,
// so warmth has no visible effect then.
private fun FloatingMenuLayoutBinding.showNightLightMode(mode: NightLightMode) {
    buttonNight.text = when (mode) {
        NightLightMode.Manual -> "Manual"
        NightLightMode.Auto -> "Auto"
        NightLightMode.Disabled -> "OFF"
    }
    nightLightContainer.visibility = if (mode == NightLightMode.Disabled) View.GONE else View.VISIBLE
}
