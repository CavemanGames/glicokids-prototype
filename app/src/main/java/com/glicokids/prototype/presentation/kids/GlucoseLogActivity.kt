package com.glicokids.prototype.presentation.kids

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.glicokids.prototype.data.local.AppPreferences
import com.glicokids.prototype.data.local.GlicoKidsDbHelper
import com.glicokids.prototype.data.model.GlucoseReading
import com.glicokids.prototype.databinding.ActivityGlucoseLogBinding
import com.glicokids.prototype.util.UIHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class GlucoseLogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGlucoseLogBinding
    private var currentInput = ""

    @Inject lateinit var prefs: AppPreferences
    @Inject lateinit var dbHelper: GlicoKidsDbHelper

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
                currentInput = currentInput.dropLast(1)
                updateDisplay()
            }
        }

        binding.btnGKeyOk.setOnClickListener { saveReading() }

        binding.tgInputMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) applyInputMode(sensor = checkedId == binding.btnModeSensor.id)
        }
    }

    /** Módulo 5 — requisito 7: a leitura vai para a tabela `glucose_readings`. */
    private fun saveReading() {
        val value = currentInput.toIntOrNull()
        if (value == null || value <= 0) {
            UIHelper.showToast(this, "Digite um valor de glicemia")
            return
        }

        val sensorMode = binding.tgInputMode.checkedButtonId == binding.btnModeSensor.id
        val reading = GlucoseReading(
            valueMgdl = value,
            status = UIHelper.glucoseStatus(value, prefs.rangeMin, prefs.rangeMax),
            source = if (sensorMode) GlucoseReading.Source.SENSOR else GlucoseReading.Source.MANUAL,
            createdAt = System.currentTimeMillis()
        )

        lifecycleScope.launch {
            withContext(Dispatchers.IO) { dbHelper.insertGlucoseReading(reading) }
            prefs.xp += XP_PER_READING
            UIHelper.showToast(
                this@GlucoseLogActivity,
                "Glicemia $value mg/dL registrada · +$XP_PER_READING XP"
            )
            finish()
        }
    }

    private fun updateDisplay() {
        binding.tvGlucoseValue.text = if (currentInput.isEmpty()) "- - -" else currentInput

        val value = currentInput.toIntOrNull() ?: prefs.targetGlucose
        // A faixa vem das prefs: mudar o alvo na Área dos Pais recolore esta tela.
        val status = UIHelper.glucoseStatus(value, prefs.rangeMin, prefs.rangeMax)

        binding.tvStatusChip.text = when (status) {
            UIHelper.GlucoseStatus.NA_META -> "Na meta! Glico adorou"
            UIHelper.GlucoseStatus.ATENCAO -> "Atenção! Glico em alerta"
            UIHelper.GlucoseStatus.FORA_DA_META -> "Fora da meta! Glico preocupado"
        }
        // O chip inteiro segue o estado: texto na cor cheia, fundo na mesma cor com alpha.
        val color = UIHelper.getStatusColor(status)
        binding.tvStatusChip.setTextColor(color)
        binding.tvStatusChip.backgroundTintList =
            android.content.res.ColorStateList.valueOf(UIHelper.withAlpha(color, 0.16f))
        binding.tvGlucoseValue.setTextColor(color)
    }

    companion object {
        private const val XP_PER_READING = 5
    }
}
