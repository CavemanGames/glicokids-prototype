package com.glicokids.prototype.domain.repository

import com.glicokids.prototype.domain.model.GeoPoint
import com.glicokids.prototype.domain.model.LocationTech
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Module 7 — executable spec for [GeocodingRepository], run against [FakeGeocodingRepository].
 * Both directions here are a direct pass-through on the fake — there is no behavioral rule to
 * exercise the way [LocationProviderContractTest] exercises the permission gate and the
 * timeout race, only the contract's shape (a resolved value, or null, never an exception). The
 * real failure surface — no geocoding service on the device, a live network call, Play
 * Services absent — only exists once `AndroidGeocoder` is written, and needs a real
 * (Robolectric/instrumented) test beside it, not a fake.
 */
class GeocodingRepositoryContractTest {

    @Test
    fun `reverse returns the resolved address`() = runTest {
        val repository = FakeGeocodingRepository().apply {
            addressForReverse = "Av. Paulista, Sao Paulo"
        }

        val result = repository.reverse(lat = -23.55, lng = -46.63)

        assertThat(result).isEqualTo("Av. Paulista, Sao Paulo")
    }

    @Test
    fun `reverse returns null without throwing when nothing resolves`() = runTest {
        val repository = FakeGeocodingRepository()

        val result = repository.reverse(lat = 0.0, lng = 0.0)

        assertThat(result).isNull()
    }

    @Test
    fun `forward returns the resolved point`() = runTest {
        val point = GeoPoint(
            lat = -23.55,
            lng = -46.63,
            accuracyMeters = 50f,
            tech = LocationTech.UNKNOWN,
            timestampMillis = 1_700_000_000_000L
        )
        val repository = FakeGeocodingRepository().apply { pointForForward = point }

        val result = repository.forward("Av. Paulista, Sao Paulo")

        assertThat(result).isEqualTo(point)
    }

    @Test
    fun `forward returns null without throwing for a query with no match`() = runTest {
        val repository = FakeGeocodingRepository()

        val result = repository.forward("this address does not exist anywhere")

        assertThat(result).isNull()
    }
}
