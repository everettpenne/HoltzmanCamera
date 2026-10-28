package app.grapheneos.camera.suite.uno

import app.grapheneos.camera.CameraMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HoltzmanLinksTest {
    @Test
    fun parsesTheFourShortcutHosts() {
        assertEquals(HoltzmanDestination.PHOTO, HoltzmanLinks.parse("holtzman://photo"))
        assertEquals(HoltzmanDestination.SELFIE, HoltzmanLinks.parse("holtzman://selfie"))
        assertEquals(HoltzmanDestination.SCAN, HoltzmanLinks.parse("holtzman://scan"))
        assertEquals(HoltzmanDestination.MANUAL, HoltzmanLinks.parse("holtzman://manual"))
        assertEquals(HoltzmanDestination.VIDEO, HoltzmanLinks.parse("holtzman://video"))
    }

    @Test
    fun destinationCarriesTheModeAndLens() {
        assertEquals(CameraMode.CAMERA, HoltzmanDestination.PHOTO.mode)
        assertEquals(CameraMode.CAMERA, HoltzmanDestination.SELFIE.mode)
        assertEquals(CameraMode.QR_SCAN, HoltzmanDestination.SCAN.mode)
        assertEquals(CameraMode.VIDEO, HoltzmanDestination.VIDEO.mode)
    }

    @Test
    fun acceptsTrailingSlashQueryAndCase() {
        assertEquals(HoltzmanDestination.PHOTO, HoltzmanLinks.parse(" HOLTZMAN://PHOTO/ "))
        assertEquals(HoltzmanDestination.SCAN, HoltzmanLinks.parse("holtzman://scan?from=shortcut"))
        assertEquals(HoltzmanDestination.VIDEO, HoltzmanLinks.parse("holtzman://video#top"))
    }

    @Test
    fun rejectsAnotherSchemeHostOrGarbage() {
        assertNull(HoltzmanLinks.parse(null))
        assertNull(HoltzmanLinks.parse(""))
        assertNull(HoltzmanLinks.parse("https://photo"))
        assertNull(HoltzmanLinks.parse("holtzman://unknown"))
        assertNull(HoltzmanLinks.parse("holtzman://photo/extra"))
        assertNull(HoltzmanLinks.parse("not a uri"))
    }

    @Test
    fun uriRoundTrips() {
        HoltzmanDestination.entries.forEach {
            assertEquals(it, HoltzmanLinks.parse(HoltzmanLinks.uri(it)))
        }
    }
}
