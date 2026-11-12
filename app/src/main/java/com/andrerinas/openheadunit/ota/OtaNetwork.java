package com.andrerinas.openheadunit.ota;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.util.Log;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

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

    /** Runs {@code attempt} over the Wi-Fi if there is one, then once more over the T-box if it failed. */
    static <T> T wifiFirst(Context context, String what, Attempt<T> attempt) throws Exception {
        Network wifi = validatedWifi(context);
        if (wifi == null) return runLogged(what, "default network (4G)", null, attempt);
        try {
            T result = attempt.run(wifi);
            Log.i(TAG, what + " over the Wi-Fi");
            return result;
        } catch (IOException e) {
            Network other = otherThanWifi(context);
            if (other == null) {
                Log.w(TAG, what + " failed over the Wi-Fi (" + e.getMessage() + "), no other network to try");
                throw e;
            }
            Log.w(TAG, what + " failed over the Wi-Fi (" + e.getMessage() + "), trying the 4G");
            return runLogged(what, "4G", other, attempt);
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
