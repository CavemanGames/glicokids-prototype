package com.glicokids.prototype.domain.repository

import com.glicokids.prototype.domain.model.GeoPoint

/**
 * Module 7 — test double for [GeocodingRepository]. A plain configured pass-through: reverse
 * and forward geocoding have no behavioral rule of their own to enforce the way
 * [FakeLocationProvider] enforces the permission gate and the timeout race — the only contract
 * to prove here is the shape (a resolved value, or null, never an exception).
 */
class FakeGeocodingRepository : GeocodingRepository {

    var addressForReverse: String? = null
    var pointForForward: GeoPoint? = null

    override suspend fun reverse(lat: Double, lng: Double): String? = addressForReverse

    override suspend fun forward(query: String): GeoPoint? = pointForForward
}
