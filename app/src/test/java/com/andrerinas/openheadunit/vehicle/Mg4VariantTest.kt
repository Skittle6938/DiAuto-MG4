package com.andrerinas.openheadunit.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Mg4VariantTest {
    @Test
    fun `the car's pack picks the nearest version`() {
        assertEquals(Mg4Variant.STANDARD, Mg4Variant.detect(47_858))
        assertEquals(Mg4Variant.LONG_RANGE, Mg4Variant.detect(61_700))
        assertEquals(Mg4Variant.EXTENDED_RANGE, Mg4Variant.detect(74_000))
    }

    @Test
    fun `a version chosen by hand wins over detection`() {
        assertEquals(Mg4Variant.XPOWER, Mg4Variant.resolve("xpower", 61_700))
        assertEquals(Mg4Variant.LONG_RANGE, Mg4Variant.resolve(Mg4Variant.AUTO, 61_700))
    }

    @Test
    fun `the curve interpolates between its 5 percent points`() {
        assertEquals(138_000, Mg4Variant.LONG_RANGE.dcPowerW(50.0))
        assertEquals(108_000, Mg4Variant.LONG_RANGE.dcPowerW(57.5))
        assertEquals(138_000, Mg4Variant.LONG_RANGE.maxDcW)
    }

    @Test
    fun `10 to 80 percent on a fast charger is close to the measured time`() {
        // EVKX: about 27 min for the 64 kWh, about 31 min (60.5 kW average) for the Standard.
        val lr = Mg4Variant.LONG_RANGE
        val lrTime = VehicleEnergyModelEncoder.dcSecondsToFullForTest(61_700, lr, 150_000, 10.0) -
            VehicleEnergyModelEncoder.dcSecondsToFullForTest(61_700, lr, 150_000, 80.0)
        assertTrue("LR 10-80 % took ${lrTime / 60} min", lrTime in 24 * 60..31 * 60)
        val std = Mg4Variant.STANDARD
        val stdTime = VehicleEnergyModelEncoder.dcSecondsToFullForTest(50_800, std, 150_000, 10.0) -
            VehicleEnergyModelEncoder.dcSecondsToFullForTest(50_800, std, 150_000, 80.0)
        assertTrue("Standard 10-80 % took ${stdTime / 60} min", stdTime in 28 * 60..40 * 60)
    }

    @Test
    fun `the full model carries the charging sections when the version is known`() {
        val bytes = VehicleEnergyModelEncoder.encode(
            VehicleEnergyModelEncoder.Snapshot(
                capacityWh = 61_700, currentWh = 30_850, rangeMeters = 200_000,
                variant = Mg4Variant.LONG_RANGE, outsideTempC = 12f,
            ),
        )
        // Field 4 (tag 0x22) and field 8 (tag 0x42) follow the battery and road-load messages.
        assertTrue(bytes.contains(0x22.toByte()))
        assertTrue(bytes.size > 1_000)
    }
}
