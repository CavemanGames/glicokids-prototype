package com.glicokids.prototype.data.remote

import com.glicokids.prototype.domain.model.HealthPlace
import com.glicokids.prototype.domain.model.HealthPlaceType
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import org.json.JSONObject

/**
 * Module 7 — turns a raw Overpass API (OpenStreetMap) JSON response into the [HealthPlace]
 * list the nearby-help screen renders, sorted nearest first from [originLat]/[originLng].
 *
 * Overpass returns every OSM node that matched the query, including ones with no `name` tag
 * (a node tagged with the wrong amenity, an incomplete community edit) — a marker with no label
 * helps nobody, so those are dropped rather than shown as "unnamed place". A node missing
 * `lat`/`lon` (should not happen for the node-only query this project sends, but the field is
 * still optional per the general Overpass response schema) is dropped the same way.
 *
 * Throws [org.json.JSONException] on a response that is not the shape this parses. Deciding
 * what that means for the caller — degrading the whole call to
 * [com.glicokids.prototype.domain.model.NetworkResult.Failure.UnreadableResponse] — is the
 * repository implementation's job, not this parser's; this stays a pure function of the
 * response text.
 */
object OverpassResponseParser {

    fun parse(json: String, originLat: Double, originLng: Double): List<HealthPlace> {
        val elements = JSONObject(json).optJSONArray("elements") ?: return emptyList()

        val places = buildList {
            for (i in 0 until elements.length()) {
                val element = elements.getJSONObject(i)
                val tags = element.optJSONObject("tags") ?: continue
                val name = tags.optString("name", "").takeIf { it.isNotBlank() } ?: continue
                if (!element.has("lat") || !element.has("lon")) continue

                val lat = element.getDouble("lat")
                val lon = element.getDouble("lon")
                add(
                    HealthPlace(
                        name = name,
                        type = toHealthPlaceType(tags.optString("amenity")),
                        lat = lat,
                        lng = lon,
                        distanceMeters = haversineMeters(originLat, originLng, lat, lon)
                    )
                )
            }
        }

        return places.sortedBy { it.distanceMeters }
    }

    private fun toHealthPlaceType(amenity: String): HealthPlaceType = when (amenity) {
        "hospital" -> HealthPlaceType.HOSPITAL
        "pharmacy" -> HealthPlaceType.PHARMACY
        "clinic" -> HealthPlaceType.CLINIC
        "doctors" -> HealthPlaceType.DOCTOR
        else -> HealthPlaceType.UNKNOWN
    }

    // Great-circle distance in meters. Accurate enough at city scale for a "how far is this
    // place" label — nothing here feeds a clinical calculation, unlike CalculateBolusUseCase.
    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusMeters = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusMeters * c
    }
}
