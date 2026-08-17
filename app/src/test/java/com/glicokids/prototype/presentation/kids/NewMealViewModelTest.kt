package com.glicokids.prototype.presentation.kids

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.glicokids.prototype.data.local.AppPreferences
import com.glicokids.prototype.data.local.GlicoKidsDbHelper
import com.glicokids.prototype.data.model.Food
import com.glicokids.prototype.domain.model.BolusResult
import com.glicokids.prototype.domain.model.FoodProduct
import com.glicokids.prototype.domain.model.NetworkResult
import com.glicokids.prototype.domain.model.Result
import com.glicokids.prototype.domain.repository.FakeFoodSearchRepository
import com.glicokids.prototype.domain.repository.FoodSearchRepository
import com.glicokids.prototype.domain.usecase.CalculateBolusUseCase
import com.glicokids.prototype.domain.usecase.CalculatePortionCarbsUseCase
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class NewMealViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    /**
     * Same bounded-wait pattern as `GlucoseAlertViewModelTest`/`AlertMapViewModelTest` — needed
     * here too because [NewMealViewModel.openFoodSearch] hops through the real
     * `Dispatchers.IO` (GlicoKidsDbHelper is synchronous by contract, so the ViewModel itself
     * does the hop), so the state it produces can land after this method has already returned.
     */
    private fun <T> LiveData<T>.getOrAwaitValue(timeoutSeconds: Long = 2, until: (T) -> Boolean = { true }): T {
        var data: T? = null
        val latch = CountDownLatch(1)
        val observer = object : Observer<T> {
            override fun onChanged(value: T) {
                if (!until(value)) return
                data = value
                latch.countDown()
                this@getOrAwaitValue.removeObserver(this)
            }
        }
        observeForever(observer)
        if (!latch.await(timeoutSeconds, TimeUnit.SECONDS)) {
            throw TimeoutException("LiveData value never set within ${timeoutSeconds}s.")
        }
        @Suppress("UNCHECKED_CAST")
        return data as T
    }

    private val calculateBolusUseCase = mockk<CalculateBolusUseCase>()
    private val prefs = mockk<AppPreferences>(relaxed = true)
    private val dbHelper = mockk<GlicoKidsDbHelper>(relaxed = true)
    private val calculatePortionCarbsUseCase = CalculatePortionCarbsUseCase()
    private lateinit var fakeFoodSearchRepository: FakeFoodSearchRepository
    private lateinit var viewModel: NewMealViewModel

    private fun createViewModel(
        foodSearchRepository: FoodSearchRepository = fakeFoodSearchRepository
    ) = NewMealViewModel(
        calculateBolusUseCase,
        prefs,
        dbHelper,
        foodSearchRepository,
        calculatePortionCarbsUseCase
    )

    @Before
    fun setup() {
        // openFoodSearch/searchOnlineFoods hop through withContext(Dispatchers.IO), a real
        // dispatcher — Unconfined lets the coroutine run eagerly including that resumption,
        // same reasoning as GlucoseAlertViewModelTest/AlertMapViewModelTest.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { prefs.targetGlucose } returns 100
        every { prefs.isf } returns 50
        every { prefs.icRatio } returns 15
        fakeFoodSearchRepository = FakeFoodSearchRepository()
        viewModel = createViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun food(
        id: Long = 1,
        nome: String,
        porcao: String = "1 unidade",
        gramas: Int = 100,
        carboidratoG: Int
    ) = Food(id = id, nome = nome, porcao = porcao, gramas = gramas, carboidratoG = carboidratoG)

    // --- existing bolus calculation behavior (unchanged by the Module 7 collaborators) ---

    @Test
    fun `usa os parametros clinicos das prefs, nao constantes do codigo`() {
        every { prefs.targetGlucose } returns 120
        every { prefs.isf } returns 40
        every { prefs.icRatio } returns 12
        every { calculateBolusUseCase.execute(45.0, 200, 120, 40, 12) } returns
            Result.Success(BolusResult(5.0, "ok"))
        val vm = createViewModel()

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

    // --- Requirement 1: local search is instant, no network ---

    @Test
    fun `opening the food search loads every locally seeded food without touching the network`() {
        every { dbHelper.getFoods() } returns listOf(
            food(id = 1, nome = "Arroz branco cozido", carboidratoG = 28),
            food(id = 2, nome = "Feijão carioca cozido", carboidratoG = 11)
        )

        viewModel.openFoodSearch()
        val state = viewModel.uiState.getOrAwaitValue(until = { it.localFoodResults.isNotEmpty() })

        assertThat(state.localFoodResults).hasSize(2)
    }

    @Test
    fun `opening the food search never reaches the online repository`() {
        every { dbHelper.getFoods() } returns listOf(food(id = 1, nome = "Maçã", carboidratoG = 18))
        val mockRepository = mockk<FoodSearchRepository>(relaxed = true)
        val vm = createViewModel(foodSearchRepository = mockRepository)

        vm.openFoodSearch()
        vm.uiState.getOrAwaitValue(until = { it.localFoodResults.isNotEmpty() })

        coVerify(exactly = 0) { mockRepository.search(any()) }
    }

    @Test
    fun `filtering the local list matches by name case-insensitively without querying the database again`() {
        every { dbHelper.getFoods() } returns listOf(
            food(id = 1, nome = "Arroz branco cozido", carboidratoG = 28),
            food(id = 2, nome = "Feijão carioca cozido", carboidratoG = 11)
        )
        viewModel.openFoodSearch()
        viewModel.uiState.getOrAwaitValue(until = { it.localFoodResults.isNotEmpty() })

        viewModel.searchLocalFoods("ARROZ")

        assertThat(viewModel.uiState.value?.localFoodResults).containsExactly(
            food(id = 1, nome = "Arroz branco cozido", carboidratoG = 28)
        )
        verify(exactly = 1) { dbHelper.getFoods() }
    }

    @Test
    fun `a blank local search term shows the full locally loaded list again`() {
        every { dbHelper.getFoods() } returns listOf(
            food(id = 1, nome = "Arroz branco cozido", carboidratoG = 28),
            food(id = 2, nome = "Feijão carioca cozido", carboidratoG = 11)
        )
        viewModel.openFoodSearch()
        viewModel.uiState.getOrAwaitValue(until = { it.localFoodResults.isNotEmpty() })
        viewModel.searchLocalFoods("arroz")

        viewModel.searchLocalFoods("")

        assertThat(viewModel.uiState.value?.localFoodResults).hasSize(2)
    }

    @Test
    fun `filtering the local list never reaches the online repository`() {
        every { dbHelper.getFoods() } returns listOf(food(id = 1, nome = "Maçã", carboidratoG = 18))
        val mockRepository = mockk<FoodSearchRepository>(relaxed = true)
        val vm = createViewModel(foodSearchRepository = mockRepository)
        vm.openFoodSearch()
        vm.uiState.getOrAwaitValue(until = { it.localFoodResults.isNotEmpty() })

        vm.searchLocalFoods("ma")

        coVerify(exactly = 0) { mockRepository.search(any()) }
    }

    // --- Requirement 2: online search only on an explicit call ---

    @Test
    fun `a blank online search term is ignored and never reaches the repository`() {
        val mockRepository = mockk<FoodSearchRepository>(relaxed = true)
        val vm = createViewModel(foodSearchRepository = mockRepository)

        vm.searchOnlineFoods("   ")

        coVerify(exactly = 0) { mockRepository.search(any()) }
        assertThat(vm.uiState.value?.isSearchingOnline).isFalse()
    }

    // Armadilha do módulo (handoff): a fake that resolves immediately would make this pass even
    // if the ViewModel forgot to publish isSearchingOnline before awaiting the call. A real
    // coAnswers { delay(...) } forces the same genuine suspension a device would see.
    @Test
    fun `isSearchingOnline is true immediately while an online search is still pending`() {
        val hangingRepository = mockk<FoodSearchRepository>()
        coEvery { hangingRepository.search(any()) } coAnswers {
            delay(Long.MAX_VALUE)
            NetworkResult.Success(emptyList())
        }
        val vm = createViewModel(foodSearchRepository = hangingRepository)

        vm.searchOnlineFoods("nutella")

        assertThat(vm.uiState.value?.isSearchingOnline).isTrue()
    }

    @Test
    fun `a successful online search publishes the matched products and clears any previous error`() {
        val product = FoodProduct(name = "Nutella", brand = "Ferrero", carbsPer100g = 57.5, servingGrams = 15.0)
        fakeFoodSearchRepository.result = NetworkResult.Success(listOf(product))

        viewModel.searchOnlineFoods("nutella")

        val state = viewModel.uiState.value
        assertThat(state?.onlineFoodResults).containsExactly(product)
        assertThat(state?.isSearchingOnline).isFalse()
        assertThat(state?.onlineSearchError).isNull()
    }

    /**
     * A stale list still lets the child pick a food; an emptied one is good for nothing. The
     * error belongs beside the previous results, not in their place — the same position the
     * alert map takes when its own lookup fails. Pinned here because the current code gets this
     * right by not touching the field, which is exactly the kind of behaviour a later refactor
     * changes without noticing.
     */
    @Test
    fun `a failed online search keeps the results already on screen instead of wiping them`() {
        val product = FoodProduct(name = "Nutella", brand = "Ferrero", carbsPer100g = 57.5, servingGrams = 15.0)
        fakeFoodSearchRepository.result = NetworkResult.Success(listOf(product))
        viewModel.searchOnlineFoods("nutella")

        fakeFoodSearchRepository.result = NetworkResult.Failure.NoConnection
        viewModel.searchOnlineFoods("nutella")

        val state = viewModel.uiState.value
        assertThat(state?.onlineFoodResults).containsExactly(product)
        assertThat(state?.onlineSearchError).isNotNull()
    }

    @Test
    fun `an online search with no matches is not treated as a failure`() {
        fakeFoodSearchRepository.result = NetworkResult.Success(emptyList())

        viewModel.searchOnlineFoods("um alimento que nao existe")

        val state = viewModel.uiState.value
        assertThat(state?.onlineFoodResults).isEmpty()
        assertThat(state?.onlineSearchError).isNull()
    }

    @Test
    fun `no connection reports a distinct message from a service outage`() {
        fakeFoodSearchRepository.result = NetworkResult.Failure.NoConnection

        viewModel.searchOnlineFoods("nutella")

        val state = viewModel.uiState.value
        assertThat(state?.onlineSearchError).isNotNull()
        assertThat(state?.isSearchingOnline).isFalse()
    }

    @Test
    fun `a service outage reports a message different from no connection`() {
        fakeFoodSearchRepository.result = NetworkResult.Failure.ServiceUnavailable(503)

        viewModel.searchOnlineFoods("nutella")

        val noConnectionMessage = run {
            fakeFoodSearchRepository.result = NetworkResult.Failure.NoConnection
            viewModel.searchOnlineFoods("nutella")
            viewModel.uiState.value?.onlineSearchError
        }
        fakeFoodSearchRepository.result = NetworkResult.Failure.ServiceUnavailable(503)
        viewModel.searchOnlineFoods("nutella")

        assertThat(viewModel.uiState.value?.onlineSearchError).isNotNull()
        assertThat(viewModel.uiState.value?.onlineSearchError).isNotEqualTo(noConnectionMessage)
    }

    @Test
    fun `an unreadable response reports a message different from the other two failures`() {
        fakeFoodSearchRepository.result = NetworkResult.Failure.UnreadableResponse(IllegalStateException("boom"))

        viewModel.searchOnlineFoods("nutella")
        val unreadableMessage = viewModel.uiState.value?.onlineSearchError

        fakeFoodSearchRepository.result = NetworkResult.Failure.NoConnection
        viewModel.searchOnlineFoods("nutella")
        val noConnectionMessage = viewModel.uiState.value?.onlineSearchError

        fakeFoodSearchRepository.result = NetworkResult.Failure.ServiceUnavailable(500)
        viewModel.searchOnlineFoods("nutella")
        val serviceUnavailableMessage = viewModel.uiState.value?.onlineSearchError

        assertThat(unreadableMessage).isNotNull()
        assertThat(unreadableMessage).isNotEqualTo(noConnectionMessage)
        assertThat(unreadableMessage).isNotEqualTo(serviceUnavailableMessage)
    }

    // Product decision (session of 16/08/2026), same call already made for AlertMapViewModel's
    // search: a failed search adds an error message alongside whatever the last successful
    // search already showed, it never wipes it — an old result the child can still pick from is
    // more useful than an empty list. One case is enough since all three failure branches share
    // the same "leave onlineFoodResults untouched" line.
    @Test
    fun `a failed search keeps showing the previous successful result instead of wiping it`() {
        val product = FoodProduct(name = "Nutella", brand = "Ferrero", carbsPer100g = 57.5, servingGrams = 15.0)
        fakeFoodSearchRepository.result = NetworkResult.Success(listOf(product))
        viewModel.searchOnlineFoods("nutella")

        fakeFoodSearchRepository.result = NetworkResult.Failure.NoConnection
        viewModel.searchOnlineFoods("nutella")

        val state = viewModel.uiState.value
        assertThat(state?.onlineFoodResults).containsExactly(product)
        assertThat(state?.onlineSearchError).isNotNull()
    }

    // --- Requirement 3: picking a food resolves the carbs value to apply ---

    @Test
    fun `picking a local food returns its fixed-portion carbs with a LocalFood origin carrying its name`() {
        val selection = viewModel.applyLocalFood(
            food(id = 9, nome = "Pão francês", porcao = "1 unidade", gramas = 50, carboidratoG = 28)
        )

        assertThat(selection).isEqualTo(CarbsSelection(28.0, CarbsOrigin.LocalFood("Pão francês")))
    }

    @Test
    fun `picking an online food with a known serving size returns the portion carbs`() {
        val product = FoodProduct(name = "Nutella", brand = "Ferrero", carbsPer100g = 57.5, servingGrams = 15.0)

        val selection = viewModel.applyOnlineFood(product)

        // 57.5 g/100g * 15g portion / 100 = 8.625
        assertThat(selection.carbs).isEqualTo(8.625)
        assertThat(selection.origin).isEqualTo(CarbsOrigin.OnlineFood("Nutella"))
    }

    @Test
    fun `picking an online food with no known serving size returns the carbs-per-100g value as-is`() {
        val product = FoodProduct(name = "Farinha de trigo", brand = null, carbsPer100g = 76.0, servingGrams = null)

        val selection = viewModel.applyOnlineFood(product)

        assertThat(selection.carbs).isEqualTo(76.0)
        assertThat(selection.origin).isEqualTo(CarbsOrigin.OnlineFood("Farinha de trigo"))
    }
}
