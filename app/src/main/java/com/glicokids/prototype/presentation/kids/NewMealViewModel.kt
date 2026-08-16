package com.glicokids.prototype.presentation.kids

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glicokids.prototype.data.local.AppPreferences
import com.glicokids.prototype.data.local.GlicoKidsDbHelper
import com.glicokids.prototype.data.model.Food
import com.glicokids.prototype.domain.model.FoodProduct
import com.glicokids.prototype.domain.model.NetworkResult
import com.glicokids.prototype.domain.model.Result
import com.glicokids.prototype.domain.repository.FoodSearchRepository
import com.glicokids.prototype.domain.usecase.CalculateBolusUseCase
import com.glicokids.prototype.domain.usecase.CalculatePortionCarbsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class NewMealViewModel @Inject constructor(
    private val calculateBolusUseCase: CalculateBolusUseCase,
    private val prefs: AppPreferences,
    // Module 7 — b9's "buscar alimento" dialog shares this ViewModel rather than getting its
    // own: the food picked there has to land on the exact carbs field calculate() already
    // reads, and a second ViewModel would need its own channel just to hand that value back.
    private val dbHelper: GlicoKidsDbHelper,
    private val foodSearchRepository: FoodSearchRepository,
    private val calculatePortionCarbsUseCase: CalculatePortionCarbsUseCase
) : ViewModel() {

    private val _uiState = MutableLiveData(NewMealUiState())
    val uiState: LiveData<NewMealUiState> = _uiState

    /** Every food from the `foods` table, loaded once by [openFoodSearch] and re-filtered in
     * memory by [searchLocalFoods] — the DB is never touched again per keystroke. */
    private var allLocalFoods: List<Food> = emptyList()

    /** Loads every locally seeded food so the search dialog has something to show the instant
     * it opens, before the child types anything or asks for an online search. */
    fun openFoodSearch() {
        viewModelScope.launch {
            val foods = withContext(Dispatchers.IO) { dbHelper.getFoods() }
            allLocalFoods = foods
            _uiState.value = _uiState.value?.copy(localFoodResults = foods)
        }
    }

    /** Filters the list already loaded by [openFoodSearch] in memory — no database or network
     * call, which is what keeps this instant while the child types. */
    fun searchLocalFoods(term: String) {
        val filtered = if (term.isBlank()) {
            allLocalFoods
        } else {
            allLocalFoods.filter { it.nome.contains(term, ignoreCase = true) }
        }
        _uiState.value = _uiState.value?.copy(localFoodResults = filtered)
    }

    /** Only called from an explicit "buscar online" tap — never from [openFoodSearch] or
     * [searchLocalFoods], so a slow network never blocks the Meal Mission's most common path.
     *
     * A failure keeps whatever results were already on screen and puts the message beside them
     * rather than in their place: a stale list still lets the child pick a food, an empty one is
     * good for nothing. The alert map takes the same position for the same reason.
     *
     * The messages are shorter than their counterparts on the map screen, and that is deliberate,
     * not an inconsistency to tidy up later — this screen is read by a child, where a long
     * sentence simply does not get read, while the map lives in the Parent Area. */
    fun searchOnlineFoods(term: String) {
        if (term.isBlank()) return

        _uiState.value = _uiState.value?.copy(isSearchingOnline = true, onlineSearchError = null)
        viewModelScope.launch {
            when (val result = foodSearchRepository.search(term)) {
                is NetworkResult.Success -> _uiState.value = _uiState.value?.copy(
                    onlineFoodResults = result.data,
                    isSearchingOnline = false,
                    onlineSearchError = null
                )
                is NetworkResult.Failure.NoConnection -> _uiState.value = _uiState.value?.copy(
                    isSearchingOnline = false,
                    onlineSearchError = "Sem conexão com a internet."
                )
                is NetworkResult.Failure.ServiceUnavailable -> _uiState.value = _uiState.value?.copy(
                    isSearchingOnline = false,
                    onlineSearchError = "Serviço de busca indisponível agora. Tente de novo mais tarde."
                )
                is NetworkResult.Failure.UnreadableResponse -> _uiState.value = _uiState.value?.copy(
                    isSearchingOnline = false,
                    onlineSearchError = "Não foi possível ler a resposta da busca."
                )
            }
        }
    }

    /** [Food.carboidratoG] already is the carb amount for that food's fixed [Food.porcao] —
     * unlike an online [FoodProduct], the local table has no per-100g value to scale. */
    fun applyLocalFood(food: Food): CarbsSelection =
        CarbsSelection(food.carboidratoG.toDouble(), CarbsOrigin.LocalFood(food.nome))

    /** A known serving size is scaled through [CalculatePortionCarbsUseCase]; with none
     * reported, [FoodProduct.carbsPer100g] is used as-is — the child can still adjust the
     * portion by hand afterwards. */
    fun applyOnlineFood(product: FoodProduct): CarbsSelection {
        val carbs = product.servingGrams?.let {
            calculatePortionCarbsUseCase.execute(product.carbsPer100g, it)
        } ?: product.carbsPer100g
        return CarbsSelection(carbs, CarbsOrigin.OnlineFood(product.name))
    }

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

        // Module 5: the parameters come from the Parent Area (SharedPreferences),
        // no longer from constants — editing ISF/IC/target genuinely changes the result.
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
