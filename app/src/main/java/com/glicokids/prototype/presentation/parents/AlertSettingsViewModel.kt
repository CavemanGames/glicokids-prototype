package com.glicokids.prototype.presentation.parents

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.glicokids.prototype.data.local.AppPreferences
import com.glicokids.prototype.data.local.GlicoKidsDbHelper
import com.glicokids.prototype.domain.model.AlertMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * b21 — Automatic alerts. Hypo and hyper are independent decisions: changing one
 * never touches the other's stored mode. There is no Save button on this screen —
 * every setter writes to [AppPreferences] immediately, same pattern as
 * [ParentAreaViewModel.updateParam].
 */
@HiltViewModel
class AlertSettingsViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val dbHelper: GlicoKidsDbHelper
) : ViewModel() {

    data class AlertSettingsUiState(
        val hypoMode: AlertMode,
        val hyperMode: AlertMode,
        val throttleMin: Int,
        val recoveryNoticeEnabled: Boolean,
        val hypoLimit: Int,
        val hyperLimit: Int,
        val recipientCount: Int,
        /** Module 7 — whether the alert SMS carries the child's approximate address. */
        val includeLocation: Boolean,
        /** Module 8 — the general sound switch and the three raw stored values (never the
         * already-translated label: resolving a name needs a `Context`, and this ViewModel
         * never touches one — that translation happens in the Activity via [SoundHelper]). */
        val soundEnabled: Boolean,
        val medalSoundUri: String?,
        val alertSoundUri: String?,
        val doseSoundUri: String?
    )

    private val _uiState = MutableLiveData<AlertSettingsUiState>()
    val uiState: LiveData<AlertSettingsUiState> = _uiState

    init {
        publishState()
    }

    fun setHypoMode(mode: AlertMode) {
        prefs.alertModeHypo = mode
        publishState()
    }

    fun setHyperMode(mode: AlertMode) {
        prefs.alertModeHyper = mode
        publishState()
    }

    fun setThrottleMin(minutes: Int) {
        prefs.alertThrottleMin = minutes
        publishState()
    }

    fun setRecoveryNoticeEnabled(enabled: Boolean) {
        prefs.alertOnRecovery = enabled
        publishState()
    }

    fun setIncludeLocation(enabled: Boolean) {
        prefs.alertIncludeLocation = enabled
        publishState()
    }

    // --- Sound prefs (Module 8) ---

    /** General switch: silences all three events without erasing their per-event choices. */
    fun setSoundEnabled(enabled: Boolean) {
        prefs.soundEnabled = enabled
        publishState()
    }

    /** `stored` already arrives translated by `SoundHelper.toStoredValue` — never a raw `Uri`. */
    fun setMedalSound(stored: String?) {
        prefs.medalSoundUri = stored
        publishState()
    }

    /** `stored` already arrives translated by `SoundHelper.toStoredValue` — never a raw `Uri`. */
    fun setAlertSound(stored: String?) {
        prefs.alertSoundUri = stored
        publishState()
    }

    /** `stored` already arrives translated by `SoundHelper.toStoredValue` — never a raw `Uri`. */
    fun setDoseSound(stored: String?) {
        prefs.doseSoundUri = stored
        publishState()
    }

    private fun publishState() {
        _uiState.value = AlertSettingsUiState(
            hypoMode = prefs.alertModeHypo,
            hyperMode = prefs.alertModeHyper,
            throttleMin = prefs.alertThrottleMin,
            recoveryNoticeEnabled = prefs.alertOnRecovery,
            hypoLimit = prefs.rangeMin,
            hyperLimit = prefs.rangeMax,
            recipientCount = dbHelper.getAlertRecipients().size,
            includeLocation = prefs.alertIncludeLocation,
            soundEnabled = prefs.soundEnabled,
            medalSoundUri = prefs.medalSoundUri,
            alertSoundUri = prefs.alertSoundUri,
            doseSoundUri = prefs.doseSoundUri
        )
    }
}
