package com.glicokids.prototype.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Module 8 — the sound preference surface on [AppPreferences]: the general switch and the
 * per-event stored URI, each a three-state property (never chosen / silenced / a real URI).
 * Same real-`SharedPreferences`-over-Robolectric pattern as [AppPreferencesLocationTest],
 * covering SPEC-MODULO-8-SOM.md §11 cases 13-17.
 *
 * RED phase: [AppPreferences.soundEnabled], [AppPreferences.medalSoundUri],
 * [AppPreferences.alertSoundUri] and [AppPreferences.doseSoundUri] are `TODO()` skeletons —
 * every test below is expected to fail with [NotImplementedError] until the next phase.
 */
@RunWith(RobolectricTestRunner::class)
class AppPreferencesSoundTest {

    private lateinit var prefs: AppPreferences

    @Before
    fun setup() {
        val context: Context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(AppPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        prefs = AppPreferences(context)
    }

    // --- Case 13: never-written keys read back as null (never chosen) ---

    @Test
    fun `medalSoundUri read before ever written is null`() {
        assertThat(prefs.medalSoundUri).isNull()
    }

    @Test
    fun `alertSoundUri read before ever written is null`() {
        assertThat(prefs.alertSoundUri).isNull()
    }

    @Test
    fun `doseSoundUri read before ever written is null`() {
        assertThat(prefs.doseSoundUri).isNull()
    }

    // --- Case 14: writing a test URI and reading it back returns the same string ---

    @Test
    fun `medalSoundUri round-trips a written URI`() {
        prefs.medalSoundUri = "content://media/external/audio/media/1"

        assertThat(prefs.medalSoundUri).isEqualTo("content://media/external/audio/media/1")
    }

    @Test
    fun `alertSoundUri round-trips a written URI`() {
        prefs.alertSoundUri = "content://media/external/audio/media/2"

        assertThat(prefs.alertSoundUri).isEqualTo("content://media/external/audio/media/2")
    }

    @Test
    fun `doseSoundUri round-trips a written URI`() {
        prefs.doseSoundUri = "content://media/external/audio/media/3"

        assertThat(prefs.doseSoundUri).isEqualTo("content://media/external/audio/media/3")
    }

    // --- Case 15: the silent sentinel round-trips as itself, never confused with null ---

    @Test
    fun `medalSoundUri round-trips the silent sentinel, never as null`() {
        prefs.medalSoundUri = AppPreferences.SILENT_SOUND_URI

        assertThat(prefs.medalSoundUri).isEqualTo(AppPreferences.SILENT_SOUND_URI)
    }

    @Test
    fun `alertSoundUri round-trips the silent sentinel, never as null`() {
        prefs.alertSoundUri = AppPreferences.SILENT_SOUND_URI

        assertThat(prefs.alertSoundUri).isEqualTo(AppPreferences.SILENT_SOUND_URI)
    }

    @Test
    fun `doseSoundUri round-trips the silent sentinel, never as null`() {
        prefs.doseSoundUri = AppPreferences.SILENT_SOUND_URI

        assertThat(prefs.doseSoundUri).isEqualTo(AppPreferences.SILENT_SOUND_URI)
    }

    // --- Case 16: soundEnabled defaults to true ---

    @Test
    fun `soundEnabled defaults to true`() {
        assertThat(prefs.soundEnabled).isTrue()
    }

    // --- Case 17: soundEnabled persists a value turned off ---

    @Test
    fun `soundEnabled persists a value turned off`() {
        prefs.soundEnabled = false

        assertThat(prefs.soundEnabled).isFalse()
    }

    @Test
    fun `soundEnabled persists turning back on`() {
        prefs.soundEnabled = false
        prefs.soundEnabled = true

        assertThat(prefs.soundEnabled).isTrue()
    }
}
