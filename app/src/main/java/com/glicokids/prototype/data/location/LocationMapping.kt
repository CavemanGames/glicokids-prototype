package com.glicokids.prototype.data.location

import android.location.Location
import com.glicokids.prototype.domain.model.GeoPoint
import com.glicokids.prototype.domain.model.LocationTech

/**
 * Module 7 — the one place the platform's [Location] turns into this project's [GeoPoint].
 * Both [FusedLocationProvider] and [RawLocationDataSource] read a fix under a different
 * [LocationTech], but the field mapping itself never differs, so it lives here once instead
 * of being copied into each call site.
 */
internal fun Location.toGeoPoint(tech: LocationTech): GeoPoint = GeoPoint(
    lat = latitude,
    lng = longitude,
    accuracyMeters = accuracy,
    tech = tech,
    timestampMillis = time
)
