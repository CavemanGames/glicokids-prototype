package com.glicokids.prototype.domain.repository

import com.glicokids.prototype.domain.model.FoodProduct
import com.glicokids.prototype.domain.model.NetworkResult

/**
 * Module 7 — food products matching a free-text search term, sourced from Open Food Facts.
 * Kotlin-only contract, same reasoning as [NearbyHealthPlacesRepository]: the domain layer
 * never depends on `java.net.HttpURLConnection` or `org.json`.
 *
 * Returns [NetworkResult] rather than a plain nullable/empty list for the same reason
 * [NearbyHealthPlacesRepository] does — search results are the entire content of the screen
 * that calls this, so "no results" and "could not reach the service" need to read differently
 * to the parent typing a search term.
 */
interface FoodSearchRepository {

    /** Products matching [term]. Products with no reported carbohydrate value are never
     * included — see [com.glicokids.prototype.domain.model.FoodProduct] for why. */
    suspend fun search(term: String): NetworkResult<List<FoodProduct>>
}
