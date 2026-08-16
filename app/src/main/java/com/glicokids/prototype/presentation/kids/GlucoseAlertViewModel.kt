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
import com.glicokids.prototype.domain.usecase.ResolveAlertLocationUseCase
import com.glicokids.prototype.domain.usecase.ShouldAutoAlertUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** What the caregiver sees on b22. [sentTo] stays empty until the automatic send or
 * [GlucoseAlertViewModel.confirmSend] actually goes out.
 *
 * Module 6 — field defect (session of 09/08/2026): [showResendAction], [showThrottleNotice]
 * and [sendFailedMessage] replace three pieces of the screen that used to be unconditional —
 * "Reenviar" always visible, the throttle caption always shown even with nothing sent, and a
 * failed confirmSend silently hiding every button with no explanation. */
data class GlucoseAlertUiState(
    val messagePreview: String,
    val sentTo: List<SentAlertRecipient>,
    val showConfirmActions: Boolean,
    val throttleMin: Int,
    val imOkButtonText: String,
    /** "Reenviar" only makes sense once something has actually gone out — the design mock
     * (b22/f22) only ever shows it inside the llSentTo block, never on its own. */
    val showResendAction: Boolean,
    /** True either because this alert just sent, or because an earlier alert's throttle
     * window has not elapsed yet — never for a reading that neither sent nor is blocked. */
    val showThrottleNotice: Boolean,
    /** Non-null only after a real send attempt (recipients existed) reached nobody — e.g.
     * SEND_SMS revoked. Null both before any attempt and after a successful one. */
    val sendFailedMessage: String?,
    /** Module 7 — best-effort address for the alert, once [ResolveAlertLocationUseCase]
     * resolves one. Null before any resolution, when consent is off, or when nothing could be
     * resolved within its budget. Defaulted so every existing caller of this constructor keeps
     * compiling unchanged. */
    val locationLabel: String? = null,
    /** Module 7 — true while an alert's location resolution has not settled yet, so the screen
     * has something to show during the async wait. Defaulted for the same reason as
     * [locationLabel]. */
    val isLocating: Boolean = false
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
    private val buildAlertMessageUseCase: BuildAlertMessageUseCase,
    // Module 7: injected and now consulted from start() below — a best-effort address that
    // rides along in the alert, never a precondition for it.
    private val resolveAlertLocationUseCase: ResolveAlertLocationUseCase
) : ViewModel() {

    private val _uiState = MutableLiveData<GlucoseAlertUiState>()
    val uiState: LiveData<GlucoseAlertUiState> = _uiState

    /** The message built in [start], kept around so [confirmSend] resends the exact
     * text the caregiver already saw in the preview instead of building a second one. */
    private var pendingMessage: String = ""

    /** The btnImOk label picked in [start] from the alert direction, kept around so [send]
     * can still put it in the [GlucoseAlertUiState] it builds after a manual confirmSend —
     * that path never sees the direction again. */
    private var pendingImOkButtonText: String = ""

    /** Module 7 — whatever [resolveAlertLocationUseCase] managed to resolve for the alert
     * currently in flight, read by [send] so a manual [confirmSend] carries the same location
     * (or lack of one) the automatic path already saw. Null before any resolution, when the
     * preference is off, or when nothing resolved in time. */
    private var pendingLocationLabel: String? = null

    fun start(
        value: Int,
        timestampMillis: Long,
        source: ReadingSource,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val direction = AlertDirection.from(value, prefs.rangeMin, prefs.rangeMax)
        val mode = if (direction == AlertDirection.HYPO) prefs.alertModeHypo else prefs.alertModeHyper
        pendingImOkButtonText = imOkButtonText(direction)
        pendingLocationLabel = null

        // Decided up front — it only depends on source/direction/modes/throttle, never on the
        // message text or the location — so start() knows before touching either of them
        // whether this alert is about to leave the screen (auto-send) or sit on it waiting for
        // a caregiver tap (suggest/off).
        val shouldSend = shouldAutoAlertUseCase.execute(
            source = source,
            direction = direction,
            hypoMode = prefs.alertModeHypo,
            hyperMode = prefs.alertModeHyper,
            lastAlertAt = prefs.lastAlertAt,
            now = nowMillis,
            throttleMin = prefs.alertThrottleMin
        )

        val includeLocation = prefs.alertIncludeLocation
        if (includeLocation) {
            // Published before the location resolution is awaited below, on both the
            // automatic and the suggest/off paths. An automatic send can now take up to
            // ResolveAlertLocationUseCase.BUDGET_MILLIS before anything reaches the screen —
            // that wait is exactly when the caregiver most needs to see something is happening,
            // not less than on the confirmation path.
            _uiState.value = GlucoseAlertUiState(
                messagePreview = "",
                sentTo = emptyList(),
                showConfirmActions = false,
                throttleMin = prefs.alertThrottleMin,
                imOkButtonText = pendingImOkButtonText,
                showResendAction = false,
                showThrottleNotice = false,
                sendFailedMessage = null,
                locationLabel = null,
                isLocating = true
            )
            // Awaited on purpose: the message must carry the resolved address (or the lack of
            // one) whenever it is built, so buildMessageAndSend only runs once this settles.
            // Safe to await unconditionally because ResolveAlertLocationUseCase owns its own
            // BUDGET_MILLIS ceiling over the whole fix + reverse-geocode chain and always
            // returns — see its kdoc and ResolveAlertLocationUseCaseTest — so the alert is
            // still guaranteed to reach every recipient, just no more than BUDGET_MILLIS later.
            viewModelScope.launch {
                val snapshot = resolveAlertLocationUseCase.execute(true)
                if (snapshot != null) {
                    pendingLocationLabel = snapshot.label
                    prefs.saveLastAlertLocation(
                        lat = snapshot.point.lat,
                        lng = snapshot.point.lng,
                        label = snapshot.label,
                        atMillis = nowMillis
                    )
                }
                buildMessageAndSend(value, timestampMillis, source, nowMillis, direction, mode, shouldSend)
            }
        } else {
            buildMessageAndSend(value, timestampMillis, source, nowMillis, direction, mode, shouldSend)
        }
    }

    private fun buildMessageAndSend(
        value: Int,
        timestampMillis: Long,
        source: ReadingSource,
        nowMillis: Long,
        direction: AlertDirection,
        mode: AlertMode,
        shouldSend: Boolean
    ) {
        val message = buildAlertMessageUseCase.execute(
            partialChildName = reportStorage.anonymizedName(prefs.childName),
            value = value,
            timestampMillis = timestampMillis,
            rangeMin = prefs.rangeMin,
            rangeMax = prefs.rangeMax,
            isHypo = direction == AlertDirection.HYPO,
            fromSensor = source == ReadingSource.SENSOR,
            locationHint = pendingLocationLabel
        )
        pendingMessage = message

        if (shouldSend) {
            send(message, nowMillis)
        } else {
            _uiState.value = GlucoseAlertUiState(
                messagePreview = message,
                sentTo = emptyList(),
                showConfirmActions = mode == AlertMode.SUGGEST,
                throttleMin = prefs.alertThrottleMin,
                imOkButtonText = pendingImOkButtonText,
                showResendAction = false,
                showThrottleNotice = isThrottleWindowActive(nowMillis),
                sendFailedMessage = null,
                locationLabel = pendingLocationLabel,
                isLocating = false
            )
        }
    }

    /** True while an earlier alert's throttle window has not elapsed yet. Read fresh every
     * time instead of cached — [prefs.lastAlertAt] can change between calls (a successful
     * [send] writes it), and this same check backs [GlucoseAlertUiState.showThrottleNotice]
     * both before and after a send attempt. */
    private fun isThrottleWindowActive(nowMillis: Long): Boolean =
        (nowMillis - prefs.lastAlertAt) < prefs.alertThrottleMin * 60_000L

    fun confirmSend(nowMillis: Long = System.currentTimeMillis()) {
        send(pendingMessage, nowMillis)
    }

    /** Sugar treats HYPOglycemia only. During a HYPER alert the button must confirm the
     * correction that was already applied — never re-offer sugar, which would invert the
     * clinical instruction shown to the child on this emergency screen. Same HYPO-vs-else
     * split as the `mode` pick above: this screen only ever opens for an out-of-range
     * reading, so NONE never reaches here in practice. */
    private fun imOkButtonText(direction: AlertDirection): String =
        if (direction == AlertDirection.HYPO) "Estou bem — já tomei açúcar"
        else "Estou bem — já apliquei a correção"

    /**
     * Sends [message] to every alert recipient off the main thread. A failure for one
     * recipient never stops the loop from reaching the next one, but a recipient only
     * shows up in [SentAlertRecipient] once the carrier has
     * actually confirmed the send, and the throttle is only written when at least one send
     * confirms; if every send fails, `lastAlertAt` is left alone so the next reading can
     * try again instead of being silently throttled for a message nobody received.
     */
    private fun send(message: String, nowMillis: Long) {
        viewModelScope.launch {
            val (sentTo, sendFailedMessage) = withContext(Dispatchers.IO) {
                val recipients = dbHelper.getAlertRecipients()
                val delivered = recipients.filter { contact ->
                    smsGateway.sendTextMessage(contact.phone, message)
                }
                if (delivered.isNotEmpty()) {
                    prefs.lastAlertAt = nowMillis
                }
                val sentTo = delivered.map { contact ->
                    SentAlertRecipient(
                        name = contact.name,
                        relationship = contact.relationship,
                        sentAtMillis = nowMillis
                    )
                }
                // Field regression: a real attempt (recipients existed) that reached nobody —
                // e.g. SEND_SMS revoked, AndroidSmsGateway catches the SecurityException and
                // returns false for every recipient — used to look identical to a clean send.
                val failedMessage = if (recipients.isNotEmpty() && delivered.isEmpty()) {
                    "Não foi possível enviar o alerta agora. Verifique se a permissão de SMS " +
                        "está ativada para o GlicoKids e tente de novo."
                } else {
                    null
                }
                sentTo to failedMessage
            }
            _uiState.value = GlucoseAlertUiState(
                messagePreview = message,
                sentTo = sentTo,
                showConfirmActions = false,
                throttleMin = prefs.alertThrottleMin,
                imOkButtonText = pendingImOkButtonText,
                showResendAction = sentTo.isNotEmpty(),
                showThrottleNotice = sentTo.isNotEmpty() || isThrottleWindowActive(nowMillis),
                sendFailedMessage = sendFailedMessage,
                locationLabel = pendingLocationLabel,
                isLocating = false
            )
        }
    }
}
