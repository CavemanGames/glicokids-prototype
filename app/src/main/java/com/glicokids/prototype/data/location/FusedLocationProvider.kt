package com.glicokids.prototype.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.glicokids.prototype.domain.model.GeoPoint
import com.glicokids.prototype.domain.model.LocationTech
import com.glicokids.prototype.domain.repository.LocationProvider
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Module 7 — [LocationProvider] backed by Play Services' fused provider for a live fix, and by
 * [LocationManager] for the two checks that do not need a fix at all.
 *
 * Same shape as `AndroidSmsGateway`: [getCurrentLocation] wraps a `suspendCancellableCoroutine`
 * in `withTimeoutOrNull`, and nothing here is allowed to escape as an exception — the contract
 * on [LocationProvider] is explicit that every function degrades to `null`/empty instead. A
 * fix from here can ride along in a hypoglycemia alert SMS, and that send can never be blocked
 * by a location read going wrong.
 */
@Singleton
class FusedLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fusedClient: FusedLocationProviderClient,
    private val locationManager: LocationManager
) : LocationProvider {

    override suspend fun getCurrentLocation(timeoutMillis: Long): GeoPoint? {
        if (!hasPermission()) return null
        return try {
            withTimeoutOrNull(timeoutMillis) { awaitCurrentLocation() }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read current location", e)
            null
        }
    }

    @SuppressLint("MissingPermission") // guarded by hasPermission() in getCurrentLocation()
    private suspend fun awaitCurrentLocation(): GeoPoint? = suspendCancellableCoroutine { continuation ->
        val cancellationSource = CancellationTokenSource()
        continuation.invokeOnCancellation { cancellationSource.cancel() }

        try {
            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationSource.token)
                .addOnSuccessListener { location ->
                    if (continuation.isActive) {
                        continuation.resume(location?.toGeoPoint(LocationTech.FUSED))
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Fused location request failed", e)
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
        } catch (e: SecurityException) {
            // Permission revoked in the gap between the hasPermission() check above and this
            // call reaching the radio — the same race AndroidSmsGateway guards against for a
            // malformed SMS send.
            Log.w(TAG, "Location permission revoked mid-read", e)
            if (continuation.isActive) {
                continuation.resume(null)
            }
        }
    }

    @SuppressLint("MissingPermission") // guarded by hasPermission() below
    override fun locationUpdates(intervalMillis: Long): Flow<GeoPoint> = callbackFlow {
        if (!hasPermission()) {
            close()
            return@callbackFlow
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis).build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it.toGeoPoint(LocationTech.FUSED)) }
            }
        }

        try {
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission revoked before subscribing to updates", e)
            close()
        }

        // Without this the callback stays registered with the fused client for as long as the
        // process lives, regardless of whether anything still collects this flow — the first
        // Flow this project builds, and the first place that leak could happen.
        awaitClose { fusedClient.removeLocationUpdates(callback) }
    }

    override fun availableTechnologies(): List<LocationTech> = try {
        locationManager.getProviders(true).mapNotNull { provider ->
            when (provider) {
                LocationManager.GPS_PROVIDER -> LocationTech.GPS
                LocationManager.NETWORK_PROVIDER -> LocationTech.NETWORK
                LocationManager.PASSIVE_PROVIDER -> LocationTech.PASSIVE
                else -> null
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to read enabled location providers", e)
        emptyList()
    }

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "GlicoKids_Location"
    }
}
