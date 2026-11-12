package com.andrerinas.openheadunit.vehicle

import com.andrerinas.openheadunit.vehicle.BatteryCapacityResolver.Source
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryWaitPolicyTest {
    @Test fun waitsOnlyWhileTheCarMayStillAnswer() {
        assertTrue("just started, no answer yet", BatteryWaitPolicy.waiting(Source.DEFAULT, false, null, 10_000))
        assertFalse("the car gave its battery", BatteryWaitPolicy.waiting(Source.CAR, false, null, 10_000))
        assertFalse("set by hand", BatteryWaitPolicy.waiting(Source.MANUAL, false, null, 10_000))
        assertFalse("battery chosen in MG4Dash", BatteryWaitPolicy.waiting(Source.DEFAULT, true, null, 10_000))
        assertFalse("read failed: defaults, as on cars that never answer", BatteryWaitPolicy.waiting(Source.DEFAULT, false, "timeout", 10_000))
        assertFalse("past 2 minutes: defaults", BatteryWaitPolicy.waiting(Source.DEFAULT, false, null, BatteryWaitPolicy.MAX_WAIT_MS))
    }
}
