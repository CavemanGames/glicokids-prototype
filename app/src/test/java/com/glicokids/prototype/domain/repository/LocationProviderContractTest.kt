package com.glicokids.prototype.domain.repository

import com.glicokids.prototype.domain.model.GeoPoint
import com.glicokids.prototype.domain.model.LocationTech
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Module 7 — executable spec for [LocationProvider], run against [FakeLocationProvider]. The
 * permission-gate and timeout-race tests exercise genuine behavior the fake implements on its
 * own (see its kdoc), not a value handed to it directly, so they are more than a
 * trivially-circular "returns what it was told to return" check. Everything specific to the
 * Android/Play Services stack — a real permission revocation, a real `LocationCallback`
 * failure — still needs the future `AndroidLocationProvider` and a Robolectric/instrumented
 * test beside it, the same split `AndroidSmsGatewayTest` keeps from `GlucoseAlertViewModelTest`.
 */
class LocationProviderContractTest {

    private val fixedPoint = GeoPoint(
        lat = -23.55,
        lng = -46.63,
        accuracyMeters = 12f,
        tech = LocationTech.GPS,
        timestampMillis = 1_700_000_000_000L
    )

    @Test
    fun `getCurrentLocation returns the fix when one is available inside the timeout`() = runTest {
        val provider = FakeLocationProvider().apply { fix = fixedPoint }

        val result = provider.getCurrentLocation(timeoutMillis = 8_000)

        assertThat(result).isEqualTo(fixedPoint)
    }

    @Test
    fun `getCurrentLocation returns null without throwing when the fix arrives after the timeout`() = runTest {
        val provider = FakeLocationProvider().apply {
            fix = fixedPoint
            fixDelayMillis = 10_000
        }

        val result = provider.getCurrentLocation(timeoutMillis = 1_000)

        assertThat(result).isNull()
    }

    @Test
    fun `getCurrentLocation returns null without throwing when permission is missing, even with a fix ready`() = runTest {
        val provider = FakeLocationProvider().apply {
            fix = fixedPoint
            permissionGranted = false
        }

        val result = provider.getCurrentLocation()

        assertThat(result).isNull()
        assertThat(provider.hasPermission()).isFalse()
    }

    @Test
    fun `availableTechnologies reflects GPS and NETWORK when both providers are enabled`() {
        val provider = FakeLocationProvider().apply {
            technologies = listOf(LocationTech.GPS, LocationTech.NETWORK)
        }

        assertThat(provider.availableTechnologies())
            .containsExactly(LocationTech.GPS, LocationTech.NETWORK)
    }

    @Test
    fun `availableTechnologies is empty, never null, when no provider is enabled`() {
        val provider = FakeLocationProvider()

        assertThat(provider.availableTechnologies()).isEmpty()
    }
}
