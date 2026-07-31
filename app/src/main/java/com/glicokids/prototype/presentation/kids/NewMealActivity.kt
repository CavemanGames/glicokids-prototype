package com.glicokids.prototype.presentation.kids

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.glicokids.prototype.data.local.AppPreferences
import com.glicokids.prototype.data.local.GlicoKidsDbHelper
import com.glicokids.prototype.data.model.MealEntry
import com.glicokids.prototype.databinding.ActivityNewMealBinding
import com.glicokids.prototype.util.UIHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class NewMealActivity : AppCompatActivity() {

    private val TAG = "GlicoKids_Lifecycle"
    private lateinit var binding: ActivityNewMealBinding
    private val viewModel: NewMealViewModel by viewModels()

    /** true enquanto o valor no campo de carboidratos veio da câmera, não da digitação. */
    private var carbsFromPhoto = false

    /** Evita gravar a mesma refeição duas vezes se o estado for reemitido. */
    private var mealSaved = false

    @Inject lateinit var prefs: AppPreferences
    @Inject lateinit var dbHelper: GlicoKidsDbHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "NewMealActivity - onCreate")
        binding = ActivityNewMealBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val childName = intent.getStringExtra("CHILD_NAME") ?: prefs.childName
        Log.d(TAG, "NewMealActivity - Iniciando missão para: $childName")

        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        // b9 · O chip "estimado pela foto" só aparece quando o valor veio da câmera.
        binding.tvCarbsSource.visibility = View.GONE

        binding.btnTakePhoto.setOnClickListener {
            Toast.makeText(this, "Simulando captura de foto...", Toast.LENGTH_SHORT).show()
            carbsFromPhoto = true
            binding.etCarbohydrates.setText(SIMULATED_CARBS)
            binding.tvCarbsSource.visibility = View.VISIBLE
        }

        // Digitar por cima da estimativa derruba o chip: o valor deixou de vir da foto.
        binding.etCarbohydrates.doAfterTextChanged {
            if (carbsFromPhoto) {
                carbsFromPhoto = false
            } else {
                binding.tvCarbsSource.visibility = View.GONE
            }
        }

        binding.btnCalculateBolus.setOnClickListener {
            mealSaved = false
            viewModel.calculate(
                binding.etCarbohydrates.text.toString(),
                binding.etCurrentGlucose.text.toString()
            )
        }
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(this) { state ->
            binding.etCarbohydrates.error = state.carbsError
            binding.etCurrentGlucose.error = state.glucoseError

            if (state.insulinDose != null) {
                binding.llResult.visibility = View.VISIBLE
                binding.tvCalculationResult.text =
                    String.format(Locale.getDefault(), "Dose sugerida: %.1f UI", state.insulinDose)

                // A cor do resultado sai da faixa configurada na Área dos Pais.
                val glucoseValue = binding.etCurrentGlucose.text.toString().toIntOrNull()
                    ?: prefs.targetGlucose
                val status = UIHelper.glucoseStatus(glucoseValue, prefs.rangeMin, prefs.rangeMax)
                binding.tvCalculationResult.setTextColor(UIHelper.getStatusColor(status))

                saveMeal(state.insulinDose, glucoseValue)

                binding.btnCalculateBolus.postDelayed({
                    UIHelper.navigateTo(this, RewardActivity::class.java)
                }, 1500)
            } else {
                binding.llResult.visibility = View.GONE
            }

            state.message?.let {
                if (state.insulinDose == null) {
                    Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /** Módulo 5 — requisito 7: a refeição vai para a tabela `meals`. */
    private fun saveMeal(dose: Double, glucose: Int) {
        if (mealSaved) return
        mealSaved = true

        val carbs = binding.etCarbohydrates.text.toString()
            .replace(',', '.').toDoubleOrNull() ?: return

        val meal = MealEntry(
            label = mealLabelForNow(),
            carbsG = carbs,
            glucoseMgdl = glucose,
            bolusUi = dose,
            photoPath = null, // câmera é simulada no protótipo
            createdAt = System.currentTimeMillis()
        )

        lifecycleScope.launch {
            withContext(Dispatchers.IO) { dbHelper.insertMeal(meal) }
            prefs.xp += XP_PER_MEAL
            prefs.coins += COINS_PER_MEAL
        }
    }

    /** Café / Almoço / Lanche / Jantar pela hora do registro. */
    private fun mealLabelForNow(): String =
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..10 -> "Café"
            in 11..14 -> "Almoço"
            in 15..18 -> "Lanche"
            else -> "Jantar"
        }

    // Requisitos do Módulo 2: Logs de ciclo de vida
    override fun onStart() {
        super.onStart()
        Log.d(TAG, "NewMealActivity - onStart")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "NewMealActivity - onResume")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "NewMealActivity - onPause")
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "NewMealActivity - onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "NewMealActivity - onDestroy")
    }

    companion object {
        private const val SIMULATED_CARBS = "45"
        private const val XP_PER_MEAL = 25
        private const val COINS_PER_MEAL = 10
    }
}
