import android.content.Context
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.provider.Settings

object SystemSettingsManager {
    private const val NIGHT_DISPLAY_COLOR_TEMPERATURE = "night_display_color_temperature"
    private const val AOSP_DEFAULT_NIGHT_LIGHT_TEMPERATURE = 2850

    // DisplayPowerController recomputes the LED split only on a brightness change it observes:
    // a notifyChange alone is ignored, and a step put back at once collapses into no change.
    // So the value is stepped, held long enough to be picked up, then put back.
    private const val BRIGHTNESS_STEP_HOLD_MS = 150L

    private val handler = Handler(Looper.getMainLooper())
    private var isStepping = false
    private var isStepRequested = false

    private fun forceUpdate(context: Context) {
        if (isStepping)
            isStepRequested = true
        else
            stepBrightnessAndBack(context.applicationContext)
    }

    private fun stepBrightnessAndBack(context: Context) {
        val brightness = getBrightnessFromSetting(context)
        if (brightness <= 0)
            return

        val stepped = if (brightness > 1) brightness - 1 else brightness + 1
        isStepping = true
        setBrightnessSetting(context, stepped)
        handler.postDelayed({
            if (getBrightnessFromSetting(context) == stepped)
                setBrightnessSetting(context, brightness)
            handler.postDelayed({
                isStepping = false
                if (isStepRequested) {
                    isStepRequested = false
                    stepBrightnessAndBack(context)
                }
            }, BRIGHTNESS_STEP_HOLD_MS)
        }, BRIGHTNESS_STEP_HOLD_MS)
    }

    fun setNightLightMode(context: Context, nightLightMode: NightLightMode) {
        try {
            val contentResolver = context.applicationContext.contentResolver
            Settings.Secure.putInt(
                contentResolver,
                "night_display_activated",
                if (nightLightMode == NightLightMode.Disabled) 0 else 1
            )
            Settings.Secure.putInt(
                contentResolver,
                "night_display_auto_mode",
                if (nightLightMode != NightLightMode.Auto) 0 else 1
            )
           forceUpdate(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun toggleNightLightMode(context: Context) {
        val contentResolver = context.applicationContext.contentResolver
        try{
            val currentSetting = Settings.Secure.getInt(
                contentResolver,
                "night_display_activated",
                0
            )
            Settings.Secure.putInt(
                contentResolver,
                "night_display_activated",
                currentSetting xor 1
            )
            forceUpdate(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getNightLightMode(context: Context): NightLightMode {
        val contentResolver = context.applicationContext.contentResolver
        return try {
            when {
                Settings.Secure.getInt(
                    contentResolver,
                    "night_display_activated",
                    0
                ) == 0 -> NightLightMode.Disabled
                Settings.Secure.getInt(
                    contentResolver,
                    "night_display_auto_mode",
                    0
                ) == 0 -> NightLightMode.Manual
                else -> NightLightMode.Auto
            }
        } catch (e: Exception) {
            NightLightMode.Disabled
        }
    }

    fun setNextNightLightMode(context: Context): NightLightMode {
        val nextMode = when(getNightLightMode(context)){
            NightLightMode.Manual -> NightLightMode.Auto
            NightLightMode.Auto -> NightLightMode.Disabled
            else -> NightLightMode.Manual
        }
        setNightLightMode(context, nextMode)
        return nextMode
    }

    fun getNightLightTemperature(context: Context): Int =
        Settings.Secure.getInt(
            context.applicationContext.contentResolver,
            NIGHT_DISPLAY_COLOR_TEMPERATURE,
            -1,
        ).takeIf { it > 0 } ?: defaultNightLightTemperature()

    fun setNightLightTemperature(context: Context, temperature: Int) {
        try {
            Settings.Secure.putInt(
                context.applicationContext.contentResolver,
                NIGHT_DISPLAY_COLOR_TEMPERATURE,
                temperature,
            )
            // On Android 16 the settings observer retints without calling the patched
            // onColorTemperatureChanged, so the LED split is redone only on a brightness change.
            forceUpdate(context)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    // Until the user picks a temperature, ColorDisplayService uses this framework config value.
    private fun defaultNightLightTemperature(): Int {
        val resources = Resources.getSystem()
        val id = resources.getIdentifier("config_nightDisplayColorTemperatureDefault", "integer", "android")
        return if (id != 0) resources.getInteger(id) else AOSP_DEFAULT_NIGHT_LIGHT_TEMPERATURE
    }

    fun getBrightnessFromSetting(context: Context): Int {
        val contentResolver = context.applicationContext.contentResolver
        return Settings.System.getInt(
            contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            255
        )
    }

    fun setBrightnessSetting(context: Context, value: Int) {
        try {
            val contentResolver = context.applicationContext.contentResolver
            Settings.System.putInt(
                contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                value
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    enum class NightLightMode {
        Manual,
        Auto,
        Disabled
    }
}