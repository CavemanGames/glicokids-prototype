package com.glicokids.prototype.data.location

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowGeocoder

/**
 * Module 7 — proves [AndroidGeocoder]'s null-safety contract. `ShadowGeocoder` resets
 * `isPresent` to true for every test on its own, so no explicit teardown is needed here.
 *
 * `ShadowGeocoder` only shadows the [android.location.Geocoder.GeocodeListener] overloads
 * (SDK 33+) — the branch this suite's default `targetSdk` of 34 exercises, matching
 * [AndroidGeocoder]'s own SDK split. The pre-Tiramisu synchronous overloads have no shadow and
 * are not exercised here; that branch needs a real device or an SDK-pinned instrumented test,
 * the same kind of gap `FakeLocationProvider`'s own kdoc documents for the Play Services stack.
 *
 * A true happy path (an address actually resolving) is also left out on purpose: the shadow
 * keeps its configured address list on the internal `Geocoder` instance that [AndroidGeocoder]
 * builds itself and never exposes, so there is no way to wire test data into it without
 * breaking that encapsulation. What is exercised instead — "the callback fires with an empty
 * result and this returns null cleanly" — already proves the coroutine bridging works; only the
 * "an address was found" data path is left unverified here.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidGeocoderTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val geocoder = AndroidGeocoder(context)

    @Test
    fun `reverse returns null without throwing when no geocoding backend is present on the device`() = runTest {
        ShadowGeocoder.setIsPresent(false)

        val result = geocoder.reverse(lat = -23.55, lng = -46.63)

        assertThat(result).isNull()
    }

    @Test
    fun `forward returns null without throwing when no geocoding backend is present on the device`() = runTest {
        ShadowGeocoder.setIsPresent(false)

        val result = geocoder.forward("Av. Paulista, Sao Paulo")

        assertThat(result).isNull()
    }

    @Test
    fun `reverse returns null without throwing when nothing resolves for the coordinate`() = runTest {
        val result = geocoder.reverse(lat = -23.55, lng = -46.63)

        assertThat(result).isNull()
    }

    @Test
    fun `forward returns null without throwing when nothing resolves for the query`() = runTest {
        val result = geocoder.forward("this address does not exist anywhere")

        assertThat(result).isNull()
    }
}
