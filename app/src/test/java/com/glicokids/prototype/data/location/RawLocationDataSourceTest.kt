package com.glicokids.prototype.data.location

import android.Manifest
import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationManager
import androidx.test.core.app.ApplicationProvider
import com.glicokids.prototype.domain.model.LocationTech
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Module 7 — proves [RawLocationDataSource] samples GPS and network side by side, tags each
 * fix with its real technology, skips a provider with nothing to report, and never throws when
 * permission is missing.
 */
@RunWith(RobolectricTestRunner::class)
class RawLocationDataSourceTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val locationManager =
        application.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val dataSource = RawLocationDataSource(application, locationManager)

    private fun fixFor(provider: String, lat: Double, lng: Double, accuracy: Float) =
        Location(provider).apply {
            latitude = lat
            longitude = lng
            this.accuracy = accuracy
        }

    @Test
    fun `sampleEachProvider returns an empty list without throwing when permission is missing`() = runTest {
        shadowOf(application).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        shadowOf(locationManager).simulateLocation(
            fixFor(LocationManager.GPS_PROVIDER, lat = -23.55, lng = -46.63, accuracy = 5f)
        )

        assertThat(dataSource.sampleEachProvider()).isEmpty()
    }

    @Test
    fun `sampleEachProvider returns one GeoPoint per provider with a last known fix`() = runTest {
        shadowOf(application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        shadowOf(locationManager).simulateLocation(
            fixFor(LocationManager.GPS_PROVIDER, lat = -23.55, lng = -46.63, accuracy = 5f)
        )
        shadowOf(locationManager).simulateLocation(
            fixFor(LocationManager.NETWORK_PROVIDER, lat = -23.56, lng = -46.64, accuracy = 800f)
        )

        val points = dataSource.sampleEachProvider()

        assertThat(points.map { it.tech }).containsExactly(LocationTech.GPS, LocationTech.NETWORK)
        assertThat(points.first { it.tech == LocationTech.GPS }.accuracyMeters).isEqualTo(5f)
        assertThat(points.first { it.tech == LocationTech.NETWORK }.accuracyMeters).isEqualTo(800f)
    }

    @Test
    fun `sampleEachProvider skips a provider with no last known fix`() = runTest {
        shadowOf(application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        shadowOf(locationManager).simulateLocation(
            fixFor(LocationManager.GPS_PROVIDER, lat = -23.55, lng = -46.63, accuracy = 5f)
        )

        val points = dataSource.sampleEachProvider()

        assertThat(points).hasSize(1)
        assertThat(points.single().tech).isEqualTo(LocationTech.GPS)
    }
}
