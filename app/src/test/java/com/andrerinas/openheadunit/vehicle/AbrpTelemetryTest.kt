package com.andrerinas.openheadunit.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AbrpTelemetryTest {

    @Test
    fun `json carries ABRP's field names and leaves out what the car did not give`() {
        val json = AbrpTelemetry.json(
            AbrpSample(utc = 1_700_000_000L, soc = 61.04, powerKw = 12.346, speedKmh = 87.0,
                lat = 45.1234567, lon = 4.7654321, isCharging = false, extTempC = 8.5)
        )
        assertEquals(
            "{\"utc\":1700000000,\"soc\":61,\"power\":12.35,\"speed\":87,\"lat\":45.123457," +
                "\"lon\":4.765432,\"is_charging\":false,\"ext_temp\":8.5}",
            json
        )
    }

    @Test
    fun `a reading without the charge is not sent`() {
        assertFalse(AbrpTelemetry.worthSending(AbrpSample(utc = 1L, soc = null)))
        assertTrue(AbrpTelemetry.worthSending(AbrpSample(utc = 1L, soc = 50.0)))
    }

    @Test
    fun `charging needs the cable in and power going into the pack`() {
        assertEquals(true, AbrpTelemetry.isCharging(chargePortConnected = true, powerKw = -7.2))
        assertEquals(false, AbrpTelemetry.isCharging(chargePortConnected = true, powerKw = 0.1))
        // Regeneration without a cable is not a charge.
        assertEquals(false, AbrpTelemetry.isCharging(chargePortConnected = false, powerKw = -20.0))
        assertNull(AbrpTelemetry.isCharging(chargePortConnected = null, powerKw = -7.2))
    }

    @Test
    fun `each reading gets up to 3 s of offset`() {
        assertEquals(0L, AbrpSchedule.jitterMs(0.0))
        assertEquals(1_500L, AbrpSchedule.jitterMs(0.5))
        assertEquals(AbrpSchedule.MAX_JITTER_MS, AbrpSchedule.jitterMs(1.0))
        assertEquals(AbrpSchedule.MAX_JITTER_MS, AbrpSchedule.jitterMs(7.0))
    }

    @Test
    fun `the log line never carries the position`() {
        val json = AbrpTelemetry.json(
            AbrpSample(utc = 1L, soc = 50.0, lat = 48.7, lon = 2.1, elevation = 120.0, heading = 90.0, speedKmh = 30.0)
        )
        val logged = AbrpTelemetry.withoutPosition(json)
        assertFalse(logged.contains("lat") || logged.contains("lon") || logged.contains("elevation") || logged.contains("heading"))
        assertTrue(logged.contains("\"soc\":50") && logged.contains("\"speed\":30"))
        assertTrue(logged.startsWith("{") && logged.endsWith("}") && !logged.contains(",}") && !logged.contains(",,"))
    }

    @Test
    fun `driving sends often, parked rarely, and a dead connection backs off`() {
        val driving = AbrpSample(utc = 1L, soc = 50.0, speedKmh = 50.0, isParked = false)
        val charging = AbrpSample(utc = 1L, soc = 50.0, isCharging = true, isParked = true)
        val parked = AbrpSample(utc = 1L, soc = 50.0, speedKmh = 0.0, isParked = true)
        assertEquals(AbrpSchedule.DRIVING_MS, AbrpSchedule.intervalMs(driving, failures = 0))
        assertEquals(AbrpSchedule.CHARGING_MS, AbrpSchedule.intervalMs(charging, failures = 0))
        assertEquals(AbrpSchedule.PARKED_MS, AbrpSchedule.intervalMs(parked, failures = 0))
        assertEquals(AbrpSchedule.BACKOFF_MS, AbrpSchedule.intervalMs(driving, failures = 3))
    }
}
