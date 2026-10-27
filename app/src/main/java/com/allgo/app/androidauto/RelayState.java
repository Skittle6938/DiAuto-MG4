package com.allgo.app.androidauto;

import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/** DiAuto's session as AllGo's rui should see it, and rui's callbacks to tell (see AndroidAutoMainService). */
public final class RelayState {
    static final String TAG = "DiAutoRelay";

    private static final String DEVICE_CB = "com.allgo.mirroring.deviceservice.IAndroidAutoDeviceCallback";
    private static final String SESSION_CB = "com.allgo.mirroring.deviceservice.IAndroidAutoSessionCallback";
    private static final int TX_DEVICE_CONNECTED = 1;
    private static final int TX_DEVICE_DISCONNECTED = 2;
    private static final int TX_SESSION_STARTED = 1;
    private static final int TX_SESSION_CLOSED = 2;
    /** IAndroidAutoSessionCallback.onSessionBTPairingRequest(String mac): rui stores the phone's MAC. */
    private static final int TX_BT_PAIRING_REQUEST = 10;
    /** The launcher can lose the session (after a car start, an AA reconnect): say it again this often. */
    private static final long REANNOUNCE_MS = 30_000L;
    private static final long PAIRING_DELAY_MS = 1500L;
    private static final android.os.Handler PAIRING_HANDLER =
            new android.os.Handler(android.os.Looper.getMainLooper());
    /** MirroringDevice.type for Android Auto, transport for wireless. */
    private static final int TYPE_AA = 2;
    // USB, not wireless: rui only matches an Android Auto device to its session by USB serial id
    // (RemoteUIDeviceListener.getReferenceFor), so a wireless one never gets "session started".
    private static final int TRANSPORT_USB = 1;
    private static final int SESSION_STARTED = 2;

    private static boolean active;
    private static String name = "Android Auto";
    private static String mac = RuiRepair.validMac(null);
    /** rui knows DiAuto by this USB serial: it stays the same when the phone's MAC changes. */
    private static String serial = serialFor(mac);
    private static final List<IBinder> deviceCallbacks = new ArrayList<>();
    private static final List<IBinder> sessionCallbacks = new ArrayList<>();

    private RelayState() {
    }

    private static boolean loaded;
    /** rui asked for the session after our last announce: it knows the phone is there. */
    private static boolean ruiAcked;
    private static android.content.SharedPreferences prefs;

    /** The last state DiAuto sent survives a restart of this process (an update, a kill). */
    public static synchronized void load(android.content.Context context) {
        if (loaded) return;
        loaded = true;
        prefs = context.getApplicationContext().getSharedPreferences("relay", 0);
        // Not restored: after a car start DiAuto has not reconnected yet, and a stale "connected"
        // would light the Android Auto tile with no phone. DiAuto repeats it at every track change.
        active = false;
        name = prefs.getString("name", name);
        mac = RuiRepair.validMac(prefs.getString("mac", mac));
        serial = prefs.getString("serial", serialFor(mac));
        Log.i(TAG, "state loaded: active=" + active);
    }

    static synchronized boolean isActive() {
        return active;
    }

    private static long lastAnnounceMs;
    /** The phone MAC rui was last given through onSessionBTPairingRequest. */
    private static String pairedMac;

    public static synchronized void update(boolean nowActive, String phoneName, String btMac) {
        boolean changed = nowActive != active;
        if (nowActive) {
            name = phoneName != null && !phoneName.isEmpty() ? phoneName : "Android Auto";
            if (btMac != null && RuiRepair.isValidMac(btMac)) mac = btMac.toUpperCase();
        }
        active = nowActive;
        if (prefs != null) {
            prefs.edit().putBoolean("active", active).putString("name", name).putString("mac", mac)
                    .putString("serial", serial).apply();
        }
        Log.i(TAG, "DiAuto session " + (active ? "started" : "ended") + " (" + name + ")");
        if (!active) {
            pairedMac = null;
            if (!changed) return;
            for (IBinder cb : sessionCallbacks) callWithDevice(cb, SESSION_CB, TX_SESSION_CLOSED, false);
            for (IBinder cb : deviceCallbacks) callWithDevice(cb, DEVICE_CB, TX_DEVICE_DISCONNECTED, false);
            return;
        }
        // An announce made while rui or the launcher were still starting is lost, and the launcher
        // sometimes drops the session later (its media card then shows "Unknown", no cover):
        // repeat it until rui asks for the session, and again every so often.
        long now = android.os.SystemClock.elapsedRealtime();
        if (changed || !ruiAcked || now - lastAnnounceMs >= REANNOUNCE_MS) {
            lastAnnounceMs = now;
            for (IBinder cb : deviceCallbacks) callWithDevice(cb, DEVICE_CB, TX_DEVICE_CONNECTED, false);
            for (IBinder cb : sessionCallbacks) callWithDevice(cb, SESSION_CB, TX_SESSION_STARTED, true);
        }
        schedulePairing();
    }

