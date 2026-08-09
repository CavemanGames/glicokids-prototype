package com.glicokids.prototype.presentation.kids

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glicokids.prototype.data.local.AppPreferences
import com.glicokids.prototype.data.local.GlicoKidsDbHelper
import com.glicokids.prototype.data.local.ReportStorage
import com.glicokids.prototype.domain.model.AlertDirection
import com.glicokids.prototype.domain.model.AlertMode
import com.glicokids.prototype.domain.model.ReadingSource
import com.glicokids.prototype.domain.repository.SmsGateway
import com.glicokids.prototype.domain.usecase.BuildAlertMessageUseCase
import com.glicokids.prototype.domain.usecase.ShouldAutoAlertUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** What the caregiver sees on b22. [sentTo] stays empty until the automatic send or
 * [GlucoseAlertViewModel.confirmSend] actually goes out. */
data class GlucoseAlertUiState(
    val messagePreview: String,
    val sentTo: List<SentAlertRecipient>,
    val showConfirmActions: Boolean,
    val throttleMin: Int
)

/** One line of the "already sent" list — name, relationship and when it went out. */
data class SentAlertRecipient(
    val name: String,
    val relationship: String,
    val sentAtMillis: Long
)

/**
 * b22 — the glucose alert screen opened from the reading flow. Never decides on its own
 * whether to send: that call belongs entirely to [ShouldAutoAlertUseCase]. This class only
 * derives the [AlertDirection] from the currently configured range, reads the mode that
 * direction owns, and reacts to the use case's answer — automatic send, a suggestion the
 * caregiver has to confirm, or nothing. The SMS text always comes out of
 * [BuildAlertMessageUseCase], built once per alert and reused for every recipient, so the
 * preview shown on screen is exactly what went out.
 */
@HiltViewModel
class GlucoseAlertViewModel @Inject constructor(
    private val dbHelper: GlicoKidsDbHelper,
    private val prefs: AppPreferences,
    private val reportStorage: ReportStorage,
    private val smsGateway: SmsGateway,
    private val shouldAutoAlertUseCase: ShouldAutoAlertUseCase,
    private val buildAlertMessageUseCase: BuildAlertMessageUseCase
) : ViewModel() {

    private val _uiState = MutableLiveData<GlucoseAlertUiState>()
    val uiState: LiveData<GlucoseAlertUiState> = _uiState

    /** The message built in [start], kept around so [confirmSend] resends the exact
     * text the caregiver already saw in the preview instead of building a second one. */
    private var pendingMessage: String = ""

    fun start(
        value: Int,
        timestampMillis: Long,
        source: ReadingSource,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val direction = AlertDirection.from(value, prefs.rangeMin, prefs.rangeMax)
        val mode = if (direction == AlertDirection.HYPO) prefs.alertModeHypo else prefs.alertModeHyper

        val message = buildAlertMessageUseCase.execute(
            partialChildName = reportStorage.anonymizedName(prefs.childName),
            value = value,
            timestampMillis = timestampMillis,
            rangeMin = prefs.rangeMin,
            rangeMax = prefs.rangeMax,
            isHypo = direction == AlertDirection.HYPO,
            fromSensor = source == ReadingSource.SENSOR
        )
        pendingMessage = message

        val shouldSend = shouldAutoAlertUseCase.execute(
            source = source,
            direction = direction,
            hypoMode = prefs.alertModeHypo,
            hyperMode = prefs.alertModeHyper,
            lastAlertAt = prefs.lastAlertAt,
            now = nowMillis,
            throttleMin = prefs.alertThrottleMin
        )

        if (shouldSend) {
            send(message, nowMillis)
        } else {
            _uiState.value = GlucoseAlertUiState(
                messagePreview = message,
                sentTo = emptyList(),
                showConfirmActions = mode == AlertMode.SUGGEST,
                throttleMin = prefs.alertThrottleMin
            )
        }
    }

    fun confirmSend(nowMillis: Long = System.currentTimeMillis()) {
        send(pendingMessage, nowMillis)
    }

    /**
     * Sends [message] to every alert recipient off the main thread. `sendTextMessage`'s
     * result is read but never branched on — a failure for one recipient never stops the
     * loop from reaching the next one.
     */
    private fun send(message: String, nowMillis: Long) {
        viewModelScope.launch {
            val sentTo = withContext(Dispatchers.IO) {
                val recipients = dbHelper.getAlertRecipients()
                prefs.lastAlertAt = nowMillis
                recipients.map { contact ->
                    smsGateway.sendTextMessage(contact.phone, message)
                    SentAlertRecipient(
                        name = contact.name,
                        relationship = contact.relationship,
                        sentAtMillis = nowMillis
                    )
                }
            }
            _uiState.value = GlucoseAlertUiState(
                messagePreview = message,
                sentTo = sentTo,
                showConfirmActions = false,
                throttleMin = prefs.alertThrottleMin
            )
        }
    }
}
