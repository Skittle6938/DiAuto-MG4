package com.andrerinas.openheadunit.vehicle

import android.content.Context
import android.os.SystemClock
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.utils.AppLog
import com.evsuite.hardware.EVHardware
import kotlin.math.abs

/**
 * Usable capacity (when new) and state of health given to Google Maps, found automatically
 * unless the driver set them in Settings.
 *
 * Capacity: the one the car publishes, else the battery MG4 Dash knows (chosen in its settings,
 * or nearest to its measurement), else the 64 kWh battery. State of health: MG4 Dash's estimate,
 * else 100 %.
 */
object BatteryCapacityResolver {
    private const val TAG = "DiAuto-MG4"
    private const val CAR_RETRY_MS = 60_000L

    enum class Source { MANUAL, CAR, MG4DASH, DEFAULT }

    data class Resolved(
        val netKwh: Float,
        val netSource: Source,
        /** Nameplate kWh behind [netKwh] when it is one of the MG4 batteries. */
        val pack: Int?,
        val sohPercent: Float,
        val sohSource: Source,
    ) {
        val effectiveKwh: Float get() = (netKwh * sohPercent / 100f).coerceIn(1f, 200f)
    }

    /** MG4 batteries: nameplate kWh to usable kWh (ev-database). */
    val PACKS = listOf(51 to 50.8f, 64 to 61.7f, 77 to 74.4f)
    const val DEFAULT_PACK = 64

    fun usableKwh(pack: Int): Float = PACKS.first { it.first == pack }.second

    /** The battery whose usable capacity is nearest to [kwh]. */
    fun nearestPack(kwh: Float): Int = PACKS.minByOrNull { abs(it.second - kwh) }!!.first

    /** Nameplate kWh of a usable figure, when it is one of [PACKS]. */
    fun packOf(netKwh: Float): Int? = PACKS.firstOrNull { abs(it.second - netKwh) < 0.05f }?.first

    fun resolve(
        manualNetKwh: Float?,
        manualSohPercent: Float?,
        carKwh: Float?,
        mg4Dash: Mg4DashBattery.Reading?,
    ): Resolved {
        val car = carKwh?.let(::carUsableKwh)
        val (net, netSource) = when {
            manualNetKwh != null -> manualNetKwh.coerceIn(1f, 200f) to Source.MANUAL
            car != null -> car to Source.CAR
            mg4Dash?.pack != null -> usableKwh(mg4Dash.pack) to Source.MG4DASH
            else -> usableKwh(DEFAULT_PACK) to Source.DEFAULT
        }
        val dashSoh = mg4Dash?.sohPercent?.takeIf { it.isFinite() && it > 0f }
        val (soh, sohSource) = when {
            manualSohPercent != null -> manualSohPercent.coerceIn(1f, 100f) to Source.MANUAL
            dashSoh != null -> dashSoh.coerceIn(1f, 100f) to Source.MG4DASH
            else -> 100f to Source.DEFAULT
        }
        return Resolved(net, netSource, packOf(net), soh, sohSource)
    }

    /**
     * Usable kWh for a published capacity: a nameplate figure (51, 64 or 77) is turned into the
     * usable one, anything else is taken as usable. Values outside 20–120 kWh are ignored.
     */
    internal fun carUsableKwh(kwh: Float): Float? {
        if (!kwh.isFinite() || kwh !in 20f..120f) return null
        return PACKS.firstOrNull { abs(it.first - kwh) <= 0.6f }?.second ?: kwh
    }

    @Volatile private var carKwh: Float? = null
    @Volatile private var carReadAt = 0L
    @Volatile private var lastLogged: Resolved? = null

    /** Values in use now, with the stored manual settings. */
    fun current(context: Context): Resolved {
        val settings = App.provide(context).settings
        return resolve(
            manualNetKwh = settings.batteryNetCapacityKwh.takeIf { settings.batteryCapacityManual },
            manualSohPercent = settings.batteryStateOfHealthPercent.takeIf { settings.batterySohManual },
            carKwh = carCapacityKwh(),
            mg4Dash = Mg4DashBattery.read(context),
        ).also(::logChange)
    }

    /** Car capacity, read once; retried every minute while the car has not answered. */
    fun carCapacityKwh(): Float? {
        carKwh?.let { return it }
        val now = SystemClock.elapsedRealtime()
        if (carReadAt != 0L && now - carReadAt < CAR_RETRY_MS) return null
        carReadAt = now
        return runCatching { EVHardware.getBatteryCapacityKwh() }.getOrNull()
            ?.takeIf { carUsableKwh(it) != null }
            ?.also {
                carKwh = it
                AppLog.i("$TAG battery capacity published by the car: $it kWh")
            }
    }

    private fun logChange(resolved: Resolved) {
        if (resolved == lastLogged) return
        lastLogged = resolved
        AppLog.i(
            "$TAG battery net=${resolved.netKwh} kWh (${resolved.netSource}) " +
                "soh=${resolved.sohPercent} % (${resolved.sohSource}) effective=${resolved.effectiveKwh} kWh",
        )
    }
}
