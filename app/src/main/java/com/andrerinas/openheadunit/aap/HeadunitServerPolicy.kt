package com.andrerinas.openheadunit.aap

/**
 * When wireless Android Auto has to use Headunit Server mode instead of Native AA.
 *
 * Native AA starts with the phone opening an RFCOMM channel to this app. On some MG4 firmwares
 * the head unit's Bluetooth library (nFore) caps app RFCOMM servers at one session, and with that
 * cap it closes every incoming connection before accept() returns: Native AA can never work there
 * ("AA Server socket error" a few seconds after listening, no connection ever accepted). The cap
 * is 1 in SWI68 R46 (nFore v0.0.26D_v1) and 2 in SWI69, SWI132 and SWI133, read from their
 * libbluetooth.so. SWI131 R05 shows the same symptom (2026-10-27 reports).
 *
 * Firmwares known to work are never switched, whatever happens; for firmwares nobody has looked
 * at, the switch follows the symptom instead. Pure so it can be tested without Android.
 */
object HeadunitServerPolicy {

    /** RFCOMM cap of 1: Native AA cannot work. */
    private val LIMITED = listOf("SWI68", "SWI131")

    /** RFCOMM cap of 2, Native AA works: never switched automatically. */
    private val SUPPORTED = listOf("SWI69", "SWI132", "SWI133")

    /** Native AA server failures in a row, with no connection accepted, before switching. */
    const val FAILURES_BEFORE_SWITCH = 2

    enum class Firmware { LIMITED, SUPPORTED, UNKNOWN }

    fun classify(firmware: String?): Firmware {
        val v = normalize(firmware) ?: return Firmware.UNKNOWN
        return when {
            SUPPORTED.any { matches(v, it) } -> Firmware.SUPPORTED
            LIMITED.any { matches(v, it) } -> Firmware.LIMITED
            else -> Firmware.UNKNOWN
        }
    }

    /** Switch at service start: Native AA selected on a firmware known to refuse it. */
    fun shouldSwitchAtStart(firmware: String?, wifiMode: Int, alreadySwitched: Boolean): Boolean =
        wifiMode == MODE_NATIVE_AA && !alreadySwitched && classify(firmware) == Firmware.LIMITED

    /** Switch after repeated failures, only on firmwares nobody has checked. */
    fun shouldSwitchAfterFailures(
        firmware: String?,
        wifiMode: Int,
        alreadySwitched: Boolean,
        failuresInARow: Int,
    ): Boolean =
        wifiMode == MODE_NATIVE_AA && !alreadySwitched &&
            classify(firmware) == Firmware.UNKNOWN && failuresInARow >= FAILURES_BEFORE_SWITCH

    const val MODE_HEADUNIT_SERVER = 1
    private const val MODE_NATIVE_AA = 3

    private fun normalize(firmware: String?): String? {
        if (firmware.isNullOrBlank()) return null
        val t = firmware.trim().uppercase()
        // Regional DBI68-… builds are the SWI68 family.
        return if (t.startsWith("DBI")) "SWI" + t.substring(3) else t
    }

    /** "SWI68" matches "SWI68-29958-…" but not "SWI680…". */
    private fun matches(version: String, gen: String): Boolean =
        version.startsWith(gen) && (version.length == gen.length || !version[gen.length].isDigit())
}
