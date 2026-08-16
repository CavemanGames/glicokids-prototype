package com.glicokids.prototype.data.location

import android.Manifest
import android.app.Application
import android.content.Context
import android.location.LocationManager
import androidx.test.core.app.ApplicationProvider
import com.glicokids.prototype.domain.model.LocationTech
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Module 7 — the two [FusedLocationProvider] checks that do not depend on the Play Services
 * Task/callback machinery: the permission gate, and the provider-to-[LocationTech] mapping over
 * the real (Robolectric-shadowed) `android.location.LocationManager`.
 *
 * `getCurrentLocation` and `locationUpdates` are not covered here. Both are driven entirely by
 * [FusedLocationProviderClient], a Play Services class Robolectric has no shadow for. Mocking
 * it would only prove this class calls the methods it is written to call — a check against its
 * own assumptions, not a real one — so that path needs a device/emulator run instead, the same
 * split `AndroidSmsGatewayTest` already draws around what a shadow can honestly stand in for.
 */
@RunWith(RobolectricTestRunner::class)
class FusedLocationProviderTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val locationManager =
        application.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val fusedClient = mockk<FusedLocationProviderClient>()
    private val provider = FusedLocationProvider(application, fusedClient, locationManager)

    @Test
    fun `hasPermission returns true once ACCESS_FINE_LOCATION is granted`() {
        shadowOf(application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)

        assertThat(provider.hasPermission()).isTrue()
    }

    @Test
    fun `hasPermission returns false before the user grants ACCESS_FINE_LOCATION`() {
        shadowOf(application).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)

        assertThat(provider.hasPermission()).isFalse()
    }

    @Test
    fun `availableTechnologies maps GPS and NETWORK when both providers are enabled`() {
        // Robolectric's LocationManager starts with GPS_PROVIDER and PASSIVE_PROVIDER already
        // enabled by default (mirroring a real device, where passive is effectively always on)
        // — PASSIVE is turned off here so this test isolates exactly the two providers it names.
        shadowOf(locationManager).setProviderEnabled(LocationManager.PASSIVE_PROVIDER, false)
        shadowOf(locationManager).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        shadowOf(locationManager).setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)

        assertThat(provider.availableTechnologies())
            .containsExactly(LocationTech.GPS, LocationTech.NETWORK)
    }

    @Test
    fun `availableTechnologies is empty, never null, when no provider is enabled`() {
        // Same default as above has to be turned off explicitly to reach the true empty case.
        shadowOf(locationManager).setProviderEnabled(LocationManager.GPS_PROVIDER, false)
        shadowOf(locationManager).setProviderEnabled(LocationManager.PASSIVE_PROVIDER, false)

        assertThat(provider.availableTechnologies()).isEmpty()
    }
}
