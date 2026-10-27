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
    fun `known limited firmwares switch at start, once, from Native AA only`() {
        assertTrue(HeadunitServerPolicy.shouldSwitchAtStart("SWI68-29958-1300R46", 3, alreadySwitched = false))
        assertFalse(HeadunitServerPolicy.shouldSwitchAtStart("SWI68-29958-1300R46", 3, alreadySwitched = true))
        assertFalse(HeadunitServerPolicy.shouldSwitchAtStart("SWI68-29958-1300R46", 1, alreadySwitched = false))
    }

    @Test
    fun `supported firmwares never switch`() {
        for (fw in listOf("SWI69-29958-1100R45", "SWI132-29958-1100R43", "SWI133-29176-1300R36")) {
            assertFalse(HeadunitServerPolicy.shouldSwitchAtStart(fw, 3, alreadySwitched = false))
            assertFalse(HeadunitServerPolicy.shouldSwitchAfterFailures(fw, 3, alreadySwitched = false, failuresInARow = 99))
        }
    }

    @Test
    fun `unknown firmwares switch only after repeated failures`() {
        val fw = "SWI165-29958-1300R10"
        assertFalse(HeadunitServerPolicy.shouldSwitchAtStart(fw, 3, alreadySwitched = false))
        assertFalse(HeadunitServerPolicy.shouldSwitchAfterFailures(fw, 3, alreadySwitched = false, failuresInARow = 1))
        assertTrue(HeadunitServerPolicy.shouldSwitchAfterFailures(fw, 3, alreadySwitched = false, failuresInARow = 2))
        assertFalse(HeadunitServerPolicy.shouldSwitchAfterFailures(fw, 3, alreadySwitched = true, failuresInARow = 5))
    }
}
