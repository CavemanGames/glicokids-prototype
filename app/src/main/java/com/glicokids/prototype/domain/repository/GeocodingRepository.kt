package com.glicokids.prototype.domain.repository

import com.glicokids.prototype.domain.model.GeoPoint

/**
 * Module 7 — turns a coordinate into a human-readable address and back. Kotlin-only contract,
 * same reasoning as [LocationProvider]: the domain layer never depends on `android.location.Geocoder`.
 *
 * Same golden rule as [LocationProvider]: **neither function ever throws**. No network, no
 * geocoding service on the device (common on devices without Play Services), an address that
 * does not resolve to any coordinate, a query with no match — all of it is `null`, never an
 * exception. An address is optional, human-friendly context for an alert or a report; it is
 * never allowed to be the reason a send fails.
 */
interface GeocodingRepository {

    /** The best-effort street address for [lat]/[lng]. `null` when nothing resolves. */
    suspend fun reverse(lat: Double, lng: Double): String?

    /** The best-effort coordinate for a free-text [query]. `null` when nothing matches. */
    suspend fun forward(query: String): GeoPoint?
}
