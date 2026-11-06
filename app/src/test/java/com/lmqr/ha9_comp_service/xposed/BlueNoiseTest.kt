package com.lmqr.ha9_comp_service.xposed

import org.junit.Assert.assertEquals
import org.junit.Test

class BlueNoiseTest {
    @Test
    fun `texture covers the whole tile`() {
        assertEquals(BlueNoise.SIZE * BlueNoise.SIZE, BlueNoise.thresholds.size)
    }

    @Test
    fun `every threshold occurs equally often`() {
        val counts = BlueNoise.thresholds.asList().groupingBy { it.toInt() and 0xFF }.eachCount()

        assertEquals((0..255).toSet(), counts.keys)
        assertEquals(setOf(BlueNoise.thresholds.size / 256), counts.values.toSet())
    }
}
