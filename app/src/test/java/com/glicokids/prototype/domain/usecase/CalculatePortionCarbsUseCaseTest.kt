package com.glicokids.prototype.domain.usecase

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CalculatePortionCarbsUseCaseTest {

    private val useCase = CalculatePortionCarbsUseCase()

    @Test
    fun `computes carbs for a portion smaller than 100g`() {
        val carbs = useCase.execute(carbsPer100g = 57.5, portionGrams = 15.0)

        assertThat(carbs).isEqualTo(8.625)
    }

    @Test
    fun `computes carbs for a portion larger than 100g`() {
        val carbs = useCase.execute(carbsPer100g = 28.0, portionGrams = 250.0)

        assertThat(carbs).isEqualTo(70.0)
    }

    @Test
    fun `a zero portion yields zero carbs`() {
        val carbs = useCase.execute(carbsPer100g = 57.5, portionGrams = 0.0)

        assertThat(carbs).isEqualTo(0.0)
    }

    @Test
    fun `a negative carbsPer100g is floored to zero instead of producing a negative result`() {
        val carbs = useCase.execute(carbsPer100g = -10.0, portionGrams = 100.0)

        assertThat(carbs).isEqualTo(0.0)
    }

    @Test
    fun `a negative portion is floored to zero instead of producing a negative result`() {
        val carbs = useCase.execute(carbsPer100g = 57.5, portionGrams = -50.0)

        assertThat(carbs).isEqualTo(0.0)
    }
}
