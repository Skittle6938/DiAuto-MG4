package com.andrerinas.openheadunit.aap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadunitServerPolicyTest {

    @Test
    fun `firmwares are classified by their SWI line`() {
        assertEquals(HeadunitServerPolicy.Firmware.LIMITED, HeadunitServerPolicy.classify("SWI68-29958-1300R46"))
        assertEquals(HeadunitServerPolicy.Firmware.LIMITED, HeadunitServerPolicy.classify("SWI131-29958-1100R05"))
        assertEquals(HeadunitServerPolicy.Firmware.LIMITED, HeadunitServerPolicy.classify("DBI68-29958-1300R46"))
        assertEquals(HeadunitServerPolicy.Firmware.SUPPORTED, HeadunitServerPolicy.classify("SWI69-29958-1100R45"))
        assertEquals(HeadunitServerPolicy.Firmware.SUPPORTED, HeadunitServerPolicy.classify("SWI132-29958-1100R43"))
        assertEquals(HeadunitServerPolicy.Firmware.SUPPORTED, HeadunitServerPolicy.classify("SWI133-29176-1300R36"))
        assertEquals(HeadunitServerPolicy.Firmware.UNKNOWN, HeadunitServerPolicy.classify("SWI165-29958-1300R10"))
        assertEquals(HeadunitServerPolicy.Firmware.UNKNOWN, HeadunitServerPolicy.classify("SWI680-1"))
        assertEquals(HeadunitServerPolicy.Firmware.UNKNOWN, HeadunitServerPolicy.classify(null))
    }

    @Test
    fun `limited firmwares try the freed Native AA experiment first, then switch`() {
        val fw = "SWI68-29958-1300R46"
        // Experiment not tried yet: run it, do not switch.
        assertTrue(HeadunitServerPolicy.shouldTryFreedNativeAa(fw, 3, experimentTried = false))
        assertFalse(HeadunitServerPolicy.shouldSwitchAtStart(fw, 3, alreadySwitched = false, experimentTried = false))
        // Experiment tried and failed: switch at start, once.
        assertFalse(HeadunitServerPolicy.shouldTryFreedNativeAa(fw, 3, experimentTried = true))
        assertTrue(HeadunitServerPolicy.shouldSwitchAtStart(fw, 3, alreadySwitched = false, experimentTried = true))
        assertFalse(HeadunitServerPolicy.shouldSwitchAtStart(fw, 3, alreadySwitched = true, experimentTried = true))
        // Only from Native AA.
        assertFalse(HeadunitServerPolicy.shouldTryFreedNativeAa(fw, 1, experimentTried = false))
    }

    @Test
    fun `supported firmwares never experiment and never switch`() {
        for (fw in listOf("SWI69-29958-1100R45", "SWI132-29958-1100R43", "SWI133-29176-1300R36")) {
            assertFalse(HeadunitServerPolicy.shouldTryFreedNativeAa(fw, 3, experimentTried = false))
            assertFalse(HeadunitServerPolicy.shouldSwitchAtStart(fw, 3, alreadySwitched = false, experimentTried = true))
            assertFalse(HeadunitServerPolicy.shouldSwitchAfterFailures(fw, 3, alreadySwitched = false, failuresInARow = 99))
        }
    }

    @Test
    fun `unknown firmwares do not experiment and switch only after repeated failures`() {
        val fw = "SWI165-29958-1300R10"
        assertFalse(HeadunitServerPolicy.shouldTryFreedNativeAa(fw, 3, experimentTried = false))
        assertFalse(HeadunitServerPolicy.shouldSwitchAtStart(fw, 3, alreadySwitched = false, experimentTried = true))
        assertFalse(HeadunitServerPolicy.shouldSwitchAfterFailures(fw, 3, alreadySwitched = false, failuresInARow = 1))
        assertTrue(HeadunitServerPolicy.shouldSwitchAfterFailures(fw, 3, alreadySwitched = false, failuresInARow = 2))
        assertFalse(HeadunitServerPolicy.shouldSwitchAfterFailures(fw, 3, alreadySwitched = true, failuresInARow = 5))
    }
}
