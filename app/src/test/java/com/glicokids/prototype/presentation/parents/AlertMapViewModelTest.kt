package com.glicokids.prototype.presentation.parents

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelStore
import com.glicokids.prototype.data.local.AppPreferences
import com.glicokids.prototype.data.location.RawLocationDataSource
import com.glicokids.prototype.domain.model.GeoPoint
import com.glicokids.prototype.domain.model.HealthPlace
import com.glicokids.prototype.domain.model.HealthPlaceType
import com.glicokids.prototype.domain.model.LocationTech
import com.glicokids.prototype.domain.model.NetworkResult
import com.glicokids.prototype.domain.repository.FakeGeocodingRepository
import com.glicokids.prototype.domain.repository.FakeLocationProvider
import com.glicokids.prototype.domain.repository.FakeNearbyHealthPlacesRepository
import com.glicokids.prototype.domain.repository.GeocodingRepository
import com.glicokids.prototype.domain.repository.LocationProvider
import com.glicokids.prototype.domain.repository.NearbyHealthPlacesRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Module 7 — b-map (Área dos Pais). `GoogleMap` itself has no Robolectric shadow, so every
 * behavior the screen needs is specified here against [AlertMapViewModel] alone; the Activity
 * and layout are out of scope for this suite on purpose.
 */
class AlertMapViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val prefs = mockk<AppPreferences>(relaxed = true)
    private val rawLocationDataSource = mockk<RawLocationDataSource>(relaxed = true)
    private lateinit var fakeLocationProvider: FakeLocationProvider
    private lateinit var fakeGeocodingRepository: FakeGeocodingRepository
    private lateinit var fakeNearbyHealthPlacesRepository: FakeNearbyHealthPlacesRepository

    private val validKey = "AIzaSyTestKeyLooksReal12345"

    /**
     * Same bounded-wait pattern as `GlucoseAlertViewModelTest`/`SupportNetworkViewModelTest` —
     * needed here too because [AlertMapViewModel] hops through the real `Dispatchers.IO` for
     * every geocoding/location/diagnostics call, so the state that reflects a call's outcome
     * can land on a genuine background thread after the triggering method has already returned.
     *
     * [until] defaults to accepting whatever value is already stored (or the first one that
     * arrives), which is only safe when nothing async is in flight. Every call below whose
     * outcome depends on a `withContext(Dispatchers.IO)` hop passes a predicate that only the
     * *settled* state satisfies — `observeForever`'s immediate replay of the current value would
     * otherwise let a still-in-flight state slip through as if it were the final one, exactly
     * the trap called out in the handoff for this task.
     */
    private fun <T> LiveData<T>.getOrAwaitValue(timeoutSeconds: Long = 2, until: (T) -> Boolean = { true }): T {
        var data: T? = null
        val latch = CountDownLatch(1)
        val observer = object : Observer<T> {
            override fun onChanged(value: T) {
                if (!until(value)) return
                data = value
                latch.countDown()
                this@getOrAwaitValue.removeObserver(this)
            }
        }
        observeForever(observer)
        if (!latch.await(timeoutSeconds, TimeUnit.SECONDS)) {
            throw TimeoutException("LiveData value never set within ${timeoutSeconds}s.")
        }
        @Suppress("UNCHECKED_CAST")
        return data as T
    }

    /**
     * `init` itself kicks off [AlertMapViewModel]'s technology-diagnostics load, which hops
     * through a real `Dispatchers.IO` thread exactly like `onMapTapped`/`search` do. Every test
     * constructs a ViewModel through here, so waiting out that hop in one place — instead of in
     * each individual test — is what keeps it from ever still being in flight when a test method
     * returns: an in-flight background hand-off left dangling past `tearDown`'s `resetMain()` is
     * exactly what raced a later test's `setMain()` into "Dispatchers.Main is used concurrently
     * with setting it" the first time this suite was written.
     */
    private fun createViewModel(
        mapsApiKey: String = validKey,
        geocodingRepository: GeocodingRepository = fakeGeocodingRepository,
        locationProvider: LocationProvider = fakeLocationProvider,
        nearbyHealthPlacesRepository: NearbyHealthPlacesRepository = fakeNearbyHealthPlacesRepository
    ): AlertMapViewModel {
        val viewModel = AlertMapViewModel(
            prefs = prefs,
            locationProvider = locationProvider,
            geocodingRepository = geocodingRepository,
            rawLocationDataSource = rawLocationDataSource,
            nearbyHealthPlacesRepository = nearbyHealthPlacesRepository,
            mapsApiKey = mapsApiKey
        )
        coVerify(timeout = 2_000) { rawLocationDataSource.sampleEachProvider() }
        return viewModel
    }

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fakeLocationProvider = FakeLocationProvider()
        fakeGeocodingRepository = FakeGeocodingRepository()
        fakeNearbyHealthPlacesRepository = FakeNearbyHealthPlacesRepository()
        every { prefs.lastAlertLocationAt } returns 0L
        every { prefs.lastAlertLocationLat } returns 0.0
        every { prefs.lastAlertLocationLng } returns 0.0
        every { prefs.lastAlertLocationLabel } returns null
        coEvery { rawLocationDataSource.sampleEachProvider() } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Requirement 1: last alert location on open ---

    @Test
    fun `starts with an empty last-alert location when no alert with a location has ever been sent`() {
        val viewModel = createViewModel()

        val state = viewModel.uiState.getOrAwaitValue()

        assertThat(state.lastAlertLocation).isNull()
    }

    @Test
    fun `shows the saved last-alert location on open`() {
        every { prefs.lastAlertLocationAt } returns 1_700_000_000_000L
        every { prefs.lastAlertLocationLat } returns -23.55
        every { prefs.lastAlertLocationLng } returns -46.63
        every { prefs.lastAlertLocationLabel } returns "Rua Tal, 123"
        val viewModel = createViewModel()

        val state = viewModel.uiState.getOrAwaitValue()

        assertThat(state.lastAlertLocation).isEqualTo(MapPoint(-23.55, -46.63, "Rua Tal, 123"))
    }

    @Test
    fun `falls back to a formatted coordinate when the saved last-alert location has no address label`() {
        every { prefs.lastAlertLocationAt } returns 1_700_000_000_000L
        every { prefs.lastAlertLocationLat } returns 10.0
        every { prefs.lastAlertLocationLng } returns 20.0
        every { prefs.lastAlertLocationLabel } returns null
        val viewModel = createViewModel()

        val state = viewModel.uiState.getOrAwaitValue()

        assertThat(state.lastAlertLocation?.label)
            .isEqualTo(String.format(Locale.US, "%.5f, %.5f", 10.0, 20.0))
    }

    // --- Requirement 2: tapping the map ---

    @Test
    fun `tapping the map resolves the address and clears a previous search error`() {
        fakeGeocodingRepository.pointForForward = null
        val viewModel = createViewModel()
        viewModel.search("endereço que não existe")
        assertThat(
            viewModel.uiState.getOrAwaitValue(until = { it.searchErrorMessage != null }).searchErrorMessage
        ).isNotNull()

        fakeGeocodingRepository.addressForReverse = "Rua Alfa, 10"
        viewModel.onMapTapped(-23.5, -46.6)
        val state = viewModel.uiState.getOrAwaitValue(until = { it.selectedPoint != null })

        assertThat(state.selectedPoint).isEqualTo(MapPoint(-23.5, -46.6, "Rua Alfa, 10"))
        assertThat(state.isResolvingAddress).isFalse()
        assertThat(state.searchErrorMessage).isNull()
    }

    @Test
    fun `a tapped point that does not reverse-geocode still gets a readable coordinate label instead of a blank one`() {
        fakeGeocodingRepository.addressForReverse = null
        val viewModel = createViewModel()

        viewModel.onMapTapped(1.23456, 7.89012)
        val state = viewModel.uiState.getOrAwaitValue(until = { it.selectedPoint != null })

        assertThat(state.selectedPoint?.label)
            .isEqualTo(String.format(Locale.US, "%.5f, %.5f", 1.23456, 7.89012))
        assertThat(state.selectedPoint?.label).isNotEmpty()
    }

    // Armadilha do módulo (see handoff to test-architect): an Unconfined dispatcher plus a fake
    // that never actually suspends would make this pass even if onMapTapped forgot to await the
    // reverse-geocode call at all. A real `coAnswers { delay(...) }` on a mockk forces the same
    // real suspension a device would see, so this only stays green if the ViewModel genuinely
    // publishes the pending state before the geocode call resumes — same technique
    // GlucoseAlertViewModelTest already uses for `isLocating`.
    @Test
    fun `isResolvingAddress is true immediately while a tap's reverse geocode is still pending`() {
        val hangingGeocoder = mockk<GeocodingRepository>()
        coEvery { hangingGeocoder.reverse(any(), any()) } coAnswers { delay(Long.MAX_VALUE); "never resolves" }
        val viewModel = createViewModel(geocodingRepository = hangingGeocoder)

        viewModel.onMapTapped(-23.5, -46.6)

        val state = viewModel.uiState.value
        assertThat(state).isNotNull()
        assertThat(state!!.isResolvingAddress).isTrue()
    }

    // --- Requirement 3: search ---

    @Test
    fun `search resolves an address to a point and labels it with the typed query`() {
        fakeGeocodingRepository.pointForForward = GeoPoint(
            lat = 10.0, lng = 20.0, accuracyMeters = 5f, tech = LocationTech.GPS, timestampMillis = 0L
        )
        val viewModel = createViewModel()

        viewModel.search("Avenida Y, 500")
        val state = viewModel.uiState.getOrAwaitValue(until = { it.selectedPoint != null })

        assertThat(state.selectedPoint).isEqualTo(MapPoint(10.0, 20.0, "Avenida Y, 500"))
        assertThat(state.searchErrorMessage).isNull()
    }

    @Test
    fun `a search that finds nothing reports an error and never moves to the zero coordinate`() {
        fakeGeocodingRepository.pointForForward = null
        val viewModel = createViewModel()

        viewModel.search("endereço que não existe")
        val state = viewModel.uiState.getOrAwaitValue(until = { it.searchErrorMessage != null })

        assertThat(state.selectedPoint).isNull()
        assertThat(state.searchErrorMessage).isNotNull()
    }

    @Test
    fun `a blank search query is ignored and never reaches geocoding`() {
        val viewModel = createViewModel()
        val before = viewModel.uiState.getOrAwaitValue()

        viewModel.search("   ")

        assertThat(viewModel.uiState.value).isSameInstanceAs(before)
    }

    // --- Requirement 4: follow location ---

    @Test
    fun `follow location explains and does not start when permission is missing`() {
        fakeLocationProvider.permissionGranted = false
        val viewModel = createViewModel()

        viewModel.setFollowingLocation(true)
        val state = viewModel.uiState.getOrAwaitValue()

        assertThat(state.isFollowingLocation).isFalse()
        assertThat(state.followError).isNotNull()
    }

    @Test
    fun `follow location updates the current position as fixes arrive`() {
        fakeLocationProvider.permissionGranted = true
        val point = GeoPoint(
            lat = 1.0, lng = 2.0, accuracyMeters = 10f, tech = LocationTech.GPS, timestampMillis = 0L
        )
        fakeLocationProvider.updates = flowOf(point)
        val viewModel = createViewModel()

        viewModel.setFollowingLocation(true)
        val state = viewModel.uiState.getOrAwaitValue()

        assertThat(state.isFollowingLocation).isTrue()
        assertThat(state.followedLocation).isEqualTo(point)
    }

    @Test
    fun `turning follow off stops the subscription so a later fix is never applied`() {
        fakeLocationProvider.permissionGranted = true
        val updates = MutableSharedFlow<GeoPoint>(extraBufferCapacity = 1)
        fakeLocationProvider.updates = updates
        val viewModel = createViewModel()
        val firstFix = GeoPoint(1.0, 2.0, 10f, LocationTech.GPS, 0L)
        val secondFix = GeoPoint(3.0, 4.0, 10f, LocationTech.GPS, 0L)

        viewModel.setFollowingLocation(true)
        updates.tryEmit(firstFix)
        assertThat(viewModel.uiState.getOrAwaitValue().followedLocation).isEqualTo(firstFix)

        viewModel.setFollowingLocation(false)
        updates.tryEmit(secondFix)

        val state = viewModel.uiState.getOrAwaitValue()
        assertThat(state.isFollowingLocation).isFalse()
        assertThat(state.followedLocation).isEqualTo(firstFix)
    }

    // The design question flagged to the orchestrator: this is the mechanism that makes the
    // "stop following" contract impossible for the Activity to forget. The subscription is
    // launched through viewModelScope, so clearing the ViewModel — exactly what the Android
    // framework does when the screen is destroyed — cancels it with no explicit stop() call
    // required from the caller. ViewModelStore.clear() is the same entry point the framework
    // itself uses, so this exercises the real mechanism rather than a stand-in for it.
    @Test
    fun `clearing the ViewModel stops the follow subscription without the caller doing anything`() {
        fakeLocationProvider.permissionGranted = true
        val updates = MutableSharedFlow<GeoPoint>(extraBufferCapacity = 1)
        fakeLocationProvider.updates = updates
        val viewModel = createViewModel()
        val firstFix = GeoPoint(1.0, 2.0, 10f, LocationTech.GPS, 0L)
        val secondFix = GeoPoint(3.0, 4.0, 10f, LocationTech.GPS, 0L)

        viewModel.setFollowingLocation(true)
        updates.tryEmit(firstFix)
        assertThat(viewModel.uiState.getOrAwaitValue().followedLocation).isEqualTo(firstFix)

        val store = ViewModelStore()
        store.put("alertMap", viewModel)
        store.clear()
        updates.tryEmit(secondFix)

        assertThat(viewModel.uiState.value?.followedLocation).isEqualTo(firstFix)
    }

    // --- Requirement 5: technology diagnostics ---

    @Test
    fun `loads one technology reading per provider that has a fix, on open`() {
        coEvery { rawLocationDataSource.sampleEachProvider() } returns listOf(
            GeoPoint(1.0, 2.0, 5f, LocationTech.GPS, 0L),
            GeoPoint(3.0, 4.0, 30f, LocationTech.NETWORK, 0L)
        )
        val viewModel = createViewModel()

        val state = viewModel.uiState.getOrAwaitValue(until = { it.technologyReadings.isNotEmpty() })

        assertThat(state.technologyReadings).containsExactly(
            TechnologyReading(LocationTech.GPS, 5f),
            TechnologyReading(LocationTech.NETWORK, 30f)
        )
    }

    @Test
    fun `technology diagnostics is an explicit empty list when no provider has a fix`() {
        coEvery { rawLocationDataSource.sampleEachProvider() } returns emptyList()

        // createViewModel() already waits (with a bounded timeout) for sampleEachProvider() to
        // have been consulted before returning — the only thing left to pin here is that an
        // empty result is faithfully carried through rather than replaced by some placeholder.
        val viewModel = createViewModel()

        assertThat(viewModel.uiState.value?.technologyReadings).isEmpty()
    }

    // --- Requirement 6: missing Maps API key ---

    @Test
    fun `hasMapsApiKey is false when the build injected the missing-key sentinel`() {
        val viewModel = createViewModel(mapsApiKey = AlertMapViewModel.MISSING_MAPS_API_KEY)

        val state = viewModel.uiState.getOrAwaitValue()

        assertThat(state.hasMapsApiKey).isFalse()
    }

    @Test
    fun `hasMapsApiKey is true for a real-looking key`() {
        val viewModel = createViewModel(mapsApiKey = validKey)

        val state = viewModel.uiState.getOrAwaitValue()

        assertThat(state.hasMapsApiKey).isTrue()
    }

    // --- Requirement 7: nearby health places ---

    private fun givenLastAlertLocation(lat: Double = -23.55, lng: Double = -46.63) {
        every { prefs.lastAlertLocationAt } returns 1_700_000_000_000L
        every { prefs.lastAlertLocationLat } returns lat
        every { prefs.lastAlertLocationLng } returns lng
        every { prefs.lastAlertLocationLabel } returns "Rua Tal, 123"
    }

    private fun healthPlace(name: String, distanceMeters: Double) = HealthPlace(
        name = name,
        type = HealthPlaceType.HOSPITAL,
        lat = -23.5,
        lng = -46.6,
        distanceMeters = distanceMeters
    )

    @Test
    fun `never searches for nearby health places on open, since the lookup is slow and unreliable`() {
        val spyRepository = mockk<NearbyHealthPlacesRepository>(relaxed = true)

        createViewModel(nearbyHealthPlacesRepository = spyRepository)

        coVerify(exactly = 0) { spyRepository.findNearby(any(), any(), any()) }
    }

    @Test
    fun `searches around the last alert location when one exists`() {
        givenLastAlertLocation(lat = -23.55, lng = -46.63)
        val spyRepository = mockk<NearbyHealthPlacesRepository>()
        coEvery { spyRepository.findNearby(any(), any(), any()) } returns NetworkResult.Success(emptyList())
        val viewModel = createViewModel(nearbyHealthPlacesRepository = spyRepository)

        viewModel.searchNearbyHealthPlaces()
        viewModel.uiState.getOrAwaitValue(until = { it.nearbyPlacesMessage != null })

        coVerify { spyRepository.findNearby(lat = -23.55, lng = -46.63, radiusMeters = any()) }
    }

    @Test
    fun `falls back to the current position when no alert has ever carried a location`() {
        val fix = GeoPoint(lat = 1.0, lng = 2.0, accuracyMeters = 5f, tech = LocationTech.GPS, timestampMillis = 0L)
        fakeLocationProvider.permissionGranted = true
        fakeLocationProvider.fix = fix
        val spyRepository = mockk<NearbyHealthPlacesRepository>()
        coEvery { spyRepository.findNearby(any(), any(), any()) } returns NetworkResult.Success(emptyList())
        val viewModel = createViewModel(nearbyHealthPlacesRepository = spyRepository)

        viewModel.searchNearbyHealthPlaces()
        viewModel.uiState.getOrAwaitValue(until = { it.nearbyPlacesMessage != null })

        coVerify { spyRepository.findNearby(lat = 1.0, lng = 2.0, radiusMeters = any()) }
    }

    @Test
    fun `explains there is nowhere to search from and never calls the repository when there is no alert and no current position`() {
        fakeLocationProvider.permissionGranted = true
        fakeLocationProvider.fix = null
        val spyRepository = mockk<NearbyHealthPlacesRepository>(relaxed = true)
        val viewModel = createViewModel(nearbyHealthPlacesRepository = spyRepository)

        viewModel.searchNearbyHealthPlaces()
        val state = viewModel.uiState.getOrAwaitValue(until = { !it.isSearchingNearbyPlaces })

        assertThat(state.nearbyPlacesMessage).isNotNull()
        assertThat(state.nearbyHealthPlaces).isEmpty()
        coVerify(exactly = 0) { spyRepository.findNearby(any(), any(), any()) }
    }

    // Same trap as `isResolvingAddress`: a fake that resolves instantly under an Unconfined
    // dispatcher would leave this green even if the ViewModel forgot to publish the pending
    // state before the lookup resumes. A genuinely suspending mock forces the same ordering a
    // device (and the 5-15 second real Overpass call) would show.
    @Test
    fun `isSearchingNearbyPlaces is true immediately while the lookup is pending`() {
        givenLastAlertLocation()
        val hangingRepository = mockk<NearbyHealthPlacesRepository>()
        coEvery { hangingRepository.findNearby(any(), any(), any()) } coAnswers {
            delay(Long.MAX_VALUE)
            NetworkResult.Success(emptyList())
        }
        val viewModel = createViewModel(nearbyHealthPlacesRepository = hangingRepository)

        viewModel.searchNearbyHealthPlaces()

        val state = viewModel.uiState.value
        assertThat(state).isNotNull()
        assertThat(state!!.isSearchingNearbyPlaces).isTrue()
    }

    @Test
    fun `a successful search with results populates the list, ordered by distance, and clears the message`() {
        givenLastAlertLocation()
        val places = listOf(healthPlace("Farmácia Perto", 120.0), healthPlace("Hospital Longe", 900.0))
        fakeNearbyHealthPlacesRepository.result = NetworkResult.Success(places)
        val viewModel = createViewModel()

        viewModel.searchNearbyHealthPlaces()
        val state = viewModel.uiState.getOrAwaitValue(until = { it.nearbyHealthPlaces.isNotEmpty() })

        assertThat(state.nearbyHealthPlaces).containsExactlyElementsIn(places).inOrder()
        assertThat(state.nearbyPlacesMessage).isNull()
        assertThat(state.isSearchingNearbyPlaces).isFalse()
    }

    @Test
    fun `an empty successful search is not treated as a failure but says nothing was found nearby`() {
        givenLastAlertLocation()
        fakeNearbyHealthPlacesRepository.result = NetworkResult.Success(emptyList())
        val viewModel = createViewModel()

        viewModel.searchNearbyHealthPlaces()
        val state = viewModel.uiState.getOrAwaitValue(until = { it.nearbyPlacesMessage != null })

        assertThat(state.nearbyHealthPlaces).isEmpty()
        assertThat(state.nearbyPlacesMessage).isNotNull()
    }

    @Test
    fun `a no-connection failure gets its own message`() {
        givenLastAlertLocation()
        fakeNearbyHealthPlacesRepository.result = NetworkResult.Failure.NoConnection
        val viewModel = createViewModel()

        viewModel.searchNearbyHealthPlaces()
        val state = viewModel.uiState.getOrAwaitValue(until = { it.nearbyPlacesMessage != null })

        assertThat(state.nearbyPlacesMessage).isEqualTo(
            "Sem conexão com a internet. Verifique sua conexão e tente novamente."
        )
    }

    @Test
    fun `a service-unavailable failure gets its own message, distinct from no-connection`() {
        givenLastAlertLocation()
        fakeNearbyHealthPlacesRepository.result = NetworkResult.Failure.ServiceUnavailable(503)
        val viewModel = createViewModel()

        viewModel.searchNearbyHealthPlaces()
        val state = viewModel.uiState.getOrAwaitValue(until = { it.nearbyPlacesMessage != null })

        assertThat(state.nearbyPlacesMessage).isEqualTo(
            "O serviço de busca está indisponível no momento. Tente novamente em alguns minutos."
        )
    }

    @Test
    fun `an unreadable-response failure gets its own message, distinct from the other two`() {
        givenLastAlertLocation()
        fakeNearbyHealthPlacesRepository.result =
            NetworkResult.Failure.UnreadableResponse(RuntimeException("malformed payload"))
        val viewModel = createViewModel()

        viewModel.searchNearbyHealthPlaces()
        val state = viewModel.uiState.getOrAwaitValue(until = { it.nearbyPlacesMessage != null })

        assertThat(state.nearbyPlacesMessage).isEqualTo(
            "Não conseguimos entender a resposta do serviço de busca. Tente novamente."
        )
    }

    @Test
    fun `a failed search keeps showing the previous successful result instead of wiping it`() {
        givenLastAlertLocation()
        val places = listOf(healthPlace("Farmácia Perto", 120.0))
        fakeNearbyHealthPlacesRepository.result = NetworkResult.Success(places)
        val viewModel = createViewModel()
        viewModel.searchNearbyHealthPlaces()
        viewModel.uiState.getOrAwaitValue(until = { it.nearbyHealthPlaces.isNotEmpty() })

        fakeNearbyHealthPlacesRepository.result = NetworkResult.Failure.NoConnection
        viewModel.searchNearbyHealthPlaces()
        val state = viewModel.uiState.getOrAwaitValue(until = { it.nearbyPlacesMessage != null })

        assertThat(state.nearbyHealthPlaces).isEqualTo(places)
    }
}
