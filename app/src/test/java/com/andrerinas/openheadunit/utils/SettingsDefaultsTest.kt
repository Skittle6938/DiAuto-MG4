package com.andrerinas.openheadunit.utils

import android.app.Application
import android.content.Context
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

/**
 * Defaults a fresh install gets, and the migration of older settings: a changed default reaches
 * every driver at the next update, so each one here was chosen on purpose.
 */
// Settings tests do not use TLS; the app bundles an Android-only Conscrypt native library.
@ConscryptMode(ConscryptMode.Mode.OFF)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class SettingsDefaultsTest {

    private lateinit var context: Context
    private lateinit var settings: Settings

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(Settings.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        settings = Settings(context)
    }

    private fun prefs() = context.getSharedPreferences(Settings.PREFS_NAME, Context.MODE_PRIVATE)

    @Test
    fun `the first call to the phone waits 15 s by default and stays within 0 to 60 s`() {
        assertEquals(15, settings.firstPhoneCallDelaySeconds)
        settings.firstPhoneCallDelaySeconds = 0
        assertEquals(0, settings.firstPhoneCallDelaySeconds)
        settings.firstPhoneCallDelaySeconds = 500
        assertEquals(60, settings.firstPhoneCallDelaySeconds)
    }

    @Test
    fun `the mic records from the default source`() {
        assertEquals(0, settings.micInputSource)
    }

    @Test
    fun `Android Auto takes the screen when the phone connects by default`() {
        assertTrue(settings.openProjectionOnConnect)
    }

    @Test
    fun `Start with the car in the background keeps Android Auto off the screen`() {
        settings.openProjectionOnConnect = false
        assertFalse(settings.openProjectionOnConnect)
        assertTrue(settings.autoStartOnBoot)
    }

    @Test
    fun `Maps energy model on and trip recording off by default`() {
        assertTrue(settings.mapsEnergyModel)
        assertFalse(settings.tripRecording)
    }

    @Test
    fun `a value set is read back`() {
        settings.micInputSource = 1
        assertEquals(1, Settings(context).micInputSource)
    }
}
