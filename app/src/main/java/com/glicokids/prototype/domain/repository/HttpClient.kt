package com.glicokids.prototype.domain.repository

import com.glicokids.prototype.domain.model.NetworkResult

/**
 * Module 7 — a single GET request against an external service, returning the response body as
 * text. Kotlin-only contract, same reasoning as [SmsGateway]: the domain layer never depends on
 * `java.net.HttpURLConnection`.
 *
 * [NearbyHealthPlacesRepository] and [FoodSearchRepository] are both built on top of this — this
 * layer knows nothing about Overpass, Open Food Facts, or how to parse a response; it only knows
 * how to fetch one. That split is what lets `OverpassResponseParser` and
 * `OpenFoodFactsResponseParser` stay pure functions tested without any network involved, while
 * the connectivity check and timeout handling live in exactly one place instead of being
 * duplicated per service.
 *
 * [userAgent] is a parameter, not a fixed constant here, because Open Food Facts and Nominatim
 * both reject requests with no service-appropriate one while Overpass does not care — each
 * caller decides its own.
 *
 * Never throws: no connection, a timeout, a non-2xx status, and the request failing at the
 * socket level all fold into [NetworkResult.Failure]. What actually parsing that body means is
 * left to the caller — see [NetworkResult.Failure.UnreadableResponse]'s own KDoc for why this
 * layer does not try to guess it.
 */
interface HttpClient {
    suspend fun get(url: String, userAgent: String): NetworkResult<String>
}
