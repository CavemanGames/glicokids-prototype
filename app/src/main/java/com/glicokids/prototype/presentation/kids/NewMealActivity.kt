package com.glicokids.prototype.presentation.kids

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.glicokids.prototype.databinding.ActivityNewMealBinding
import com.glicokids.prototype.util.UIHelper
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class NewMealActivity : AppCompatActivity() {

    private val TAG = "GlicoKids_Lifecycle"
    private lateinit var binding: ActivityNewMealBinding
    private val viewModel: NewMealViewModel by viewModels()

    /** true enquanto o valor no campo de carboidratos veio da câmera, não da digitação. */
    private var carbsFromPhoto = false

    @Inject
    lateinit var storageRepository: com.glicokids.prototype.domain.repository.StorageRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "NewMealActivity - onCreate")
        binding = ActivityNewMealBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val childName = intent.getStringExtra("CHILD_NAME") ?: "Pequeno Herói"
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
            binding.etCarbohydrates.setText("45")
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
            viewModel.calculate(
                binding.etCarbohydrates.text.toString(),
                binding.etCurrentGlucose.text.toString()
            )
        }
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(this) { state ->
            // Update errors
            binding.etCarbohydrates.error = state.carbsError
            binding.etCurrentGlucose.error = state.glucoseError

            // Update Result
            if (state.insulinDose != null) {
                binding.llResult.visibility = View.VISIBLE
                binding.tvCalculationResult.text = String.format(Locale.getDefault(), "Dose sugerida: %.1f UI", state.insulinDose)
                
                // Ajuste 4: Centralized color based on range
                val min = storageRepository.getInt("range_min", 70)
                val max = storageRepository.getInt("range_max", 180)
                val glucoseValue = binding.etCurrentGlucose.text.toString().toIntOrNull() ?: 100
                
                val status = UIHelper.glucoseStatus(glucoseValue, min, max)
                binding.tvCalculationResult.setTextColor(UIHelper.getStatusColor(status))

                // Navigate to Reward Screen after a short delay to show the result
                binding.btnCalculateBolus.postDelayed({
                    UIHelper.navigateTo(this, RewardActivity::class.java)
                }, 1500)
            } else {
                binding.llResult.visibility = View.GONE
            }

            // Show system messages
            state.message?.let {
                if (state.insulinDose == null) {
                    Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                }
            }
        }
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
}
