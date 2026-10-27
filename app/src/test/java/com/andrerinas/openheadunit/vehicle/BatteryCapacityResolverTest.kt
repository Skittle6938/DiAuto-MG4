package com.andrerinas.openheadunit.vehicle

import com.andrerinas.openheadunit.vehicle.BatteryCapacityResolver.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryCapacityResolverTest {

    private fun resolve(
        manualNet: Float? = null,
        manualSoh: Float? = null,
        car: Float? = null,
        dash: Mg4DashBattery.Reading? = null,
    ) = BatteryCapacityResolver.resolve(manualNet, manualSoh, car, dash)

    @Test
    fun `without any source the 64 kWh battery and 100 percent are used`() {
        val r = resolve()
        assertEquals(61.7f, r.netKwh, 0.001f)
        assertEquals(Source.DEFAULT, r.netSource)
        assertEquals(64, r.pack)
        assertEquals(100f, r.sohPercent, 0.001f)
        assertEquals(Source.DEFAULT, r.sohSource)
    }

    @Test
    fun `a capacity published by the car wins over MG4 Dash`() {
        val dash = Mg4DashBattery.Reading(pack = 51, packChosen = true, measuredKwh = 47f, sohPercent = 95.9f)
        val r = resolve(car = 64f, dash = dash)
        assertEquals(61.7f, r.netKwh, 0.001f)
        assertEquals(Source.CAR, r.netSource)
        assertEquals(Source.MG4DASH, r.sohSource)
        assertEquals(95.9f, r.sohPercent, 0.001f)
    }

    @Test
    fun `nameplate figures from the car become usable ones, other figures are kept`() {
        assertEquals(50.8f, BatteryCapacityResolver.carUsableKwh(51f)!!, 0.001f)
        assertEquals(74.4f, BatteryCapacityResolver.carUsableKwh(77.2f)!!, 0.001f)
        assertEquals(58.0f, BatteryCapacityResolver.carUsableKwh(58f)!!, 0.001f)
        assertNull(BatteryCapacityResolver.carUsableKwh(0f))
        assertNull(BatteryCapacityResolver.carUsableKwh(0.064f))
        assertNull(BatteryCapacityResolver.carUsableKwh(Float.NaN))
    }

    @Test
    fun `the battery MG4 Dash knows is used when the car says nothing`() {
        val dash = Mg4DashBattery.Reading(pack = 51, packChosen = false, measuredKwh = 47f, sohPercent = 95.9f)
        val r = resolve(dash = dash)
        assertEquals(50.8f, r.netKwh, 0.001f)
        assertEquals(Source.MG4DASH, r.netSource)
        assertEquals(51, r.pack)
        assertEquals(50.8f * 0.959f, r.effectiveKwh, 0.01f)
    }

    @Test
    fun `MG4 Dash without a measurement leaves the health at 100 percent`() {
        val dash = Mg4DashBattery.Reading(pack = 77, packChosen = true, measuredKwh = null, sohPercent = null)
        val r = resolve(dash = dash)
        assertEquals(74.4f, r.netKwh, 0.001f)
        assertEquals(100f, r.sohPercent, 0.001f)
        assertEquals(Source.DEFAULT, r.sohSource)
    }

    @Test
    fun `settings forced by hand win over everything`() {
        val dash = Mg4DashBattery.Reading(pack = 64, packChosen = true, measuredKwh = 60f, sohPercent = 97.2f)
        val r = resolve(manualNet = 50.8f, manualSoh = 98.75f, car = 64f, dash = dash)
        assertEquals(50.8f, r.netKwh, 0.001f)
        assertEquals(Source.MANUAL, r.netSource)
        assertEquals(98.75f, r.sohPercent, 0.001f)
        assertEquals(Source.MANUAL, r.sohSource)
    }

    @Test
    fun `the nearest battery is picked from a usable capacity`() {
        assertEquals(51, BatteryCapacityResolver.nearestPack(46.5f))
        assertEquals(51, BatteryCapacityResolver.nearestPack(55.5f))
        assertEquals(64, BatteryCapacityResolver.nearestPack(57f))
        assertEquals(77, BatteryCapacityResolver.nearestPack(70f))
    }
}
