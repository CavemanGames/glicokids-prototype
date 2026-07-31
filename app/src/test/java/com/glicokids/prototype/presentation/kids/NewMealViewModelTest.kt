package com.glicokids.prototype.presentation.kids

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.glicokids.prototype.data.local.AppPreferences
import com.glicokids.prototype.domain.model.BolusResult
import com.glicokids.prototype.domain.model.Result
import com.glicokids.prototype.domain.usecase.CalculateBolusUseCase
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class NewMealViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val calculateBolusUseCase = mockk<CalculateBolusUseCase>()
    private val prefs = mockk<AppPreferences>(relaxed = true)
    private lateinit var viewModel: NewMealViewModel

    @Before
    fun setup() {
        every { prefs.targetGlucose } returns 100
        every { prefs.isf } returns 50
        every { prefs.icRatio } returns 15
        viewModel = NewMealViewModel(calculateBolusUseCase, prefs)
    }

    @Test
    fun `usa os parametros clinicos das prefs, nao constantes do codigo`() {
        every { prefs.targetGlucose } returns 120
        every { prefs.isf } returns 40
        every { prefs.icRatio } returns 12
        every { calculateBolusUseCase.execute(45.0, 200, 120, 40, 12) } returns
            Result.Success(BolusResult(5.0, "ok"))
        val vm = NewMealViewModel(calculateBolusUseCase, prefs)

        vm.calculate("45", "200")

        verify { calculateBolusUseCase.execute(45.0, 200, 120, 40, 12) }
        assertThat(vm.uiState.value?.insulinDose).isEqualTo(5.0)
    }

    @Test
    fun `when calculating with empty carbs, should show error`() {
        viewModel.calculate("", "110")
        
        val state = viewModel.uiState.value
        assertThat(state?.carbsError).isEqualTo("Campo obrigatório")
        assertThat(state?.insulinDose).isNull()
    }

    @Test
    fun `when calculating with empty glucose, should show error`() {
        viewModel.calculate("45", "")
        
        val state = viewModel.uiState.value
        assertThat(state?.glucoseError).isEqualTo("Campo obrigatório")
    }

    @Test
    fun `when calculation is successful, should update insulin dose`() {
        val mockResult = BolusResult(3.5, "Success")
        every { calculateBolusUseCase.execute(45.0, 110, 100, 50, 15) } returns Result.Success(mockResult)

        viewModel.calculate("45", "110")

        val state = viewModel.uiState.value
        assertThat(state?.insulinDose).isEqualTo(3.5)
        assertThat(state?.carbsError).isNull()
        assertThat(state?.glucoseError).isNull()
    }

    @Test
    fun `when calculation fails, should show error message`() {
        every { calculateBolusUseCase.execute(any(), any(), any(), any(), any()) } returns 
                Result.Failure(Exception(), "System Error")

        viewModel.calculate("45", "110")

        val state = viewModel.uiState.value
        assertThat(state?.message).isEqualTo("System Error")
        assertThat(state?.insulinDose).isNull()
    }
}
