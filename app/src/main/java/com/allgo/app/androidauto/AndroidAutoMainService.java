package com.allgo.app.androidauto;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;

/**
 * Answers com.allgo.rui as AllGo's Android Auto service would (IAndroidAutoMainService, read from
 * rui's own proxy), with DiAuto's session as the connected phone. DiAuto is installed as an update of
 * com.allgo.app.androidauto, so rui, the stock launcher and MG4 Dash find it under AllGo's name.
 */
public class AndroidAutoMainService extends Service {

    private static final String DESCRIPTOR = "com.allgo.app.androidauto.IAndroidAutoMainService";

    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());

    /** Apps outside the car's own that may still drive the session (MG4 Dash's media controls). */
    private static final String[] ALLOWED_PACKAGES = { "app.mg4dash" };

    /**
     * Only the car's own apps (system ids, or signed with the car's platform key like rui and the
     * launcher) and MG4 Dash: this service hands out the phone's name and Bluetooth address and
     * drives the assistant and the media, so any other app installed on the car is refused.
     */
    /**
     * Which car app asked. On the car every caller runs as uid 1000, which ~45 packages share:
     * only the calling process tells which app it is (issue #4). The package list is kept for
     * when the process is unknown.
     */
    private String callerName(int uid, int pid) {
        String process = pid > 0 ? processName(pid) : null;
        if (process != null) return "pid " + pid + " " + process + " (uid " + uid + ")";
        try {
            String[] packages = getPackageManager().getPackagesForUid(uid);
            if (packages != null && packages.length > 0) {
                return "pid " + pid + " uid " + uid + " " + String.join(",", packages);
            }
        } catch (Exception ignored) {
        }
        return "pid " + pid + " uid " + uid;
    }

    /** The running process with this pid, else its /proc command line. Not cached: pids are reused. */
    private String processName(int pid) {
        try {
            android.app.ActivityManager am = getSystemService(android.app.ActivityManager.class);
            java.util.List<android.app.ActivityManager.RunningAppProcessInfo> running =
                    am != null ? am.getRunningAppProcesses() : null;
            if (running != null) {
                for (android.app.ActivityManager.RunningAppProcessInfo info : running) {
                    if (info.pid == pid) return info.processName;
                }
            }
        } catch (Exception ignored) {
        }
        try (java.io.FileInputStream in = new java.io.FileInputStream("/proc/" + pid + "/cmdline")) {
            byte[] buf = new byte[256];
            int n = in.read(buf);
            int end = 0;
            while (end < n && buf[end] != 0) end++;
            if (end > 0) return new String(buf, 0, end, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
        return null;
    }

    private boolean callerAllowed(int uid) {
        if (uid < android.os.Process.FIRST_APPLICATION_UID || uid == android.os.Process.myUid()) return true;
        android.content.pm.PackageManager pm = getPackageManager();
        try {
            if (pm.checkSignatures(android.os.Process.myUid(), uid) == android.content.pm.PackageManager.SIGNATURE_MATCH) return true;
            String[] packages = pm.getPackagesForUid(uid);
            if (packages != null) {
                for (String pkg : packages) {
                    for (String allowed : ALLOWED_PACKAGES) if (allowed.equals(pkg)) return true;
                }
            }
        } catch (Exception e) {
            RelayLog.w("caller check failed: " + e);
        }
        return false;
    }

    private final Binder binder = new Binder() {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            if (code == INTERFACE_TRANSACTION) {
                reply.writeString(DESCRIPTOR);
                return true;
            }
            data.enforceInterface(DESCRIPTOR);
            int uid = Binder.getCallingUid();
            int pid = Binder.getCallingPid();
            if (!callerAllowed(uid)) {
                RelayLog.w("call " + code + " refused for " + callerName(uid, pid));
                reply.writeException(new SecurityException("not a car app"));
                return true;
            }
            RelayLog.i("call " + code + " from " + callerName(uid, pid));
            switch (code) {
                case 1: // register(deviceCb, sessionCb)
                    RelayState.register(data.readStrongBinder(), data.readStrongBinder());
                    reply.writeNoException();
                    return true;
                case 8: // unregister(deviceCb, sessionCb)
                    RelayState.unregister(data.readStrongBinder(), data.readStrongBinder());
                    reply.writeNoException();
                    return true;
                case 6: // getActiveDevice()
                    reply.writeNoException();
                    if (RelayState.isActive()) {
                        reply.writeInt(1);
                        RelayState.writeDevice(reply);
                    } else {
                        reply.writeInt(0);
                    }
                    return true;
                case 7: // isSessionActive()
                case 24: // getVRStatus()
                    reply.writeNoException();
                    reply.writeInt(code == 7 && RelayState.isActive() ? 1 : 0);
                    return true;
                case 2: // startSession(device, screenMode, audioMode)
                case 3: // resumeSession(device)
                    handler.postDelayed(RelayState::sessionRequested, 300);
                    reply.writeNoException();
                    reply.writeInt(0);
                    return true;
                case 25: { // sendMediaPlayControl(code)
                    int key = data.readInt();
                    handler.post(() -> DiAutoLink.mediaControl(AndroidAutoMainService.this, key));
                    reply.writeNoException();
                    reply.writeInt(0);
                    return true;
                }
                case 10: { // launchGoogleAssistant(boolean)
                    data.readInt();
                    handler.post(() -> DiAutoLink.launchAssistant(AndroidAutoMainService.this));
                    reply.writeNoException();
                    reply.writeInt(RelayState.isActive() ? 0 : -1);
                    return true;
                }
                case 11: { // launchApp(app): 0 home, 1 music, 2 navigation, 3 phone, 4 now playing
                    int app = data.readInt();
                    handler.post(() -> DiAutoLink.launchApp(AndroidAutoMainService.this, app));
                    reply.writeNoException();
                    reply.writeInt(RelayState.isActive() ? 0 : -1);
                    return true;
                }
                case 12: // getPluginVersion()
                    reply.writeNoException();
                    reply.writeString("diauto-relay-1.0");
                    return true;
                case 9: case 19: case 20: case 21: case 22: case 23: // void calls
                    reply.writeNoException();
                    return true;
                default: // every other call returns an int status: 0 = OK
                    RelayLog.i("rui call " + code + " answered OK");
                    reply.writeNoException();
                    reply.writeInt(0);
                    return true;
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        RelayState.load(this);
        RuiRepair.run(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        RelayLog.i("bound by " + intent);
        RuiRepair.run(this);
        return binder;
    }
}
