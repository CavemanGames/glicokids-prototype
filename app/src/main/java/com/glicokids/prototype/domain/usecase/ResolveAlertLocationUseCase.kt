package com.glicokids.prototype.domain.usecase

import com.glicokids.prototype.domain.model.AlertLocationSnapshot
import com.glicokids.prototype.domain.repository.GeocodingRepository
import com.glicokids.prototype.domain.repository.LocationProvider
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Module 7 — resolves best-effort "where is the child" context for a glucose alert, inside a
 * time budget entirely its own ([BUDGET_MILLIS]), independent of whatever timeout it hands
 * [LocationProvider.getCurrentLocation] internally. Gated by [execute]'s `consentGiven`: when
 * false, neither dependency is touched at all.
 *
 * The one rule every future change to this class must keep: a glucose alert is sent identically
 * whether this returns a populated [AlertLocationSnapshot] or null. Missing consent, no fix,
 * geocoding that does not resolve, or simply taking longer than [BUDGET_MILLIS] — none of it is
 * allowed to block or delay the alert, only to leave the location out of it. Same defensive
 * shape as [LocationProvider] and [GeocodingRepository] themselves: never throws.
 *
 * The whole fix + reverse-geocode chain runs inside a single `withTimeoutOrNull(BUDGET_MILLIS)`
 * window; a timeout here degrades to `null` exactly like a missing fix or a permission denial —
 * there is no distinct error path for it.
 */
class ResolveAlertLocationUseCase @Inject constructor(
    private val locationProvider: LocationProvider,
    private val geocodingRepository: GeocodingRepository
) {

    suspend fun execute(consentGiven: Boolean): AlertLocationSnapshot? {
        if (!consentGiven) return null

        return withTimeoutOrNull(BUDGET_MILLIS) {
            val point = locationProvider.getCurrentLocation() ?: return@withTimeoutOrNull null
            val label = geocodingRepository.reverse(point.lat, point.lng)
            AlertLocationSnapshot(point = point, label = label)
        }
    }

    companion object {
        /** Own ceiling for the whole fix + reverse-geocode chain, regardless of any timeout
         * passed to [LocationProvider.getCurrentLocation] itself. */
        const val BUDGET_MILLIS = 6_000L
    }
}
