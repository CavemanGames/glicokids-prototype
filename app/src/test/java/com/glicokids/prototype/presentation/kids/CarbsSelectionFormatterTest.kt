package com.glicokids.prototype.presentation.kids

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CarbsSelectionFormatterTest {

    @Test
    fun `manual origin has no chip text`() {
        assertThat(CarbsSelectionFormatter.chipText(CarbsOrigin.Manual)).isNull()
    }

    @Test
    fun `photo origin reads estimado pela foto`() {
        assertThat(CarbsSelectionFormatter.chipText(CarbsOrigin.Photo))
            .isEqualTo("estimado pela foto")
    }

    @Test
    fun `local food origin names the food and the app table`() {
        val origin = CarbsOrigin.LocalFood("Pão francês")

        assertThat(CarbsSelectionFormatter.chipText(origin))
            .isEqualTo("Pão francês · tabela do app")
    }

    @Test
    fun `online food origin names the food and the online search`() {
        val origin = CarbsOrigin.OnlineFood("Nutella")

        assertThat(CarbsSelectionFormatter.chipText(origin))
            .isEqualTo("Nutella · buscado online")
    }

    @Test
    fun `a whole carbs value has no decimal part`() {
        assertThat(CarbsSelectionFormatter.carbsFieldText(45.0)).isEqualTo("45")
    }

    @Test
    fun `a fractional carbs value keeps one decimal`() {
        assertThat(CarbsSelectionFormatter.carbsFieldText(27.5)).isEqualTo("27.5")
    }

    @Test
    fun `a fractional carbs value rounds to one decimal`() {
        assertThat(CarbsSelectionFormatter.carbsFieldText(8.625)).isEqualTo("8.6")
    }

    @Test
    fun `zero reads as a whole number`() {
        assertThat(CarbsSelectionFormatter.carbsFieldText(0.0)).isEqualTo("0")
    }
}
