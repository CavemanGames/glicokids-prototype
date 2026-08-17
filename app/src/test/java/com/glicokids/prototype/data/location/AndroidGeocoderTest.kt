package com.glicokids.prototype.data.location

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.glicokids.prototype.domain.model.NetworkResult
import com.glicokids.prototype.domain.repository.FakeHttpClient
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowGeocoder

/**
 * Module 7 — proves [AndroidGeocoder]'s null-safety contract, and the Nominatim fallback
 * `reverse` chains through when the platform geocoder comes back empty. `ShadowGeocoder` resets
 * `isPresent` to true for every test on its own, so no explicit teardown is needed here. Every
 * test below leaves [FakeHttpClient] at its default (`NoConnection`) unless a test is
 * specifically about the fallback path, so a platform miss keeps resolving to `null` exactly as
 * it did before the fallback existed.
 *
 * `ShadowGeocoder` only shadows the [android.location.Geocoder.GeocodeListener] overloads
 * (SDK 33+) — the branch this suite's default `targetSdk` of 34 exercises, matching
 * [AndroidGeocoder]'s own SDK split. The pre-Tiramisu synchronous overloads have no shadow and
 * are not exercised here; that branch needs a real device or an SDK-pinned instrumented test,
 * the same kind of gap `FakeLocationProvider`'s own kdoc documents for the Play Services stack.
 *
 * A true platform happy path (an address actually resolving) is also left out on purpose: the
 * shadow keeps its configured address list on the internal `Geocoder` instance that
 * [AndroidGeocoder] builds itself and never exposes, so there is no way to wire test data into
 * it without breaking that encapsulation. What is exercised instead — "the callback fires with
 * an empty result and this returns null cleanly" — already proves the coroutine bridging works;
 * only the "an address was found" data path is left unverified there, and the Nominatim fallback
 * tests below cover the same shape of assertion through the path this class actually controls.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidGeocoderTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val httpClient = FakeHttpClient()
    private val geocoder = AndroidGeocoder(context, httpClient)

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
    fun `reverse returns null without throwing when nothing resolves for the coordinate and the fallback is offline`() =
        runTest {
            val result = geocoder.reverse(lat = -23.55, lng = -46.63)

            assertThat(result).isNull()
        }

    @Test
    fun `forward returns null without throwing when nothing resolves for the query`() = runTest {
        val result = geocoder.forward("this address does not exist anywhere")

        assertThat(result).isNull()
    }

    @Test
    fun `reverse falls back to Nominatim and returns its address when the platform resolves nothing`() = runTest {
        val nominatimXml = """
            <reversegeocode>
              <result place_id="1">Avenida Paulista, Sao Paulo, Brasil</result>
            </reversegeocode>
        """.trimIndent()
        httpClient.whenUrlContains("nominatim.openstreetmap.org", NetworkResult.Success(nominatimXml))

        val result = geocoder.reverse(lat = -23.55, lng = -46.63)

        assertThat(result).isEqualTo("Avenida Paulista, Sao Paulo, Brasil")
    }

    @Test
    fun `reverse returns null when the platform is empty and Nominatim also cannot resolve anything`() = runTest {
        httpClient.whenUrlContains(
            "nominatim.openstreetmap.org",
            NetworkResult.Success("<reversegeocode><error>Unable to geocode</error></reversegeocode>")
        )

        val result = geocoder.reverse(lat = -23.55, lng = -46.63)

        assertThat(result).isNull()
    }

}
