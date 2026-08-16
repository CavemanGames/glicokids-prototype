package com.glicokids.prototype.domain.repository

import com.glicokids.prototype.domain.model.HealthPlace
import com.glicokids.prototype.domain.model.HealthPlaceType
import com.glicokids.prototype.domain.model.NetworkResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Module 7 — executable spec for [NearbyHealthPlacesRepository], run against
 * [FakeNearbyHealthPlacesRepository]. Same intent as [GeocodingRepositoryContractTest]: prove
 * the contract's shape, not a real network call. The one rule this contract adds over
 * [GeocodingRepository]'s is the point of [NetworkResult] existing at all — every failure case
 * has to come back as a distinct value, never an exception.
 */
class NearbyHealthPlacesRepositoryContractTest {

    private val place = HealthPlace(
        name = "Hospital Central",
        type = HealthPlaceType.HOSPITAL,
        lat = -23.55,
        lng = -46.63,
        distanceMeters = 120.0
    )

    @Test
    fun `findNearby returns the resolved list on success`() = runTest {
        val repository = FakeNearbyHealthPlacesRepository().apply {
            result = NetworkResult.Success(listOf(place))
        }

        val result = repository.findNearby(lat = -23.55, lng = -46.63, radiusMeters = 2000)

        assertThat(result).isEqualTo(NetworkResult.Success(listOf(place)))
    }

    @Test
    fun `findNearby reports no connection without throwing`() = runTest {
        val repository = FakeNearbyHealthPlacesRepository().apply {
            result = NetworkResult.Failure.NoConnection
        }

        val result = repository.findNearby(lat = 0.0, lng = 0.0, radiusMeters = 2000)

        assertThat(result).isEqualTo(NetworkResult.Failure.NoConnection)
    }

    @Test
    fun `findNearby reports service unavailable with the http status when known`() = runTest {
        val repository = FakeNearbyHealthPlacesRepository().apply {
            result = NetworkResult.Failure.ServiceUnavailable(httpStatusCode = 503)
        }

        val result = repository.findNearby(lat = 0.0, lng = 0.0, radiusMeters = 2000)

        assertThat(result).isEqualTo(NetworkResult.Failure.ServiceUnavailable(503))
    }

    @Test
    fun `findNearby reports an unreadable response instead of throwing`() = runTest {
        val cause = IllegalStateException("boom")
        val repository = FakeNearbyHealthPlacesRepository().apply {
            result = NetworkResult.Failure.UnreadableResponse(cause)
        }

        val result = repository.findNearby(lat = 0.0, lng = 0.0, radiusMeters = 2000)

        assertThat(result).isEqualTo(NetworkResult.Failure.UnreadableResponse(cause))
    }
}
