package com.andrerinas.openheadunit.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarDataSensorsTest {

    @Test
    fun `charge is sent in whole percent with the car's range`() {
        assertEquals(CarDataSensors.Fuel(levelPercent = 73, rangeKm = 251, low = false), CarDataSensors.fuel(72.6, 251))
    }

    @Test
    fun `out of range readings are clamped`() {
        assertEquals(CarDataSensors.Fuel(100, 0, false), CarDataSensors.fuel(104.0, -5))
        assertEquals(CarDataSensors.Fuel(0, 0, true), CarDataSensors.fuel(-2.0, 0))
        assertNull(CarDataSensors.fuel(Double.NaN, 100))
    }

    @Test
    fun `low battery below 10 percent`() {
        assertTrue(CarDataSensors.fuel(9.9, 30)!!.low)
        assertFalse(CarDataSensors.fuel(10.0, 32)!!.low)
    }

    @Test
    fun `odometer goes in tenths of a km`() {
        assertEquals(123456, CarDataSensors.odometerTenths(12345.6f))
        assertNull(CarDataSensors.odometerTenths(null))
        assertNull(CarDataSensors.odometerTenths(-1f))
        assertNull(CarDataSensors.odometerTenths(Float.NaN))
    }

    @Test
    fun `sent at once after the start, then every 5 s`() {
        assertTrue(CarDataSensors.due(started = true, lastSentMs = 0L, nowMs = 1_000L))
        assertFalse(CarDataSensors.due(started = true, lastSentMs = 10_000L, nowMs = 14_999L))
        assertTrue(CarDataSensors.due(started = true, lastSentMs = 10_000L, nowMs = 15_000L))
        assertFalse(CarDataSensors.due(started = false, lastSentMs = 0L, nowMs = 60_000L))
    }

    @Test
    fun `only fuel level and odometer are car data sensors`() {
        assertTrue(CarDataSensors.isCarDataType(6))
        assertTrue(CarDataSensors.isCarDataType(5))
        assertFalse(CarDataSensors.isCarDataType(23))
        assertFalse(CarDataSensors.isCarDataType(10))
    }
}
