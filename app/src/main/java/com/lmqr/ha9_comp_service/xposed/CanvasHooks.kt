package com.lmqr.ha9_comp_service.xposed

import android.graphics.BlendMode
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.os.Build
import androidx.annotation.RequiresApi
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Method
import java.lang.reflect.Modifier

private const val ORIGINAL = "recolor.original"

@RequiresApi(Build.VERSION_CODES.Q)
object CanvasHooks {
    private val textMethods =
        setOf("drawText", "drawTextRun", "drawTextOnPath", "drawPosText", "drawGlyphs")
    private val lineMethods = setOf("drawLine", "drawLines", "drawPoint", "drawPoints")
    private val shapeMethods = setOf(
        "drawRect", "drawRoundRect", "drawDoubleRoundRect", "drawOval", "drawCircle",
        "drawArc", "drawPath", "drawPaint"
    )
    private val bitmapMethods = setOf("drawBitmap", "drawBitmapMesh")

    // Only the display-list canvas is hooked. Software canvases (BaseCanvas) mostly draw into
    // app-owned bitmaps - edited photos, thumbnail caches - that must keep their real colours.
    private const val RECORDING_CANVAS = "android.graphics.BaseRecordingCanvas"

    fun install(session: RecolorSession) {
        XposedHelpers.findClass(RECORDING_CANVAS, null).declaredMethods
            .filter { Modifier.isPublic(it.modifiers) }
            .forEach { method -> hookFor(method, session)?.let { XposedBridge.hookMethod(method, it) } }
    }

    private fun hookFor(method: Method, session: RecolorSession): XC_MethodHook? {
        if (method.name == "drawColor")
            return DrawColorHook(session)

        val paintIndex = method.parameterTypes.indexOf(Paint::class.java)
        if (paintIndex < 0)
            return null

        return when (method.name) {
            in textMethods, in lineMethods ->
                PaintColorHook(session, paintIndex) { palette, paint -> palette.mapper.line(paint.color) }

            in shapeMethods -> PaintColorHook(session, paintIndex) { palette, paint ->
                if (paint.style == Paint.Style.STROKE)
                    palette.mapper.line(paint.color)
                else
                    palette.mapper.fill(paint.color)
            }

            in bitmapMethods -> BitmapTintHook(session, paintIndex)
            else -> null
        }
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
private class PaintColorHook(
    private val session: RecolorSession,
    private val paintIndex: Int,
    private val recolor: (Palette, Paint) -> Int,
) : XC_MethodHook() {
    override fun beforeHookedMethod(param: MethodHookParam) {
        val palette = session.palette ?: return
        val paint = param.args[paintIndex] as Paint? ?: return
        if (paint.shader != null)
            return

        param.setObjectExtra(ORIGINAL, paint.colorLong)
        paint.color = recolor(palette, paint)
    }

    override fun afterHookedMethod(param: MethodHookParam) {
        val original = param.getObjectExtra(ORIGINAL) as Long? ?: return
        (param.args[paintIndex] as Paint).setColor(original)
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
private class BitmapTintHook(
    private val session: RecolorSession,
    private val paintIndex: Int,
) : XC_MethodHook() {
    override fun beforeHookedMethod(param: MethodHookParam) {
        val palette = session.palette ?: return
        val paint = param.args[paintIndex] as Paint? ?: return
        val tint = paint.colorFilter ?: return
        val replacement = palette.lineTint(tint) ?: return

        param.setObjectExtra(ORIGINAL, tint)
        paint.colorFilter = replacement
    }

    override fun afterHookedMethod(param: MethodHookParam) {
        val original = param.getObjectExtra(ORIGINAL) as ColorFilter? ?: return
        (param.args[paintIndex] as Paint).colorFilter = original
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
private class DrawColorHook(private val session: RecolorSession) : XC_MethodHook() {
    override fun beforeHookedMethod(param: MethodHookParam) {
        val palette = session.palette ?: return
        val color = param.args[0] as? Int ?: return
        if (param.args.size > 1 && param.args[1] !in paintingModes)
            return

        param.args[0] = palette.mapper.fill(color)
    }

    private companion object {
        val paintingModes = setOf(
            PorterDuff.Mode.SRC, PorterDuff.Mode.SRC_OVER, BlendMode.SRC, BlendMode.SRC_OVER
        )
    }
}
