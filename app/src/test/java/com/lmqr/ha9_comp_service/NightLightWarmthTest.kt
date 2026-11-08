package com.lmqr.ha9_comp_service

import org.junit.Assert.assertEquals
import org.junit.Test

class NightLightWarmthTest {
    private val range = ColorTemperatureRange(coldest = 4082, warmest = 2596)

    @Test
    fun coldestTemperatureIsNoWarmth() {
        assertEquals(0, range.warmthOf(4082))
    }

    @Test
    fun warmestTemperatureIsFullWarmth() {
        assertEquals(range.warmthSteps, range.warmthOf(2596))
    }

    @Test
    fun warmthRoundTripsToTemperature() {
        assertEquals(2850, range.temperatureOf(range.warmthOf(2850)))
    }

    @Test
    fun temperaturesOutsideTheRangeClampToItsEnds() {
        assertEquals(0, range.warmthOf(6500))
        assertEquals(range.warmthSteps, range.warmthOf(1000))
    }

    @Test
    fun warmthOutsideTheSliderClampsToTheRange() {
        assertEquals(4082, range.temperatureOf(-5))
        assertEquals(2596, range.temperatureOf(range.warmthSteps + 5))
    }
}
