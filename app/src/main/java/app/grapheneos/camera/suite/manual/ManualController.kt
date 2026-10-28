package app.grapheneos.camera.suite.manual

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.RggbChannelVector
import android.util.Log
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.Camera

/**
 * Drives the Camera2 interop request options for Manual mode.
 *
 * The interop control is owned by the currently bound camera; [attach] is called after every
 * (re)bind, and [apply] is a no-op until it has succeeded. Everything is main-thread only.
 */
class ManualController {
    private companion object {
        const val TAG = "ManualController"
        const val DEFAULT_ISO = 100
        const val DEFAULT_EXPOSURE_NS = 16_666_667L // 1/60 s
        const val DEFAULT_WB_K = 5500
    }

    var ranges: ManualRanges? = null
        private set
    var state: ManualState? = null
        private set
    val isAttached: Boolean get() = control != null

    private var control: Camera2CameraControl? = null

    fun attach(camera: Camera): Boolean {
        return try {
            val info = Camera2CameraInfo.from(camera.cameraInfo)
            val isoRange = info.getCameraCharacteristic(
                CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE
            )
            val exposureRange = info.getCameraCharacteristic(
                CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE
            )
            val focusMax = info.getCameraCharacteristic(
                CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE
            ) ?: 0f
            val awbModes = info.getCameraCharacteristic(
                CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES
            )
            val hasWb = awbModes == null ||
                awbModes.contains(CaptureRequest.CONTROL_AWB_MODE_OFF)

            val nextRanges = ManualRanges(
                isoMin = isoRange?.lower ?: 100,
                isoMax = isoRange?.upper ?: 3200,
                exposureMinNs = exposureRange?.lower ?: 125_000L,
                exposureMaxNs = exposureRange?.upper ?: 66_666_667L,
                focusMaxDiopters = focusMax,
                whiteBalanceAvailable = hasWb,
            )

            control = Camera2CameraControl.from(camera.cameraControl)
            ranges = nextRanges
            state = defaults(nextRanges)
            true
        } catch (t: Throwable) {
            // A non-Camera2 implementation, or a vendor that rejects interop: leave Manual mode
            // visible but inert instead of crashing the camera.
            Log.w(TAG, "Manual controls unavailable", t)
            control = null
            ranges = null
            state = null
            false
        }
    }

    fun setEnabled(enabled: Boolean): Boolean {
        val current = state ?: return false
        state = current.copy(enabled = enabled)
        return apply()
    }

    fun updateIso(iso: Int): Boolean {
        val current = state ?: return false
        val range = ranges ?: return false
        state = current.copy(enabled = true, iso = iso.coerceIn(range.isoMin, range.isoMax))
        return apply()
    }

    fun updateExposure(ns: Long): Boolean {
        val current = state ?: return false
        val range = ranges ?: return false
        state = current.copy(
            enabled = true,
            exposureNs = ns.coerceIn(range.exposureMinNs, range.exposureMaxNs),
        )
        return apply()
    }

    fun updateFocus(diopters: Float): Boolean {
        val current = state ?: return false
        val range = ranges ?: return false
        state = current.copy(
            enabled = true,
            focusDiopters = diopters.coerceIn(0f, range.focusMaxDiopters),
        )
        return apply()
    }

    fun updateWhiteBalance(kelvin: Int): Boolean {
        val current = state ?: return false
        val range = ranges ?: return false
        state = current.copy(
            enabled = true,
            whiteBalanceK = kelvin.coerceIn(range.whiteBalanceMinK, range.whiteBalanceMaxK),
        )
        return apply()
    }

    fun clear(): Boolean {
        val current = state ?: return false
        state = current.copy(enabled = false)
        return try {
            control?.clearCaptureRequestOptions()
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to clear manual controls", t)
            false
        }
    }

    private fun apply(): Boolean {
        val current = state ?: return false
        val cameraControl = control ?: return false
        if (!current.enabled) {
            return try {
                cameraControl.clearCaptureRequestOptions()
                true
            } catch (t: Throwable) {
                Log.w(TAG, "Unable to restore auto controls", t)
                false
            }
        }

        val range = ranges ?: return false
        val gains = ManualMath.kelvinToRggb(current.whiteBalanceK)
        return try {
            val builder = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_OFF,
                )
                .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, current.iso)
                .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, current.exposureNs)
                .setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_OFF,
                )

            if (range.hasFocus) {
                builder.setCaptureRequestOption(
                    CaptureRequest.LENS_FOCUS_DISTANCE,
                    current.focusDiopters,
                )
            }

            if (range.whiteBalanceAvailable) {
                builder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AWB_MODE,
                    CaptureRequest.CONTROL_AWB_MODE_OFF,
                )
                builder.setCaptureRequestOption(
                    CaptureRequest.COLOR_CORRECTION_MODE,
                    CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX,
                )
                builder.setCaptureRequestOption(
                    CaptureRequest.COLOR_CORRECTION_GAINS,
                    RggbChannelVector(gains[0], gains[1], gains[2], gains[3]),
                )
            }

            cameraControl.setCaptureRequestOptions(builder.build())
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to apply manual controls", t)
            false
        }
    }

    private fun defaults(range: ManualRanges) = ManualState(
        enabled = false,
        iso = DEFAULT_ISO.coerceIn(range.isoMin, range.isoMax),
        exposureNs = DEFAULT_EXPOSURE_NS.coerceIn(range.exposureMinNs, range.exposureMaxNs),
        focusDiopters = 0f,
        whiteBalanceK = DEFAULT_WB_K.coerceIn(range.whiteBalanceMinK, range.whiteBalanceMaxK),
    )
}
