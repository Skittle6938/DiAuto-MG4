package com.andrerinas.openheadunit.vehicle

/**
 * The car's battery and odometer as Android Auto's standard sensors (#18): FUEL_LEVEL and
 * ODOMETER, which the phone hands to Android Auto apps through CarInfo (EnergyLevel, Mileage),
 * so that an app like ABRP can read the car's charge over the existing link. Test option, off
 * by default: whether ABRP reads them, and how Maps takes them next to the energy model, is
 * still to be seen on a drive.
 */
object CarDataSensors {
    /** Android Auto sensor types (Sensors.SensorType). */
    const val TYPE_ODOMETER = 5
    const val TYPE_FUEL_LEVEL = 6

    /** Sent again this often while the phone has started them: the charge moves slowly. */
    const val INTERVAL_MS = 5_000L

    /** Below this charge the low-battery flag is set, as the car's own warning. */
    const val LOW_BATTERY_PERCENT = 10.0

    data class Fuel(val levelPercent: Int, val rangeKm: Int, val low: Boolean)

    fun isCarDataType(type: Int) = type == TYPE_FUEL_LEVEL || type == TYPE_ODOMETER

    /** Charge in whole percent (0..100), range in km as the car shows it (never negative). */
    fun fuel(batteryPercent: Double, rangeKm: Int): Fuel? {
        if (!batteryPercent.isFinite()) return null
        val percent = batteryPercent.coerceIn(0.0, 100.0)
        return Fuel(
            levelPercent = Math.round(percent).toInt(),
            rangeKm = rangeKm.coerceAtLeast(0),
            low = percent < LOW_BATTERY_PERCENT,
        )
    }

    /** The odometer field carries tenths of a km ("kms_e1"). Null when the car gives none. */
    fun odometerTenths(odometerKm: Float?): Int? {
        val km = odometerKm?.takeIf { it.isFinite() && it >= 0f } ?: return null
        return Math.round(km * 10.0).toInt()
    }

    /** Sending is due when the phone has started a sensor and the interval has passed. */
    fun due(started: Boolean, lastSentMs: Long, nowMs: Long): Boolean =
        started && (lastSentMs == 0L || nowMs - lastSentMs >= INTERVAL_MS)
}
