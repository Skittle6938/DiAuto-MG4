package com.andrerinas.openheadunit.vehicle

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.Config

// org.json comes from Android.
@ConscryptMode(ConscryptMode.Mode.OFF)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33], manifest = Config.NONE)
class ConsumptionMapFitTest {

    /** MG4Dash's export: cells [speed bin, temp bin, km, kWh, n, squares]. */
    private fun map(vararg cells: List<Number>) =
        """{"version":1,"speedStep":10,"tempMin":-20,"tempStep":5,"cells":[${cells.joinToString(",") { it.joinToString(",", "[", "]") }}]}"""

    /** kWh/100 km of the fitted road load at [kmh]. */
    private fun per100(load: ConsumptionMapFit.RoadLoad, kmh: Float): Float {
        val v = kmh / 3.6f
        return (load.a + load.b * v + load.c * v * v) / 36f
    }

    @Test
    fun thinMapKeepsTheCarsAverage() {
        assertNull(ConsumptionMapFit.fit(map(listOf(5, 8, 5, 0.75, 5, 0)), 20f))
    }

    @Test
    fun curveFollowsMeasuredSpeeds() {
        // 20 °C = temp bin 8. 14, 13, 15, 18, 22 kWh/100 km at 35, 55, 75, 105, 125 km/h.
        val json = map(
            listOf(3, 8, 20, 2.8, 20, 0), listOf(5, 8, 20, 2.6, 20, 0), listOf(7, 8, 20, 3.0, 20, 0),
            listOf(10, 8, 20, 3.6, 20, 0), listOf(12, 8, 20, 4.4, 20, 0),
        )
        val load = ConsumptionMapFit.fit(json, 21f)!!
        assertEquals(5, load.points)
        assertTrue(load.c > 0f)
        assertEquals(22f, per100(load, 125f), 1.5f)
        assertEquals(15f, per100(load, 75f), 1.5f)
    }

    @Test
    fun missingSpeedTakenFromNearbyTemperature() {
        // 50 km/h only measured at 10 °C (bin 6), asked at 20 °C (bin 8): still used. 0 °C (bin 4) is too far.
        val json = map(listOf(5, 6, 20, 3.0, 20, 0), listOf(9, 4, 20, 5.0, 20, 0))
        val load = ConsumptionMapFit.fit(json, 20f)!!
        assertEquals(1, load.points)
        assertEquals(15f, per100(load, 50f), 0.1f)
    }
}
