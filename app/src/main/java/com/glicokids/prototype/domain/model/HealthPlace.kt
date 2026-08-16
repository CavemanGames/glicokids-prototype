package com.glicokids.prototype.domain.model

/**
 * Module 7 — a health-related place (hospital, pharmacy, clinic or doctor's office) found near
 * the child, sourced from OpenStreetMap via the Overpass API. [distanceMeters] is computed at
 * parse time from the point the search was centered on — the API itself does not return it.
 */
data class HealthPlace(
    val name: String,
    val type: HealthPlaceType,
    val lat: Double,
    val lng: Double,
    val distanceMeters: Double
)

/**
 * Mirrors the OSM `amenity` values the Overpass query filters on
 * (`hospital|pharmacy|clinic|doctors`). [UNKNOWN] is the safe fallback for an amenity value
 * outside that set, matching the pattern [LocationTech.UNKNOWN] uses for the same reason: a
 * value this enum does not recognize is never a reason to throw or drop the place.
 */
enum class HealthPlaceType { HOSPITAL, PHARMACY, CLINIC, DOCTOR, UNKNOWN }
