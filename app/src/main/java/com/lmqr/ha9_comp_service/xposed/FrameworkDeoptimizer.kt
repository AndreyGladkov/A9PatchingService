package com.lmqr.ha9_comp_service.xposed

import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier

// The boot image is compiled ahead of time with the short final draw methods of
// BaseRecordingCanvas inlined into framework callers such as ColorDrawable.draw, so those
// calls never reach the Canvas hooks. Deoptimizing the callers sends them through the hooked
// methods again. deoptimizeMethod is a Vector/LSPosed extension, absent from Xposed API 82.
object FrameworkDeoptimizer {
    private val drawingClasses = listOf(
        "android.view.View",
        "android.view.ViewGroup",
        "android.widget.TextView",
        "android.widget.Editor",
        "android.widget.ImageView",
        "android.widget.CompoundButton",
        "android.widget.Switch",
        "android.widget.ProgressBar",
        "android.widget.AbsSeekBar",
        "android.widget.ListView",
        "android.widget.ScrollBarDrawable",
        "android.text.Layout",
        "android.text.TextLine",
        "android.text.style.DynamicDrawableSpan",
        "android.graphics.NinePatch",
        "android.graphics.drawable.ColorDrawable",
        "android.graphics.drawable.GradientDrawable",
        "android.graphics.drawable.BitmapDrawable",
        "android.graphics.drawable.NinePatchDrawable",
        "android.graphics.drawable.ShapeDrawable",
        "android.graphics.drawable.LayerDrawable",
        "android.graphics.drawable.DrawableContainer",
        "android.graphics.drawable.DrawableWrapper",
        "android.graphics.drawable.RippleDrawable",
        "android.graphics.drawable.RippleForeground",
        "android.graphics.drawable.RippleBackground",
        "android.graphics.drawable.shapes.RectShape",
        "android.graphics.drawable.shapes.RoundRectShape",
        "android.graphics.drawable.shapes.OvalShape",
        "android.graphics.drawable.shapes.ArcShape",
        "android.graphics.drawable.shapes.PathShape",
    )

    private val drawMethodNames = setOf(
        "draw", "onDraw", "dispatchDraw", "drawChild", "drawBackground", "onDrawForeground",
        "onDrawScrollBars", "onDrawHorizontalScrollBar", "onDrawVerticalScrollBar",
        "drawDefaultFocusHighlight", "drawText", "drawRun", "drawTextRun", "drawStroke",
        "drawHardwareAccelerated", "drawCursor", "drawTrack", "drawThumb", "drawTickMarks",
        "drawDivider", "drawSoftware",
    )

    fun deoptimizeDrawCallers() {
        val deoptimize = try {
            XposedBridge::class.java.getMethod("deoptimizeMethod", Member::class.java)
        } catch (e: NoSuchMethodException) {
            XposedBridge.log(e)
            return
        }
        drawingClasses
            .mapNotNull { XposedHelpers.findClassIfExists(it, null) }
            .flatMap { it.declaredMethods.asList() }
            .filter(::mayDraw)
            .forEach { method ->
                try {
                    deoptimize.invoke(null, method)
                } catch (e: ReflectiveOperationException) {
                    XposedBridge.log(e)
                }
            }
    }

    private fun mayDraw(method: Method) =
        method.name in drawMethodNames &&
            !Modifier.isAbstract(method.modifiers) && !Modifier.isNative(method.modifiers)
}
