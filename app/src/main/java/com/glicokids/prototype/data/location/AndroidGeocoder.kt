package com.glicokids.prototype.data.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import com.glicokids.prototype.domain.model.GeoPoint
import com.glicokids.prototype.domain.model.LocationTech
import com.glicokids.prototype.domain.repository.GeocodingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Module 7 — [GeocodingRepository] over the platform [Geocoder].
 *
 * The API itself is split across a hard SDK line, and this class is where that split is
 * absorbed so no caller ever has to know it exists:
 *  - SDK 33 (Tiramisu) and up: the callback overloads (`Geocoder.GeocodeListener`), wrapped in
 *    a `suspendCancellableCoroutine` inside `withTimeoutOrNull` — the geocoder does its own
 *    network I/O internally and reports back on its own thread, so there is nothing to move
 *    off the calling thread here.
 *  - Below 33: the synchronous overloads are the *only* ones that exist. They are deprecated
 *    in favor of the callback pair above, but minSdk is 26, so there is no SDK range where a
 *    single unbranched call works — deprecated-but-only-option, moved onto `Dispatchers.IO`
 *    since this branch blocks on real network I/O. `AndroidSmsGateway` makes the same
 *    deliberate call for `SmsManager.getDefault()`, for the same reason: the modern
 *    alternative needs something (a subscription id there, API 33 here) that not every
 *    supported device can give it.
 *
 * Both branches, and [Geocoder.isPresent] up front, are wrapped so nothing here ever throws:
 * no network, no geocoding service on the device (common without Play Services), a query with
 * no match — all of it is `null`, per the [GeocodingRepository] contract, never an exception.
 */
@Singleton
class AndroidGeocoder @Inject constructor(
    @ApplicationContext private val context: Context
) : GeocodingRepository {

    private val geocoder: Geocoder by lazy { Geocoder(context, Locale.getDefault()) }

    override suspend fun reverse(lat: Double, lng: Double): String? {
        if (!Geocoder.isPresent()) return null
        return try {
            resolveAddresses(
                modernCall = { listener -> geocoder.getFromLocation(lat, lng, MAX_RESULTS, listener) },
                legacyCall = { geocoder.getFromLocation(lat, lng, MAX_RESULTS) }
            )?.firstOrNull()?.getAddressLine(0)
        } catch (e: IOException) {
            Log.w(TAG, "Reverse geocoding failed", e)
            null
        }
    }

    override suspend fun forward(query: String): GeoPoint? {
        if (!Geocoder.isPresent()) return null
        return try {
            resolveAddresses(
                modernCall = { listener -> geocoder.getFromLocationName(query, MAX_RESULTS, listener) },
                legacyCall = { geocoder.getFromLocationName(query, MAX_RESULTS) }
            )?.firstOrNull()?.toGeoPoint()
        } catch (e: IOException) {
            Log.w(TAG, "Forward geocoding failed", e)
            null
        }
    }

    // The SDK-33 line, absorbed once instead of duplicated across reverse/forward: the modern
    // callback overload gets a real timeout (Geocoder is not obligated to ever call back), the
    // legacy synchronous overload below it does not need one — it already blocks the calling
    // thread until it has an answer, which is exactly why it only runs on Dispatchers.IO.
    private suspend fun resolveAddresses(
        modernCall: (Geocoder.GeocodeListener) -> Unit,
        legacyCall: () -> List<Address>?
    ): List<Address>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        withTimeoutOrNull(GEOCODE_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                modernCall(object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (continuation.isActive) continuation.resume(addresses)
                    }

                    override fun onError(errorMessage: String?) {
                        Log.w(TAG, "Geocoding failed: $errorMessage")
                        if (continuation.isActive) continuation.resume(null)
                    }
                })
            }
        }
    } else {
        @Suppress("DEPRECATION")
        withContext(Dispatchers.IO) { legacyCall() }
    }

    private fun Address.toGeoPoint(): GeoPoint = GeoPoint(
        lat = latitude,
        lng = longitude,
        // The geocoder never reports a precision for a forward-geocoded point — there is no
        // real value to put here, so this is documented as unknown rather than a guess.
        accuracyMeters = 0f,
        tech = LocationTech.UNKNOWN,
        timestampMillis = System.currentTimeMillis()
    )

    companion object {
        private const val TAG = "GlicoKids_Geocoder"
        private const val MAX_RESULTS = 1
        private const val GEOCODE_TIMEOUT_MILLIS = 8_000L
    }
}
