package com.andrerinas.openheadunit.connection

/**
 * Which remembered phone goes wireless when several are linked over Bluetooth: the first of the
 * driver's list that is there. A phone on USB always comes first, which the USB path handles itself.
 */
object PhonePriority {

    /** The highest of [ordered] among [linked], else null. */
    fun pick(ordered: List<String>, linked: Collection<String>): String? =
        ordered.firstOrNull { mac -> linked.any { it.equals(mac, ignoreCase = true) } }

    /** [ordered] with [mac] moved by [delta] places, kept inside the list. */
    fun move(ordered: List<String>, mac: String, delta: Int): List<String> {
        val from = ordered.indexOf(mac)
        if (from < 0) return ordered
        val to = (from + delta).coerceIn(0, ordered.lastIndex)
        return ordered.toMutableList().apply { add(to, removeAt(from)) }
    }
}
