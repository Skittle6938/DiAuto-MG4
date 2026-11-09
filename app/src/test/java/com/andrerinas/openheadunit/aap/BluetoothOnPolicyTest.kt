package com.andrerinas.openheadunit.aap

import com.andrerinas.openheadunit.aap.BluetoothWakePolicy.BluetoothOnAction
import org.junit.Assert.assertEquals
import org.junit.Test

/** Issue #17: what happens when the car's Bluetooth comes on after DiAuto. */
class BluetoothOnPolicyTest {

    private fun action(
        wirelessOn: Boolean = true,
        userExited: Boolean = false,
        sessionBusy: Boolean = false,
        listenersOpen: Boolean = false,
        phonesRemembered: Boolean = true,
        phoneLinked: Boolean = false
    ) = BluetoothWakePolicy.onBluetoothOn(wirelessOn, userExited, sessionBusy, listenersOpen, phonesRemembered, phoneLinked)

    @Test
    fun `cold start - listeners opened and the phone woken at once`() {
        assertEquals(BluetoothOnAction.OPEN_AND_POKE, action())
    }

    @Test
    fun `a linked phone only needs the listeners`() {
        assertEquals(BluetoothOnAction.OPEN_LISTENERS, action(phoneLinked = true))
        assertEquals(BluetoothOnAction.OPEN_LISTENERS, action(phonesRemembered = false))
    }

    @Test
    fun `nothing to do`() {
        assertEquals(BluetoothOnAction.NOTHING, action(wirelessOn = false))
        assertEquals(BluetoothOnAction.NOTHING, action(userExited = true))
        assertEquals(BluetoothOnAction.NOTHING, action(sessionBusy = true))
        assertEquals(BluetoothOnAction.NOTHING, action(listenersOpen = true))
    }
}
