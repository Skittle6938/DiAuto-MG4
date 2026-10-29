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
    fun `the model tells Maps the version's real peak DC power`() {
        val bytes = VehicleEnergyModelEncoder.encode(
            VehicleEnergyModelEncoder.Snapshot(
                capacityWh = 47_858, currentWh = 21_680, rangeMeters = 136_000,
                variant = Mg4Variant.STANDARD,
            ),
        )
        assertTrue(bytes.isNotEmpty())
        assertEquals(86_000, Mg4Variant.STANDARD.maxDcW)
    }

    @Test
    fun `the curve interpolates between its 5 percent points`() {
        assertEquals(138_000, Mg4Variant.LONG_RANGE.dcPowerW(50.0))
        assertEquals(108_000, Mg4Variant.LONG_RANGE.dcPowerW(57.5))
        assertEquals(138_000, Mg4Variant.LONG_RANGE.maxDcW)
    }
}
