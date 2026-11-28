package com.lmqr.ha9_comp_service.offline

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/* Where the E-Ink button sits on the left side of the A9, as a fraction of the panel height. */
private const val BUTTON_TOP_FRACTION = 0.3125f
private const val BUTTON_HEIGHT_FRACTION = 0.10f
private const val PADDING_DP = 3f
private const val TEXT_SP = 9f
private const val LABEL = "OFFLINE"

/** Black strip with OFFLINE in white at the left edge next to the E-Ink button while the mode is on. */
class OfflineStrip(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: View? = null

    fun show(on: Boolean) = if (on) add() else remove()

    fun remove() {
        view?.let(windowManager::removeView)
        view = null
    }

    @SuppressLint("RtlHardcoded")
    private fun add() {
        if (view != null) return
        val strip = StripView(context)
        val panel = Point().also { @Suppress("DEPRECATION") windowManager.defaultDisplay.getRealSize(it) }
        val params = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            gravity = Gravity.TOP or Gravity.LEFT
            x = 0
            y = (panel.y * BUTTON_TOP_FRACTION).toInt()
            width = strip.widthPx
            height = (panel.y * BUTTON_HEIGHT_FRACTION).toInt()
        }
        windowManager.addView(strip, params)
        view = strip
    }

    private class StripView(context: Context) : View(context) {
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.DEFAULT_BOLD
            textSize = px(TypedValue.COMPLEX_UNIT_SP, TEXT_SP)
            letterSpacing = 0.15f
            textAlign = Paint.Align.CENTER
        }
        private val capHeight = Rect().also { text.getTextBounds(LABEL, 0, LABEL.length, it) }.height()
        val widthPx = capHeight + 2 * px(TypedValue.COMPLEX_UNIT_DIP, PADDING_DP).toInt()

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(Color.BLACK)
            canvas.rotate(-90f)
            canvas.drawText(LABEL, -height / 2f, (width + capHeight) / 2f, text)
        }

        private fun px(unit: Int, value: Float) =
            TypedValue.applyDimension(unit, value, resources.displayMetrics)
    }
}
