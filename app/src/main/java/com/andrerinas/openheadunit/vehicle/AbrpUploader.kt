package com.andrerinas.openheadunit.vehicle

import android.content.Context
import android.os.SystemClock
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.location.LocationHolder
import com.andrerinas.openheadunit.utils.AppLog
import com.evsuite.hardware.EVHardware
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Sends the car's live state to ABRP (A Better Routeplanner) over the car's own internet, the
 * T-box, so ABRP plans from the real charge without an OBD dongle or the iSMART gateway.
 *
 * Off unless the user turned it on and entered the token ABRP gives for the car. A reading that
 * cannot be sent is dropped, never queued: a backlog would be sent in one go after a dead zone and
 * eat the T-box's monthly data. How often it sends depends on what the car is doing, see
 * [AbrpSchedule].
 */
object AbrpUploader {
    private const val TAG = "ABRP"
    private const val TICK_MS = 5_000L
    private const val TIMEOUT_MS = 8_000

    @Volatile private var app: Context? = null
    private var tick: ScheduledFuture<*>? = null
    private var lastSentAt = 0L
    private var failures = 0
    private var reportedFirstSuccess = false
    private val executor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "diauto-abrp").apply { isDaemon = true }
    }

    @Synchronized
    fun start(context: Context) {
        app = context.applicationContext
        if (tick != null) return
        tick = executor.scheduleWithFixedDelay(::tickSafely, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS)
    }

    @Synchronized
    fun stop() {
        tick?.cancel(false)
        tick = null
    }

    private fun tickSafely() {
        try {
            tickOnce()
        } catch (t: Throwable) {
            AppLog.w("$TAG: reading or sending failed: ${t.message}")
        }
    }

    private fun tickOnce() {
        val context = app ?: return
        val settings = App.provide(context).settings
        if (!settings.abrpEnabled) return
        val token = settings.abrpToken.trim()
        val apiKey = settings.abrpApiKey.trim()
        if (token.isEmpty() || apiKey.isEmpty()) return

        EVHardware.init(context)
        val sample = read(context)
        val now = SystemClock.elapsedRealtime()
        val interval = AbrpSchedule.intervalMs(sample, failures)
        if (lastSentAt != 0L && now - lastSentAt < interval) return
        if (!AbrpTelemetry.worthSending(sample)) return
        lastSentAt = now
        send(apiKey, token, AbrpTelemetry.json(sample))
    }

    private fun read(context: Context): AbrpSample {
        val power = EVHardware.getBatteryPowerKw()?.toDouble()
        val speed = EVHardware.getVehicleSpeedKmh()?.let { kotlin.math.abs(it).toDouble() }
        val fix = LocationHolder.currentLocation(context, maxAgeMs = 60_000L)
        val battery = runCatching { BatteryCapacityResolver.current(context) }.getOrNull()
        return AbrpSample(
            utc = System.currentTimeMillis() / 1000L,
            soc = EVHardware.getBatterySocPercent()?.toDouble(),
            powerKw = power,
            speedKmh = speed,
            lat = fix?.latitude,
            lon = fix?.longitude,
            elevation = fix?.takeIf { it.hasAltitude() }?.altitude,
            heading = fix?.takeIf { it.hasBearing() }?.bearing?.toDouble(),
            isCharging = AbrpTelemetry.isCharging(EVHardware.isChargePortConnected(), power),
            isParked = EVHardware.isVehicleInPark(),
            extTempC = EVHardware.getOutsideTempCelsius()?.toDouble(),
            battTempC = EVHardware.getBatteryTemperatureCelsius()?.toDouble(),
            estBatteryRangeKm = EVHardware.getRemainingRangeKm()?.toDouble(),
            odometerKm = EVHardware.getOdometerKm()?.toDouble(),
            soh = battery?.sohPercent?.toDouble(),
            capacityKwh = battery?.netKwh?.toDouble(),
        )
    }

    private fun send(apiKey: String, token: String, tlm: String) {
        val url = URL(
            AbrpTelemetry.API_URL +
                "?token=" + URLEncoder.encode(token, "UTF-8") +
                "&tlm=" + URLEncoder.encode(tlm, "UTF-8")
        )
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Authorization", "APIKEY $apiKey")
            connection.setFixedLengthStreamingMode(0)
            connection.doOutput = true
            connection.outputStream.close()
            val code = connection.responseCode
            val body = runCatching {
                (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText().take(200) }
            }.getOrNull().orEmpty()
            if (code in 200..299 && !body.contains("\"error\"")) {
                if (!reportedFirstSuccess || failures > 0) {
                    // Never the position in the log: exported logs end up in public issues.
                    AppLog.i("$TAG: telemetry accepted (${AbrpTelemetry.withoutPosition(tlm)})")
                }
                reportedFirstSuccess = true
                failures = 0
            } else {
                failures++
                // A wrong token or key fails the same way every time: say so once, then quietly.
                if (failures == 1 || failures % 60 == 0) AppLog.w("$TAG: ABRP refused the telemetry: HTTP $code $body")
            }
        } catch (e: java.io.IOException) {
            failures++
            if (failures == 1 || failures % 60 == 0) {
                AppLog.w("$TAG: no internet for ABRP (${e.javaClass.simpleName}: ${e.message}) - reading dropped")
            }
        } finally {
            // Keeps the TLS connection in the pool for the next reading: a new handshake each
            // time would cost the T-box several times the reading itself.
            runCatching { connection.inputStream?.close() }
        }
    }
}

/**
 * How often a reading goes out. Driving needs it often for the plan to follow the car; a parked
 * car changes slowly, and a charging one only needs the charge to move. After repeated failures
 * (no signal, data quota used up) it backs off, so a dead T-box costs nothing.
 */
object AbrpSchedule {
    const val DRIVING_MS = 10_000L
    const val CHARGING_MS = 30_000L
    const val PARKED_MS = 300_000L
    const val BACKOFF_MS = 120_000L

    fun intervalMs(sample: AbrpSample, failures: Int): Long {
        val base = when {
            sample.isCharging == true -> CHARGING_MS
            (sample.speedKmh ?: 0.0) > 1.0 -> DRIVING_MS
            sample.isParked == false -> DRIVING_MS
            else -> PARKED_MS
        }
        return if (failures >= 3) maxOf(base, BACKOFF_MS) else base
    }
}
