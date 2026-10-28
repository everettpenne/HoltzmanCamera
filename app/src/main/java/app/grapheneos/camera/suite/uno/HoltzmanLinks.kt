package app.grapheneos.camera.suite.uno

import androidx.camera.core.CameraSelector
import app.grapheneos.camera.CameraMode

/**
 * The contract between a launcher shortcut/deep link and a camera mode.
 *
 * Pure data: the parser takes a string so it can be covered by app-side JVM tests, and the
 * manifest filter is deliberately only `holtzman://`; this object decides which hosts exist.
 * `manual` is added when D3 lands; see docs/integration.md.
 */
enum class HoltzmanDestination(
    val host: String,
    val mode: CameraMode,
    val lensFacing: Int,
) {
    PHOTO("photo", CameraMode.CAMERA, CameraSelector.LENS_FACING_BACK),
    SELFIE("selfie", CameraMode.CAMERA, CameraSelector.LENS_FACING_FRONT),
    SCAN("scan", CameraMode.QR_SCAN, CameraSelector.LENS_FACING_BACK),
    VIDEO("video", CameraMode.VIDEO, CameraSelector.LENS_FACING_BACK),
}

object HoltzmanLinks {
    const val SCHEME = "holtzman"

    fun uri(destination: HoltzmanDestination): String = "$SCHEME://${destination.host}"

    /** Accepts `holtzman://photo`, a trailing slash, a query, or upper case; rejects the rest. */
    fun parse(raw: String?): HoltzmanDestination? {
        val value = raw?.trim().orEmpty()
        if (!value.startsWith("$SCHEME://", ignoreCase = true)) return null

        val host = value
            .substring(SCHEME.length + 3)
            .substringBefore('?')
            .substringBefore('#')
            .trim('/')
            .lowercase()

        return HoltzmanDestination.entries.firstOrNull { it.host == host }
    }
}
