package com.andrerinas.openheadunit.ota;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.andrerinas.openheadunit.aap.AapService;

import java.lang.reflect.Method;

/**
 * Uygulama OTA ile yenilendikten sonra İndirilenler’deki eski APK’ları siler.
 * The install kills the old process, so the new version is recompiled and the service
 * restarted here; otherwise Android Auto stays down until the next car start.
 */
public final class OtaPackageReplacedReceiver extends BroadcastReceiver {

    private static final String TAG = "DiAuto_OTA";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        if (!Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) return;
        Log.i(TAG, "MY_PACKAGE_REPLACED → eski OTA APK temizliği");
        OtaCleanup.deleteAllOtaApks(context);
        recompile(context);
        try {
            ContextCompat.startForegroundService(context, new Intent(context, AapService.class));
            Log.i(TAG, "AapService restarted after the update");
        } catch (Throwable t) {
            Log.w(TAG, "AapService restart failed: " + t.getMessage());
        }
    }

    /**
     * Same as "adb shell cmd package compile -m speed -f": on the MG4 the compilation done at
     * install time is sometimes unusable (ClassNotFoundException at start). Needs the system uid.
     */
    private static void recompile(Context context) {
        try {
            Object pm = Class.forName("android.app.ActivityThread")
                    .getMethod("getPackageManager").invoke(null);
            for (Method m : pm.getClass().getMethods()) {
                if (m.getName().equals("performDexOptMode") && m.getParameterTypes().length == 6) {
                    Object ok = m.invoke(pm, context.getPackageName(), false, "speed", true, true, null);
                    Log.i(TAG, "recompile: " + ok);
                    return;
                }
            }
            Log.w(TAG, "recompile: performDexOptMode not found");
        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            Log.w(TAG, "recompile failed: " + cause.getMessage());
        }
    }
}
