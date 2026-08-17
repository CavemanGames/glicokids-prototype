package com.glicokids.prototype.domain.repository

import com.glicokids.prototype.domain.model.HealthPlace
import com.glicokids.prototype.domain.model.NetworkResult

/**
 * Module 7 — test double for [NearbyHealthPlacesRepository]. A plain configured pass-through,
 * same shape as [FakeGeocodingRepository]: the only contract to prove here is the shape (a
 * [NetworkResult], one of its four cases, never an exception).
 */
class FakeNearbyHealthPlacesRepository : NearbyHealthPlacesRepository {

    var result: NetworkResult<List<HealthPlace>> = NetworkResult.Success(emptyList())

    override suspend fun findNearby(
        lat: Double,
        lng: Double,
        radiusMeters: Int
    ): NetworkResult<List<HealthPlace>> = result
}
