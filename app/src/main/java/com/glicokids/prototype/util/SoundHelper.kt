package com.glicokids.prototype.util

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.glicokids.prototype.data.local.AppPreferences

/**
 * Module 8 — the three approved sound triggers (medal earned, glucose alert, dose calculated).
 * Kept apart from [UIHelper] for the same reason [NotificationHelper] is its own file: audio is
 * a distinct responsibility, not another entry in a catch-all.
 *
 * See `.artifacts/SPEC-MODULO-8-SOM.md` §3.1 for the full behavior this file implements.
 */
object SoundHelper {

    /** One sound event per approved trigger — nested here, not in `domain/model`, because it is
     * UI vocabulary with no associated business rule (same reasoning as `UIHelper.GlucoseStatus`). */
    enum class SoundEvent {
        MEDAL_EARNED,
        GLUCOSE_ALERT,
        DOSE_CALCULATED
    }

    /** Duration of a single synthesized alert tone, in milliseconds. */
    private const val ALERT_TONE_DURATION_MS = 400

    /** Silent gap between two synthesized alert tones, in milliseconds. */
    private const val ALERT_TONE_GAP_MS = 100

    /** The alert's factory pattern repeats the tone exactly this many times — never a loop. */
    private const val ALERT_TONE_REPEAT_COUNT = 3

    /** Safety margin, on top of the three tones and gaps, before releasing the ToneGenerator. */
    private const val ALERT_RELEASE_MARGIN_MS = 200L

