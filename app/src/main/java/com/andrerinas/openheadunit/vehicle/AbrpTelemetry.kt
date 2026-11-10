package com.andrerinas.openheadunit.vehicle

/**
 * One reading of the car for ABRP's telemetry API (`https://api.iternio.com/1/tlm/send`), with the
 * field names that API documents. Every field but [utc] and [soc] is optional: a value the car does
 * not give is left out rather than guessed.
 */
data class AbrpSample(
    val utc: Long,
    val soc: Double?,
    val powerKw: Double? = null,
    val speedKmh: Double? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val elevation: Double? = null,
    val heading: Double? = null,
    val isCharging: Boolean? = null,
    val isParked: Boolean? = null,
    val extTempC: Double? = null,
    val battTempC: Double? = null,
    val estBatteryRangeKm: Double? = null,
    val odometerKm: Double? = null,
    val soh: Double? = null,
    val capacityKwh: Double? = null,
)

/** Pure: the JSON ABRP expects, and whether a sample is worth sending. Unit-tested. */
object AbrpTelemetry {

    const val API_URL = "https://api.iternio.com/1/tlm/send"

    /** ABRP plans from the charge: a reading without it is not worth the data. */
    fun worthSending(sample: AbrpSample): Boolean = sample.soc != null && sample.soc in 0.0..100.0

    fun json(sample: AbrpSample): String {
        val fields = linkedMapOf<String, Any?>(
            "utc" to sample.utc,
            "soc" to sample.soc?.round(1),
            "power" to sample.powerKw?.round(2),
            "speed" to sample.speedKmh?.round(1),
            "lat" to sample.lat?.round(6),
            "lon" to sample.lon?.round(6),
            "elevation" to sample.elevation?.round(0),
            "heading" to sample.heading?.round(0),
            "is_charging" to sample.isCharging,
            "is_parked" to sample.isParked,
            "ext_temp" to sample.extTempC?.round(1),
            "batt_temp" to sample.battTempC?.round(1),
            "est_battery_range" to sample.estBatteryRangeKm?.round(0),
            "odometer" to sample.odometerKm?.round(1),
            "soh" to sample.soh?.round(1),
            "capacity" to sample.capacityKwh?.round(1),
        )
        return fields.entries
            .filter { it.value != null }
            .joinToString(",", "{", "}") { (k, v) -> "\"$k\":${literal(v!!)}" }
    }

    /**
     * Charging when a cable is in and the pack takes power. Power alone also goes negative under
     * regeneration, and the cable alone stays in after a charge has finished.
     */
    fun isCharging(chargePortConnected: Boolean?, powerKw: Double?): Boolean? = when {
        chargePortConnected == null -> null
        !chargePortConnected -> false
        powerKw == null -> null
        else -> powerKw < -0.3
    }

    private fun literal(v: Any): String = when (v) {
        is Boolean -> v.toString()
        is Long, is Int -> v.toString()
        is Double -> if (v == Math.floor(v) && kotlin.math.abs(v) < 1e15) v.toLong().toString()
            else v.toString()
        else -> "\"$v\""
    }

    private fun Double.round(digits: Int): Double? {
        if (!isFinite()) return null
        val f = Math.pow(10.0, digits.toDouble())
        return Math.round(this * f) / f
    }
}
