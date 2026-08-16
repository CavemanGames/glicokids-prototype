package com.glicokids.prototype.domain.usecase

import javax.inject.Inject

/**
 * Module 7 — carbohydrates in a portion of a food whose label reports carbs per 100g. Plain
 * rule of three, pulled out of the food-search screen so it is testable without any UI or
 * network involved — same reasoning [CalculateBolusUseCase] keeps the dose math out of the
 * ViewModel.
 *
 * Negative inputs are floored to 0.0 rather than propagated: a negative carb value or portion
 * has no physical meaning, and letting the arithmetic carry a bad input into a bolus dose
 * downstream would be worse than clamping it here, at the one place it is computed.
 */
class CalculatePortionCarbsUseCase @Inject constructor() {

    fun execute(carbsPer100g: Double, portionGrams: Double): Double {
        val safeCarbsPer100g = carbsPer100g.coerceAtLeast(0.0)
        val safePortionGrams = portionGrams.coerceAtLeast(0.0)
        return safeCarbsPer100g * safePortionGrams / 100.0
    }
}
