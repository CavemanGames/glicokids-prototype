package com.glicokids.prototype.data.remote

import com.glicokids.prototype.domain.model.FoodProduct
import com.glicokids.prototype.domain.model.NetworkResult
import com.glicokids.prototype.domain.repository.FakeHttpClient
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Module 7 — [OpenFoodFactsFoodSearchRepository] against [FakeHttpClient], offline. Robolectric
 * because a successful response is parsed through [OpenFoodFactsResponseParser], which needs it
 * for `org.json`.
 */
@RunWith(RobolectricTestRunner::class)
class OpenFoodFactsFoodSearchRepositoryTest {

    private val validJson = """
        {"products":[{"product_name":"Nutella","brands":"Ferrero","carbohydrates_100g":57.5,
          "serving_quantity":"15"}]}
    """.trimIndent()

    private fun repository(httpClient: FakeHttpClient) = OpenFoodFactsFoodSearchRepository(httpClient)

    @Test
    fun `returns the parsed products and sends the required user agent`() = runTest {
        val httpClient = FakeHttpClient().apply {
            whenUrlContains("openfoodfacts.org", NetworkResult.Success(validJson))
        }

        val result = repository(httpClient).search("nutella")

        assertThat(result).isInstanceOf(NetworkResult.Success::class.java)
        val products = (result as NetworkResult.Success<List<FoodProduct>>).data
        assertThat(products).hasSize(1)
        assertThat(products[0].name).isEqualTo("Nutella")
        // Verified against the live service: a request with no User-Agent is refused with a 503.
        assertThat(httpClient.requestedUserAgents.single()).isNotEmpty()
    }

    @Test
    fun `reports no connection without throwing`() = runTest {
        val httpClient = FakeHttpClient().apply { default = NetworkResult.Failure.NoConnection }

        val result = repository(httpClient).search("nutella")

        assertThat(result).isEqualTo(NetworkResult.Failure.NoConnection)
    }

    @Test
    fun `reports service unavailable with the http status when known`() = runTest {
        val httpClient = FakeHttpClient().apply {
            whenUrlContains("openfoodfacts.org", NetworkResult.Failure.ServiceUnavailable(503))
        }

        val result = repository(httpClient).search("nutella")

        assertThat(result).isEqualTo(NetworkResult.Failure.ServiceUnavailable(503))
    }

    @Test
    fun `reports an unreadable response instead of throwing on malformed JSON`() = runTest {
        val httpClient = FakeHttpClient().apply {
            whenUrlContains("openfoodfacts.org", NetworkResult.Success("not json"))
        }

        val result = repository(httpClient).search("nutella")

        assertThat(result).isInstanceOf(NetworkResult.Failure.UnreadableResponse::class.java)
    }

    @Test
    fun `returns an empty list when nothing matches`() = runTest {
        val httpClient = FakeHttpClient().apply {
            whenUrlContains("openfoodfacts.org", NetworkResult.Success("""{"products":[]}"""))
        }

        val result = repository(httpClient).search("xyzxyzxyz")

        assertThat(result).isEqualTo(NetworkResult.Success(emptyList<FoodProduct>()))
    }
}
