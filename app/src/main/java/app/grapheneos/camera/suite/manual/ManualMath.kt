package app.grapheneos.camera.suite.manual

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/** The ranges the current camera advertises for the manual controls. */
data class ManualRanges(
    val isoMin: Int,
    val isoMax: Int,
    val exposureMinNs: Long,
    val exposureMaxNs: Long,
    val focusMaxDiopters: Float,
    val whiteBalanceMinK: Int = 2500,
    val whiteBalanceMaxK: Int = 8000,
    val whiteBalanceAvailable: Boolean = true,
) {
    val hasIso: Boolean get() = isoMax > isoMin
    val hasExposure: Boolean get() = exposureMaxNs > exposureMinNs
    val hasFocus: Boolean get() = focusMaxDiopters > 0f
}

/** The values last applied to the camera; [enabled] is the manual/auto switch. */
data class ManualState(
    val enabled: Boolean = false,
    val iso: Int,
    val exposureNs: Long,
    val focusDiopters: Float,
    val whiteBalanceK: Int,
)

/**
 * Pure slider and label maths, kept out of the Camera2 controller so it can be unit-tested.
 * ISO and exposure are logarithmic (a linear slider would spend most of its travel at the top);
 * focus is linear in dioptres, which is linear in focus distance.
 */
object ManualMath {
    const val STEPS = 1000

    fun progressToIso(progress: Int, min: Int, max: Int): Int {
        if (max <= min) return min
        return logValue(progress.toDouble() / STEPS, min.toDouble(), max.toDouble())
            .roundToInt().coerceIn(min, max)
    }

    fun isoToProgress(iso: Int, min: Int, max: Int): Int {
        if (max <= min) return 0
        return logProgress(iso.toDouble(), min.toDouble(), max.toDouble())
    }

    fun progressToExposure(progress: Int, min: Long, max: Long): Long {
        if (max <= min) return min
        return logValue(progress.toDouble() / STEPS, min.toDouble(), max.toDouble())
            .toLong().coerceIn(min, max)
    }

    fun exposureToProgress(ns: Long, min: Long, max: Long): Int {
        if (max <= min) return 0
        return logProgress(ns.toDouble(), min.toDouble(), max.toDouble())
    }

    fun progressToFocus(progress: Int, maxDiopters: Float): Float =
        (progress.toFloat() / STEPS) * maxDiopters

    fun focusToProgress(diopters: Float, maxDiopters: Float): Int {
        if (maxDiopters <= 0f) return 0
        return ((diopters / maxDiopters) * STEPS).roundToInt().coerceIn(0, STEPS)
    }

    fun progressToWhiteBalance(progress: Int, minK: Int, maxK: Int): Int =
        minK + ((maxK - minK) * (progress.toFloat() / STEPS)).roundToInt().coerceIn(0, maxK - minK)

    fun whiteBalanceToProgress(kelvin: Int, minK: Int, maxK: Int): Int {
        if (maxK <= minK) return 0
        return (((kelvin - minK).toFloat() / (maxK - minK)) * STEPS)
            .roundToInt().coerceIn(0, STEPS)
    }

    fun exposureLabel(ns: Long): String {
        if (ns <= 0L) return "—"
        if (ns >= 1_000_000_000L) {
            val seconds = ns / 1_000_000_000.0
            return if (seconds >= 10.0) "${seconds.roundToInt()} s"
            else String.format(java.util.Locale.US, "%.1f s", seconds)
        }
        return "1/${(1_000_000_000.0 / ns).roundToInt()} s"
    }

    fun isoLabel(iso: Int): String = "ISO $iso"

    fun focusLabel(diopters: Float): String {
        if (diopters <= 0.0001f) return "∞"
        val metres = 1f / diopters
        return if (metres < 10f) {
            String.format(java.util.Locale.US, "%.2f m", metres)
        } else {
            String.format(java.util.Locale.US, "%.1f m", metres)
        }
    }

    fun whiteBalanceLabel(kelvin: Int): String = "$kelvin K"

    /**
     * Approximate R/G/B multipliers from a colour temperature, expressed as an
     * [android.hardware.camera2.params.RggbChannelVector] (red, even green, odd green, blue).
     * The model is deliberately simple; a real device pass should calibrate it before it is
     * described as a colour-accurate white balance.
     */
    fun kelvinToRggb(kelvin: Int): FloatArray {
        val t = kelvin.coerceIn(1000, 12000) / 100f
        val r: Float
        val g: Float
        val b: Float
        if (t <= 66f) {
            r = 255f
            g = (99.4708 * ln(t) - 161.1196).toFloat().coerceIn(0f, 255f)
            b = if (t <= 19f) 0f
            else (138.5177 * ln(t - 10f) - 305.0448).toFloat().coerceIn(0f, 255f)
        } else {
            r = (329.6987 * (t - 60f).pow(-0.1332f)).toFloat().coerceIn(0f, 255f)
            g = (288.1222 * (t - 60f).pow(-0.0755f)).toFloat().coerceIn(0f, 255f)
            b = 255f
        }

        val rGain = if (r > 1f) (g / r).coerceIn(0.25f, 4f) else 1f
        val bGain = if (b > 1f) (g / b).coerceIn(0.25f, 4f) else 1f
        return floatArrayOf(rGain, 1f, 1f, bGain)
    }

    private fun logValue(fraction: Double, min: Double, max: Double): Double =
        min * (max / min).pow(fraction.coerceIn(0.0, 1.0))

    private fun logProgress(value: Double, min: Double, max: Double): Int {
        if (value <= min) return 0
        if (value >= max) return STEPS
        return ((ln(value / min) / ln(max / min)) * STEPS).roundToInt().coerceIn(0, STEPS)
    }
}
