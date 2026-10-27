package com.andrerinas.openheadunit.vehicle

import android.content.Context
import android.util.Xml
import org.json.JSONArray
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream

/**
 * What MG4 Dash knows about the battery, read from its own settings file.
 *
 * Both apps are platform-signed with the system shared user id, so DiAuto may read MG4 Dash's
 * preferences directly; on a phone build, or without MG4 Dash, [read] returns null.
 *
 * MG4 Dash measures the usable capacity while driving (`cap_samples`) and estimates the state of
 * health against the battery chosen in its settings (`battery_model`). The health below is its
 * own formula: samples of the last 30 days (otherwise all), weighted by their charge drop, over
 * MG4 Dash's usable figure for that battery, at most 100 %. When no battery was chosen there,
 * the one nearest to the measurement is used instead of MG4 Dash's 51 kWh default.
 */
object Mg4DashBattery {
    const val PACKAGE = "app.mg4dash"
    private const val PREFS_FILE = "shared_prefs/mg4launcher.xml"
    private const val MONTH_MS = 30L * 24 * 3600_000L

    /** MG4 Dash's usable figures behind its health estimate (its BatteryCapacity.MODELS). */
    private val MG4DASH_USABLE = mapOf(51 to 49.0f, 64 to 61.7f, 77 to 74.4f)

    data class Reading(
        /** Nameplate kWh (51, 64 or 77), or null when MG4 Dash has neither a choice nor a measurement. */
        val pack: Int?,
        /** True when the battery was chosen in MG4 Dash's settings rather than inferred. */
        val packChosen: Boolean,
        /** Usable capacity measured by MG4 Dash today, kWh. */
        val measuredKwh: Float?,
        val sohPercent: Float?,
    )

    @Volatile private var cached: Reading? = null
    @Volatile private var cachedStamp = Long.MIN_VALUE

    fun read(context: Context): Reading? {
        val file = prefsFile(context) ?: return null
        val stamp = file.lastModified()
        if (stamp == cachedStamp) return cached
        val reading = runCatching { file.inputStream().use { parse(it, System.currentTimeMillis()) } }.getOrNull()
        cached = reading
        cachedStamp = stamp
        return reading
    }

    private fun prefsFile(context: Context): File? = runCatching {
        val dataDir = context.packageManager.getApplicationInfo(PACKAGE, 0).dataDir
        File(dataDir, PREFS_FILE).takeIf { it.canRead() }
    }.getOrNull()

    internal fun parse(xml: InputStream, now: Long): Reading? {
        val parser = Xml.newPullParser()
        parser.setInput(xml, null)
        var model: Int? = null
        var samples: String? = null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.getAttributeValue(null, "name")) {
                "battery_model" -> model = parser.getAttributeValue(null, "value")?.toIntOrNull()
                "cap_samples" -> samples = parser.nextText()
            }
        }
        return fromValues(model, samples, now)
    }

    internal fun fromValues(model: Int?, samplesJson: String?, now: Long): Reading? {
        val measured = measuredKwh(samplesJson, now)
        val chosen = model?.takeIf { it in MG4DASH_USABLE }
        val pack = chosen ?: measured?.let(BatteryCapacityResolver::nearestPack) ?: return null
        val soh = measured?.let { (it / MG4DASH_USABLE.getValue(pack) * 100f).coerceAtMost(100f) }
        return Reading(pack, chosen != null, measured, soh)
    }

    private fun measuredKwh(samplesJson: String?, now: Long): Float? {
        if (samplesJson.isNullOrBlank()) return null
        val all = runCatching {
            val arr = JSONArray(samplesJson)
            (0 until arr.length()).map { i ->
                arr.getJSONObject(i).let { o -> Triple(o.getLong("t"), o.getDouble("k"), o.getInt("d")) }
            }
        }.getOrNull()?.filter { it.third > 0 && it.second.isFinite() } ?: return null
        val recent = all.filter { now - it.first < MONTH_MS }.ifEmpty { all }
        val weight = recent.sumOf { it.third }
        if (weight == 0) return null
        return (recent.sumOf { it.second * it.third } / weight).toFloat()
    }
}
