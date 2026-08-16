package com.glicokids.prototype.domain.repository

import com.glicokids.prototype.domain.model.FoodProduct
import com.glicokids.prototype.domain.model.NetworkResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Module 7 — executable spec for [FoodSearchRepository], run against
 * [FakeFoodSearchRepository]. Same intent as [NearbyHealthPlacesRepositoryContractTest].
 */
class FoodSearchRepositoryContractTest {

    private val product = FoodProduct(
        name = "Nutella",
        brand = "Ferrero",
        carbsPer100g = 57.5,
        servingGrams = 15.0
    )

    @Test
    fun `search returns the resolved list on success`() = runTest {
        val repository = FakeFoodSearchRepository().apply {
            result = NetworkResult.Success(listOf(product))
        }

        val result = repository.search("nutella")

        assertThat(result).isEqualTo(NetworkResult.Success(listOf(product)))
    }

    @Test
    fun `search reports no connection without throwing`() = runTest {
        val repository = FakeFoodSearchRepository().apply {
            result = NetworkResult.Failure.NoConnection
        }

        val result = repository.search("nutella")

        assertThat(result).isEqualTo(NetworkResult.Failure.NoConnection)
    }

    @Test
    fun `search reports service unavailable with the http status when known`() = runTest {
        val repository = FakeFoodSearchRepository().apply {
            result = NetworkResult.Failure.ServiceUnavailable(httpStatusCode = 403)
        }

        val result = repository.search("nutella")

        assertThat(result).isEqualTo(NetworkResult.Failure.ServiceUnavailable(403))
    }

    @Test
    fun `search reports an unreadable response instead of throwing`() = runTest {
        val cause = IllegalStateException("boom")
        val repository = FakeFoodSearchRepository().apply {
            result = NetworkResult.Failure.UnreadableResponse(cause)
        }

        val result = repository.search("nutella")

        assertThat(result).isEqualTo(NetworkResult.Failure.UnreadableResponse(cause))
    }
}