    /** The phone's own MAC became known during a session. */
    public static synchronized void phoneMac(String btMac) {
        if (btMac == null || !RuiRepair.isValidMac(btMac)) return;
        mac = btMac.toUpperCase();
        if (active) schedulePairing();
    }

    /**
     * As AllGo does after the Android Auto Bluetooth pairing request: rui stores the phone's MAC for
     * this device and tells the car, whose Settings then keep that phone's hands-free link instead
     * of cutting it (BootService.disconnectAABtHfp). The next "session started" carries it.
     */
    /** rui takes "session started" on its handler and only then has an active device to store the MAC on. */
    private static void schedulePairing() {
        PAIRING_HANDLER.postDelayed(RelayState::sendPairingNow, PAIRING_DELAY_MS);
    }

    private static synchronized void sendPairingNow() {
        if (!active || !RuiRepair.isPhoneMac(mac) || mac.equals(pairedMac) || sessionCallbacks.isEmpty()) return;
        pairedMac = mac;
        Log.i(TAG, "phone MAC to rui (Bluetooth pairing request)");
        for (IBinder cb : sessionCallbacks) {
            Parcel data = Parcel.obtain();
            try {
                data.writeInterfaceToken(SESSION_CB);
                data.writeString(mac);
                cb.transact(TX_BT_PAIRING_REQUEST, data, null, IBinder.FLAG_ONEWAY);
            } catch (Exception e) {
                Log.w(TAG, "pairing request to rui failed: " + e);
            } finally {
                data.recycle();
            }
        }
        for (IBinder cb : sessionCallbacks) callWithDevice(cb, SESSION_CB, TX_SESSION_STARTED, true);
    }

    /** rui asked to start (or resume) the session: answer as AllGo does once it has started. */
    static synchronized void sessionRequested() {
        Log.i(TAG, "rui asked for the session (active=" + active + ")");
        ruiAcked = true;
        if (!active) return;
        for (IBinder cb : sessionCallbacks) callWithDevice(cb, SESSION_CB, TX_SESSION_STARTED, true);
        schedulePairing();
    }

    static synchronized void register(IBinder deviceCb, IBinder sessionCb) {
        ruiAcked = false;
        pairedMac = null;
        deviceCallbacks.removeIf(b -> !b.isBinderAlive());
        sessionCallbacks.removeIf(b -> !b.isBinderAlive());
        Log.i(TAG, "register device=" + (deviceCb != null) + " session=" + (sessionCb != null) + " active=" + active);
        if (deviceCb != null && !deviceCallbacks.contains(deviceCb)) {
            deviceCallbacks.add(deviceCb);
            if (active) callWithDevice(deviceCb, DEVICE_CB, TX_DEVICE_CONNECTED, false);
        }
        if (sessionCb != null && !sessionCallbacks.contains(sessionCb)) {
            sessionCallbacks.add(sessionCb);
            if (active) callWithDevice(sessionCb, SESSION_CB, TX_SESSION_STARTED, true);
        }
    }

    static synchronized void unregister(IBinder deviceCb, IBinder sessionCb) {
        if (deviceCb != null) deviceCallbacks.remove(deviceCb);
        if (sessionCb != null) sessionCallbacks.remove(sessionCb);
    }

    /** MirroringDevice as rui reads it: status, name, serial, BT MAC, type, transport. */
    static synchronized void writeDevice(Parcel p) {
        p.writeInt(active ? SESSION_STARTED : 0);
        p.writeString(name);
        p.writeString(serial);
        p.writeString(mac);
        p.writeInt(TYPE_AA);
        p.writeInt(TRANSPORT_USB);
    }

    private static String serialFor(String btMac) {
        return "DIAUTO" + btMac.replace(":", "");
    }

    private static void callWithDevice(IBinder cb, String descriptor, int code, boolean launchStatus) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        Log.i(TAG, "announce " + descriptor.substring(descriptor.lastIndexOf(46) + 1) + "#" + code + " active=" + active);
        try {
            data.writeInterfaceToken(descriptor);
            data.writeInt(1);
            writeDevice(data);
            if (launchStatus) data.writeInt(0);
            // One-way: rui may call back into this service from its handler; never hold it waiting.
            cb.transact(code, data, reply, IBinder.FLAG_ONEWAY);
        } catch (Exception e) {
            Log.w(TAG, "callback " + descriptor + "#" + code + " failed: " + e);
        } finally {
            reply.recycle();
            data.recycle();
        }
    }
}
