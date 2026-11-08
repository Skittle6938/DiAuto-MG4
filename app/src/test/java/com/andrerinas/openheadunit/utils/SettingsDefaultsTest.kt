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

/**
 * Defaults a fresh install gets, and the migration of older settings: a changed default reaches
 * every driver at the next update, so each one here was chosen on purpose.
 */
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
    fun `the mic records from the default source`() {
        assertEquals(0, settings.micInputSource)
    }

    @Test
    fun `Android Auto takes the screen when the phone connects by default`() {
        assertEquals(Settings.PROJECTION_OPEN, settings.projectionOnConnect)
        assertTrue(settings.openProjectionOnConnect)
    }

    @Test
    fun `the former Open Android Auto when connected setting maps to the new choices`() {
        prefs().edit().putBoolean("open-projection-on-connect", false).commit()
        assertEquals(Settings.PROJECTION_HIDDEN, settings.projectionOnConnect)
        assertFalse(settings.openProjectionOnConnect)

        prefs().edit().putBoolean("open-projection-on-connect", true).commit()
        assertEquals(Settings.PROJECTION_OPEN, settings.projectionOnConnect)
    }

    @Test
    fun `the new choice wins over the former setting`() {
        prefs().edit().putBoolean("open-projection-on-connect", true).commit()
        settings.projectionOnConnect = Settings.PROJECTION_BACKGROUND
        assertEquals(Settings.PROJECTION_BACKGROUND, settings.projectionOnConnect)
        assertFalse(settings.openProjectionOnConnect)
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
