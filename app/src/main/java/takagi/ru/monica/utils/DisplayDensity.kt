package takagi.ru.monica.utils

import android.content.Context
import android.content.res.Configuration
import kotlin.math.roundToInt

/** Applies the app's UI scale without changing the device-wide display setting. */
object DisplayDensity {
    const val DEFAULT_SCALE = 100
    const val MIN_SCALE = 75
    const val MAX_SCALE = 150
    const val STEP = 5

    fun clampScale(scale: Int): Int = scale.coerceIn(MIN_SCALE, MAX_SCALE)

    fun densityDpiFor(baseDensityDpi: Int, scale: Int): Int =
        (baseDensityDpi * clampScale(scale) / DEFAULT_SCALE.toFloat())
            .roundToInt()
            .coerceAtLeast(1)

    fun apply(context: Context, scale: Int): Context {
        val configuration = Configuration(context.resources.configuration)
        configuration.densityDpi = densityDpiFor(
            context.resources.displayMetrics.densityDpi,
            scale
        )
        return context.createConfigurationContext(configuration)
    }
}
