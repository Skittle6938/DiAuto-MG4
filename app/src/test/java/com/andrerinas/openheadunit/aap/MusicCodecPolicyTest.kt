package com.andrerinas.openheadunit.aap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicCodecPolicyTest {

    @Test
    fun `uncompressed music by default on a 5 GHz hotspot and over USB`() {
        assertFalse(MusicCodecPolicy.useAac(choice = null, wireless = true, apBand = ApBand.BAND_5GHZ))
        assertFalse(MusicCodecPolicy.useAac(choice = null, wireless = false, apBand = ApBand.BAND_2GHZ))
    }

    @Test
    fun `compressed music by default on a 2_4 GHz hotspot`() {
        assertTrue(MusicCodecPolicy.useAac(choice = null, wireless = true, apBand = ApBand.BAND_2GHZ))
    }

    @Test
    fun `an unknown band counts as 5 GHz, the band DiAuto asks for`() {
        assertFalse(MusicCodecPolicy.useAac(choice = null, wireless = true, apBand = null))
    }

    @Test
    fun `the driver's own choice always wins`() {
        assertTrue(MusicCodecPolicy.useAac(choice = true, wireless = true, apBand = ApBand.BAND_5GHZ))
        assertFalse(MusicCodecPolicy.useAac(choice = false, wireless = true, apBand = ApBand.BAND_2GHZ))
    }

    @Test
    fun `legacy band numbers`() {
        assertEquals(ApBand.BAND_2GHZ, MusicCodecPolicy.bandOfLegacy(0))
        assertEquals(ApBand.BAND_5GHZ, MusicCodecPolicy.bandOfLegacy(1))
        assertNull(MusicCodecPolicy.bandOfLegacy(null))
        assertNull(MusicCodecPolicy.bandOfLegacy(-1))
    }
}
