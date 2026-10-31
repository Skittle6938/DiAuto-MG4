package com.andrerinas.openheadunit.vehicle

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import com.andrerinas.openheadunit.utils.AppLog

/**
 * MG4Dash's consumption map, read through its content provider (DiAuto cannot read its files):
 * content://app.mg4dash.consumption/map, one row with enabled, json, aux_w, updated_at.
 * Experimental and off by default; only offered when MG4Dash is installed.
 */
object Mg4DashConsumption {
    const val PACKAGE = "app.mg4dash"
    private val URI: Uri = Uri.parse("content://app.mg4dash.consumption/map")
    private const val CACHE_MS = 60_000L
    /** Average speed used to turn the accessories' power into a force. */
    private const val AUX_REFERENCE_KMH = 60f

    data class Map(val json: String, val auxW: Float?)

    @Volatile private var cached: Map? = null
    @Volatile private var readAt = 0L

    fun installed(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE, 0); true
    } catch (_: Exception) {
        false
    }

    /** The map when MG4Dash has it switched on, at most a minute old; null otherwise. */
    fun read(context: Context): Map? {
        val now = SystemClock.elapsedRealtime()
        if (now - readAt < CACHE_MS) return cached
        readAt = now
        cached = try {
            context.contentResolver.query(URI, null, null, null, null)?.use { c ->
                if (!c.moveToFirst()) null
                else if (c.getInt(c.getColumnIndexOrThrow("enabled")) != 1) null
                else {
                    val aux = c.getColumnIndex("aux_w").takeIf { it >= 0 && !c.isNull(it) }?.let { c.getFloat(it) }
                    Map(c.getString(c.getColumnIndexOrThrow("json")), aux)
                }
            }
        } catch (e: Exception) {
            AppLog.w("Mg4DashConsumption: map not readable (${e.message})")
            null
        }
        return cached
    }

    /** Road load for the energy model, accessories included in A; null to keep the car's average. */
    fun roadLoad(context: Context, outsideC: Float?): ConsumptionMapFit.RoadLoad? {
        val map = read(context) ?: return null
        val fit = try {
            ConsumptionMapFit.fit(map.json, outsideC)
        } catch (e: Exception) {
            AppLog.w("Mg4DashConsumption: map not usable (${e.message})")
            null
        } ?: return null
        val auxN = (map.auxW ?: 0f).coerceIn(0f, 8000f) / (AUX_REFERENCE_KMH / 3.6f)
        return fit.copy(a = fit.a + auxN)
    }
}
