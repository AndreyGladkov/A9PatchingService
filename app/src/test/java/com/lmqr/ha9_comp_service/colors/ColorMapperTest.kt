package com.lmqr.ha9_comp_service.colors

import org.junit.Assert.assertEquals
import org.junit.Test

class ColorMapperTest {
    private val config = RecolorConfig(
        lineThreshold = 178,
        fillLevels = FillLevels.THREE,
        fillDarkCutoff = 127,
        fillLightCutoff = 229,
        lightGray = 204,
    )
    private val mapper = ColorMapper(config)

    @Test
    fun `panel luma uses vendor weights`() {
        assertEquals(255, panelLuma(0xFFFFFFFF.toInt()))
        assertEquals(0, panelLuma(0xFF000000.toInt()))
        assertEquals(75, panelLuma(0xFFFF0000.toInt()))
        assertEquals(149, panelLuma(0xFF00FF00.toInt()))
        assertEquals(29, panelLuma(0xFF0000FF.toInt()))
    }

    @Test
    fun `secondary grey text becomes black`() {
        assertEquals(0xFF000000.toInt(), mapper.line(0xFF9E9E9E.toInt()))
    }

    @Test
    fun `white text on dark button stays white`() {
        assertEquals(0xFFFFFFFF.toInt(), mapper.line(0xFFFFFFFF.toInt()))
    }

    @Test
    fun `line keeps source alpha`() {
        assertEquals(0x80000000.toInt(), mapper.line(0x80607D8B.toInt()))
        assertEquals(0x00000000, mapper.line(0x00757575))
    }

    @Test
    fun `three level fill quantises around cutoffs`() {
        assertEquals(0xFF000000.toInt(), mapper.fill(0xFF1A73E8.toInt()))
        assertEquals(0xFFCCCCCC.toInt(), mapper.fill(0xFFD3E3FD.toInt()))
        assertEquals(0xFFFFFFFF.toInt(), mapper.fill(0xFFF8F9FA.toInt()))
    }

    @Test
    fun `two level fill splits at dark cutoff`() {
        val twoLevels = ColorMapper(config.copy(fillLevels = FillLevels.TWO))

        assertEquals(0xFF000000.toInt(), twoLevels.fill(0xFF6E6E6E.toInt()))
        assertEquals(0xFFFFFFFF.toInt(), twoLevels.fill(0xFFD3E3FD.toInt()))
    }

    @Test
    fun `dark text stays readable on light blue button in two level mode`() {
        val twoLevels = ColorMapper(config.copy(fillLevels = FillLevels.TWO))

        assertEquals(0xFFFFFFFF.toInt(), twoLevels.fill(0xFF8AB4F8.toInt()))
        assertEquals(0xFF000000.toInt(), twoLevels.line(0xFF202124.toInt()))
    }

    @Test
    fun `fill is idempotent`() {
        listOf(0xFF1A73E8.toInt(), 0xFFD3E3FD.toInt(), 0xFFF8F9FA.toInt(), 0x40BDBDBD).forEach { color ->
            val once = mapper.fill(color)
            assertEquals(once, mapper.fill(once))
        }
    }

    @Test
    fun `threshold matrix agrees with line away from the edge`() {
        val matrix = mapper.lineThresholdMatrix()
        listOf(0xFF9E9E9E, 0xFF1A73E8, 0xFFD32F2F, 0xFFE0E0E0, 0xFFFFFFFF, 0xFF000000)
            .map(Long::toInt)
            .forEach { color -> assertEquals(mapper.line(color), applyMatrix(matrix, color)) }
    }

    private fun applyMatrix(m: FloatArray, color: Int): Int {
        val a = color ushr 24
        val r = color shr 16 and 0xFF
        val g = color shr 8 and 0xFF
        val b = color and 0xFF
        fun row(i: Int) =
            (m[i] * r + m[i + 1] * g + m[i + 2] * b + m[i + 3] * a + m[i + 4])
                .toInt().coerceIn(0, 255)
        return (row(15) shl 24) or (row(0) shl 16) or (row(5) shl 8) or row(10)
    }
}
