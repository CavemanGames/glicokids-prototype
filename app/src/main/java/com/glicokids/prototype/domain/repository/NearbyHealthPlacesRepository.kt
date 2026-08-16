package com.glicokids.prototype.domain.repository

import com.glicokids.prototype.domain.model.HealthPlace
import com.glicokids.prototype.domain.model.NetworkResult

/**
 * Module 7 — health-related places (hospital, pharmacy, clinic, doctor's office) near a point,
 * ordered by distance. Kotlin-only contract, same reasoning as [SmsGateway] and
 * [LocationProvider]: the domain layer never depends on `java.net.HttpURLConnection` or
 * `org.json`.
 *
 * Unlike [GeocodingRepository] and [LocationProvider], this returns [NetworkResult] instead of
 * a plain nullable — see the KDoc on [NetworkResult] for why. A blank "no places found" state
 * with no explanation is a worse UX here than telling the parent "you are offline" or "the
 * service is down"; on the alert-sending path a location is optional best-effort context, but
 * here it is the entire content of the screen.
 */
interface NearbyHealthPlacesRepository {

    /** Health places within [radiusMeters] of [lat]/[lng], nearest first. */
    suspend fun findNearby(lat: Double, lng: Double, radiusMeters: Int): NetworkResult<List<HealthPlace>>
}
