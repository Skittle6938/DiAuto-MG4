package com.andrerinas.openheadunit.ota;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.util.Log;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Update traffic on the car's Wi-Fi first, the T-box 4G only without one (owner's choice, as in
 * MG4Dash): an APK is 30 MB of a small data plan. Only a Wi-Fi Android has validated (internet
 * through it) is used, and only for these connections: the process is never bound to it, so
 * Android Auto's own sockets on the hotspot are left alone.
 */
final class OtaNetwork {

    private static final String TAG = "DiAuto_OTA";

    private OtaNetwork() {
    }

    /** The car's Wi-Fi client with working internet, null without one. */
    static Network validatedWifi(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return null;
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(n);
                if (caps != null
                        && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                    return n;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "no network list: " + e.getMessage());
        }
        return null;
    }

    /** A connection to {@code url} over {@code network}, or Android's default network when null. */
    static HttpURLConnection open(Network network, URL url) throws IOException {
        return (HttpURLConnection) (network != null ? network.openConnection(url) : url.openConnection());
    }

    static String describe(Network network) {
        return network != null ? "Wi-Fi" : "default network (4G)";
    }

    interface Attempt<T> {
        T run(Network network) throws Exception;
    }

    /**
     * A network other than the Wi-Fi to fall back on (the T-box): Android's default network when it
     * is not a Wi-Fi, else the first other network with internet. Null when there is none: with the
     * Wi-Fi as Android's default network, a second try "over the default network" would only go
     * over the same weak Wi-Fi again.
     */
    static Network otherThanWifi(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return null;
            Network active = cm.getActiveNetwork();
            if (active != null && !isWifi(cm, active)) return active;
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(n);
                if (caps != null && !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    return n;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "no network list: " + e.getMessage());
        }
        return null;
    }

    private static boolean isWifi(ConnectivityManager cm, Network n) {
        NetworkCapabilities caps = cm.getNetworkCapabilities(n);
        return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    /** MG4Dash's "Updates on Wi-Fi only" (Settings › 4G data), shared in Settings.Global; off without MG4Dash. */
    static final String MG4DASH_WIFI_ONLY = "mg4dash_updates_wifi_only";

    static boolean wifiOnly(Context context) {
        try {
            return "1".equals(android.provider.Settings.Global.getString(context.getContentResolver(), MG4DASH_WIFI_ONLY));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Runs {@code attempt} over the Wi-Fi if there is one, then once more over the T-box if it failed.
     * With MG4Dash's "Wi-Fi only" on, over the Wi-Fi alone.
     */
    static <T> T wifiFirst(Context context, String what, Attempt<T> attempt) throws Exception {
        Network wifi = validatedWifi(context);
        if (wifiOnly(context)) {
            if (wifi == null) {
                Log.w(TAG, what + ": MG4Dash allows updates on Wi-Fi only, and the car has no Wi-Fi with internet");
                throw new IOException("Wi-Fi only (MG4Dash): no Wi-Fi");
            }
            return runLogged(what, "Wi-Fi (Wi-Fi only in MG4Dash)", wifi, attempt);
        }
        if (wifi == null) {
            // No Wi-Fi client: Android's default is the T-box if it lists it, else the T-box is asked for
            Network other = otherThanWifi(context);
            if (other != null) return runLogged(what, "4G", other, attempt);
            Log.i(TAG, what + ": no Wi-Fi and no T-box listed, asking Android for the T-box");
            try {
                return overRequestedTbox(context, what, attempt, new IOException("no network"));
            } catch (IOException e) {
                return runLogged(what, "default network", null, attempt);
            }
        }
        try {
            T result = attempt.run(wifi);
            Log.i(TAG, what + " over the Wi-Fi");
            return result;
        } catch (IOException e) {
            Network other = otherThanWifi(context);
            if (other != null) {
                Log.w(TAG, what + " failed over the Wi-Fi (" + e.getMessage() + "), trying the 4G");
                return runLogged(what, "4G", other, attempt);
            }
            Log.w(TAG, what + " failed over the Wi-Fi (" + e.getMessage() + "), asking Android for the T-box");
            return overRequestedTbox(context, what, attempt, e);
        }
    }

    static final long TBOX_WAIT_MS = 10_000;

    /**
     * With the car's Wi-Fi client on, Android drops the T-box network (an Ethernet link to Android)
     * because nothing needs it, though tbox0 keeps its address. Asking for a wired network with
     * internet brings it back next to the Wi-Fi, for this attempt only: the Wi-Fi client and the
     * default network do not change, and the T-box goes idle again once the request is released.
     */
    private static <T> T overRequestedTbox(Context context, String what, Attempt<T> attempt, IOException wifiError) throws Exception {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) throw wifiError;
        AtomicReference<Network> found = new AtomicReference<>();
        CountDownLatch ready = new CountDownLatch(1);
        ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                found.compareAndSet(null, network);
                ready.countDown();
            }

            @Override
            public void onUnavailable() {
                ready.countDown();
            }
        };
        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        try {
            cm.requestNetwork(request, callback, (int) TBOX_WAIT_MS);
        } catch (Exception e) {
            Log.w(TAG, what + ": T-box request refused (" + e.getMessage() + ")");
            throw wifiError;
        }
        try {
            ready.await(TBOX_WAIT_MS + 1_000, TimeUnit.MILLISECONDS);
            Network tbox = found.get();
            if (tbox == null) {
                Log.w(TAG, what + ": no T-box network within " + TBOX_WAIT_MS / 1000 + " s, no other network to try");
                throw wifiError;
            }
            return runLogged(what, "4G", tbox, attempt);
        } finally {
            try {
                cm.unregisterNetworkCallback(callback);
            } catch (Exception ignored) {
                // already released after a timeout
            }
        }
    }

    private static <T> T runLogged(String what, String name, Network network, Attempt<T> attempt) throws Exception {
        try {
            T result = attempt.run(network);
            Log.i(TAG, what + " over the " + name);
            return result;
        } catch (Exception e) {
            Log.w(TAG, what + " failed over the " + name + " (" + e.getMessage() + ")");
            throw e;
        }
    }
}
