package com.andrerinas.openheadunit.ota;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.andrerinas.openheadunit.aap.AapService;

/**
 * Uygulama OTA ile yenilendikten sonra İndirilenler’deki eski APK’ları siler.
 * The install kills the old process, so the service is restarted here; otherwise Android Auto
 * stays down until the next car start. No recompile from here: this process already runs the
 * new code, and a forced dexopt while it is mapped fails on the MG4 and deletes the odex the
 * install produced (the app then runs from the APK).
 */
public final class OtaPackageReplacedReceiver extends BroadcastReceiver {

    private static final String TAG = "DiAuto_OTA";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        if (!Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) return;
        Log.i(TAG, "MY_PACKAGE_REPLACED → eski OTA APK temizliği");
        OtaCleanup.deleteAllOtaApks(context);
        try {
            ContextCompat.startForegroundService(context, new Intent(context, AapService.class));
            Log.i(TAG, "AapService restarted after the update");
        } catch (Throwable t) {
            Log.w(TAG, "AapService restart failed: " + t.getMessage());
        }
    }
}
