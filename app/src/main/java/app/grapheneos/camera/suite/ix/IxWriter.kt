package app.grapheneos.camera.suite.ix

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.WorkerThread
import app.grapheneos.camera.CamConfig
import app.grapheneos.camera.util.getTreeDocumentUri
import com.everettpenne.ix.IxFile
import com.everettpenne.ix.IxResult
import java.io.IOException

/** Where an [IxResult] should be written. */
sealed interface IxTarget {
    /** The MediaStore, under [relativePath] (for example `DCIM/Holtzman`). */
    data class MediaStore(val relativePath: String = IxWriter.DEFAULT_RELATIVE_PATH) : IxTarget

    /** A directory the person granted through the Storage Access Framework. */
    data class Directory(val treeUri: Uri) : IxTarget
}

/** One file that was written, paired with the descriptor Ix returned. */
data class WrittenIxFile(val uri: Uri, val file: IxFile)

/**
 * Writes Ix's files. This is the only adapter that touches storage, and it uses the same
 * MediaStore/SAF rules as upstream's ImageSaver: insert pending, write, then clear pending.
 */
object IxWriter {
    /** The app's own folder; the suite's photos are not mixed into DCIM/Camera. */
    const val DEFAULT_RELATIVE_PATH: String = "DCIM/Holtzman"

    private const val TAG = "IxWriter"

    /**
     * Writes every file in [result]. If any write fails, files already written by this call are
     * deleted, so a RAW+JPEG pair is not left half-saved.
     */
    @WorkerThread
    fun write(context: Context, result: IxResult, target: IxTarget): List<WrittenIxFile> {
        require(result.files.isNotEmpty()) { "nothing to write" }
        val written = mutableListOf<WrittenIxFile>()
        try {
            result.files.forEach { file ->
                written += when (target) {
                    is IxTarget.MediaStore -> writeToMediaStore(context, file, target.relativePath)
                    is IxTarget.Directory -> writeToDirectory(context, file, target.treeUri)
                }
            }
            return written
        } catch (e: Exception) {
            written.forEach { delete(context, it.uri) }
            throw e
        }
    }

    private fun writeToMediaStore(context: Context, file: IxFile, relativePath: String): WrittenIxFile {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, file.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(CamConfig.imageCollectionUri, values)
            ?: throw IOException("MediaStore refused ${file.name}")
        try {
            resolver.openOutputStream(uri)?.use { it.write(file.bytes) }
                ?: throw IOException("could not open $uri")
            val finished = resolver.update(uri, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null)
            if (finished <= 0) throw IOException("could not finish ${file.name}")
        } catch (e: Exception) {
            delete(context, uri)
            throw e
        }
        return WrittenIxFile(uri, file)
    }

    private fun writeToDirectory(context: Context, file: IxFile, treeUri: Uri): WrittenIxFile {
        val resolver = context.contentResolver
        val documentUri = DocumentsContract.createDocument(
            resolver, getTreeDocumentUri(treeUri), file.mimeType, file.name,
        ) ?: throw IOException("the folder refused ${file.name}")
        try {
            resolver.openOutputStream(documentUri)?.use { it.write(file.bytes) }
                ?: throw IOException("could not open $documentUri")
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, documentUri) }
            throw e
        }
        return WrittenIxFile(documentUri, file)
    }

    private fun delete(context: Context, uri: Uri) {
        runCatching { context.contentResolver.delete(uri, null, null) }
            .onFailure { Log.w(TAG, "could not delete incomplete $uri", it) }
    }
}
