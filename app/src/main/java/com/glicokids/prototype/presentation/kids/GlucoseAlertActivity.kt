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
        binding.btnSendNow.setOnClickListener { viewModel.confirmSend() }
        binding.btnResend.setOnClickListener { viewModel.confirmSend() }
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(this) { state ->
            binding.tvMessagePreview.text = state.messagePreview
            binding.llConfirmActions.visibility = if (state.showConfirmActions) View.VISIBLE else View.GONE
            binding.tvThrottleNotice.text =
                "próximo alerta só daqui a ${state.throttleMin} min · registrado no histórico"

            binding.llSentTo.visibility = if (state.sentTo.isNotEmpty()) View.VISIBLE else View.GONE
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
