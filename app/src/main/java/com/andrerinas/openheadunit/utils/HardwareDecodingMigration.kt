package com.andrerinas.openheadunit.utils

import android.content.SharedPreferences

/**
 * Once per install: puts every driver back on hardware video decoding (Marc, 2026-11-12). The
 * settings screens of 2.0/2.1 could switch software decoding on, the bundled FFmpeg decoder by
 * default, and that choice outlived the screens. The FFmpeg decoder is gone, so those keys are
 * dropped and the hardware decoder (with Android's own software one as its fallback) is used.
 */
object HardwareDecodingMigration {

    const val DONE_KEY = "migration-hardware-decoding-v1"

    /** The keys that selected a software video decoder. */
    val SOFTWARE_DECODING_KEYS = listOf("force-software-decoding", "software-video-decoder")

    /**
     * @return the keys removed this time (empty when already done or nothing was set), so the
     *   caller can log whether it changed anything.
     */
    fun apply(prefs: SharedPreferences): List<String> {
        if (prefs.getBoolean(DONE_KEY, false)) return emptyList()
        val present = SOFTWARE_DECODING_KEYS.filter { prefs.contains(it) }
        val editor = prefs.edit()
        present.forEach { editor.remove(it) }
        editor.putBoolean(DONE_KEY, true).apply()
        return present
    }
}
