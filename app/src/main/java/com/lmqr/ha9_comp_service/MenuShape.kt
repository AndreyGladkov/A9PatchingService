package com.lmqr.ha9_comp_service

import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.RoundedCorner
import android.view.WindowInsets
import android.view.WindowManager
import com.lmqr.ha9_comp_service.databinding.FloatingMenuLayoutBinding
import kotlin.math.max
import kotlin.math.roundToInt

private const val MENU_INSET_DP = 4f
private const val MENU_TOP_RADIUS_DP = 8f
private const val MENU_STROKE_DP = 2f
private const val MENU_FILL = 0xFFF7F8FD.toInt()
private const val GESTURE_ZONE_GAP_DP = 8f
/** The GSI's gesture handle as measured on the A9 screen; the menu covers it and draws it in place. */
private const val HANDLE_WIDTH_DP = 108f
private const val HANDLE_HEIGHT_DP = 3.5f
private const val HANDLE_BOTTOM_DP = 10f
/** Hisense ships no rounded-corner config, so the system reports 0; measured on the A9 panel. */
private const val A9_SCREEN_CORNER_DP = 24f

const val NAVIGATION_MODE = "navigation_mode"
private const val NAVIGATION_MODE_GESTURAL = 2

/**
 * With gesture navigation only a handle sits at the bottom, so the menu rests on the screen edge and
 * its corners follow the screen's; buttons of the other modes must stay reachable above it.
 */
fun menuBottomOffset(navigationMode: Int, navBarHeight: Int) =
    if (navigationMode == NAVIGATION_MODE_GESTURAL) 0 else navBarHeight

/** A corner drawn inset from a rounded edge stays parallel to it with the edge radius minus the inset. */
fun concentricRadius(outerRadiusPx: Int, insetPx: Int, minRadiusPx: Int) =
    max(minRadiusPx, outerRadiusPx - insetPx)

fun screenBottomCornerRadiusPx(context: Context): Int {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        @Suppress("DEPRECATION")
        val display = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        display.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius?.let { if (it > 0) return it }
    }
    val system = Resources.getSystem()
    for (name in listOf("rounded_corner_radius_bottom", "rounded_corner_radius")) {
        val id = system.getIdentifier(name, "dimen", "android")
        if (id != 0) system.getDimensionPixelSize(id).let { if (it > 0) return it }
    }
    return 0
}

/** The menu sits on the bottom edge, so its lower corners follow the screen's rounded corners. */
private fun Context.dp(value: Float) =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

fun FloatingMenuLayoutBinding.applyMenuShape(context: Context) {
    fun dp(value: Float) = context.dp(value)
    val inset = dp(MENU_INSET_DP).toInt()
    val top = dp(MENU_TOP_RADIUS_DP)
    val screenCorner = screenBottomCornerRadiusPx(context).takeIf { it > 0 } ?: dp(A9_SCREEN_CORNER_DP).toInt()
    val bottom = concentricRadius(screenCorner, inset, top.toInt()).toFloat()
    val frame = GradientDrawable().apply {
        setColor(MENU_FILL)
        setStroke(dp(MENU_STROKE_DP).toInt(), Color.BLACK)
        cornerRadii = floatArrayOf(top, top, top, top, bottom, bottom, bottom, bottom)
    }
    // setBackground replaces the view's padding with the drawable's insets; keep the layout's.
    val padding = intArrayOf(root.paddingLeft, root.paddingTop, root.paddingRight, root.paddingBottom)
    root.background = InsetDrawable(frame, inset, 0, inset, inset)
    root.setPadding(padding[0], padding[1], padding[2], padding[3])
    // The selected segment's square fill is clipped to the block's rounded outline.
    linearLayout.clipToOutline = true
    staticAodLinearLayout.clipToOutline = true
}

fun menuBottomPadding(basePx: Int, gestureZonePx: Int, gapPx: Int) = max(basePx, gestureZonePx + gapPx)

/**
 * A touch that starts in the mandatory gesture zone also reaches the launcher's gesture monitor,
 * which takes a horizontal drag there as an app switch even over this overlay: the slider stops and
 * the app below slides. The zone cannot be excluded, so the controls are kept above it.
 */
fun FloatingMenuLayoutBinding.keepControlsAboveSystemGestures(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R)
        return
    val padding = intArrayOf(root.paddingLeft, root.paddingTop, root.paddingRight, root.paddingBottom)
    val gap = context.dp(GESTURE_ZONE_GAP_DP).toInt()
    val plainBackground = root.background
    val backgroundWithHandle = withGestureHandle(plainBackground, context)
    root.setOnApplyWindowInsetsListener { view, insets ->
        val gestureZone = insets.getInsets(WindowInsets.Type.mandatorySystemGestures()).bottom
        view.background = if (gestureZone > 0) backgroundWithHandle else plainBackground
        view.setPadding(padding[0], padding[1], padding[2], menuBottomPadding(padding[3], gestureZone, gap))
        insets
    }
}

private fun withGestureHandle(background: Drawable, context: Context): LayerDrawable {
    val height = context.dp(HANDLE_HEIGHT_DP)
    val handle = GradientDrawable().apply {
        setColor(Color.BLACK)
        cornerRadius = height / 2
    }
    return LayerDrawable(arrayOf(background, handle)).apply {
        paddingMode = LayerDrawable.PADDING_MODE_STACK
        setLayerGravity(1, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        setLayerSize(1, context.dp(HANDLE_WIDTH_DP).roundToInt(), height.roundToInt())
        setLayerInsetBottom(1, context.dp(HANDLE_BOTTOM_DP).roundToInt())
    }
}
