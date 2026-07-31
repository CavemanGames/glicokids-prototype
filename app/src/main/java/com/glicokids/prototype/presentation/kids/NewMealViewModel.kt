package com.glicokids.prototype.presentation.kids

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.glicokids.prototype.data.local.AppPreferences
import com.glicokids.prototype.domain.model.Result
import com.glicokids.prototype.domain.usecase.CalculateBolusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class NewMealViewModel @Inject constructor(
    private val calculateBolusUseCase: CalculateBolusUseCase,
    private val prefs: AppPreferences
) : ViewModel() {

    private val _uiState = MutableLiveData(NewMealUiState())
    val uiState: LiveData<NewMealUiState> = _uiState

    fun calculate(carbsText: String, glucoseText: String) {
        // Sanitization: Remove everything that is not a digit or decimal point
        val cleanCarbs = carbsText.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.')
        val cleanGlucose = glucoseText.filter { it.isDigit() }

        if (cleanCarbs.isBlank()) {
            _uiState.value = _uiState.value?.copy(carbsError = "Campo obrigatório", insulinDose = null)
            return
        }
        if (cleanGlucose.isBlank()) {
            _uiState.value = _uiState.value?.copy(glucoseError = "Campo obrigatório", insulinDose = null)
            return
        }

        val carbs = cleanCarbs.toDoubleOrNull() ?: 0.0
        val glucose = cleanGlucose.toIntOrNull() ?: 0

        // Módulo 5: os parâmetros vêm da Área dos Pais (SharedPreferences),
        // não mais de constantes — editar o FSI/I/C/alvo muda o cálculo de verdade.
        val result = calculateBolusUseCase.execute(
            carbs = carbs,
            currentGlucose = glucose,
            targetGlucose = prefs.targetGlucose,
            sensitivityFactor = prefs.isf,
            carbRatio = prefs.icRatio
        )

        when (result) {
            is Result.Success -> {
                _uiState.value = NewMealUiState(
                    insulinDose = result.data.insulinDose,
                    message = result.data.message
                )
            }
            is Result.Failure -> {
                _uiState.value = NewMealUiState(
                    message = result.message
                )
            }
            else -> {}
        }
    }
}
