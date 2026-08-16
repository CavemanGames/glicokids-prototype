package com.glicokids.prototype.presentation.kids

import com.glicokids.prototype.data.model.Food
import com.glicokids.prototype.domain.model.FoodProduct

data class NewMealUiState(
    val insulinDose: Double? = null,
    val message: String? = null,
    val carbsError: String? = null,
    val glucoseError: String? = null,
    val isLoading: Boolean = false,
    // Module 7 — b9's "buscar alimento" dialog. Local results come from the `foods` table
    // (loaded once, filtered in memory — see NewMealViewModel.openFoodSearch/searchLocalFoods),
    // online results only ever change after an explicit searchOnlineFoods() call.
    val localFoodResults: List<Food> = emptyList(),
    val onlineFoodResults: List<FoodProduct> = emptyList(),
    val isSearchingOnline: Boolean = false,
    val onlineSearchError: String? = null
)
