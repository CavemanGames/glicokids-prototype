package com.glicokids.prototype.domain.usecase

import com.glicokids.prototype.domain.model.Result
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CalculateBolusUseCaseTest {

    private val useCase = CalculateBolusUseCase()

    @Test
    fun `quando houver carboidratos, deve calcular dose de alimento corretamente`() {
        val result = useCase.execute(
            carbs = 40.0,
            currentGlucose = 100,
            targetGlucose = 100,
            sensitivityFactor = 50,
            carbRatio = 10
        )

        assertThat(result).isInstanceOf(Result.Success::class.java)
        val successResult = result as Result.Success
        assertThat(successResult.data.insulinDose).isEqualTo(4.0)
    }

    @Test
    fun `FAIL PATH - quando fator de sensibilidade for zero, deve retornar falha`() {
        val result = useCase.execute(
            carbs = 40.0,
            currentGlucose = 150,
            targetGlucose = 100,
            sensitivityFactor = 0, // Faulty value under test
            carbRatio = 10
        )

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        val failureResult = result as Result.Failure
        assertThat(failureResult.message).contains("configuração clínica")
    }

    @Test
    fun `FAIL PATH - quando carboidratos forem negativos, deve retornar falha`() {
        val result = useCase.execute(
            carbs = -10.0,
            currentGlucose = 100,
            targetGlucose = 100,
            sensitivityFactor = 50,
            carbRatio = 10
        )

        assertThat(result).isInstanceOf(Result.Failure::class.java)
    }
}
