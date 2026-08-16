package com.glicokids.prototype.domain.repository

import com.glicokids.prototype.domain.model.GeoPoint
import com.glicokids.prototype.domain.model.LocationTech
import kotlinx.coroutines.flow.Flow

/**
 * Module 7 — reads device position. Kotlin-only contract so the domain layer never depends on
 * `com.google.android.gms.location` or `android.location`, same reasoning as [SmsGateway].
 *
 * Golden rule of this contract, binding on every implementation: **none of these functions
 * ever throw**. Missing permission, a timed-out fix, every provider disabled, no Play
 * Services on the device — all of it degrades to `null` (or an empty list), never an
 * exception. The reason is concrete, not defensive-programming-for-its-own-sake: a fix from
 * here rides along in a hypoglycemia alert SMS (Module 6), and the alert send can never be
 * blocked or aborted because the location could not be read. The address/position is optional,
 * degradable context — never a precondition for the message going out. Same defensive shape
 * as `AndroidSmsGateway.sendTextMessage`: catch everything at the implementation boundary, log
 * it there, hand the caller a neutral value.
 */
interface LocationProvider {

    /**
     * A single, one-shot fix. Returns `null` if no fix arrives within [timeoutMillis], if
     * permission is missing, or if the read fails for any other reason.
     */
    suspend fun getCurrentLocation(timeoutMillis: Long = 8_000): GeoPoint?

    /** A continuous stream of fixes, spaced roughly every [intervalMillis]. Never throws or completes with an exception; an unrecoverable failure simply stops emitting. */
    fun locationUpdates(intervalMillis: Long): Flow<GeoPoint>

    /** Which technologies currently have a usable, enabled provider behind them. Empty when none do — never a reason to throw. */
    fun availableTechnologies(): List<LocationTech>

    /** Whether this app currently holds the location permission it needs. Never triggers the system permission prompt — that is an Activity-level concern, same split as everywhere else in this codebase. */
    fun hasPermission(): Boolean
}
