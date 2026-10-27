package com.andrerinas.openheadunit.aap

import android.content.Context
import com.allgo.app.androidauto.RelayState

/**
 * DiAuto carries AllGo's package name and answers rui itself
 * (com.allgo.app.androidauto.AndroidAutoMainService): this tells that service when a session starts
 * or ends, so the stock launcher's Android Auto tile and media card see a connected phone.
 */
object AllgoRelayNotifier {
    fun sessionStarted(context: Context, phoneName: String?, btMac: String?) {
        RelayState.load(context)
        RelayState.update(true, phoneName, btMac)
    }

    fun sessionEnded(context: Context) {
        RelayState.load(context)
        RelayState.update(false, null, null)
    }
}
