package com.glicokids.prototype.data.remote

import com.glicokids.prototype.domain.model.HealthPlace
import com.glicokids.prototype.domain.model.NetworkResult
import com.glicokids.prototype.domain.repository.HttpClient
import com.glicokids.prototype.domain.repository.NearbyHealthPlacesRepository
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONException

/**
 * Module 7 — [NearbyHealthPlacesRepository] over the Overpass API (OpenStreetMap), composing
 * [HttpClient] with the pure [OverpassResponseParser].
 *
 * Verified against the live service while building this: [PRIMARY_ENDPOINT] is frequently too
 * busy to answer and, worse than a clean error, returns an HTML error page under a *200* status
 * — [HttpClient] alone has no way to tell that apart from a real answer, since it only looks at
 * the status code. [looksLikeHtml] catches that before the body ever reaches
 * [OverpassResponseParser], which only knows how to read JSON and would otherwise either throw or
 * (worse) silently return an empty list. Either way this is treated exactly like a real non-2xx
 * response: [MIRROR_ENDPOINT] (`overpass.kumi.systems`, verified to answer the same query
 * correctly) gets one retry before this gives up. [NetworkResult.Failure.NoConnection] skips the
 * mirror outright — with no network at all, a different endpoint will not fix that.
 */
@Singleton
class OverpassNearbyHealthPlacesRepository @Inject constructor(
    private val httpClient: HttpClient
) : NearbyHealthPlacesRepository {

    override suspend fun findNearby(
        lat: Double,
        lng: Double,
        radiusMeters: Int
    ): NetworkResult<List<HealthPlace>> {
        val query = buildQuery(lat, lng, radiusMeters)

        val primary = fetch(PRIMARY_ENDPOINT, query)
        val response = when (primary) {
            is NetworkResult.Success -> primary
            is NetworkResult.Failure.NoConnection -> return primary
            is NetworkResult.Failure -> fetch(MIRROR_ENDPOINT, query)
        }

        return when (response) {
            is NetworkResult.Success -> try {
                NetworkResult.Success(OverpassResponseParser.parse(response.data, lat, lng))
            } catch (e: JSONException) {
                NetworkResult.Failure.UnreadableResponse(e)
            }
            is NetworkResult.Failure -> response
        }
    }

    private suspend fun fetch(endpoint: String, query: String): NetworkResult<String> {
        val url = "$endpoint?data=${URLEncoder.encode(query, "UTF-8")}"
        val result = httpClient.get(url, USER_AGENT)
        // A busy server answers 200 with an HTML page, not a real status this app can attach
        // to ServiceUnavailable — see the class KDoc. No status code applies here on purpose.
        return if (result is NetworkResult.Success && looksLikeHtml(result.data)) {
            NetworkResult.Failure.ServiceUnavailable(httpStatusCode = null)
        } else {
            result
        }
    }

    private fun looksLikeHtml(body: String): Boolean = body.trimStart().startsWith("<")

    private fun buildQuery(lat: Double, lng: Double, radiusMeters: Int): String =
        "[out:json][timeout:10];" +
            "(node[\"amenity\"~\"hospital|pharmacy|clinic|doctors\"](around:$radiusMeters,$lat,$lng);" +
            ");out center 20;"

    companion object {
        private const val USER_AGENT = "GlicoKids/1.0 (academic prototype)"
        private const val PRIMARY_ENDPOINT = "https://overpass-api.de/api/interpreter"
        private const val MIRROR_ENDPOINT = "https://overpass.kumi.systems/api/interpreter"
    }
}
