package app.grapheneos.camera.suite.ix

import android.util.Log
import app.grapheneos.camera.BuildConfig
import com.everettpenne.ix.IxCaptureInfo
import com.everettpenne.ix.IxFileKind
import com.everettpenne.ix.IxLensFacing
import com.everettpenne.ix.IxNaming
import com.everettpenne.ix.IxOutput
import com.everettpenne.ix.IxRequest
import com.everettpenne.ix.IxResult
import com.everettpenne.ix.IxStyle
import com.everettpenne.ix.PassThroughIxEngine

/**
 * The first, deliberately small capture hook: in debug builds only, the final JPEG from upstream's
 * ImageSaver takes one pass through Ix before it is written.
 *
 * The contract is fail-open: if Ix throws, returns no file, or returns anything other than one
 * JPEG, the caller keeps upstream's bytes. A debug experiment must never lose a photo.
 */
object IxJpegDebug {
    private const val TAG = "IxJpegDebug"

    /** Debug builds only; release builds never enter the engine from the capture path. */
    @Volatile
    var enabled: Boolean = BuildConfig.DEBUG

    private val engine = PassThroughIxEngine()

    /**
     * Returns the JPEG Ix produced, or null when the caller should keep the original bytes.
     * [width] and [height] are the decoded image dimensions, not the preview dimensions.
     */
    fun render(
        jpeg: ByteArray,
        width: Int,
        height: Int,
        orientationDegrees: Int,
        lensFacing: IxLensFacing,
        timestampMillis: Long,
        style: IxStyle? = null,
    ): ByteArray? {
        if (!enabled) return null
        if (width <= 0 || height <= 0) return null
        return try {
            val source = IxSources.fromJpegBytes(jpeg, width, height)
            val result: IxResult = engine.render(IxRequest(
                source = source,
                capture = IxCaptureInfo(
                    timestampMillis = timestampMillis,
                    orientationDegrees = orientationDegrees,
                    lensFacing = lensFacing,
                ),
                output = IxOutput.JPEG,
                style = style,
                baseName = IxNaming.baseName(timestampMillis),
            ))
            if (result.warnings.isNotEmpty()) {
                Log.w(TAG, "Ix warnings: ${result.warnings.joinToString { it.message }}")
            }
            val file = result.files.singleOrNull()
            if (file == null || file.kind != IxFileKind.JPEG) {
                Log.w(TAG, "Ix returned ${result.files.size} files; keeping upstream bytes")
                null
            } else {
                Log.d(TAG, "Ix pass ${engine.version}: ${jpeg.size} -> ${file.bytes.size} bytes")
                file.bytes
            }
        } catch (t: Throwable) {
            // A debug hook is never allowed to turn a capture into a crash.
            Log.w(TAG, "Ix pass failed; keeping upstream bytes", t)
            null
        }
    }
}
