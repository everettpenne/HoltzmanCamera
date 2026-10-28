package app.grapheneos.camera.suite.uno

import android.content.Intent
import android.content.pm.ShortcutManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HoltzmanShortcutsTest {
    @Test
    fun publishesFourShortcutsWithPackageScopedViewIntents() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        HoltzmanShortcuts.publish(context)

        val shortcuts = context.getSystemService(ShortcutManager::class.java).dynamicShortcuts
        assertEquals(setOf("photo", "selfie", "scan", "manual"), shortcuts.map { it.id }.toSet())

        shortcuts.forEach { shortcut ->
            val intent = shortcut.intent
            assertEquals(context.packageName, intent?.`package`)
            assertEquals(Intent.ACTION_VIEW, intent?.action)
            assertTrue(intent?.data.toString().startsWith("holtzman://"))
        }
    }
}
