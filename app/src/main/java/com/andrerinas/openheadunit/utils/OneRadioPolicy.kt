package com.andrerinas.openheadunit.utils

/**
 * The car has one Wi-Fi radio for its Wi-Fi client and the access point, so never both at once,
 * in either direction: an access point started while the client is on a 2.4 GHz network comes up
 * on 2.4 GHz instead of 5 GHz (test, 2026-11-11), and a client switched on while the access point
 * is still up drags the next one onto the client's band.
 */
object OneRadioPolicy {

    /** The access point may start only once the client is off, even after asking it to go off. */
    fun mayStartAccessPoint(clientOn: Boolean): Boolean = !clientOn

    /** The client may come back only once the access point is down. */
    fun mayStartClient(accessPointUp: Boolean): Boolean = !accessPointUp
}
