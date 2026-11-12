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

    /** Runs {@code attempt} over the Wi-Fi if there is one, then once more over the default network if it failed. */
    static <T> T wifiFirst(Context context, String what, Attempt<T> attempt) throws Exception {
        Network wifi = validatedWifi(context);
        if (wifi != null) {
            try {
                T result = attempt.run(wifi);
                Log.i(TAG, what + " over the Wi-Fi");
                return result;
            } catch (IOException e) {
                Log.w(TAG, what + " failed over the Wi-Fi (" + e.getMessage() + "), trying the default network");
            }
        }
        T result = attempt.run(null);
        Log.i(TAG, what + " over the " + describe(null));
        return result;
    }
}
