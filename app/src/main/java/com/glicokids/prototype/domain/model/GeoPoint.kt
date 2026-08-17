package com.glicokids.prototype.domain.model

/**
 * Module 7 — a single positioning fix: where, how precise, which technology produced it and
 * when. `accuracyMeters` and [tech] both come straight off the platform location object, so a
 * consumer (a report screen, an alert SMS body) can decide on its own whether a fix is precise
 * enough to show, without the location layer making that call.
 */
data class GeoPoint(
    val lat: Double,
    val lng: Double,
    val accuracyMeters: Float,
    val tech: LocationTech,
    val timestampMillis: Long
)
