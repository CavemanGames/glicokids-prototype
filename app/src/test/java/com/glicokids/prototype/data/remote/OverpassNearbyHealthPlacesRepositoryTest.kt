package com.glicokids.prototype.data.remote

import com.glicokids.prototype.domain.model.HealthPlace
import com.glicokids.prototype.domain.model.NetworkResult
import com.glicokids.prototype.domain.repository.FakeHttpClient
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Module 7 — [OverpassNearbyHealthPlacesRepository] against [FakeHttpClient], offline. Covers
 * the two field-verified behaviors from building this repository: the primary Overpass endpoint
 * answering with an HTML error page under a 200 status, and the single fallback to the mirror
 * that follows. Robolectric because a successful response is parsed through
 * [OverpassResponseParser], which needs it for `org.json`.
 */
@RunWith(RobolectricTestRunner::class)
class OverpassNearbyHealthPlacesRepositoryTest {

    private val validJson = """
        {"elements":[{"type":"node","id":1,"lat":0.001,"lon":0.001,
          "tags":{"amenity":"hospital","name":"Hospital Central"}}]}
    """.trimIndent()

    private fun repository(httpClient: FakeHttpClient) = OverpassNearbyHealthPlacesRepository(httpClient)

    @Test
    fun `returns the parsed places when the primary endpoint answers`() = runTest {
        val httpClient = FakeHttpClient().apply {
            whenUrlContains("overpass-api.de", NetworkResult.Success(validJson))
        }

        val result = repository(httpClient).findNearby(lat = 0.0, lng = 0.0, radiusMeters = 2000)

        assertThat(result).isInstanceOf(NetworkResult.Success::class.java)
        val places = (result as NetworkResult.Success<List<HealthPlace>>).data
        assertThat(places).hasSize(1)
        assertThat(places[0].name).isEqualTo("Hospital Central")
    }

    @Test
    fun `reports no connection without trying the mirror`() = runTest {
        val httpClient = FakeHttpClient().apply { default = NetworkResult.Failure.NoConnection }

        val result = repository(httpClient).findNearby(lat = 0.0, lng = 0.0, radiusMeters = 2000)

        assertThat(result).isEqualTo(NetworkResult.Failure.NoConnection)
        assertThat(httpClient.requestedUrls).hasSize(1)
    }

    @Test
    fun `falls back to the mirror when the primary returns a non-2xx status`() = runTest {
        val httpClient = FakeHttpClient().apply {
            whenUrlContains("overpass-api.de", NetworkResult.Failure.ServiceUnavailable(503))
            whenUrlContains("overpass.kumi.systems", NetworkResult.Success(validJson))
        }

        val result = repository(httpClient).findNearby(lat = 0.0, lng = 0.0, radiusMeters = 2000)

        assertThat(result).isInstanceOf(NetworkResult.Success::class.java)
        assertThat(httpClient.requestedUrls).hasSize(2)
    }

    @Test
    fun `treats a 200 response carrying an HTML error page as service unavailable and falls back to the mirror`() =
        runTest {
            val html = "<html><body>The server is probably too busy to handle your request.</body></html>"
            val httpClient = FakeHttpClient().apply {
                whenUrlContains("overpass-api.de", NetworkResult.Success(html))
                whenUrlContains("overpass.kumi.systems", NetworkResult.Success(validJson))
            }

            val result = repository(httpClient).findNearby(lat = 0.0, lng = 0.0, radiusMeters = 2000)

            assertThat(result).isInstanceOf(NetworkResult.Success::class.java)
            assertThat(httpClient.requestedUrls).hasSize(2)
        }

    @Test
    fun `reports service unavailable with no status when both the primary and the mirror answer HTML`() = runTest {
        val html = "<html>busy</html>"
        val httpClient = FakeHttpClient().apply {
            whenUrlContains("overpass-api.de", NetworkResult.Success(html))
            whenUrlContains("overpass.kumi.systems", NetworkResult.Success(html))
        }

        val result = repository(httpClient).findNearby(lat = 0.0, lng = 0.0, radiusMeters = 2000)

        assertThat(result).isEqualTo(NetworkResult.Failure.ServiceUnavailable(null))
    }

    @Test
    fun `reports an unreadable response instead of throwing on malformed JSON`() = runTest {
        val httpClient = FakeHttpClient().apply {
            whenUrlContains("overpass-api.de", NetworkResult.Success("not json"))
        }

        val result = repository(httpClient).findNearby(lat = 0.0, lng = 0.0, radiusMeters = 2000)

        assertThat(result).isInstanceOf(NetworkResult.Failure.UnreadableResponse::class.java)
    }

    @Test
    fun `returns an empty list when nothing named matches`() = runTest {
        val httpClient = FakeHttpClient().apply {
            whenUrlContains("overpass-api.de", NetworkResult.Success("""{"elements":[]}"""))
        }

        val result = repository(httpClient).findNearby(lat = 0.0, lng = 0.0, radiusMeters = 2000)

        assertThat(result).isEqualTo(NetworkResult.Success(emptyList<HealthPlace>()))
    }
}
