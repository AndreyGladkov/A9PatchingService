package com.lmqr.ha9_comp_service.colors

private const val BLACK = 0x000000
private const val WHITE = 0xFFFFFF
private const val GREY_STEP = 0x010101
private const val LINE_EDGE_GAIN = 16f

enum class FillLevels { TWO, THREE }

data class RecolorConfig(
    val lineThreshold: Int,
    val fillLevels: FillLevels,
    val fillDarkCutoff: Int,
    val fillLightCutoff: Int,
    val lightGray: Int,
)

// Same integer weights the A9 vendor composer uses to turn a frame into grey
// before applying the e-ink lookup tables, so thresholds match what the panel shows.
fun panelLuma(color: Int): Int {
    val r = color shr 16 and 0xFF
    val g = color shr 8 and 0xFF
    val b = color and 0xFF
    return (76 * r + 150 * g + 30 * b) shr 8
}

class ColorMapper(private val config: RecolorConfig) {

    fun line(color: Int): Int =
        withAlphaOf(color, if (panelLuma(color) < config.lineThreshold) BLACK else WHITE)

    fun fill(color: Int): Int = withAlphaOf(color, fillGrey(panelLuma(color)) * GREY_STEP)

    // A steep linear ramp around the threshold, clamped by the colour filter, approximates
    // the hard black/white step of line() for content whose individual colours are not
    // reachable from Java (vector drawable paths). A finite gain keeps antialiased edges.
    fun lineThresholdMatrix(): FloatArray {
        val gain = LINE_EDGE_GAIN / 256f
        val offset = 127.5f - LINE_EDGE_GAIN * config.lineThreshold
        val greyRow = floatArrayOf(76 * gain, 150 * gain, 30 * gain, 0f, offset)
        return greyRow + greyRow + greyRow + floatArrayOf(0f, 0f, 0f, 1f, 0f)
    }

    private fun fillGrey(luma: Int): Int = when (config.fillLevels) {
        // Splitting at the dark cutoff rather than the line threshold keeps dark text on
        // mid-light fills (e.g. light blue buttons) from collapsing to black on black.
        FillLevels.TWO -> if (luma < config.fillDarkCutoff) 0 else 255

        FillLevels.THREE -> when {
            luma < config.fillDarkCutoff -> 0
            luma > config.fillLightCutoff -> 255
            else -> config.lightGray
        }
    }

    private fun withAlphaOf(source: Int, rgb: Int) = (source and 0xFF000000.toInt()) or rgb
}
