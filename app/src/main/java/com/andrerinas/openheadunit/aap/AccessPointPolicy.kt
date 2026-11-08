package com.andrerinas.openheadunit.aap

/**
 * When the car's hotspot (the access point wireless Android Auto needs) comes up and goes off
 * (2.2.3-beta1 and beta2). The car has one Wi-Fi radio: a hotspot up for nobody slows its Wi-Fi
 * client down, and the two are never on at once. The I/O lives in [AapService].
 *
 * Pure and unit-tested.
 */
object AccessPointPolicy {

    /** A USB session runs: it needs no hotspot, and the car keeps its Wi-Fi client for internet. */
    fun usbSession(connected: Boolean, wirelessSession: Boolean): Boolean = connected && !wirelessSession

    /** At service start: the hotspot comes up only for a phone linked over Bluetooth, never for USB. */
    fun startAtLaunch(phoneLinked: Boolean, connected: Boolean, wirelessSession: Boolean): Boolean =
        phoneLinked && !usbSession(connected, wirelessSession)

    /** A phone needs the hotspot: brought up unless wireless is off, a USB session runs, or it is already up. */
    fun shouldStart(wirelessEnabled: Boolean, connected: Boolean, wirelessSession: Boolean, running: Boolean): Boolean =
        wirelessEnabled && !usbSession(connected, wirelessSession) && !running

    /** No phone to serve, or a USB session: a running hotspot goes off, never under a wireless session. */
    fun shouldRelease(connected: Boolean, wirelessSession: Boolean, running: Boolean): Boolean =
        !(connected && wirelessSession) && running

    /** What a phone's request for the hotspot does. */
    enum class Request { REFRESH, STAY_OFF, START }

    /**
     * A wake-up sent to a phone that isn't there must not bring the hotspot up next to the car's
     * Wi-Fi client: with no phone linked over Bluetooth it stays off. The Bluetooth links are only
     * read when the hotspot is not running.
     */
    fun onPhoneRequest(running: Boolean, phonesLinkedOverBluetooth: () -> Int): Request = when {
        running -> Request.REFRESH
        phonesLinkedOverBluetooth() == 0 -> Request.STAY_OFF
        else -> Request.START
    }
}
