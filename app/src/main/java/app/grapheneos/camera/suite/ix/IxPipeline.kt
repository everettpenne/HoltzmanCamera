package app.grapheneos.camera.suite.ix

import android.content.Context
import androidx.annotation.WorkerThread
import androidx.camera.core.ImageProxy
import com.everettpenne.ix.IxCaptureInfo
import com.everettpenne.ix.IxEngine
import com.everettpenne.ix.IxNaming
import com.everettpenne.ix.IxOutput
import com.everettpenne.ix.IxRequest
import com.everettpenne.ix.IxResult
import com.everettpenne.ix.IxSource
import com.everettpenne.ix.IxStyle
import com.everettpenne.ix.PassThroughIxEngine

/** The result of one end-to-end pass: what Ix made, and where the adapter put it. */
data class IxProcessResult(val result: IxResult, val written: List<WrittenIxFile>)

/**
 * The app-side bridge: capture → Ix → storage, in one call.
 *
 * It is deliberately blocking and annotated [WorkerThread]. The camera already has a writer
 * executor; call this there rather than on the main thread. It does not decide the capture mode,
 * the style or the destination for the user — the caller does, which is what keeps the engine
 * independent of the camera.
 */
class IxPipeline(context: Context, private val engine: IxEngine = PassThroughIxEngine()) {
    private val appContext: Context = context.applicationContext

    /** Runs [engine] on [source] and writes every file it returns to [target]. */
    @WorkerThread
    fun process(
        source: IxSource,
        capture: IxCaptureInfo,
        output: IxOutput,
        style: IxStyle? = null,
        target: IxTarget = IxTarget.MediaStore(),
        baseName: String = IxNaming.baseName(capture.timestampMillis),
    ): IxProcessResult {
        val result = engine.render(IxRequest(
            source = source,
            capture = capture,
            output = output,
            style = style,
            baseName = baseName,
        ))
        val written = if (result.files.isEmpty()) emptyList() else IxWriter.write(appContext, result, target)
        return IxProcessResult(result, written)
    }

    /** Convenience for a CameraX buffer; writes to the app's MediaStore folder by default. */
    @WorkerThread
    fun process(
        image: ImageProxy,
        capture: IxCaptureInfo,
        output: IxOutput,
        style: IxStyle? = null,
        target: IxTarget = IxTarget.MediaStore(),
    ): IxProcessResult = process(IxSources.from(image), capture, output, style, target)
}
