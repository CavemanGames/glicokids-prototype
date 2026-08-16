package com.glicokids.prototype.data.remote

import com.glicokids.prototype.domain.model.FoodProduct
import com.glicokids.prototype.domain.model.NetworkResult
import com.glicokids.prototype.domain.repository.FoodSearchRepository
import com.glicokids.prototype.domain.repository.HttpClient
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONException

/**
 * Module 7 — [FoodSearchRepository] over the Open Food Facts search API, composing [HttpClient]
 * with the pure [OpenFoodFactsResponseParser].
 *
 * [USER_AGENT] is required, not decorative: verified against the live service while building
 * this — a request with no `User-Agent` header comes back 503, the same header used here gets a
 * normal answer. No mirror or HTML-body detection here — unlike Overpass (see
 * [OverpassNearbyHealthPlacesRepository]), nothing in that verification pass showed Open Food
 * Facts doing that; a malformed body still degrades safely to
 * [NetworkResult.Failure.UnreadableResponse] either way.
 */
@Singleton
class OpenFoodFactsFoodSearchRepository @Inject constructor(
    private val httpClient: HttpClient
) : FoodSearchRepository {

    override suspend fun search(term: String): NetworkResult<List<FoodProduct>> {
        val url = "$ENDPOINT?search_terms=${URLEncoder.encode(term, "UTF-8")}" +
            "&json=1&page_size=$PAGE_SIZE&fields=$FIELDS"

        return when (val response = httpClient.get(url, USER_AGENT)) {
            is NetworkResult.Success -> try {
                NetworkResult.Success(OpenFoodFactsResponseParser.parse(response.data))
            } catch (e: JSONException) {
                NetworkResult.Failure.UnreadableResponse(e)
            }
            is NetworkResult.Failure -> response
        }
    }

    companion object {
        private const val USER_AGENT = "GlicoKids/1.0 (academic prototype)"
        private const val ENDPOINT = "https://world.openfoodfacts.org/cgi/search.pl"
        private const val PAGE_SIZE = 20
        private const val FIELDS = "product_name,brands,carbohydrates_100g,serving_size,serving_quantity"
    }
}
