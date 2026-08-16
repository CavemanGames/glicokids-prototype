package com.glicokids.prototype.presentation.parents

import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import com.glicokids.prototype.R
import com.glicokids.prototype.databinding.ActivityAlertSettingsBinding
import com.glicokids.prototype.domain.model.AlertMode
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
        }
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
