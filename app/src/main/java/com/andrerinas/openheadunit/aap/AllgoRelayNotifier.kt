package com.andrerinas.openheadunit.aap

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.andrerinas.openheadunit.utils.AppLog

/**
 * Tells the experimental AllGo relay (an update of `com.allgo.app.androidauto` that stands in for
 * AllGo's Android Auto, kept outside this repo) when a DiAuto session starts or ends, so the stock
 * launcher's Android Auto button and media card see a connected phone. Without the relay this
 * does nothing.
 */
object AllgoRelayNotifier {
    const val RELAY_PACKAGE = "com.allgo.app.androidauto"
    private const val RELAY_META = "diauto.relay"
    private val RECEIVER = ComponentName(RELAY_PACKAGE, "com.allgo.app.androidauto.RelayStateReceiver")
    const val ACTION_STATE = "com.drivehub.diauto.mg4.RELAY_STATE"

    /** The installed `com.allgo.app.androidauto` is the relay, not AllGo's app. */
    fun relayInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getApplicationInfo(RELAY_PACKAGE, PackageManager.GET_META_DATA)
            .metaData?.getBoolean(RELAY_META, false) == true
    }.getOrDefault(false)

    fun sessionStarted(context: Context, phoneName: String?, btMac: String?) = send(context, true, phoneName, btMac)

    fun sessionEnded(context: Context) = send(context, false, null, null)

    private fun send(context: Context, active: Boolean, phoneName: String?, btMac: String?) {
        if (!relayInstalled(context)) return
        runCatching {
            context.sendBroadcast(Intent(ACTION_STATE).setComponent(RECEIVER).apply {
                putExtra("active", active)
                putExtra("name", phoneName ?: "Android Auto")
                putExtra("mac", btMac ?: "")
            })
            AppLog.i("AllgoRelay: session ${if (active) "started" else "ended"} sent to the relay")
        }.onFailure { AppLog.w("AllgoRelay: notify failed: ${it.message}") }
    }
}
