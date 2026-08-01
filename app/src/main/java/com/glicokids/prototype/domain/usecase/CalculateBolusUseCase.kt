package com.glicokids.prototype.domain.usecase

import com.glicokids.prototype.domain.model.BolusResult
import com.glicokids.prototype.domain.model.Result
import javax.inject.Inject

/**
 * Bolus calculation use case with error handling (resilience).
 */
class CalculateBolusUseCase @Inject constructor() {

    fun execute(
        carbs: Double,
        currentGlucose: Int,
        targetGlucose: Int,
        sensitivityFactor: Int,
        carbRatio: Int
    ): Result<BolusResult> {
        return try {
            // Fail path validations
            if (sensitivityFactor <= 0) {
                return Result.Failure(IllegalArgumentException("Fator de Sensibilidade deve ser maior que zero."), "Erro de configuração clínica.")
            }
            if (carbRatio <= 0) {
                return Result.Failure(IllegalArgumentException("Relação IC deve ser maior que zero."), "Erro de configuração nutricional.")
            }
            if (carbs < 0) {
                return Result.Failure(IllegalArgumentException("Carboidratos não podem ser negativos."), "Dados de entrada inválidos.")
            }

            // Happy Path
            if (carbs == 0.0 && currentGlucose <= targetGlucose) {
                return Result.Success(BolusResult(0.0, "Nenhuma dose necessária."))
            }

            val foodBolus = carbs / carbRatio
            val correctionBolus = if (currentGlucose > targetGlucose) {
                (currentGlucose - targetGlucose).toDouble() / sensitivityFactor
            } else {
                0.0
            }

            val totalDose = foodBolus + correctionBolus
            
            // Conservative safety rule: always round down in 0.5 U steps
            val safeDose = Math.floor(totalDose * 2) / 2.0

            Result.Success(
                BolusResult(
                    insulinDose = safeDose,
                    message = "Cálculo realizado com sucesso.",
                    isSafetyAlert = safeDose > 5.0
                )
            )
        } catch (e: Exception) {
            // Catches unexpected errors (process failure / simulated 500)
            Result.Failure(e, "Ocorreu um erro inesperado no processamento do cálculo.")
        }
    }
}
