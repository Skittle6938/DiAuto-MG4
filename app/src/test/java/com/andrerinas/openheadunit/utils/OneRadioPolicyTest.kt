package com.andrerinas.openheadunit.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OneRadioPolicyTest {

    @Test
    fun `no access point while the car's Wi-Fi client is still on`() {
        assertFalse(OneRadioPolicy.mayStartAccessPoint(clientOn = true))
        assertTrue(OneRadioPolicy.mayStartAccessPoint(clientOn = false))
    }

    @Test
    fun `no Wi-Fi client while the access point is still up`() {
        assertFalse(OneRadioPolicy.mayStartClient(accessPointUp = true))
        assertTrue(OneRadioPolicy.mayStartClient(accessPointUp = false))
    }
}
