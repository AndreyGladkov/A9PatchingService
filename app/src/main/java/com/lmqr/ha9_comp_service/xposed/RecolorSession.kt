package com.lmqr.ha9_comp_service.xposed

import android.app.Application
import android.content.ContentResolver
import android.database.ContentObserver
import android.graphics.BlendModeColorFilter
import android.graphics.ColorFilter
import android.graphics.ColorMatrixColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import com.lmqr.ha9_comp_service.colors.ColorMapper
import com.lmqr.ha9_comp_service.colors.ColorSettingsProvider
import com.lmqr.ha9_comp_service.colors.RecolorConfig
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.util.concurrent.ConcurrentHashMap

@RequiresApi(Build.VERSION_CODES.Q)
class Palette(config: RecolorConfig) {
    val mapper = ColorMapper(config)
    val lineThresholdFilter = ColorMatrixColorFilter(mapper.lineThresholdMatrix())
    private val lineTints = ConcurrentHashMap<Pair<Int, Any>, ColorFilter>()

    fun lineTint(tint: ColorFilter): ColorFilter? = when (tint) {
        is BlendModeColorFilter -> mapper.line(tint.color).let { color ->
            lineTints.getOrPut(color to tint.mode) { BlendModeColorFilter(color, tint.mode) }
        }

        is PorterDuffColorFilter -> {
            val color = mapper.line(XposedHelpers.callMethod(tint, "getColor") as Int)
            val mode = XposedHelpers.callMethod(tint, "getMode") as PorterDuff.Mode
            lineTints.getOrPut(color to mode) { PorterDuffColorFilter(color, mode) }
        }

        else -> null
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
class RecolorSession(private val packageName: String) {
    @Volatile
    var palette: Palette? = null
        private set
    private var config: RecolorConfig? = null

    fun attach(application: Application) {
        val resolver = application.contentResolver
        reload(resolver)
        try {
            resolver.registerContentObserver(
                ColorSettingsProvider.BASE_URI,
                true,
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        if (reload(resolver))
                            WindowInvalidator.invalidateAll()
                    }
                }
            )
        } catch (e: SecurityException) {
            XposedBridge.log(e)
        }
    }

    private fun reload(resolver: ContentResolver): Boolean {
        val loaded = try {
            resolver.query(ColorSettingsProvider.appUri(packageName), null, null, null, null)
                ?.use(ColorSettingsProvider::recolorConfigFrom)
        } catch (e: RuntimeException) {
            XposedBridge.log(e)
            null
        }
        if (loaded == config)
            return false

        config = loaded
        palette = loaded?.let(::Palette)
        return true
    }
}
