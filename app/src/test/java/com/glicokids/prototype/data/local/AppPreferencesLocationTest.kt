package com.glicokids.prototype.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Module 7 — the new alert-location surface on [AppPreferences]: whether an alert is allowed to
 * carry a location hint at all, and the single last-known-location record a future map screen
 * will read. No dedicated `AppPreferencesTest` exists for the rest of the class — every other
 * property there is exercised indirectly through mocks in its callers' tests — but these three
 * get a real, non-mocked Robolectric test (same pattern as `ReportStorageTest`) because the
 * consent default in particular is safety-relevant, worth proving against real
 * `SharedPreferences` rather than trusting a mock stub to match the real default.
 */
@RunWith(RobolectricTestRunner::class)
class AppPreferencesLocationTest {

    private lateinit var prefs: AppPreferences

    @Before
    fun setup() {
        val context: Context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(AppPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        prefs = AppPreferences(context)
    }

    @Test
    fun `alertIncludeLocation defaults to on`() {
        assertThat(prefs.alertIncludeLocation).isTrue()
    }

    @Test
    fun `alertIncludeLocation persists a value turned off`() {
        prefs.alertIncludeLocation = false

        assertThat(prefs.alertIncludeLocation).isFalse()
    }

    @Test
    fun `no last alert location is recorded before saveLastAlertLocation is ever called`() {
        assertThat(prefs.lastAlertLocationAt).isEqualTo(0L)
        assertThat(prefs.lastAlertLocationLabel).isNull()
    }

    @Test
    fun `saveLastAlertLocation records lat, lng, label and timestamp together`() {
        prefs.saveLastAlertLocation(lat = -23.55, lng = -46.63, label = "Rua Tal, 123", atMillis = 1_700_000_000_000L)

        assertThat(prefs.lastAlertLocationLat).isWithin(0.001).of(-23.55)
        assertThat(prefs.lastAlertLocationLng).isWithin(0.001).of(-46.63)
        assertThat(prefs.lastAlertLocationLabel).isEqualTo("Rua Tal, 123")
        assertThat(prefs.lastAlertLocationAt).isEqualTo(1_700_000_000_000L)
    }

    @Test
    fun `saveLastAlertLocation accepts a null label without breaking the coordinate round trip`() {
        prefs.saveLastAlertLocation(lat = 10.0, lng = 20.0, label = null, atMillis = 5_000L)

        assertThat(prefs.lastAlertLocationLabel).isNull()
        assertThat(prefs.lastAlertLocationLat).isWithin(0.001).of(10.0)
    }
}
