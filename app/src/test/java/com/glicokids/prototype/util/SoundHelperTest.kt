package com.glicokids.prototype.util

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.glicokids.prototype.data.local.AppPreferences
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Resetter
import org.robolectric.shadows.ShadowToneGenerator
import java.io.File
import java.time.Duration

/**
 * Module 8 — covers [SoundHelper], SPEC-MODULO-8-SOM.md §11, cases 1-12 plus the direct
 * `toStoredValue`/`labelFor` translation cases from §10.
 *
 * RED phase: [SoundHelper.play]/[SoundHelper.toStoredValue]/[SoundHelper.labelFor] and every
 * touched [AppPreferences] sound property are `TODO()` skeletons — every test below is expected
 * to fail (mostly with [NotImplementedError]) until the next phase implements the real logic.
 *
 * There is no `ShadowRingtone` in Robolectric 4.16.1 (confirmed against the
 * `shadows-framework-4.16.1.jar` on this machine — only `ShadowRingtoneManager` and
 * `ShadowToneGenerator` exist), so this suite registers its own local shadow,
 * [ShadowRingtoneManagerRecorder], replacing the built-in one for this test class only
 * (`@Config(shadows = [...])`). It shadows the one static method [SoundHelper] actually calls —
 * `RingtoneManager.getRingtone(Context, Uri)` — recording every URI requested and always
 * returning `null`. That `null` is not a workaround: in a Robolectric JVM there is no real
 * device sound behind any test URI anyway, so "does not resolve" is the honest behavior for
 * every URI this suite can construct — it is also exactly the case SPEC §7/§11 case 11 asks to
 * be proven safe.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [SoundHelperTest.ShadowRingtoneManagerRecorder::class])
class SoundHelperTest {

    /** Records every `Uri` passed into `RingtoneManager.getRingtone`, standing in for the
     * missing `ShadowRingtone` — see the class kdoc above. */
    @Implements(RingtoneManager::class)
    class ShadowRingtoneManagerRecorder {
        companion object {
            val requestedUris = mutableListOf<Uri?>()

            @JvmStatic
            @Implementation
            fun getRingtone(context: Context?, uri: Uri?): Ringtone? {
                requestedUris.add(uri)
                return null
            }

            @JvmStatic
            @Resetter
            fun reset() {
                requestedUris.clear()
            }
        }
    }

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var prefs: AppPreferences

    @Before
    fun setup() {
        application.getSharedPreferences(AppPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        prefs = AppPreferences(application)
        ShadowRingtoneManagerRecorder.reset()
        ShadowToneGenerator.reset()
    }

    /** Runs every `Handler.postDelayed` task the synthesized alert pattern scheduled — SPEC §8
     * describes ~1.5s of tones and pauses plus a safety margin before `release()`; 3s comfortably
     * clears that whole window. */
    private fun idlePastAlertPattern() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
    }

    // --- Case 1: each event resolves the URI stored in ITS OWN preference, never a neighbour's ---

    @Test
    fun `MEDAL_EARNED plays the medal URI, not the alert or dose one`() {
        prefs.soundEnabled = true
        prefs.medalSoundUri = "content://media/external/audio/media/100"
        prefs.alertSoundUri = "content://media/external/audio/media/200"
        prefs.doseSoundUri = "content://media/external/audio/media/300"

        SoundHelper.play(application, SoundHelper.SoundEvent.MEDAL_EARNED, prefs)

        assertThat(ShadowRingtoneManagerRecorder.requestedUris)
            .containsExactly(Uri.parse("content://media/external/audio/media/100"))
    }

    @Test
    fun `GLUCOSE_ALERT plays the alert URI, not the medal or dose one`() {
        prefs.soundEnabled = true
        prefs.medalSoundUri = "content://media/external/audio/media/100"
        prefs.alertSoundUri = "content://media/external/audio/media/200"
        prefs.doseSoundUri = "content://media/external/audio/media/300"

        SoundHelper.play(application, SoundHelper.SoundEvent.GLUCOSE_ALERT, prefs)

        assertThat(ShadowRingtoneManagerRecorder.requestedUris)
            .containsExactly(Uri.parse("content://media/external/audio/media/200"))
        assertThat(ShadowToneGenerator.getPlayedTones()).isEmpty()
    }

    @Test
    fun `DOSE_CALCULATED plays the dose URI, not the medal or alert one`() {
        prefs.soundEnabled = true
        prefs.medalSoundUri = "content://media/external/audio/media/100"
        prefs.alertSoundUri = "content://media/external/audio/media/200"
        prefs.doseSoundUri = "content://media/external/audio/media/300"

        SoundHelper.play(application, SoundHelper.SoundEvent.DOSE_CALCULATED, prefs)

        assertThat(ShadowRingtoneManagerRecorder.requestedUris)
            .containsExactly(Uri.parse("content://media/external/audio/media/300"))
    }

    // --- Case 2: MEDAL_EARNED/DOSE_CALCULATED never chosen -> the device's default notification sound ---

    @Test
    fun `MEDAL_EARNED never chosen resolves the device default notification sound`() {
        prefs.soundEnabled = true

        SoundHelper.play(application, SoundHelper.SoundEvent.MEDAL_EARNED, prefs)

        assertThat(ShadowRingtoneManagerRecorder.requestedUris)
            .containsExactly(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
    }

    @Test
    fun `DOSE_CALCULATED never chosen resolves the device default notification sound`() {
        prefs.soundEnabled = true

        SoundHelper.play(application, SoundHelper.SoundEvent.DOSE_CALCULATED, prefs)

        assertThat(ShadowRingtoneManagerRecorder.requestedUris)
            .containsExactly(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
    }

    // --- Case 3: GLUCOSE_ALERT never chosen -> synthesized factory pattern, never getRingtone ---

    @Test
    fun `GLUCOSE_ALERT never chosen fires the synthesized pattern, 3 CDMA alert-guard tones, and never resolves a Uri`() {
        prefs.soundEnabled = true

        SoundHelper.play(application, SoundHelper.SoundEvent.GLUCOSE_ALERT, prefs)
        idlePastAlertPattern()

        val tones = ShadowToneGenerator.getPlayedTones()
        assertThat(tones).hasSize(3)
        tones.forEach { assertThat(it.type()).isEqualTo(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD) }
        assertThat(ShadowRingtoneManagerRecorder.requestedUris).isEmpty()
    }

    // --- Case 4 (critical): GLUCOSE_ALERT WITH a chosen URI plays that URI, never the synthesized pattern ---

    @Test
    fun `GLUCOSE_ALERT with a chosen URI plays that URI and never fires the synthesized pattern`() {
        prefs.soundEnabled = true
        prefs.alertSoundUri = "content://media/external/audio/media/777"

        SoundHelper.play(application, SoundHelper.SoundEvent.GLUCOSE_ALERT, prefs)
        idlePastAlertPattern()

        assertThat(ShadowRingtoneManagerRecorder.requestedUris)
            .containsExactly(Uri.parse("content://media/external/audio/media/777"))
        assertThat(ShadowToneGenerator.getPlayedTones()).isEmpty()
    }

    // --- Case 5 (critical): GLUCOSE_ALERT set to "Nenhum" is silent — never falls back to the pattern ---

    @Test
    fun `GLUCOSE_ALERT set to Nenhum is silent, and does not fall back to the synthesized pattern`() {
        prefs.soundEnabled = true
        prefs.alertSoundUri = AppPreferences.SILENT_SOUND_URI

        SoundHelper.play(application, SoundHelper.SoundEvent.GLUCOSE_ALERT, prefs)
        idlePastAlertPattern()

        assertThat(ShadowRingtoneManagerRecorder.requestedUris).isEmpty()
        assertThat(ShadowToneGenerator.getPlayedTones()).isEmpty()
    }

    // --- Case 6: MEDAL_EARNED/DOSE_CALCULATED set to "Nenhum" request no sound at all ---

    @Test
    fun `MEDAL_EARNED set to Nenhum requests no sound`() {
        prefs.soundEnabled = true
        prefs.medalSoundUri = AppPreferences.SILENT_SOUND_URI

        SoundHelper.play(application, SoundHelper.SoundEvent.MEDAL_EARNED, prefs)

        assertThat(ShadowRingtoneManagerRecorder.requestedUris).isEmpty()
    }

    @Test
    fun `DOSE_CALCULATED set to Nenhum requests no sound`() {
        prefs.soundEnabled = true
        prefs.doseSoundUri = AppPreferences.SILENT_SOUND_URI

        SoundHelper.play(application, SoundHelper.SoundEvent.DOSE_CALCULATED, prefs)

        assertThat(ShadowRingtoneManagerRecorder.requestedUris).isEmpty()
    }

    // --- Case 7 (critical): the general switch off silences all three, without erasing their URIs ---

    @Test
    fun `soundEnabled off silences all three events and keeps their stored URIs untouched`() {
        prefs.medalSoundUri = "content://media/external/audio/media/1"
        prefs.alertSoundUri = "content://media/external/audio/media/2"
        prefs.doseSoundUri = "content://media/external/audio/media/3"
        prefs.soundEnabled = false

        SoundHelper.play(application, SoundHelper.SoundEvent.MEDAL_EARNED, prefs)
        SoundHelper.play(application, SoundHelper.SoundEvent.GLUCOSE_ALERT, prefs)
        SoundHelper.play(application, SoundHelper.SoundEvent.DOSE_CALCULATED, prefs)
        idlePastAlertPattern()

        assertThat(ShadowRingtoneManagerRecorder.requestedUris).isEmpty()
        assertThat(ShadowToneGenerator.getPlayedTones()).isEmpty()
        assertThat(prefs.medalSoundUri).isEqualTo("content://media/external/audio/media/1")
        assertThat(prefs.alertSoundUri).isEqualTo("content://media/external/audio/media/2")
        assertThat(prefs.doseSoundUri).isEqualTo("content://media/external/audio/media/3")
    }

    // --- Case 8 (critical): re-enabling restores exactly what was configured before, unprompted ---

    @Test
    fun `re-enabling soundEnabled brings back the alert synthesized pattern with no reconfiguration`() {
        prefs.soundEnabled = false
        SoundHelper.play(application, SoundHelper.SoundEvent.GLUCOSE_ALERT, prefs)
        idlePastAlertPattern()
        assertThat(ShadowToneGenerator.getPlayedTones()).isEmpty() // silenced while off

        prefs.soundEnabled = true
        SoundHelper.play(application, SoundHelper.SoundEvent.GLUCOSE_ALERT, prefs)
        idlePastAlertPattern()

        assertThat(ShadowToneGenerator.getPlayedTones()).hasSize(3)
    }

    @Test
    fun `re-enabling soundEnabled brings back a previously chosen URI with no reconfiguration`() {
        prefs.medalSoundUri = "content://media/external/audio/media/42"
        prefs.soundEnabled = false
        SoundHelper.play(application, SoundHelper.SoundEvent.MEDAL_EARNED, prefs)
        assertThat(ShadowRingtoneManagerRecorder.requestedUris).isEmpty() // silenced while off

        prefs.soundEnabled = true
        SoundHelper.play(application, SoundHelper.SoundEvent.MEDAL_EARNED, prefs)

        assertThat(ShadowRingtoneManagerRecorder.requestedUris)
            .containsExactly(Uri.parse("content://media/external/audio/media/42"))
    }

    // --- Case 9/10 (never STREAM_ALARM, never a real Ringtone loop): source-scan, not behavioral ---
    //
    // Neither invariant has an observable hook through the Robolectric shadows available here:
    // ShadowToneGenerator (see the class kdoc) does not record the stream type its real
    // constructor received, and there is no ShadowRingtone to ask whether setLooping(true) was
    // ever called — the SPEC's own architecture (§10) deliberately has no seam/abstraction that
    // would let a test intercept either call otherwise. This is a source-scan proxy instead: it
    // starts red (the skeleton mentions neither the required stream constant nor the two
    // Android classes yet) and turns green only once the real implementation both names the
    // right stream and never references the forbidden APIs.

    @Test
    fun `source never references STREAM_ALARM, setStreamVolume or Ringtone looping, and does reference the notification stream`() {
        val source = File("src/main/java/com/glicokids/prototype/util/SoundHelper.kt").readText()

        assertThat(source).contains("RingtoneManager")
        assertThat(source).contains("ToneGenerator")
        assertThat(source).contains("STREAM_NOTIFICATION")
        assertThat(source).doesNotContain("STREAM_ALARM")
        assertThat(source).doesNotContain("setStreamVolume")
        assertThat(source).doesNotContain("setLooping")
    }

    // --- Case 11: an unresolvable stored URI never throws ---

    @Test
    fun `an unresolvable stored URI does not throw and still requests the same Uri`() {
        prefs.soundEnabled = true
        prefs.medalSoundUri = "content://media/external/audio/media/does-not-exist"

        SoundHelper.play(application, SoundHelper.SoundEvent.MEDAL_EARNED, prefs)

        assertThat(ShadowRingtoneManagerRecorder.requestedUris)
            .containsExactly(Uri.parse("content://media/external/audio/media/does-not-exist"))
    }

    // --- Case 12: the ToneGenerator behind the alert's factory pattern is released after the sequence ---
    //
    // ShadowToneGenerator (see class kdoc) exposes no "was release() called" signal in
    // Robolectric 4.16.1 — only getPlayedTones()/reset(). This proxies the invariant instead:
    // once the looper is idled well past the whole pattern's scheduled duration, exactly the 3
    // tones fired and nothing further gets scheduled/played — the sequence runs to completion
    // and stops, rather than a leaked repeat. Reported to the team as a genuine testability gap
    // for literally proving release().

    @Test
    fun `the synthesized alert pattern finishes at exactly 3 tones and schedules nothing further`() {
        prefs.soundEnabled = true

        SoundHelper.play(application, SoundHelper.SoundEvent.GLUCOSE_ALERT, prefs)
        idlePastAlertPattern()
        val tonesRightAfterPattern = ShadowToneGenerator.getPlayedTones()

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        val tonesMuchLater = ShadowToneGenerator.getPlayedTones()

        assertThat(tonesRightAfterPattern).hasSize(3)
        assertThat(tonesMuchLater).hasSize(3)
    }

    // --- toStoredValue: picker result -> stored value translation (SPEC §10) ---

    @Test
    fun `toStoredValue translates a null picker result (Nenhum) into the silent sentinel`() {
        assertThat(SoundHelper.toStoredValue(null)).isEqualTo(AppPreferences.SILENT_SOUND_URI)
    }

    @Test
    fun `toStoredValue translates a picked Uri into its string form`() {
        val uri = Uri.parse("content://media/external/audio/media/55")

        assertThat(SoundHelper.toStoredValue(uri)).isEqualTo("content://media/external/audio/media/55")
    }

    // --- labelFor: stored value -> display label translation (SPEC §10) ---

    @Test
    fun `labelFor shows Nenhum for the silent sentinel, for any event`() {
        assertThat(
            SoundHelper.labelFor(application, SoundHelper.SoundEvent.MEDAL_EARNED, AppPreferences.SILENT_SOUND_URI)
        ).isEqualTo("Nenhum")
        assertThat(
            SoundHelper.labelFor(application, SoundHelper.SoundEvent.GLUCOSE_ALERT, AppPreferences.SILENT_SOUND_URI)
        ).isEqualTo("Nenhum")
        assertThat(
            SoundHelper.labelFor(application, SoundHelper.SoundEvent.DOSE_CALCULATED, AppPreferences.SILENT_SOUND_URI)
        ).isEqualTo("Nenhum")
    }

    @Test
    fun `labelFor shows Padrao do app only for a never-chosen GLUCOSE_ALERT`() {
        assertThat(
            SoundHelper.labelFor(application, SoundHelper.SoundEvent.GLUCOSE_ALERT, null)
        ).isEqualTo("Padrão do app")
    }

    @Test
    fun `labelFor shows Padrao do aparelho for a never-chosen MEDAL_EARNED or DOSE_CALCULATED`() {
        assertThat(
            SoundHelper.labelFor(application, SoundHelper.SoundEvent.MEDAL_EARNED, null)
        ).isEqualTo("Padrão do aparelho")
        assertThat(
            SoundHelper.labelFor(application, SoundHelper.SoundEvent.DOSE_CALCULATED, null)
        ).isEqualTo("Padrão do aparelho")
    }

    @Test
    fun `labelFor shows Som indisponivel for a stored Uri that does not resolve`() {
        assertThat(
            SoundHelper.labelFor(
                application,
                SoundHelper.SoundEvent.MEDAL_EARNED,
                "content://media/external/audio/media/does-not-resolve"
            )
        ).isEqualTo("Som indisponível")
    }

    // --- buildRingtonePickerIntent: fixes the "Silent pre-selected for a never-chosen sound"
    // defect found on a real device (Samsung SM-S938B, Android 16). The Activity used to skip
    // EXTRA_RINGTONE_EXISTING_URI whenever `currentStored == null`, and the picker treats "no
    // extra" the same as "Silent" — misleading the caregiver into believing the sound is muted
    // when it actually plays. See SPEC-MODULO-8-SOM.md §4/§6 for the factory-default table this
    // extraction has to stay consistent with.

    @Test
    fun `buildRingtonePickerIntent for a never-chosen MEDAL_EARNED pre-selects the device default notification sound`() {
        val intent = SoundHelper.buildRingtonePickerIntent(SoundHelper.SoundEvent.MEDAL_EARNED, null)

        assertThat(intent.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI))
            .isEqualTo(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
    }

    @Test
    fun `buildRingtonePickerIntent for a never-chosen DOSE_CALCULATED pre-selects the device default notification sound`() {
        val intent = SoundHelper.buildRingtonePickerIntent(SoundHelper.SoundEvent.DOSE_CALCULATED, null)

        assertThat(intent.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI))
            .isEqualTo(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
    }

    @Test
    fun `buildRingtonePickerIntent for a never-chosen GLUCOSE_ALERT carries no existing-Uri extra`() {
        val intent = SoundHelper.buildRingtonePickerIntent(SoundHelper.SoundEvent.GLUCOSE_ALERT, null)

        assertThat(intent.hasExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI)).isFalse()
    }

    @Test
    fun `buildRingtonePickerIntent for a chosen Uri pre-selects exactly that Uri, for any event`() {
        val chosen = "content://media/external/audio/media/999"

        val medal = SoundHelper.buildRingtonePickerIntent(SoundHelper.SoundEvent.MEDAL_EARNED, chosen)
        val alert = SoundHelper.buildRingtonePickerIntent(SoundHelper.SoundEvent.GLUCOSE_ALERT, chosen)
        val dose = SoundHelper.buildRingtonePickerIntent(SoundHelper.SoundEvent.DOSE_CALCULATED, chosen)

        listOf(medal, alert, dose).forEach {
            assertThat(it.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI))
                .isEqualTo(Uri.parse(chosen))
        }
    }

    @Test
    fun `buildRingtonePickerIntent for the silent sentinel carries no existing-Uri extra, for any event`() {
        val medal = SoundHelper.buildRingtonePickerIntent(SoundHelper.SoundEvent.MEDAL_EARNED, AppPreferences.SILENT_SOUND_URI)
        val alert = SoundHelper.buildRingtonePickerIntent(SoundHelper.SoundEvent.GLUCOSE_ALERT, AppPreferences.SILENT_SOUND_URI)
        val dose = SoundHelper.buildRingtonePickerIntent(SoundHelper.SoundEvent.DOSE_CALCULATED, AppPreferences.SILENT_SOUND_URI)

        listOf(medal, alert, dose).forEach {
            assertThat(it.hasExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI)).isFalse()
        }
    }

    // --- resolveStoredValue: what to persist after the picker returns (fixes a device defect on
    // the same Samsung SM-S938B, Android 16 — its system picker returns RESULT_OK even when the
    // caregiver presses Back, so the app used to persist whatever happened to be marked, even
    // though nobody chose anything). Decided by the human 23/08/2026: when the previous state was
    // `null` ("never chosen") AND the picker's Uri is exactly the event's own factory default, the
    // app keeps `null` instead of writing that Uri — the audible sound is identical either way, so
    // nothing real is lost, but opening the picker and backing out stops mutating the preference.
    // GLUCOSE_ALERT's factory default has no Uri (it is the synthesized pattern), so no picked Uri
    // can ever equal it — any Uri returned for a never-chosen alert is a real choice and is always
    // stored. An explicit "Nenhum" (`pickedUri == null`) is always stored as the silent sentinel:
    // it is indistinguishable from a Back in that state, and is treated as a legitimate choice.

    @Test
    fun `resolveStoredValue for a never-chosen GLUCOSE_ALERT always stores whatever Uri the picker returned`() {
        val picked = Uri.parse("content://media/external/audio/media/1")

        val result = SoundHelper.resolveStoredValue(SoundHelper.SoundEvent.GLUCOSE_ALERT, null, picked)

        assertThat(result).isEqualTo(picked.toString())
    }

    @Test
    fun `resolveStoredValue for a never-chosen MEDAL_EARNED or DOSE_CALCULATED keeps null when the picker returned the device default`() {
        val deviceDefault = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        assertThat(SoundHelper.resolveStoredValue(SoundHelper.SoundEvent.MEDAL_EARNED, null, deviceDefault))
            .isNull()
        assertThat(SoundHelper.resolveStoredValue(SoundHelper.SoundEvent.DOSE_CALCULATED, null, deviceDefault))
            .isNull()
    }

    @Test
    fun `resolveStoredValue for a never-chosen MEDAL_EARNED or DOSE_CALCULATED stores a Uri different from the device default`() {
        val chosen = Uri.parse("content://media/external/audio/media/2")

        assertThat(SoundHelper.resolveStoredValue(SoundHelper.SoundEvent.MEDAL_EARNED, null, chosen))
            .isEqualTo(chosen.toString())
        assertThat(SoundHelper.resolveStoredValue(SoundHelper.SoundEvent.DOSE_CALCULATED, null, chosen))
            .isEqualTo(chosen.toString())
    }

    @Test
    fun `resolveStoredValue stores the silent sentinel for any event when the picker returned Nenhum, previous state never chosen`() {
        SoundHelper.SoundEvent.values().forEach { event ->
            assertThat(SoundHelper.resolveStoredValue(event, null, null))
                .isEqualTo(AppPreferences.SILENT_SOUND_URI)
        }
    }

    @Test
    fun `resolveStoredValue stores the silent sentinel for any event when the picker returned Nenhum, previous state a chosen Uri`() {
        SoundHelper.SoundEvent.values().forEach { event ->
            assertThat(
                SoundHelper.resolveStoredValue(event, "content://media/external/audio/media/old", null)
            ).isEqualTo(AppPreferences.SILENT_SOUND_URI)
        }
    }

    @Test
    fun `resolveStoredValue for any event with a previously chosen Uri always stores the new Uri`() {
        val previous = "content://media/external/audio/media/old"
        val picked = Uri.parse("content://media/external/audio/media/new")

        SoundHelper.SoundEvent.values().forEach { event ->
            assertThat(SoundHelper.resolveStoredValue(event, previous, picked))
                .isEqualTo(picked.toString())
        }
    }

    @Test
    fun `resolveStoredValue for any event with a previously chosen Uri keeps it unchanged when the picker returns the same Uri`() {
        val same = Uri.parse("content://media/external/audio/media/same")

        SoundHelper.SoundEvent.values().forEach { event ->
            assertThat(SoundHelper.resolveStoredValue(event, same.toString(), same))
                .isEqualTo(same.toString())
        }
    }

    @Test
    fun `buildRingtonePickerIntent always filters to TYPE_NOTIFICATION with silent and default shown`() {
        val intents = listOf(
            SoundHelper.buildRingtonePickerIntent(SoundHelper.SoundEvent.MEDAL_EARNED, null),
            SoundHelper.buildRingtonePickerIntent(SoundHelper.SoundEvent.GLUCOSE_ALERT, null),
            SoundHelper.buildRingtonePickerIntent(SoundHelper.SoundEvent.DOSE_CALCULATED, "content://media/external/audio/media/1"),
            SoundHelper.buildRingtonePickerIntent(SoundHelper.SoundEvent.MEDAL_EARNED, AppPreferences.SILENT_SOUND_URI)
        )

        intents.forEach { intent ->
            assertThat(intent.action).isEqualTo(RingtoneManager.ACTION_RINGTONE_PICKER)
            assertThat(intent.getIntExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, -1))
                .isEqualTo(RingtoneManager.TYPE_NOTIFICATION)
            assertThat(intent.getBooleanExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)).isTrue()
            assertThat(intent.getBooleanExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)).isTrue()
        }
    }
}
