package com.andrerinas.openheadunit.aap

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue #17: the reconnect watchdog must poke within its first rounds after a cold start. */
class RecentPhoneConnectTest {

    private val window = 180_000L

    @Test
    fun `no phone has connected yet - the watchdog pokes right after boot`() {
        assertFalse(BluetoothWakePolicy.recentPhoneConnect(nowMs = 30_000L, lastConnectMs = 0L, windowMs = window))
    }

    @Test
    fun `a connect a minute ago - the handshake is left to run`() {
        assertTrue(BluetoothWakePolicy.recentPhoneConnect(nowMs = 120_000L, lastConnectMs = 60_000L, windowMs = window))
    }

    @Test
    fun `a connect older than the window - the watchdog may poke again`() {
        assertFalse(BluetoothWakePolicy.recentPhoneConnect(nowMs = 400_000L, lastConnectMs = 60_000L, windowMs = window))
    }
}
