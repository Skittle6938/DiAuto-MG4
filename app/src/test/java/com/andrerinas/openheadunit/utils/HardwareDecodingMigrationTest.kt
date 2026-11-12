package com.andrerinas.openheadunit.utils

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

// Settings tests do not use TLS; the app bundles an Android-only Conscrypt native library.
@ConscryptMode(ConscryptMode.Mode.OFF)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class HardwareDecodingMigrationTest {

    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        prefs = RuntimeEnvironment.getApplication()
            .getSharedPreferences(Settings.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun `software decoding saved by an older version is removed once`() {
        prefs.edit()
            .putBoolean("force-software-decoding", true)
            .putInt("software-video-decoder", 1)
            .putInt("media-audio-buffer-ms", 500)
            .commit()

        assertEquals(listOf("force-software-decoding", "software-video-decoder"), HardwareDecodingMigration.apply(prefs))

        assertFalse(prefs.contains("force-software-decoding"))
        assertFalse(prefs.contains("software-video-decoder"))
        // Nothing else is touched.
        assertEquals(500, prefs.getInt("media-audio-buffer-ms", 0))
        assertTrue(prefs.getBoolean(HardwareDecodingMigration.DONE_KEY, false))
    }

    @Test
    fun `runs only once`() {
        HardwareDecodingMigration.apply(prefs)
        prefs.edit().putBoolean("force-software-decoding", true).commit()

        assertEquals(emptyList<String>(), HardwareDecodingMigration.apply(prefs))
        assertTrue(prefs.contains("force-software-decoding"))
    }

    @Test
    fun `a fresh install has nothing to change but is marked done`() {
        assertEquals(emptyList<String>(), HardwareDecodingMigration.apply(prefs))
        assertTrue(prefs.getBoolean(HardwareDecodingMigration.DONE_KEY, false))
    }
}
