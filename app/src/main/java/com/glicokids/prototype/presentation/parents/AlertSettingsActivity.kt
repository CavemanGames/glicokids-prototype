package com.glicokids.prototype.presentation.parents

import android.app.Activity
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import com.glicokids.prototype.R
import com.glicokids.prototype.databinding.ActivityAlertSettingsBinding
import com.glicokids.prototype.domain.model.AlertMode
import com.glicokids.prototype.util.SoundHelper
import dagger.hilt.android.AndroidEntryPoint

/**
 * b21 — Automatic alerts. Hypo and hyper are two independent RadioGroups: touching
 * one row never changes the other group's selection. Each row (not just the
 * RadioButton, which is `clickable=false`) is the 56dp click target, per §10.
 */
@AndroidEntryPoint
class AlertSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlertSettingsBinding
    private val viewModel: AlertSettingsViewModel by viewModels()

    private val throttleOptions = listOf(15, 30, 60)

    /** Module 8 — which of the three sound rows opened the shared picker below; read and
     * cleared as soon as its result comes back. See SPEC-MODULO-8-SOM.md §13 for the accepted
     * risk of this being in-memory state (lost on process death mid-picker). */
    private var pendingSoundEvent: SoundHelper.SoundEvent? = null

    private val ringtonePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val event = pendingSoundEvent ?: return@registerForActivityResult
        pendingSoundEvent = null
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val pickedUri: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        // Device defect (Samsung SM-S938B, Android 16): the system picker returns RESULT_OK even
        // on Back, so `previousStored` (read before this call writes anything) lets
        // SoundHelper.resolveStoredValue tell an untouched Back apart from a real pick.
        val previousStored = storedSoundFor(event)
        val stored = SoundHelper.resolveStoredValue(event, previousStored, pickedUri)
        when (event) {
            SoundHelper.SoundEvent.MEDAL_EARNED -> viewModel.setMedalSound(stored)
            SoundHelper.SoundEvent.GLUCOSE_ALERT -> viewModel.setAlertSound(stored)
            SoundHelper.SoundEvent.DOSE_CALCULATED -> viewModel.setDoseSound(stored)
        }
    }

    private fun storedSoundFor(event: SoundHelper.SoundEvent): String? = when (event) {
        SoundHelper.SoundEvent.MEDAL_EARNED -> viewModel.uiState.value?.medalSoundUri
        SoundHelper.SoundEvent.GLUCOSE_ALERT -> viewModel.uiState.value?.alertSoundUri
        SoundHelper.SoundEvent.DOSE_CALCULATED -> viewModel.uiState.value?.doseSoundUri
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlertSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.optHypoAuto.setOnClickListener { viewModel.setHypoMode(AlertMode.AUTO) }
        binding.optHypoSuggest.setOnClickListener { viewModel.setHypoMode(AlertMode.SUGGEST) }
        binding.optHypoOff.setOnClickListener { viewModel.setHypoMode(AlertMode.OFF) }

        binding.optHyperAuto.setOnClickListener { viewModel.setHyperMode(AlertMode.AUTO) }
        binding.optHyperSuggest.setOnClickListener { viewModel.setHyperMode(AlertMode.SUGGEST) }
        binding.optHyperOff.setOnClickListener { viewModel.setHyperMode(AlertMode.OFF) }

        binding.rowThrottle.setOnClickListener { showThrottleDialog() }

        binding.swRecoveryNotice.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setRecoveryNoticeEnabled(isChecked)
        }

        binding.swIncludeLocation.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setIncludeLocation(isChecked)
        }

        binding.swSoundEnabled.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setSoundEnabled(isChecked)
        }

        binding.rowMedalSound.setOnClickListener {
            openRingtonePicker(SoundHelper.SoundEvent.MEDAL_EARNED, viewModel.uiState.value?.medalSoundUri)
        }
        binding.rowAlertSound.setOnClickListener {
            openRingtonePicker(SoundHelper.SoundEvent.GLUCOSE_ALERT, viewModel.uiState.value?.alertSoundUri)
        }
        binding.rowDoseSound.setOnClickListener {
            openRingtonePicker(SoundHelper.SoundEvent.DOSE_CALCULATED, viewModel.uiState.value?.doseSoundUri)
        }
    }

    /** Opens the system's ringtone picker filtered to notification sounds — the one seam that
     * both keeps sounds short (§4) and lets the picker itself show "Nenhum"/"Padrão" already
     * translated to the device's language.
     *
     * Two states short-circuit straight to the picker's replacement, each with its own
     * confirmation dialog instead: a row that already has a sound configured (any event — see
     * [showChangeOrResetDialog], the "back to default" path decided by the human 23/08/2026) and a
     * never-chosen `GLUCOSE_ALERT` (whose factory default is the app's own synthesized pattern,
     * with no matching picker option — see [confirmReplacingAppAlertSound]). A never-chosen
     * `MEDAL_EARNED`/`DOSE_CALCULATED` opens the picker directly, unchanged. */
    private fun openRingtonePicker(event: SoundHelper.SoundEvent, currentStored: String?) {
        when {
            currentStored != null -> showChangeOrResetDialog(event, currentStored)
            event == SoundHelper.SoundEvent.GLUCOSE_ALERT -> confirmReplacingAppAlertSound(event, currentStored)
            else -> launchRingtonePicker(event, currentStored)
        }
    }

    /** Shown when the row already has a sound configured (a chosen Uri or "Nenhum") — there was no
     * way back to the factory default once a sound was picked, since the system picker offers no
     * "App/device default" option of its own. Three outcomes: open the picker to choose another
     * sound, reset this event back to `null` ("never chosen") without opening the picker, or leave
     * everything untouched. */
    private fun showChangeOrResetDialog(event: SoundHelper.SoundEvent, currentStored: String) {
        val currentLabel = SoundHelper.labelFor(this, event, currentStored)
        AlertDialog.Builder(this)
            .setTitle("Som configurado")
            .setMessage("Som atual: $currentLabel")
            .setPositiveButton("Escolher outro som") { _, _ -> launchRingtonePicker(event, currentStored) }
            .setNeutralButton("Voltar ao padrão") { _, _ -> resetToDefault(event) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** Restores the event's factory default by writing `null` back — "Padrão do app" for the
     * alert, "Padrão do aparelho" for medal/dose (SoundHelper.labelFor resolves the exact text). */
    private fun resetToDefault(event: SoundHelper.SoundEvent) {
        when (event) {
            SoundHelper.SoundEvent.MEDAL_EARNED -> viewModel.setMedalSound(null)
            SoundHelper.SoundEvent.GLUCOSE_ALERT -> viewModel.setAlertSound(null)
            SoundHelper.SoundEvent.DOSE_CALCULATED -> viewModel.setDoseSound(null)
        }
    }

    /** Shown only when the alert has never been configured — see [openRingtonePicker]. Cancelling
     * changes nothing and never opens the picker. */
    private fun confirmReplacingAppAlertSound(event: SoundHelper.SoundEvent, currentStored: String?) {
        AlertDialog.Builder(this)
            .setTitle("Trocar o som do alerta?")
            .setMessage(
                "Hoje, quando a glicemia sai da faixa, o app toca um som próprio do GlicoKids " +
                    "(3 bipes). Se você escolher um som do aparelho agora, ele vai substituir esse som."
            )
            .setPositiveButton("Escolher som") { _, _ -> launchRingtonePicker(event, currentStored) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun launchRingtonePicker(event: SoundHelper.SoundEvent, currentStored: String?) {
        pendingSoundEvent = event
        ringtonePickerLauncher.launch(SoundHelper.buildRingtonePickerIntent(event, currentStored))
    }

    private fun showThrottleDialog() {
        val labels = throttleOptions.map { "$it min" }.toTypedArray()
        val current = viewModel.uiState.value?.throttleMin
        val checkedIndex = throttleOptions.indexOf(current).takeIf { it >= 0 } ?: -1

        AlertDialog.Builder(this)
            .setTitle("Repetir no máximo")
            .setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
                viewModel.setThrottleMin(throttleOptions[which])
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(this) { state ->
            binding.tvAlertRecipients.text = recipientsLabel(state.recipientCount)
            binding.tvHypoLimit.text = "abaixo de ${state.hypoLimit}"
            binding.tvHyperLimit.text = "acima de ${state.hyperLimit}"
            binding.tvThrottleValue.text = "${state.throttleMin} min"

            applyHypoSelection(state.hypoMode)
            applyHyperSelection(state.hyperMode)

            if (binding.swRecoveryNotice.isChecked != state.recoveryNoticeEnabled) {
                binding.swRecoveryNotice.isChecked = state.recoveryNoticeEnabled
            }
            if (binding.swIncludeLocation.isChecked != state.includeLocation) {
                binding.swIncludeLocation.isChecked = state.includeLocation
            }

            if (binding.swSoundEnabled.isChecked != state.soundEnabled) {
                binding.swSoundEnabled.isChecked = state.soundEnabled
            }
            binding.swSoundEnabled.contentDescription =
                "Sons do app, ${stateLabel(state.soundEnabled)}"

            applySoundRow(
                binding.tvMedalSoundValue, binding.rowMedalSound,
                "Som da medalha",
                SoundHelper.labelFor(this, SoundHelper.SoundEvent.MEDAL_EARNED, state.medalSoundUri)
            )
            applySoundRow(
                binding.tvAlertSoundValue, binding.rowAlertSound,
                "Som do alerta de glicemia",
                SoundHelper.labelFor(this, SoundHelper.SoundEvent.GLUCOSE_ALERT, state.alertSoundUri)
            )
            applySoundRow(
                binding.tvDoseSoundValue, binding.rowDoseSound,
                "Som da dose calculada",
                SoundHelper.labelFor(this, SoundHelper.SoundEvent.DOSE_CALCULATED, state.doseSoundUri)
            )
        }
    }

    private fun stateLabel(enabled: Boolean) = if (enabled) "ligado" else "desligado"

    /** Applies a sound row's label, only touching the view when the value actually changed —
     * same guard already used for the switches, so an identical re-emission of state does not
     * rebuild the UI. `contentDescription` is set on the whole row (not just the value
     * TextView), since the row itself is the 48dp click target. */
    private fun applySoundRow(
        valueView: android.widget.TextView,
        row: LinearLayout,
        label: String,
        value: String
    ) {
        if (valueView.text != value) {
            valueView.text = value
        }
        row.contentDescription = "$label, $value"
    }

    private fun recipientsLabel(count: Int): String =
        if (count == 1) "1 pessoa marcada para receber SMS" else "$count pessoas marcadas para receber SMS"

    private fun applyHypoSelection(mode: AlertMode) {
        val rows = listOf(
            AlertMode.AUTO to (binding.optHypoAuto to binding.rbHypoAuto),
            AlertMode.SUGGEST to (binding.optHypoSuggest to binding.rbHypoSuggest),
            AlertMode.OFF to (binding.optHypoOff to binding.rbHypoOff)
        )
        applySelection(rows, mode, R.drawable.bg_option_selected)
    }

    private fun applyHyperSelection(mode: AlertMode) {
        val rows = listOf(
            AlertMode.AUTO to (binding.optHyperAuto to binding.rbHyperAuto),
            AlertMode.SUGGEST to (binding.optHyperSuggest to binding.rbHyperSuggest),
            AlertMode.OFF to (binding.optHyperOff to binding.rbHyperOff)
        )
        applySelection(rows, mode, R.drawable.bg_option_selected_purple)
    }

    /** Marks the RadioButton of the selected row and swaps its background; every other row goes idle. */
    private fun applySelection(
        rows: List<Pair<AlertMode, Pair<LinearLayout, android.widget.RadioButton>>>,
        selected: AlertMode,
        selectedBackground: Int
    ) {
        rows.forEach { (mode, row) ->
            val (container, radioButton) = row
            val isSelected = mode == selected
            radioButton.isChecked = isSelected
            container.setBackgroundResource(if (isSelected) selectedBackground else R.drawable.bg_option_idle)
        }
    }
}
