package com.allgo.app.androidauto;

import android.util.Log;

/**
 * The relay's lines go to logcat and to DiAuto's own log too: with the app log as the log source,
 * an exported log had none of them, so it could not show whether the car's phone service talks to
 * the relay at all (issue #4, SWI68).
 */
final class RelayLog {
    private RelayLog() {}

    static void i(String msg) {
        Log.i(RelayState.TAG, msg);
        toAppLog(msg, false);
    }

    static void w(String msg) {
        Log.w(RelayState.TAG, msg);
        toAppLog(msg, true);
    }

    private static void toAppLog(String msg, boolean warn) {
        try {
            if (warn) com.andrerinas.openheadunit.utils.AppLog.INSTANCE.w(RelayState.TAG + ": " + msg);
            else com.andrerinas.openheadunit.utils.AppLog.INSTANCE.i(RelayState.TAG + ": " + msg);
        } catch (Throwable ignored) {
            // The app log may not be up yet when the car binds the relay at boot.
        }
    }
}
