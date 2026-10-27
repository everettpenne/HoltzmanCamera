package app.grapheneos.camera.suite.ix

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.everettpenne.ix.IxCaptureInfo
import com.everettpenne.ix.IxOutput
import com.everettpenne.ix.IxStyle
import com.everettpenne.ix.IxStylePreset
import com.everettpenne.ix.IxWarning
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IxAdapterInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun aBitmapGoesThroughIxAndComesBackFromMediaStore() {
        val bitmap = Bitmap.createBitmap(16, 12, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val source = IxSources.fromBitmap(bitmap)
        bitmap.recycle()

        val result = IxPipeline(context).process(
            source = source,
            capture = IxCaptureInfo(timestampMillis = 0L, orientationDegrees = 0),
            output = IxOutput.JPEG,
            target = IxTarget.MediaStore("DCIM/HoltzmanTest"),
        )

        assertEquals(1, result.written.size)
        val uri = result.written.single().uri
        try {
            assertEquals("image/jpeg", context.contentResolver.getType(uri))
            val back = IxSources.from(context, uri)
            assertEquals(source.width, back.width)
            assertEquals(source.height, back.height)
            assertArrayEquals(source.bytes, back.bytes)
        } finally {
            context.contentResolver.delete(uri, null, null)
        }
    }

    @Test
    fun aRequestedStyleIsReportedAndTheFileIsStillWritten() {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        val source = IxSources.fromBitmap(bitmap)
        bitmap.recycle()

        val result = IxPipeline(context).process(
            source = source,
            capture = IxCaptureInfo(timestampMillis = 0L),
            output = IxOutput.JPEG,
            style = IxStyle(IxStylePreset.WARM, strength = 0.5f),
            target = IxTarget.MediaStore("DCIM/HoltzmanTest"),
            baseName = "IMG_test",
        )

        val uri = result.written.single().uri
        try {
            assertTrue(result.result.warnings.any { it is IxWarning.StyleSkipped })
            assertEquals("IMG_test.jpg", result.written.single().file.name)
        } finally {
            context.contentResolver.delete(uri, null, null)
        }
    }

    @Test
    fun zeroPaddingAfterAnEoiMarkerIsRemoved() {
        val padded = byteArrayOf(1, 2, 0xFF.toByte(), 0xD9.toByte(), 0, 0)
        assertArrayEquals(padded.copyOf(4), IxSources.withoutTrailingPadding(padded))

        val realData = byteArrayOf(1, 2, 0, 0)
        assertArrayEquals(realData, IxSources.withoutTrailingPadding(realData))
    }
}
