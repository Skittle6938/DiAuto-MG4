package com.allgo.app.androidauto;

import android.content.Context;
import android.content.Intent;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.util.Log;

/** What callers of AllGo's Android Auto ask for, done by DiAuto (this app now carries AllGo's name). */
final class DiAutoLink {
    private static final String PROJECTION = "com.andrerinas.openheadunit.aap.AapProjectionActivity";
    private static final String MAIN = "com.andrerinas.openheadunit.main.MainActivity";

    private DiAutoLink() {
    }

    /** IAndroidAutoMainService.sendMediaPlayControl: 1 play, 2 pause, 3 next, 4 previous. */
    static void mediaControl(Context context, int code) {
        MediaController controller = diAutoSession(context);
        if (controller == null) {
            Log.w(RelayState.TAG, "media control " + code + ": no DiAuto media session");
            return;
        }
        MediaController.TransportControls t = controller.getTransportControls();
        switch (code) {
            case 1: t.play(); break;
            case 2: t.pause(); break;
            case 3: t.skipToNext(); break;
            case 4: t.skipToPrevious(); break;
            default: Log.i(RelayState.TAG, "media control " + code + " ignored"); return;
        }
        Log.i(RelayState.TAG, "media control " + code + " sent to DiAuto");
    }

    private static MediaController diAutoSession(Context context) {
        try {
            MediaSessionManager msm = context.getSystemService(MediaSessionManager.class);
            for (MediaController c : msm.getActiveSessions(null)) {
                if (context.getPackageName().equals(c.getPackageName())) return c;
            }
        } catch (Exception e) {
            Log.w(RelayState.TAG, "media sessions unreadable: " + e);
        }
        return null;
    }

    /** The Android Auto screen: DiAuto's projection while a phone is connected, else its home. */
    static void openScreen(Context context) {
        Intent intent = new Intent().setClassName(context, RelayState.isActive() ? PROJECTION : MAIN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            Log.w(RelayState.TAG, "open DiAuto failed: " + e);
            context.startActivity(new Intent().setClassName(context, MAIN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }
}
