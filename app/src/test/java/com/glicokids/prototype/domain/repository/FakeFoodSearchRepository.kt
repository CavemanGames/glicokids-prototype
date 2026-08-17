package com.glicokids.prototype.domain.repository

import com.glicokids.prototype.domain.model.FoodProduct
import com.glicokids.prototype.domain.model.NetworkResult

/**
 * Module 7 — test double for [FoodSearchRepository]. Same shape as
 * [FakeNearbyHealthPlacesRepository]: a plain configured pass-through.
 */
class FakeFoodSearchRepository : FoodSearchRepository {

    var result: NetworkResult<List<FoodProduct>> = NetworkResult.Success(emptyList())

    override suspend fun search(term: String): NetworkResult<List<FoodProduct>> = result
}
