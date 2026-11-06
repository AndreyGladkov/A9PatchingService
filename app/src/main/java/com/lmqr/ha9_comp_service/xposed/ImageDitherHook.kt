package com.lmqr.ha9_comp_service.xposed

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import com.lmqr.ha9_comp_service.colors.PANEL_LUMA_BLUE
import com.lmqr.ha9_comp_service.colors.PANEL_LUMA_GREEN
import com.lmqr.ha9_comp_service.colors.PANEL_LUMA_RED
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XCallback
import java.lang.reflect.Method

private const val PANEL_GREY_LEVELS = 16f
private const val MIN_PHOTO_SIDE_PX = 64

private val DITHER_SHADER = """
    uniform shader image;
    uniform shader noise;
    uniform float levels;

    half4 main(float2 position) {
        half4 color = image.eval(position);
        if (color.a == 0.0)
            return color;
        float luma = dot(color.rgb / color.a,
            half3($PANEL_LUMA_RED.0, $PANEL_LUMA_GREEN.0, $PANEL_LUMA_BLUE.0) / 256.0);
        float threshold = noise.eval(position).r + 0.5 / 256.0;
        float level = clamp(floor(luma * (levels - 1.0) + threshold), 0.0, levels - 1.0);
        return half4(half3(level / (levels - 1.0)) * color.a, color.a);
    }
""".trimIndent()

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class ImageDitherHook private constructor(
    private val session: RecolorSession,
    private val placement: Placement,
) : XC_MethodHook(PRIORITY_BEFORE_TINT) {

    override fun beforeHookedMethod(param: MethodHookParam) {
        if (!session.ditherImages)
            return
        val bitmap = param.args[0] as Bitmap? ?: return
        val paint = param.args[placement.paintIndex] as Paint?
        if (!looksLikePhoto(bitmap, paint))
            return

        if (placement.draw(param.thisObject as Canvas, bitmap, param.args, paint))
            param.result = null
    }

    companion object {
        private const val PRIORITY_BEFORE_TINT = XCallback.PRIORITY_DEFAULT + 1

        fun forMethod(method: Method, session: RecolorSession): ImageDitherHook? =
            Placement.of(method.parameterTypes)?.let { ImageDitherHook(session, it) }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class ImageShaderDitherHook(
    private val session: RecolorSession,
    private val paintIndex: Int,
) : XC_MethodHook() {

    override fun beforeHookedMethod(param: MethodHookParam) {
        if (!session.ditherImages)
            return
        val paint = param.args[paintIndex] as Paint? ?: return
        val image = paint.shader as? BitmapShader ?: return
        val bitmap = image.bitmap() ?: return
        if (!looksLikePhoto(bitmap, paint))
            return
        val dither = DitheredImage.shaderFor(param.thisObject as Canvas, image) ?: return

        param.setObjectExtra(ORIGINAL_SHADER, image)
        paint.shader = dither
    }

    override fun afterHookedMethod(param: MethodHookParam) {
        val original = param.getObjectExtra(ORIGINAL_SHADER) as Shader? ?: return
        (param.args[paintIndex] as Paint).shader = original
    }

    private fun BitmapShader.bitmap(): Bitmap? = try {
        XposedHelpers.getObjectField(this, "mBitmap") as? Bitmap
    } catch (e: NoSuchFieldError) {
        null
    }

    private companion object {
        const val ORIGINAL_SHADER = "dither.originalShader"
    }
}

private fun looksLikePhoto(bitmap: Bitmap, paint: Paint?) =
    !bitmap.isRecycled &&
        bitmap.config != Bitmap.Config.ALPHA_8 &&
        minOf(bitmap.width, bitmap.height) >= MIN_PHOTO_SIDE_PX &&
        paint?.colorFilter == null

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private sealed class Placement(val paintIndex: Int) {
    abstract fun draw(canvas: Canvas, bitmap: Bitmap, args: Array<Any?>, paint: Paint?): Boolean

    object AtPosition : Placement(paintIndex = 3) {
        override fun draw(canvas: Canvas, bitmap: Bitmap, args: Array<Any?>, paint: Paint?): Boolean {
            if (!drawnAtOwnDensity(canvas, bitmap))
                return false
            val left = args[1] as Float
            val top = args[2] as Float
            val destination = RectF(left, top, left + bitmap.width, top + bitmap.height)
            return DitheredImage.draw(canvas, bitmap, bitmap.bounds(), destination, paint)
        }

        private fun drawnAtOwnDensity(canvas: Canvas, bitmap: Bitmap) =
            bitmap.density == Bitmap.DENSITY_NONE ||
                canvas.density == Bitmap.DENSITY_NONE ||
                bitmap.density == canvas.density
    }

    object WithMatrix : Placement(paintIndex = 2) {
        override fun draw(canvas: Canvas, bitmap: Bitmap, args: Array<Any?>, paint: Paint?): Boolean {
            val saveCount = canvas.save()
            try {
                (args[1] as Matrix?)?.let(canvas::concat)
                return DitheredImage.draw(canvas, bitmap, bitmap.bounds(), bitmap.bounds(), paint)
            } finally {
                canvas.restoreToCount(saveCount)
            }
        }
    }

    object SourceToDestination : Placement(paintIndex = 3) {
        override fun draw(canvas: Canvas, bitmap: Bitmap, args: Array<Any?>, paint: Paint?): Boolean {
            val source = (args[1] as Rect?)?.let(::RectF) ?: bitmap.bounds()
            val destination = when (val target = args[2]) {
                is RectF -> target
                is Rect -> RectF(target)
                else -> return false
            }
            return DitheredImage.draw(canvas, bitmap, source, destination, paint)
        }
    }

    companion object {
        private val float = Float::class.javaPrimitiveType

        fun of(types: Array<Class<*>>): Placement? = when {
            types.matches(Bitmap::class.java, float, float, Paint::class.java) -> AtPosition
            types.matches(Bitmap::class.java, Matrix::class.java, Paint::class.java) -> WithMatrix
            types.matches(Bitmap::class.java, Rect::class.java, RectF::class.java, Paint::class.java) ->
                SourceToDestination
            types.matches(Bitmap::class.java, Rect::class.java, Rect::class.java, Paint::class.java) ->
                SourceToDestination
            else -> null
        }

        private fun Array<Class<*>>.matches(vararg expected: Class<*>?) = contentEquals(expected)
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object DitheredImage {
    private val noiseBitmap: Bitmap by lazy {
        val pixels = IntArray(BlueNoise.thresholds.size) { index ->
            val value = BlueNoise.thresholds[index].toInt() and 0xFF
            0xFF000000.toInt() or (value * 0x010101)
        }
        Bitmap.createBitmap(pixels, BlueNoise.SIZE, BlueNoise.SIZE, Bitmap.Config.ARGB_8888)
    }

    private val ditherShaders = ThreadLocal.withInitial { RuntimeShader(DITHER_SHADER) }

    private val noiseShaders = ThreadLocal.withInitial {
        BitmapShader(noiseBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
            filterMode = BitmapShader.FILTER_MODE_NEAREST
        }
    }

    fun shaderFor(canvas: Canvas, image: Shader): Shader? {
        @Suppress("DEPRECATION")
        val localToDevice = canvas.matrix
        val deviceToLocal = Matrix()
        if (!localToDevice.invert(deviceToLocal))
            return null

        val noise = noiseShaders.get()!!.apply { setLocalMatrix(deviceToLocal) }
        return ditherShaders.get()!!.apply {
            setInputShader("image", image)
            setInputShader("noise", noise)
            setFloatUniform("levels", PANEL_GREY_LEVELS)
        }
    }

    fun draw(canvas: Canvas, bitmap: Bitmap, source: RectF, destination: RectF, paint: Paint?): Boolean {
        val image = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply { setRectToRect(source, destination, Matrix.ScaleToFit.FILL) })
            filterMode = if (paint?.isFilterBitmap != false)
                BitmapShader.FILTER_MODE_LINEAR
            else
                BitmapShader.FILTER_MODE_NEAREST
        }
        val dither = shaderFor(canvas, image) ?: return false
        val ditherPaint = (paint?.let(::Paint) ?: Paint()).apply {
            style = Paint.Style.FILL
            shader = dither
        }
        canvas.drawRect(destination, ditherPaint)
        return true
    }
}

private fun Bitmap.bounds() = RectF(0f, 0f, width.toFloat(), height.toFloat())
