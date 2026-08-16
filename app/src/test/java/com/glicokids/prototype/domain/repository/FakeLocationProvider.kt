package com.glicokids.prototype.domain.repository

import com.glicokids.prototype.domain.model.GeoPoint
import com.glicokids.prototype.domain.model.LocationTech
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Module 7 — test double for [LocationProvider]. Unlike a plain stub, it enforces two of the
 * contract's own rules instead of just handing back whatever a test configures: [permissionGranted]
 * gates every read the same way a real implementation must, and a [fix] that takes longer than
 * [fixDelayMillis] to "arrive" genuinely races [getCurrentLocation]'s own `timeoutMillis` through
 * `withTimeoutOrNull` — meant to be driven with `kotlinx-coroutines-test` virtual time so the
 * timeout test does not need to wait in real time.
 *
 * What this double does not, and cannot honestly, reproduce is anything specific to the
 * Android/Play Services stack itself (a real `SecurityException`, a `LocationCallback` failure,
 * a provider disabled mid-read) — that needs the real implementation and a Robolectric or
 * instrumented test beside it, the same split `AndroidSmsGatewayTest` keeps from
 * `GlucoseAlertViewModelTest`.
 */
class FakeLocationProvider : LocationProvider {

    var permissionGranted: Boolean = true
    var fix: GeoPoint? = null
    var fixDelayMillis: Long = 0
    var technologies: List<LocationTech> = emptyList()
    var updates: Flow<GeoPoint> = flow { }

    override suspend fun getCurrentLocation(timeoutMillis: Long): GeoPoint? {
        if (!permissionGranted) return null
        return withTimeoutOrNull(timeoutMillis) {
            delay(fixDelayMillis)
            fix
        }
    }

    override fun locationUpdates(intervalMillis: Long): Flow<GeoPoint> = updates

    override fun availableTechnologies(): List<LocationTech> = technologies

    override fun hasPermission(): Boolean = permissionGranted
}
