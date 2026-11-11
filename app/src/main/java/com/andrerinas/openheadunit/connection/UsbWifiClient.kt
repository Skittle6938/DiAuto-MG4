package com.andrerinas.openheadunit.connection

import android.content.Context
import android.net.wifi.WifiManager
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.utils.AppLog
import com.andrerinas.openheadunit.utils.HotspotManager
import com.andrerinas.openheadunit.utils.OneRadioPolicy

/**
 * With Android Auto on the USB cable the car's Wi-Fi is free: its client is switched on so the car
 * joins a known network, typically the phone's hotspot, and has internet. Over Wi-Fi the car stays
 * the hotspot the phone is on, and in client mode it stops wireless projection, so the client is
 * switched off again when the USB session ends, but only when DiAuto switched it on.
 */
object UsbWifiClient {
    private const val TAG = "UsbWifiClient"

    private const val AP_DOWN_WAIT_MS = 6_000L

    @Volatile private var enabledByUs = false
    /** Bumped at each session start and end, so a wait outlived by its session does nothing. */
    private val generation = java.util.concurrent.atomic.AtomicInteger()

    fun onSessionStarted(context: Context, wireless: Boolean) {
        if (wireless || !App.provide(context).settings.usbWifiClient) return
        val wifi = wifiManager(context) ?: return
        if (wifi.isWifiEnabled) {
            AppLog.i("$TAG: USB session, the car's Wi-Fi client is already on")
            return
        }
        val mine = generation.incrementAndGet()
        // One radio: the client only once the access point is down (released for the USB
        // session), else the next access point follows the client's band. Off the caller's
        // thread, the wait can take seconds.
        Thread({
            val deadline = System.currentTimeMillis() + AP_DOWN_WAIT_MS
            while (HotspotManager.isApUp(context) && System.currentTimeMillis() < deadline) {
                try { Thread.sleep(200) } catch (e: InterruptedException) { return@Thread }
            }
            if (generation.get() != mine) return@Thread
            if (!OneRadioPolicy.mayStartClient(accessPointUp = HotspotManager.isApUp(context))) {
                AppLog.w("$TAG: the hotspot is still up after ${AP_DOWN_WAIT_MS} ms - leaving the car Wi-Fi client off")
                return@Thread
            }
            @Suppress("DEPRECATION")
            val ok = runCatching { wifi.setWifiEnabled(true) }.getOrElse {
                AppLog.w("$TAG: could not switch the Wi-Fi client on: ${it.message}")
                false
            }
            enabledByUs = ok
            AppLog.i("$TAG: USB session, car Wi-Fi client switched on=$ok (joins known networks)")
        }, "diauto-usb-wifi").apply { isDaemon = true }.start()
    }

    fun onSessionEnded(context: Context) {
        generation.incrementAndGet()
        if (!enabledByUs) return
        enabledByUs = false
        @Suppress("DEPRECATION")
        val ok = runCatching { wifiManager(context)?.setWifiEnabled(false) }.getOrNull()
        AppLog.i("$TAG: USB session over, car Wi-Fi client switched off=$ok")
    }

    private fun wifiManager(context: Context): WifiManager? =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
}