    /**
     * Plays the sound the caregiver picked for this event. The routing is by the STATE of the
     * preference, not by the event: an alert with no choice gets the synthesized factory tone,
     * an alert with a chosen Uri plays that Uri exactly like the other two events.
     */
    fun play(context: Context, event: SoundEvent, prefs: AppPreferences) {
        if (!prefs.soundEnabled) return
        val stored = storedUriFor(event, prefs)
        if (stored == AppPreferences.SILENT_SOUND_URI) return // "Nenhum": never falls back to a factory default
        if (stored == null && event == SoundEvent.GLUCOSE_ALERT) {
            playAlertPattern(context) // alert with no choice: the factory default is synthesized, not getDefaultUri
            return
        }
        val uri = stored?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) // medal/dose with no choice
        playUri(context, uri)
    }

    /** Translates the ringtone picker's result into the value stored in [AppPreferences] —
     * `null` (picker's "Nenhum") becomes [AppPreferences.SILENT_SOUND_URI]. Same translation the
     * Activity reuses when saving a choice, so the rule lives in one place. */
    fun toStoredValue(pickedUri: Uri?): String? =
        pickedUri?.toString() ?: AppPreferences.SILENT_SOUND_URI

    /**
     * What to persist after the picker returns — fixes a device defect (Samsung SM-S938B,
     * Android 16): its system picker returns `RESULT_OK` even when the caregiver presses Back, so
     * whatever happened to be marked got saved even though nobody chose anything.
     *
     * Decided by the human 23/08/2026: when [previousStored] is `null` ("never chosen") **and**
     * [pickedUri] is exactly the event's own factory default, this keeps `null` instead of writing
     * that Uri — the audible sound is identical either way, so opening the picker and backing out
     * stops mutating the preference. `GLUCOSE_ALERT`'s factory default has no Uri (it is the
     * synthesized pattern, see [playAlertPattern]), so no picked Uri can ever equal it — any Uri
     * returned for a never-chosen alert is always a real choice.
     *
     * An explicit "Nenhum" ([pickedUri] `== null`) is always stored as the silent sentinel,
     * regardless of [previousStored]: it is indistinguishable from a Back in that state, and is
     * treated as a legitimate choice rather than guessed away.
     *
     * A [previousStored] that already holds a real choice (Uri or the silent sentinel) always
     * stores the translation of [pickedUri] as-is — the "preserve on Back" rule only applies to
     * the never-chosen state.
     */
    fun resolveStoredValue(event: SoundEvent, previousStored: String?, pickedUri: Uri?): String? {
        if (pickedUri == null) return AppPreferences.SILENT_SOUND_URI
        if (previousStored == null && event != SoundEvent.GLUCOSE_ALERT) {
            val deviceDefault = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            if (pickedUri == deviceDefault) return null // untouched Back: keep "never chosen"
        }
        return pickedUri.toString()
    }

    /**
     * Readable label for the Parent Area row. The "never chosen" text depends on the event,
     * because the two factory defaults are of a different nature: the alert's is synthesized by
     * the app itself (there is no matching device Uri), the other two are the device's real
     * notification sound.
     */
    fun labelFor(context: Context, event: SoundEvent, storedUri: String?): String = when {
        storedUri == AppPreferences.SILENT_SOUND_URI -> "Nenhum"
        storedUri == null && event == SoundEvent.GLUCOSE_ALERT -> "Padrão do app"
        storedUri == null -> "Padrão do aparelho"
        else -> RingtoneManager.getRingtone(context, Uri.parse(storedUri))
            ?.getTitle(context) ?: "Som indisponível" // a chosen Uri that no longer resolves
    }

    /**
     * Builds the `ACTION_RINGTONE_PICKER` Intent for a given event/stored preference — extracted
     * out of the Activity so the extras it carries are testable in the JVM suite (a device bug
     * on a Samsung SM-S938B, Android 16 slipped through exactly because this used to be private
     * wiring inside `AlertSettingsActivity`, out of the suite's reach).
     *
     * The fix is in [existingUriFor]: the Activity used to pass `EXTRA_RINGTONE_EXISTING_URI`
     * only when a real Uri was stored, so a never-chosen medal/dose sound (`storedUri == null`)
     * left the extra out entirely — and the picker treats "no extra" the same as "Silent",
     * showing it pre-selected even though the device's default notification sound plays. Medal
     * and dose now pre-select that default explicitly. The alert has no such fix: its factory
     * default is the app's own synthesized pattern (§4 of the SPEC), which has no matching device
     * Uri to pre-select — so a never-chosen alert still carries no existing-Uri extra, same as
     * before.
     */
    fun buildRingtonePickerIntent(event: SoundEvent, storedUri: String?): Intent =
        Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            existingUriFor(event, storedUri)?.let { existing ->
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
            }
        }

    /** See [buildRingtonePickerIntent] kdoc for why `null` splits by event and `SILENT_SOUND_URI`
     * never pre-selects anything (the picker marking "Silent" is the truth in that state). */
    private fun existingUriFor(event: SoundEvent, storedUri: String?): Uri? = when {
        storedUri == AppPreferences.SILENT_SOUND_URI -> null
        storedUri != null -> Uri.parse(storedUri)
        event == SoundEvent.GLUCOSE_ALERT -> null
        else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    private fun storedUriFor(event: SoundEvent, prefs: AppPreferences): String? = when (event) {
        SoundEvent.MEDAL_EARNED -> prefs.medalSoundUri
        SoundEvent.GLUCOSE_ALERT -> prefs.alertSoundUri
        SoundEvent.DOSE_CALCULATED -> prefs.doseSoundUri
    }

    /** Plays a stored Uri via [android.media.Ringtone], degrading to silence — never throwing —
     * both when the Uri no longer resolves to anything (`getRingtone` returns `null`) and when
     * `play()` itself fails: a broken choice leaves this event silent, it never falls back
     * automatically to a sound the caregiver did not pick. */
    private fun playUri(context: Context, uri: Uri) {
        val ringtone = RingtoneManager.getRingtone(context, uri) ?: return
        try {
            ringtone.play()
        } catch (e: Exception) {
            // Device in a state where this particular sound cannot play — silence, not a crash.
        }
    }

    /**
     * The `GLUCOSE_ALERT` factory default: a short synthesized pattern, never used for
     * `MEDAL_EARNED`/`DOSE_CALCULATED` and never alongside a chosen Uri for the alert either.
     * A single [ToneGenerator] on `STREAM_NOTIFICATION` fires the alert-guard tone three times,
     * each scheduled through [Handler.postDelayed] so the previous tone and its gap have already
     * finished, and is released once the whole sequence is done. The `Handler` only references
     * the local `tg`, never a `Context`/Activity, so the screen can close mid-pattern without
     * leaking anything — the worst case is the pattern finishing after the screen is gone.
     */
    private fun playAlertPattern(context: Context) {
        val tg = try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, ToneGenerator.MAX_VOLUME)
        } catch (e: RuntimeException) {
            return // audio resource unavailable — degrade to silence, same spirit as playUri
        }
        val handler = Handler(Looper.getMainLooper())
        val stepMs = (ALERT_TONE_DURATION_MS + ALERT_TONE_GAP_MS).toLong()
        repeat(ALERT_TONE_REPEAT_COUNT) { index ->
            handler.postDelayed({
                tg.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, ALERT_TONE_DURATION_MS)
            }, index * stepMs)
        }
        val totalPatternMs = ALERT_TONE_REPEAT_COUNT * stepMs
        handler.postDelayed({ tg.release() }, totalPatternMs + ALERT_RELEASE_MARGIN_MS)
    }
}
