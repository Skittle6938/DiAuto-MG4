package com.andrerinas.openheadunit.vehicle

import android.os.SystemClock
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.aap.AapTransport
import com.andrerinas.openheadunit.aap.protocol.messages.RawVehicleEnergyModelEvent
import com.andrerinas.openheadunit.aap.protocol.messages.VehicleEnergyModelEvent
import com.andrerinas.openheadunit.utils.AppLog
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Sends VehicleEnergyModel on sensor 23 and/or 25 while the phone has requested them.
 * Some AA/Maps builds start 25 instead of 23 — both must be served.
 */
object Mg4EnergyReporter {
    private const val TAG = "DiAuto-MG4"
    private const val INTERVAL_MS = 30_000L

    private val requestedTypes = ConcurrentHashMap.newKeySet<Int>()
    private val vemEverSent = AtomicBoolean(false)
    private val transportRef = AtomicReference<AapTransport?>(null)
    private var tickFuture: ScheduledFuture<*>? = null
    private var lastSentElapsed = 0L
    private var lastSkipLogMs = 0L
    private const val SKIP_LOG_EVERY_MS = 30_000L
    private val executor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "diauto-mg4-vem").apply { isDaemon = true }
    }

    @Synchronized
    fun bind(transport: AapTransport) {
        transportRef.set(transport)
        // App.onCreate starts the provider only if the profile was already unlocked; a process born
        // during the locked boot never does, and no reading ever reached the phone. A connection
        // means the profile is unlocked, and start() is harmless when it is already running.
        App.instance?.let { app ->
            if (App.provide(app).settings.batteryForGoogleMaps) Mg4EnergyProvider.start(app)
        }
        if (tickFuture == null) {
            tickFuture = executor.scheduleWithFixedDelay(::tick, 1_000L, 1_000L, TimeUnit.MILLISECONDS)
        }
    }

    @Synchronized
    fun unbind() {
        transportRef.set(null)
        requestedTypes.clear()
        vemEverSent.set(false)
        tickFuture?.cancel(false)
        tickFuture = null
    }

    fun onSensorStart(type: Int) {
        if (type == 23 || type == 25) {
            requestedTypes.add(type)
            AppLog.i("$TAG phone requested VEM sensor type=$type (active=$requestedTypes)")
            sendNow(force = true)
        }
    }

    private fun tick() {
        if (requestedTypes.isEmpty()) return
        val due = SystemClock.elapsedRealtime() - lastSentElapsed >= INTERVAL_MS
        if (due || !vemEverSent.get()) sendNow(force = !vemEverSent.get())
    }

    private fun sendNow(force: Boolean) {
        if (requestedTypes.isEmpty() && !force) return
        val transport = transportRef.get() ?: return
        val snap = Mg4EnergyProvider.snapshot() ?: run {
            // Every second would fill the log; once in a while still shows it is waiting.
            val now = SystemClock.elapsedRealtime()
            if (now - lastSkipLogMs >= SKIP_LOG_EVERY_MS) {
                lastSkipLogMs = now
                AppLog.i("$TAG VEM skip: no battery snapshot yet")
            }
            // Kick a poll so the next 1s tick can send without waiting for the 5s cadence.
            Mg4EnergyProvider.refresh()
            return
        }
        val encoded = VehicleEnergyModelEncoder.Snapshot(
            capacityWh = snap.capacityWh,
            currentWh = snap.currentWh,
            rangeMeters = snap.rangeMeters,
            batteryPercent = snap.batteryPercent,
        )

        // Prefer whichever the phone started; if none yet but force, try both.
        val types = if (requestedTypes.isNotEmpty()) {
            requestedTypes.toSet()
        } else {
            setOf(23, 25)
        }

        var sentAny = false
        if (23 in types) {
            sentAny = transport.send(VehicleEnergyModelEvent(encoded)) || sentAny
        }
        if (25 in types) {
            sentAny = transport.send(RawVehicleEnergyModelEvent(encoded)) || sentAny
        }

        if (sentAny) {
            lastSentElapsed = SystemClock.elapsedRealtime()
            vemEverSent.set(true)
            AppLog.i(
                "$TAG vem tx capacity=${snap.capacityWh}Wh current=${snap.currentWh}Wh " +
                    "range=${snap.rangeKm}km battery=${"%.1f".format(snap.batteryPercent)}% " +
                    "types=$types",
            )
        } else {
            AppLog.i("$TAG vem tx dropped (sensors $types not started or transport not alive)")
        }
    }
}
