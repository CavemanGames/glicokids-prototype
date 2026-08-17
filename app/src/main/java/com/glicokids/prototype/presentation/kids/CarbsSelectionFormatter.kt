package com.glicokids.prototype.presentation.kids

import java.util.Locale

/**
 * Module 7 — turns a [CarbsOrigin] into what b9 shows beside the carbohydrate field, and a
 * carbohydrate value into what goes inside it. Pulled out of [NewMealActivity] so the four
 * chip strings and the decimal formatting are covered by a plain JVM test instead of only ever
 * being exercised on a device.
 */
object CarbsSelectionFormatter {

    /** Null means the chip stays hidden — only [CarbsOrigin.Manual] does that; typing over any
     * other origin already drops back to [CarbsOrigin.Manual] before this is ever called for it. */
    fun chipText(origin: CarbsOrigin): String? = when (origin) {
        is CarbsOrigin.Manual -> null
        is CarbsOrigin.Photo -> "estimado pela foto"
        is CarbsOrigin.LocalFood -> "${origin.name} · tabela do app"
        is CarbsOrigin.OnlineFood -> "${origin.name} · buscado online"
    }

    /** A whole number reads as "45", never "45.0" — [CalculatePortionCarbsUseCase] only ever
     * produces a fraction when an online serving size does not divide evenly into 100g. */
    fun carbsFieldText(carbs: Double): String =
        if (carbs == Math.floor(carbs)) {
            carbs.toInt().toString()
        } else {
            String.format(Locale.getDefault(), "%.1f", carbs)
        }
}
