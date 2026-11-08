package com.andrerinas.openheadunit.aap

import com.andrerinas.openheadunit.aap.AccessPointPolicy.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The car's hotspot only for a phone that needs it, never with a USB session (2.2.3-beta1/beta2). */
class AccessPointPolicyTest {

    @Test
    fun `a USB session is a connected session that is not wireless`() {
        assertTrue(AccessPointPolicy.usbSession(connected = true, wirelessSession = false))
        assertFalse(AccessPointPolicy.usbSession(connected = true, wirelessSession = true))
        assertFalse(AccessPointPolicy.usbSession(connected = false, wirelessSession = false))
    }

    @Test
    fun `at launch the hotspot waits for a phone linked over Bluetooth`() {
        assertTrue(AccessPointPolicy.startAtLaunch(phoneLinked = true, connected = false, wirelessSession = false))
        assertFalse(AccessPointPolicy.startAtLaunch(phoneLinked = false, connected = false, wirelessSession = false))
    }

    @Test
    fun `at launch no hotspot for a USB session, even with a phone linked`() {
        assertFalse(AccessPointPolicy.startAtLaunch(phoneLinked = true, connected = true, wirelessSession = false))
    }

    @Test
    fun `the hotspot comes up for a phone when nothing stands in the way`() {
        assertTrue(AccessPointPolicy.shouldStart(wirelessEnabled = true, connected = false, wirelessSession = false, running = false))
    }

    @Test
    fun `never a hotspot with wireless off, a USB session or one already up`() {
        assertFalse(AccessPointPolicy.shouldStart(wirelessEnabled = false, connected = false, wirelessSession = false, running = false))
        assertFalse(AccessPointPolicy.shouldStart(wirelessEnabled = true, connected = true, wirelessSession = false, running = false))
        assertFalse(AccessPointPolicy.shouldStart(wirelessEnabled = true, connected = false, wirelessSession = false, running = true))
    }

    @Test
    fun `a running hotspot goes off with no phone or a USB session`() {
        assertTrue(AccessPointPolicy.shouldRelease(connected = false, wirelessSession = false, running = true))
        assertTrue(AccessPointPolicy.shouldRelease(connected = true, wirelessSession = false, running = true))
    }

    @Test
    fun `the hotspot is never cut under a wireless session`() {
        assertFalse(AccessPointPolicy.shouldRelease(connected = true, wirelessSession = true, running = true))
    }

    @Test
    fun `nothing to release when the hotspot is off`() {
        assertFalse(AccessPointPolicy.shouldRelease(connected = false, wirelessSession = false, running = false))
    }

    @Test
    fun `a phone request refreshes a running hotspot without reading Bluetooth`() {
        assertEquals(Request.REFRESH, AccessPointPolicy.onPhoneRequest(running = true) { error("Bluetooth read") })
    }

    @Test
    fun `a wake-up with no phone linked over Bluetooth leaves the hotspot off`() {
        assertEquals(Request.STAY_OFF, AccessPointPolicy.onPhoneRequest(running = false) { 0 })
    }

    @Test
    fun `a phone linked over Bluetooth gets the hotspot`() {
        assertEquals(Request.START, AccessPointPolicy.onPhoneRequest(running = false) { 1 })
        assertEquals(Request.START, AccessPointPolicy.onPhoneRequest(running = false) { 2 })
    }
}
