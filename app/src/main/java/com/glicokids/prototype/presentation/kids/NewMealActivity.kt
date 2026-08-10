package com.glicokids.prototype.presentation.kids

import android.Manifest
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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

    /** true while the carbohydrate value came from the camera rather than typing. */
    private var carbsFromPhoto = false

    /** Prevents storing the same meal twice if the state is re-emitted. */
    private var mealSaved = false

    @Inject lateinit var prefs: AppPreferences
    @Inject lateinit var dbHelper: GlicoKidsDbHelper

    // Module 6 (Etapa D) — real camera capture (`.artifacts/SPEC-camera-captura-real.md` §3).
    // Permission is requested unconditionally in onCreate, same pattern as SupportNetworkActivity
    // (b19): a denial just falls back to a toast when the button is pressed, never a re-prompt.
    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Log.d(TAG, "NewMealActivity - permissão de câmera negada")
            }
        }

    // TakePicturePreview() returns the Bitmap straight into the callback — no Uri, no
    // FileProvider, no file. The thumbnail lives only in memory for as long as this screen is
    // open (SPEC §4/§7): it is never written to photoPath, never survives process recreation.
    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            onPhotoCaptured(bitmap)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "NewMealActivity - onCreate")
        binding = ActivityNewMealBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val childName = intent.getStringExtra("CHILD_NAME") ?: prefs.childName
        Log.d(TAG, "NewMealActivity - Iniciando missão para: $childName")

        requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)

        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        // b9 · The "estimado pela foto" chip only shows when the value came from the camera.
        binding.tvCarbsSource.visibility = View.GONE
        applyPhotoState(hasPhoto = false)

        binding.btnTakePhoto.setOnClickListener {
            when {
                !UIHelper.hasCameraPermission(this) ->
                    UIHelper.showToast(this, "Permita o acesso à câmera para fotografar o prato.")
                !UIHelper.hasCameraAppAvailable(this) ->
                    UIHelper.showToast(this, "Nenhum aplicativo de câmera disponível.")
                else -> takePictureLauncher.launch(null)
            }
        }

        // Typing over the estimate drops the chip: the value no longer comes from the photo.
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

    /**
     * Module 6 (Etapa D) — `TakePicturePreview()` callback. `bitmap == null` means the user
     * cancelled or the camera app failed; nothing on screen changes and no toast fires (SPEC
     * §7 — that is not an error to flag). `bitmap != null` shows the real thumbnail and only
     * then runs the pre-existing simulated-carbs behaviour, unchanged.
     */
    private fun onPhotoCaptured(bitmap: Bitmap?) {
        if (bitmap == null) return

        binding.ivMealPhoto.setImageBitmap(bitmap)
        applyPhotoState(hasPhoto = true)

        Toast.makeText(this, "Simulando captura de foto...", Toast.LENGTH_SHORT).show()
        carbsFromPhoto = true
        binding.etCarbohydrates.setText(SIMULATED_CARBS)
        binding.tvCarbsSource.visibility = View.VISIBLE
    }

    /** Applies [MealPhotoUiState] to `ivMealPhoto`/`ivPhotoPlaceholder` (SPEC §6). */
    private fun applyPhotoState(hasPhoto: Boolean) {
        val state = MealPhotoUiState.forPhoto(hasPhoto)
        binding.ivPhotoPlaceholder.visibility = if (state.placeholderVisible) View.VISIBLE else View.GONE
        binding.ivMealPhoto.importantForAccessibility = if (state.photoImportantForAccessibility) {
            View.IMPORTANT_FOR_ACCESSIBILITY_YES
        } else {
            View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        binding.ivMealPhoto.contentDescription = state.photoContentDescription
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(this) { state ->
            binding.etCarbohydrates.error = state.carbsError
            binding.etCurrentGlucose.error = state.glucoseError

            if (state.insulinDose != null) {
                binding.llResult.visibility = View.VISIBLE
                binding.tvCalculationResult.text =
                    String.format(Locale.getDefault(), "Dose sugerida: %.1f UI", state.insulinDose)

                // The result colour comes from the range configured in the Parent Area.
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

    /** Module 5 — requirement 7: the meal goes into the `meals` table. */
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
            photoPath = null, // Module 6 (Etapa D): the photo is real but lives only in memory, never on disk
            createdAt = System.currentTimeMillis()
        )

        lifecycleScope.launch {
            withContext(Dispatchers.IO) { dbHelper.insertMeal(meal) }
            prefs.xp += XP_PER_MEAL
            prefs.coins += COINS_PER_MEAL
        }
    }

    /** Breakfast / lunch / snack / dinner, chosen by the time of day. */
    private fun mealLabelForNow(): String =
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..10 -> "Café"
            in 11..14 -> "Almoço"
            in 15..18 -> "Lanche"
            else -> "Jantar"
        }

    // Module 2 requirement: lifecycle logging
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
