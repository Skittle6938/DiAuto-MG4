package com.andrerinas.openheadunit.vehicle

import android.content.Context
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.utils.AppLog
import com.evsuite.hardware.EVHardware
import java.util.concurrent.Executors

/**
 * Battery heating on the way to a charger: switched on a set time before the next stop when that
 * stop is a charger, switched back off when the trip changes. Only heating DiAuto switched on is
 * ever switched off by it.
 */
object ChargeStopPreheatPolicy {

    /** A next stop closer than this when the route ends counts as reached, not cancelled. */
    const val ARRIVED_SECONDS = 180

    /**
     * The phone sends a stop's name or address, not its kind, so chargers are told by name: the
     * networks and the words a charging stop carries.
     */
    private val CHARGER_WORDS = listOf(
        "ionity", "supercharger", "tesla", "fastned", "electra", "allego", "powerdot", "atlante",
        "zunder", "izivia", "freshmile", "chargepoint", "charge point", "charging", "charger",
        "recharge", "borne", "ladestation", "ladepark", "ladesäule", "laadstation", "laadpaal",
        "snellader", "enbw", "ewe go", "bp pulse", "aral pulse", "shell recharge", "evgo",
        "electrify", "gridserve", "instavolt", "osprey", "plenitude", "be charge", "endesa x",
        "iberdrola", "wenea", "tesla destination",
    )

    fun isCharger(stop: String?): Boolean {
        if (stop.isNullOrBlank()) return false
        val s = stop.lowercase()
        return CHARGER_WORDS.any { s.contains(it) }
    }

    enum class Action { NONE, TURN_ON, TURN_OFF, RELEASE }

    /**
     * @param navigating a route is being followed
     * @param secondsToStop time to the next stop, or null if the phone has not said
     * @param heatingOn the car's switch, or null if unreadable
     * @param ownedByUs DiAuto switched the heating on for the current trip
     * @param lastSecondsToStop the last time to the next stop seen while navigating
     */
    fun decide(
        enabled: Boolean,
        navigating: Boolean,
        nextStopIsCharger: Boolean,
        secondsToStop: Int?,
        leadSeconds: Int,
        heatingOn: Boolean?,
        ownedByUs: Boolean,
        lastSecondsToStop: Int?,
    ): Action {
        if (ownedByUs) {
            // Switched off by hand since: the driver decides from here.
            if (heatingOn == false) return Action.RELEASE
            if (!enabled) return Action.TURN_OFF
            if (!navigating) {
                val arrived = lastSecondsToStop != null && lastSecondsToStop <= ARRIVED_SECONDS
                return if (arrived) Action.RELEASE else Action.TURN_OFF
            }
            return if (nextStopIsCharger) Action.NONE else Action.TURN_OFF
        }
        if (!enabled || !navigating || !nextStopIsCharger || heatingOn != false) return Action.NONE
        return if (secondsToStop != null && secondsToStop in 0..leadSeconds) Action.TURN_ON else Action.NONE
    }
}

object ChargeStopPreheater {
    private const val TAG = "Preheat"

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "diauto-preheat").apply { isDaemon = true }
    }

    @Volatile private var ownedByUs = false
    @Volatile private var lastSecondsToStop: Int? = null
    @Volatile private var lastAction: ChargeStopPreheatPolicy.Action? = null

    /** Called on every navigation update; [nextStop] is the next stop's name or address. */
    fun update(context: Context, navigating: Boolean, nextStop: String?, secondsToStop: Int?) {
        val settings = App.provide(context).settings
        if (!settings.batteryPreheatBeforeCharger && !ownedByUs) return
        if (navigating && secondsToStop != null) lastSecondsToStop = secondsToStop
        val charger = ChargeStopPreheatPolicy.isCharger(nextStop)
        val lead = settings.batteryPreheatMinutes * 60
        executor.execute {
            val heating = runCatching { EVHardware.isDrivingBatteryHeatOn() }.getOrNull()
            val action = ChargeStopPreheatPolicy.decide(
                enabled = settings.batteryPreheatBeforeCharger,
                navigating = navigating,
                nextStopIsCharger = charger,
                secondsToStop = secondsToStop,
                leadSeconds = lead,
                heatingOn = heating,
                ownedByUs = ownedByUs,
                lastSecondsToStop = lastSecondsToStop,
            )
            if (action != lastAction && action != ChargeStopPreheatPolicy.Action.NONE) {
                AppLog.i("$TAG: $action (next stop charger=$charger, ${secondsToStop?.div(60) ?: "-"} min away, " +
                    "lead ${lead / 60} min, heating=${heating ?: "?"})")
            }
            lastAction = action
            when (action) {
                ChargeStopPreheatPolicy.Action.TURN_ON ->
                    if (EVHardware.setDrivingBatteryHeat(true)) ownedByUs = true
                ChargeStopPreheatPolicy.Action.TURN_OFF -> {
                    EVHardware.setDrivingBatteryHeat(false)
                    release()
                }
                ChargeStopPreheatPolicy.Action.RELEASE -> release()
                ChargeStopPreheatPolicy.Action.NONE -> {}
            }
            if (!navigating && !ownedByUs) lastSecondsToStop = null
        }
    }

    private fun release() {
        ownedByUs = false
        lastSecondsToStop = null
    }
}
