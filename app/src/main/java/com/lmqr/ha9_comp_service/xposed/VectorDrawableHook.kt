package com.lmqr.ha9_comp_service.xposed

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.drawable.VectorDrawable
import android.os.Build
import androidx.annotation.RequiresApi
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers

private const val SAVED_FILTER = "recolor.vector.filter"

// VectorDrawable renders its paths natively, out of reach of the Canvas hooks, and reads
// mColorFilter (falling back to the tint in mBlendModeColorFilter) only inside draw().
// Swapping the field for the duration of draw() avoids setColorFilter(), whose
// invalidateSelf() would schedule another frame on every draw.
@RequiresApi(Build.VERSION_CODES.Q)
object VectorDrawableHook {
    private class Saved(val filter: ColorFilter?)

    fun install(session: RecolorSession) {
        XposedHelpers.findAndHookMethod(
            VectorDrawable::class.java,
            "draw",
            Canvas::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val palette = session.palette ?: return
                    if (!(param.args[0] as Canvas).isHardwareAccelerated)
                        return
                    val drawable = param.thisObject
                    val own = XposedHelpers.getObjectField(drawable, "mColorFilter") as ColorFilter?
                    val tint = XposedHelpers.getObjectField(drawable, "mBlendModeColorFilter")
                        as ColorFilter?
                    val replacement = when {
                        own != null -> palette.lineTint(own) ?: return
                        tint != null -> palette.lineTint(tint) ?: return
                        else -> palette.lineThresholdFilter
                    }

                    param.setObjectExtra(SAVED_FILTER, Saved(own))
                    XposedHelpers.setObjectField(drawable, "mColorFilter", replacement)
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    val saved = param.getObjectExtra(SAVED_FILTER) as Saved? ?: return
                    XposedHelpers.setObjectField(param.thisObject, "mColorFilter", saved.filter)
                }
            }
        )
    }
}
