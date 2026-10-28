package app.grapheneos.camera.suite.uno

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.grapheneos.camera.R

/**
 * Dynamic shortcuts instead of a static shortcuts.xml: a static `<intent>` needs a target package,
 * and the package differs between the debug (.dev) and release builds. Dynamic intents are built
 * with `packageName`, so both variants work and Uno sees exactly four shortcuts.
 */
object HoltzmanShortcuts {
    private const val TAG = "HoltzmanShortcuts"

    private data class Entry(
        val destination: HoltzmanDestination,
        val icon: Int,
        val shortLabel: Int,
        val longLabel: Int,
    )

    private val entries = listOf(
        Entry(HoltzmanDestination.PHOTO, R.drawable.shortcut_photo,
            R.string.shortcut_photo, R.string.shortcut_photo_long),
        Entry(HoltzmanDestination.SELFIE, R.drawable.flip_camera,
            R.string.shortcut_selfie, R.string.shortcut_selfie_long),
        Entry(HoltzmanDestination.SCAN, R.drawable.shortcut_scan,
            R.string.shortcut_scan, R.string.shortcut_scan_long),
        Entry(HoltzmanDestination.VIDEO, R.drawable.shortcut_video,
            R.string.shortcut_video, R.string.shortcut_video_long),
    )

    fun publish(context: Context) {
        val shortcuts = entries.mapIndexed { rank, entry ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(HoltzmanLinks.uri(entry.destination)))
                .setPackage(context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            ShortcutInfoCompat.Builder(context, entry.destination.host)
                .setShortLabel(context.getString(entry.shortLabel))
                .setLongLabel(context.getString(entry.longLabel))
                .setIcon(IconCompat.createWithResource(context, entry.icon))
                .setIntent(intent)
                .setRank(rank)
                .build()
        }

        try {
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        } catch (e: Exception) {
            // A launcher without shortcut support must not take the camera down with it.
            Log.w(TAG, "Unable to publish shortcuts", e)
        }
    }
}
