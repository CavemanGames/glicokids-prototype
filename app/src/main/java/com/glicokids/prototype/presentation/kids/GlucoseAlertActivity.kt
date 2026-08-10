package com.glicokids.prototype.presentation.kids

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.glicokids.prototype.data.local.AppPreferences
import com.glicokids.prototype.data.local.GlicoKidsDbHelper
import com.glicokids.prototype.databinding.ActivityGlucoseAlertBinding
import com.glicokids.prototype.databinding.ItemAlertRecipientBinding
import com.glicokids.prototype.domain.model.AlertDirection
import com.glicokids.prototype.domain.model.ReadingSource
import com.glicokids.prototype.util.UIHelper
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * b22 — the one red screen in the app, for the one situation that deserves it: a glucose
 * reading outside the target range. Opened by [GlucoseLogActivity] whenever the status it
 * just saved is not [UIHelper.GlucoseStatus.NA_META]; every decision about sending or
 * suggesting an SMS lives in [GlucoseAlertViewModel], this class only renders what it publishes.
 */
@AndroidEntryPoint
class GlucoseAlertActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGlucoseAlertBinding
    private val viewModel: GlucoseAlertViewModel by viewModels()

    @Inject lateinit var prefs: AppPreferences
    @Inject lateinit var dbHelper: GlicoKidsDbHelper

    private val timeFormat = SimpleDateFormat("HH:mm", Locale("pt", "BR"))

    /** Achado 5 (Etapa D): [tvSentHeader][observeViewModel] used to claim "AUTOMATICAMENTE"
     * even on the SUGGEST path, where the caregiver had just tapped a button. Set the moment
     * [setupListeners] fires `confirmSend`, so the very next `sentTo`-carrying state can be
     * told apart from one the ViewModel produced entirely on its own in [GlucoseAlertViewModel.start]. */
    private var userTriggeredSend = false

    /** Module 6 — field defect: [observeViewModel]'s LiveData replays the last state on every
     * re-subscribe (e.g. a screen rotation), so a naive "sendFailedMessage != null -> toast"
     * would toast again on every replay. Seeded in [observeViewModel] from whatever the
     * ViewModel already holds ([GlucoseAlertViewModel.uiState] survives rotation even though
     * this Activity does not), so a rotation right after a failed send re-observes the SAME
     * value and never re-fires — only a genuine null-to-non-null transition toasts. */
    private var lastSendFailedMessage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGlucoseAlertBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val value = intent.getIntExtra(EXTRA_VALUE, 0)
        val timestampMillis = intent.getLongExtra(EXTRA_TIMESTAMP, System.currentTimeMillis())
        val source = ReadingSource.valueOf(
            intent.getStringExtra(EXTRA_SOURCE) ?: ReadingSource.SENSOR.name
        )

        renderStaticInfo(value, timestampMillis, source)
        setupListeners()
        observeViewModel()

        viewModel.start(value = value, timestampMillis = timestampMillis, source = source)
    }

    /** Everything the ViewModel never touches — value, direction and the primary contact's
     * call label — is display-only and derived straight from the Intent extras and prefs. */
    private fun renderStaticInfo(value: Int, timestampMillis: Long, source: ReadingSource) {
        val direction = AlertDirection.from(value, prefs.rangeMin, prefs.rangeMax)
        val sourceLabel = if (source == ReadingSource.SENSOR) "Sensor" else "Manual"

        binding.tvAlertSource.text = "$sourceLabel · ${timeFormat.format(Date(timestampMillis))}"
        binding.tvAlertTitle.text = if (direction == AlertDirection.HYPO) "Glicemia baixa" else "Glicemia alta"
        binding.tvAlertValue.text = value.toString()
        binding.tvAlertRange.text = "mg/dL · faixa alvo ${prefs.rangeMin}–${prefs.rangeMax}"

        val primaryContact = dbHelper.getContacts().firstOrNull { it.isPrimary }
        if (primaryContact != null) {
            binding.btnCallPrimary.text = "Ligar para a ${primaryContact.relationship}"
            binding.btnCallPrimary.setOnClickListener {
                if (!UIHelper.dialPhone(this, primaryContact.phone)) {
                    UIHelper.showToast(this, "Nenhum aplicativo de telefone encontrado no aparelho")
                }
            }
        } else {
            binding.btnCallPrimary.visibility = View.GONE
        }
    }

    private fun setupListeners() {
        binding.btnImOk.setOnClickListener { finish() }
        binding.btnNotNow.setOnClickListener { finish() }
        binding.btnSendNow.setOnClickListener {
            userTriggeredSend = true
            viewModel.confirmSend()
        }
        binding.btnResend.setOnClickListener {
            userTriggeredSend = true
            viewModel.confirmSend()
        }
    }

    private fun observeViewModel() {
        lastSendFailedMessage = viewModel.uiState.value?.sendFailedMessage
        viewModel.uiState.observe(this) { state ->
            binding.tvMessagePreview.text = state.messagePreview
            binding.llConfirmActions.visibility = if (state.showConfirmActions) View.VISIBLE else View.GONE
            binding.tvThrottleNotice.visibility = if (state.showThrottleNotice) View.VISIBLE else View.GONE
            binding.tvThrottleNotice.text =
                "próximo alerta só daqui a ${state.throttleMin} min · registrado no histórico"
            binding.btnResend.visibility = if (state.showResendAction) View.VISIBLE else View.GONE
            binding.btnImOk.text = state.imOkButtonText

            // Field defect: confirmSend reaching nobody (e.g. SEND_SMS revoked) used to hide
            // every button with no explanation. Toast only on a genuine null -> non-null
            // transition — see [lastSendFailedMessage] for why a naive check would double-fire
            // on rotation.
            if (lastSendFailedMessage == null && state.sendFailedMessage != null) {
                UIHelper.showToast(this, state.sendFailedMessage)
            }
            lastSendFailedMessage = state.sendFailedMessage

            // Achado 5 (Etapa D): sentTo only ever carries recipients whose send actually
            // confirmed (GlucoseAlertViewModel.send filters by the real gateway result), so
            // whenever this card is visible the outcome shown below really is "enviado" —
            // the header just has to stop claiming "automatically" for a send the caregiver
            // triggered by hand.
            binding.llSentTo.visibility = if (state.sentTo.isNotEmpty()) View.VISIBLE else View.GONE
            binding.tvSentHeader.text =
                if (userTriggeredSend) "✓ SMS ENVIADO" else "✓ SMS ENVIADO AUTOMATICAMENTE"
            binding.llRecipients.removeAllViews()
            state.sentTo.forEach { recipient ->
                val itemBinding = ItemAlertRecipientBinding.inflate(
                    layoutInflater, binding.llRecipients, true
                )
                itemBinding.tvRecipientName.text = "${recipient.name} · ${recipient.relationship}"
                itemBinding.tvRecipientStatus.text =
                    "enviado ${timeFormat.format(Date(recipient.sentAtMillis))}"
            }
        }
    }

    companion object {
        const val EXTRA_VALUE = "EXTRA_VALUE"
        const val EXTRA_TIMESTAMP = "EXTRA_TIMESTAMP"
        const val EXTRA_SOURCE = "EXTRA_SOURCE"
    }
}
