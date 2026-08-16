package com.glicokids.prototype.domain.usecase

import com.glicokids.prototype.domain.model.AlertLocationSnapshot
import com.glicokids.prototype.domain.model.GeoPoint
import com.glicokids.prototype.domain.model.LocationTech
import com.glicokids.prototype.domain.repository.GeocodingRepository
import com.glicokids.prototype.domain.repository.LocationProvider
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Module 7 — RED spec for [ResolveAlertLocationUseCase]. Uses plain mockk doubles on the
 * [LocationProvider]/[GeocodingRepository] interfaces rather than the shared
 * `FakeLocationProvider`/`FakeGeocodingRepository` (from `domain/repository`), specifically so
 * `coVerify(exactly = 0) { ... }` can prove "never touched" — the Fakes are a great fit for their
 * own contract tests, but a plain mock is what lets this class's own budget be driven with
 * `runTest`'s virtual time (`kotlinx-coroutines-test`) without a real 6-second wait, decoupled
 * from GlucoseAlertViewModelTest's real-dispatcher harness.
 *
 * The implementation under test is a stub that always returns `null` — see its kdoc. Every
 * assertion below that expects a populated [AlertLocationSnapshot], or that a dependency was
 * actually consulted, fails until GREEN wires the real behavior in.
 */
class ResolveAlertLocationUseCaseTest {

    private val fixedPoint = GeoPoint(
        lat = -23.55,
        lng = -46.63,
        accuracyMeters = 12f,
        tech = LocationTech.GPS,
        timestampMillis = 1_700_000_000_000L
    )

    private val locationProvider = mockk<LocationProvider>()
    private val geocodingRepository = mockk<GeocodingRepository>()
    private val useCase = ResolveAlertLocationUseCase(locationProvider, geocodingRepository)

    @Test
    fun `consent off never touches either dependency`() = runTest {
        val result = useCase.execute(consentGiven = false)

        assertThat(result).isNull()
        coVerify(exactly = 0) { locationProvider.getCurrentLocation(any()) }
        coVerify(exactly = 0) { geocodingRepository.reverse(any(), any()) }
    }

    @Test
    fun `consent on resolves a snapshot pairing the fix with its reverse geocoded label`() = runTest {
        coEvery { locationProvider.getCurrentLocation(any()) } returns fixedPoint
        coEvery { geocodingRepository.reverse(fixedPoint.lat, fixedPoint.lng) } returns "Rua Tal, 123"

        val result = useCase.execute(consentGiven = true)

        assertThat(result).isEqualTo(AlertLocationSnapshot(point = fixedPoint, label = "Rua Tal, 123"))
    }

    @Test
    fun `a fix with no resolvable address still returns the point, with a null label`() = runTest {
        coEvery { locationProvider.getCurrentLocation(any()) } returns fixedPoint
        coEvery { geocodingRepository.reverse(fixedPoint.lat, fixedPoint.lng) } returns null

        val result = useCase.execute(consentGiven = true)

        assertThat(result).isEqualTo(AlertLocationSnapshot(point = fixedPoint, label = null))
    }

    @Test
    fun `no fix at all returns null and never asks for a reverse address`() = runTest {
        coEvery { locationProvider.getCurrentLocation(any()) } returns null

        val result = useCase.execute(consentGiven = true)

        assertThat(result).isNull()
        coVerify(exactly = 0) { geocodingRepository.reverse(any(), any()) }
    }

    @Test
    fun `consent on still asks the location provider for a fix`() = runTest {
        coEvery { locationProvider.getCurrentLocation(any()) } returns fixedPoint
        coEvery { geocodingRepository.reverse(any(), any()) } returns null

        useCase.execute(consentGiven = true)

        coVerify { locationProvider.getCurrentLocation(any()) }
    }

    // Requirement 3 of the spec: the resolution window belongs to this use case, independent of
    // whatever timeout it hands the provider. This fix arrives at BUDGET_MILLIS + 500 — well
    // inside LocationProvider.getCurrentLocation's own 8_000 default, which alone would happily
    // deliver it — but this use case's own budget must win first.
    @Test
    fun `a fix that only arrives after this use case's own budget counts as no fix, even within the provider's own default timeout`() = runTest {
        coEvery { locationProvider.getCurrentLocation(any()) } coAnswers {
            delay(ResolveAlertLocationUseCase.BUDGET_MILLIS + 500)
            fixedPoint
        }

        val result = useCase.execute(consentGiven = true)

        assertThat(result).isNull()
    }

    // Not honestly testable here: LocationProvider/GeocodingRepository's own contract promises
    // never to throw (see their kdoc), so a mock that DOES throw would be exercising a case the
    // real implementations are documented never to produce. That failure mode belongs with
    // FusedLocationProvider/AndroidGeocoder's own tests, not this use case's.
}
