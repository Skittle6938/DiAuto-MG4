package com.andrerinas.openheadunit.connection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhonePriorityTest {

    private val phones = listOf("AA:01", "BB:02", "CC:03")

    @Test
    fun `the first phone of the list that is linked wins`() {
        assertEquals("BB:02", PhonePriority.pick(phones, listOf("cc:03", "bb:02")))
        assertEquals("AA:01", PhonePriority.pick(phones, listOf("CC:03", "AA:01")))
    }

    @Test
    fun `no remembered phone linked gives nothing`() {
        assertNull(PhonePriority.pick(phones, listOf("DD:04")))
    }

    @Test
    fun `a phone moves up or down without leaving the list`() {
        assertEquals(listOf("BB:02", "AA:01", "CC:03"), PhonePriority.move(phones, "BB:02", -1))
        assertEquals(listOf("AA:01", "CC:03", "BB:02"), PhonePriority.move(phones, "BB:02", 1))
        assertEquals(phones, PhonePriority.move(phones, "AA:01", -1))
    }
}
