package app.grapheneos.camera.suite.ix

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.net.Uri
import androidx.camera.core.ImageProxy
import com.everettpenne.ix.IxCaptureInfo
import com.everettpenne.ix.IxLensFacing
import com.everettpenne.ix.IxSource
import com.everettpenne.ix.IxSourceFormat
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * The Android side of the Ix boundary: turn what the camera and the gallery give us into the pure
 * [IxSource] the engine understands.
 *
 * Nothing here decodes or re-encodes except [fromBitmap], which has to produce bytes from a
 * `Bitmap`. A JPEG from CameraX is passed through byte-for-byte, minus any trailing zero padding;
 * the engine receives exactly what the camera wrote.
 */
object IxSources {

    /** A JPEG that is already in memory, e.g. after upstream's EXIF processing. */
    fun fromJpegBytes(bytes: ByteArray, width: Int, height: Int): IxSource =
        IxSource(withoutTrailingPadding(bytes), IxSourceFormat.JPEG, width, height)

    /**
     * A `Bitmap` from the gallery or a share. JPEG is the only honest common format here; the
     * quality is high enough that this path is for editing inputs, not for the capture pipeline.
     */
    fun fromBitmap(bitmap: Bitmap, quality: Int = 95): IxSource {
        require(quality in 1..100) { "JPEG quality must be 1..100, was $quality" }
        val out = ByteArrayOutputStream()
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
            throw IOException("the bitmap could not be encoded as JPEG")
        }
        return IxSource(out.toByteArray(), IxSourceFormat.JPEG, bitmap.width, bitmap.height)
    }

    /**
     * A JPEG or DNG from a content URI. Dimensions are read with [BitmapFactory]; a format it
     * cannot measure is refused rather than guessed.
     */
    fun from(context: Context, uri: Uri): IxSource {
        val resolver = context.contentResolver
        val format = when (val mime = resolver.getType(uri)?.lowercase()) {
            "image/jpeg" -> IxSourceFormat.JPEG
            "image/x-adobe-dng", "image/dng" -> IxSourceFormat.RAW
            else -> throw IllegalArgumentException("unsupported Ix source type: $mime")
        }
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("could not open $uri")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("could not measure $uri")
        }
        return IxSource(bytes, format, bounds.outWidth, bounds.outHeight)
    }

    /**
     * The capture buffer. CameraX gives JPEG (including Ultra HDR) or RAW here; a YUV buffer is
     * refused because converting it belongs to the encoder, not to the boundary.
     */
    fun from(image: ImageProxy): IxSource {
        val format = when (image.format) {
            ImageFormat.JPEG -> IxSourceFormat.JPEG
            ImageFormat.RAW_SENSOR -> IxSourceFormat.RAW
            else -> throw IllegalArgumentException(
                "unsupported ImageProxy format ${image.format}; Ix expects JPEG or RAW",
            )
        }
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val payload = if (format == IxSourceFormat.JPEG) withoutTrailingPadding(bytes) else bytes
        return IxSource(payload, format, image.width, image.height)
    }

    /** The facts Ix records: rotation comes from the proxy; the caller supplies the rest. */
    fun captureInfo(
        image: ImageProxy,
        timestampMillis: Long = System.currentTimeMillis(),
        lensFacing: IxLensFacing = IxLensFacing.BACK,
        iso: Int? = null,
        exposureNanos: Long? = null,
    ): IxCaptureInfo = IxCaptureInfo(
        timestampMillis = timestampMillis,
        orientationDegrees = image.imageInfo.rotationDegrees,
        lensFacing = lensFacing,
        iso = iso,
        exposureNanos = exposureNanos,
    )

    /**
     * JPEGs from some HALs end with zero padding. Only trim when the last non-zero byte is an EOI
     * marker; anything else is treated as image data.
     */
    internal fun withoutTrailingPadding(jpeg: ByteArray): ByteArray {
        val length = jpeg.indexOfLast { it != 0.toByte() } + 1
        if (length == jpeg.size) return jpeg
        if (length < 2 || jpeg[length - 2] != 0xFF.toByte() || jpeg[length - 1] != 0xD9.toByte()) return jpeg
        return jpeg.copyOf(length)
    }
}
