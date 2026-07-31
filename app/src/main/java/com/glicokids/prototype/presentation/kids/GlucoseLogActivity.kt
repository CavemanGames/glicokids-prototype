package com.glicokids.prototype.presentation.kids

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.glicokids.prototype.databinding.ActivityGlucoseLogBinding
import com.glicokids.prototype.util.UIHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class GlucoseLogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGlucoseLogBinding
    private var currentInput = ""

    @Inject
    lateinit var storageRepository: com.glicokids.prototype.domain.repository.StorageRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGlucoseLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        applyInputMode(sensor = binding.tgInputMode.checkedButtonId == binding.btnModeSensor.id)
        updateDisplay()
    }

    /** b16 · O banner do sensor só existe no modo Sensor; "Digitar" mostra o teclado. */
    private fun applyInputMode(sensor: Boolean) {
        binding.cvSensorBanner.visibility = if (sensor) View.VISIBLE else View.GONE
        binding.glKeypad.visibility = if (sensor) View.GONE else View.VISIBLE
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        val keys = listOf(
            binding.btnGKey1, binding.btnGKey2, binding.btnGKey3,
            binding.btnGKey4, binding.btnGKey5, binding.btnGKey6,
            binding.btnGKey7, binding.btnGKey8, binding.btnGKey9,
            binding.btnGKey0
        )

        keys.forEach { btn ->
            btn.setOnClickListener { 
                if (currentInput.length < 3) {
                    currentInput += btn.text.toString()
                    updateDisplay()
                }
            }
        }

        binding.btnGKeyDel.setOnClickListener {
            if (currentInput.isNotEmpty()) {
                currentInput = currentInput.substring(0, currentInput.length - 1)
                updateDisplay()
            }
        }

        binding.btnGKeyOk.setOnClickListener {
            if (currentInput.isNotEmpty()) {
                UIHelper.showToast(this, "Glicemia registrada: $currentInput mg/dL")
                finish()
            }
        }

        binding.tgInputMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) applyInputMode(sensor = checkedId == binding.btnModeSensor.id)
        }
    }

    private fun updateDisplay() {
        binding.tvGlucoseValue.text = if (currentInput.isEmpty()) "---" else currentInput
        
        val value = currentInput.toIntOrNull() ?: 100
        val min = storageRepository.getInt("range_min", 70)
        val max = storageRepository.getInt("range_max", 180)
        
        val status = UIHelper.glucoseStatus(value, min, max)
        
        binding.tvStatusChip.text = when (status) {
            UIHelper.GlucoseStatus.NA_META -> "Na meta! Glico adorou 🎯"
            UIHelper.GlucoseStatus.ATENCAO -> "Atenção! Glico em alerta ⚠️"
            UIHelper.GlucoseStatus.FORA_DA_META -> "Fora da meta! Glico preocupado 😟"
        }
        
        // Note: Using hardcoded color strings as per design tokens if needed, 
        // but UIHelper already provides them.
    }
}
