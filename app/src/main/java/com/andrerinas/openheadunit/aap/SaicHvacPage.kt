package com.andrerinas.openheadunit.aap

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.SystemClock
import com.andrerinas.openheadunit.utils.AppLog
import com.evsuite.hardware.FirmwareInfo

/**
 * SWI68 climate page (#13): when the climate comes back on as the car starts, SystemUI opens its
 * climate page on its own, on top of Android Auto. MG4Link closes it, as SystemUI itself does,
 * through its exported StartActivityService, during the first minute after the start only: a
 * page the driver opens later stays open. R73 names the interface IPageService (closeHvac),
 * R46 IStartActivityService (stopHVAC); both use transaction 2. MG4 Dash does the same (#62).
 */
object SaicHvacPage {
    private const val ACTION = "com.android.systemui.saicmotor.action.StartActivity"
    private val DESCRIPTORS = listOf("com.saicmotor.sdk.external.IPageService", "com.android.systemui.IStartActivityService")
    private const val TX_CLOSE = 2
    private const val WATCH_MS = 60_000L
    private const val POLL_MS = 500L
    /** SystemUI posts the opening on its handler: closing at once could come before it. */
    private const val CLOSE_DELAY_MS = 300L

    private val handler = Handler(Looper.getMainLooper())
    /** MG4Link starts with the car. */
    private val startedAtMs = android.os.Process.getStartElapsedRealtime()
    @Volatile private var binder: IBinder? = null
    @Volatile private var binding = false
    private var watching = false
    private var lastPower: Boolean? = null
    private var appContext: Context? = null

    private fun applies() = FirmwareInfo.getGeneration() == FirmwareInfo.Gen.SWI68

    /** Android Auto took the screen: watches the climate for the rest of the first minute. */
    fun onProjectionShown(context: Context) {
        if (!applies() || watching) return
        val left = WATCH_MS - (SystemClock.elapsedRealtime() - startedAtMs)
        if (left <= 0) return
        appContext = context.applicationContext
        bind(context.applicationContext)
        watching = true
        lastPower = null
        handler.post(poll)
        handler.postDelayed({ stop() }, left)
    }

    private fun stop() {
        watching = false
        handler.removeCallbacks(poll)
    }

    private val poll = object : Runnable {
        override fun run() {
            if (!watching) return
            Thread({
                val power = runCatching { com.evsuite.hardware.saic.SaicClimate.powerOn() }.getOrNull()
                handler.post {
                    // On at the first reading too: the page opened just before Android Auto came up.
                    if (power == true && lastPower != true && AapProjectionActivity.isForeground) {
                        AppLog.i("SaicHvacPage: climate on at start with Android Auto in front - closing the climate page")
                        handler.postDelayed({ Thread({ close() }, "hvac-page").start() }, CLOSE_DELAY_MS)
                    }
                    if (power != null) lastPower = power
                    if (watching) handler.postDelayed(this, POLL_MS)
                }
            }, "hvac-page").start()
        }
    }

    private fun bind(ctx: Context) {
        if (binder?.isBinderAlive == true || binding) return
        binding = true
        val i = Intent(ACTION).setPackage("com.android.systemui")
            .setComponent(ComponentName("com.android.systemui", "com.android.systemui.StartActivityService"))
        val ok = try {
            ctx.bindService(i, object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) { binder = service; binding = false }
                override fun onServiceDisconnected(name: ComponentName?) { binder = null; binding = false }
            }, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            AppLog.w("SaicHvacPage: bind failed: ${e.message}")
            false
        }
        if (!ok) {
            binding = false
            AppLog.i("SaicHvacPage: SystemUI StartActivityService not available on this firmware")
        }
    }

    private fun close() {
        val b = binder?.takeIf { it.isBinderAlive } ?: run {
            appContext?.let { bind(it) }
            AppLog.i("SaicHvacPage: SystemUI service not connected yet - page left open")
            return
        }
        for (descriptor in DESCRIPTORS) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(descriptor)
                b.transact(TX_CLOSE, data, reply, 0)
                reply.readException()
                AppLog.i("SaicHvacPage: climate page closed (${descriptor.substringAfterLast('.')})")
                return
            } catch (e: Exception) {
                AppLog.i("SaicHvacPage: ${descriptor.substringAfterLast('.')} refused: ${e.message}")
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
    }
}
