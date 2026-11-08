package com.lmqr.ha9_comp_service

data class ColorTemperatureRange(val coldest: Int, val warmest: Int) {
    val warmthSteps get() = coldest - warmest

    fun warmthOf(temperature: Int) = coldest - temperature.coerceIn(warmest, coldest)

    fun temperatureOf(warmth: Int) = coldest - warmth.coerceIn(0, warmthSteps)
}

// patch_system_img.py splits the front light between the white and yellow LEDs linearly over
// 2596..4082 K (the AOSP Night Light bounds); outside it one channel would go negative.
val FRONT_LIGHT_TEMPERATURES = ColorTemperatureRange(coldest = 4082, warmest = 2596)
