package com.glicokids.prototype.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.glicokids.prototype.domain.model.GeoPoint
import com.glicokids.prototype.domain.model.LocationTech
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Module 7 — samples GPS and network last-known positions side by side, each tagged with its
 * own real technology and accuracy.
 *
 * Why this exists alongside [FusedLocationProvider]: the academic requirement asks the app to
 * demonstrate a fix obtained through GPS, Cell ID and Wi-Fi specifically. Google's fused
 * provider blends all three internally and only ever reports back as "fused" — on its own it
 * demonstrates nothing about which technology actually produced the fix. `LocationManager`
 * splits satellite ([LocationManager.GPS_PROVIDER]) from network ([LocationManager.NETWORK_PROVIDER]),
 * and that second provider is itself cell tower **and** Wi-Fi combined — the Android public API
 * does not expose a way to tell those two apart. Sampling both providers side by side, each
 * with its own [LocationTech] and its own real `accuracyMeters`, is what makes the difference
 * in precision between them visible and honest instead of collapsing everything into one
 * opaque "located" state.
 *
 * Same golden rule as [FusedLocationProvider]: never throws, missing permission or an unset
 * provider degrades to an empty (or shorter) list.
 */
@Singleton
class RawLocationDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationManager: LocationManager
) {

    @SuppressLint("MissingPermission") // guarded by the permission check below
    suspend fun sampleEachProvider(): List<GeoPoint> {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }

        return listOfNotNull(
            sampleProvider(LocationManager.GPS_PROVIDER, LocationTech.GPS),
            sampleProvider(LocationManager.NETWORK_PROVIDER, LocationTech.NETWORK)
        )
    }

    private fun sampleProvider(provider: String, tech: LocationTech): GeoPoint? {
        val location = try {
            locationManager.getLastKnownLocation(provider)
        } catch (e: Exception) {
            // SecurityException (permission revoked mid-read) or IllegalArgumentException
            // (provider not present on this device, e.g. no GPS chip) — either way, this
            // provider simply contributes nothing to the sample.
            Log.w(TAG, "Failed to read last known location for provider=$provider", e)
            null
        }
        return location?.toGeoPoint(tech)
    }

    companion object {
        private const val TAG = "GlicoKids_Location"
    }
}
