package app.grapheneos.camera.suite.manual

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualMathTest {
    @Test
    fun isoUsesTheWholeRangeAndRoundTrips() {
        assertEquals(100, ManualMath.progressToIso(0, 100, 3200))
        assertEquals(3200, ManualMath.progressToIso(ManualMath.STEPS, 100, 3200))
        assertEquals(ManualMath.STEPS, ManualMath.isoToProgress(3200, 100, 3200))
        assertEquals(0, ManualMath.isoToProgress(100, 100, 3200))
    }

    @Test
    fun exposureUsesTheWholeRangeAndRoundTrips() {
        val min = 16_666_667L
        val max = 1_000_000_000L
        assertEquals(min, ManualMath.progressToExposure(0, min, max))
        assertEquals(max, ManualMath.progressToExposure(ManualMath.STEPS, min, max))
        assertEquals(ManualMath.STEPS, ManualMath.exposureToProgress(max, min, max))
        assertEquals(0, ManualMath.exposureToProgress(min, min, max))
    }

    @Test
    fun focusAndWhiteBalanceClamp() {
        assertEquals(0f, ManualMath.progressToFocus(0, 10f))
        assertEquals(10f, ManualMath.progressToFocus(ManualMath.STEPS, 10f))
        assertEquals(2500, ManualMath.progressToWhiteBalance(0, 2500, 8000))
        assertEquals(8000, ManualMath.progressToWhiteBalance(ManualMath.STEPS, 2500, 8000))
        assertEquals(ManualMath.STEPS, ManualMath.whiteBalanceToProgress(8000, 2500, 8000))
    }

    @Test
    fun labelsAreReadable() {
        assertEquals("1/60 s", ManualMath.exposureLabel(16_666_667L))
        assertEquals("2.0 s", ManualMath.exposureLabel(2_000_000_000L))
        assertEquals("∞", ManualMath.focusLabel(0f))
        assertEquals("1.00 m", ManualMath.focusLabel(1f))
        assertEquals("ISO 400", ManualMath.isoLabel(400))
        assertEquals("5500 K", ManualMath.whiteBalanceLabel(5500))
    }

    @Test
    fun whiteBalanceGainsAreFiniteAndPlausible() {
        for (kelvin in listOf(2000, 2500, 3200, 4500, 5500, 6500, 8000, 10000)) {
            val gains = ManualMath.kelvinToRggb(kelvin)
            assertEquals(4, gains.size)
            gains.forEach {
                assertTrue("$kelvin K: $it", it.isFinite())
                assertTrue("$kelvin K: $it", it in 0.25f..4f)
            }
        }
    }
}
