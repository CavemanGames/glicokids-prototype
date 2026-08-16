package com.glicokids.prototype.presentation.parents

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glicokids.prototype.data.local.AppPreferences
import com.glicokids.prototype.data.location.RawLocationDataSource
import com.glicokids.prototype.domain.model.GeoPoint
import com.glicokids.prototype.domain.model.HealthPlace
import com.glicokids.prototype.domain.model.LocationTech
import com.glicokids.prototype.domain.model.NetworkResult
import com.glicokids.prototype.domain.repository.GeocodingRepository
import com.glicokids.prototype.domain.repository.LocationProvider
import com.glicokids.prototype.domain.repository.NearbyHealthPlacesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A resolved point on the map — either the last saved alert location, a tapped point, or a
 * search result — together with the best label available for it. [label] is always something
 * showable: a real address when one resolved, the formatted coordinate otherwise. Never a
 * blank string and never (0.0, 0.0) standing in for "nothing resolved" — the caller checks
 * nullability of the field holding this instead. */
data class MapPoint(
    val lat: Double,
    val lng: Double,
    val label: String
)

/** One row of the technology diagnostic list (requirement 5): a fix and the technology that
 * produced it, straight off [RawLocationDataSource.sampleEachProvider]. */
data class TechnologyReading(
    val tech: LocationTech,
    val accuracyMeters: Float
)

/**
 * Module 7 — b-map (Área dos Pais). What the map Activity reads and drives; the `GoogleMap`
 * object itself is never touched here, since Robolectric has no shadow for it — every piece of
 * logic this screen needs lives in this class instead, precisely so it can be unit-tested.
 *
 * [hasMapsApiKey] is decided once, at construction, from whatever the build injected into
 * [mapsApiKey] — see `app/build.gradle.kts`, which falls back to [MISSING_MAPS_API_KEY] when
 * `local.properties` has no real key. The Activity reads it to decide whether to even attempt
 * showing the `GoogleMap` fragment or to explain its absence instead.
 */
@HiltViewModel
class AlertMapViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val locationProvider: LocationProvider,
    private val geocodingRepository: GeocodingRepository,
    private val rawLocationDataSource: RawLocationDataSource,
    private val nearbyHealthPlacesRepository: NearbyHealthPlacesRepository,
    @Named("mapsApiKey") private val mapsApiKey: String
) : ViewModel() {

    private val _uiState = MutableLiveData<AlertMapUiState>()
    val uiState: LiveData<AlertMapUiState> = _uiState

    /** The active `locationUpdates` subscription, alive only between a [setFollowingLocation]
     * `true` and its matching `false`. Launched through [viewModelScope] on purpose (see the
     * class kdoc): that ties its lifetime to this ViewModel's own, so it is guaranteed to stop
     * the instant the screen goes away and is cleared — even if the Activity itself never calls
     * `setFollowingLocation(false)` on the way out (back press, process death, a caregiver just
     * navigating elsewhere). [viewModelScope] is cancelled by the base [ViewModel.onCleared]
     * with no code of ours needed for that half of the contract; this field only exists so the
     * explicit "toggle off" path can cancel the same job early, before the screen is destroyed.
     */
    private var followJob: Job? = null

    init {
        _uiState.value = AlertMapUiState(
            hasMapsApiKey = mapsApiKey != MISSING_MAPS_API_KEY,
            lastAlertLocation = readLastAlertLocation(),
            selectedPoint = null,
            isResolvingAddress = false,
            searchErrorMessage = null,
            isFollowingLocation = false,
            followedLocation = null,
            followError = null,
            technologyReadings = emptyList(),
            isSearchingNearbyPlaces = false,
            nearbyHealthPlaces = emptyList(),
            nearbyPlacesMessage = null
        )
        loadTechnologyReadings()
    }

    /** Requirement 1 — the last place a glucose alert was ever sent from, read straight out of
     * [AppPreferences]. `lastAlertLocationAt == 0L` is the same never-happened sentinel used
     * everywhere else in this project ([AppPreferences.lastAlertAt] and friends): null here, so
     * the screen shows an explicit empty state instead of plotting (0.0, 0.0). */
    private fun readLastAlertLocation(): MapPoint? {
        if (prefs.lastAlertLocationAt == 0L) return null
        val lat = prefs.lastAlertLocationLat
        val lng = prefs.lastAlertLocationLng
        val label = prefs.lastAlertLocationLabel ?: formatCoordinate(lat, lng)
        return MapPoint(lat = lat, lng = lng, label = label)
    }

    /** Requirement 5 — GPS and network fixes side by side, for the diagnostic list. Run from
     * [init] so the list is already populated by the time the Activity's first observation
     * lands; an empty result is a legitimate outcome ([RawLocationDataSource] never throws) and
     * is left to the Activity to explain, the same way an empty [AlertMapUiState.lastAlertLocation]
     * is the Activity's cue for its own empty state rather than a second flag here. */
    private fun loadTechnologyReadings() {
        viewModelScope.launch {
            val readings = withContext(Dispatchers.IO) {
                rawLocationDataSource.sampleEachProvider()
            }.map { TechnologyReading(tech = it.tech, accuracyMeters = it.accuracyMeters) }
            val current = _uiState.value ?: return@launch
            _uiState.value = current.copy(technologyReadings = readings)
        }
    }

    /** Requirement 2 — a tap on the map resolves its address. [GeocodingRepository.reverse]
     * never throws and never blocks indefinitely on its own (same contract as every other
     * location/geocoding dependency in this project), so this needs no timeout of its own; it
     * only needs to fall back to a formatted coordinate — never a blank label — when nothing
     * resolves. */
    fun onMapTapped(lat: Double, lng: Double) {
        val started = _uiState.value ?: return
        _uiState.value = started.copy(isResolvingAddress = true)
        viewModelScope.launch {
            val label = withContext(Dispatchers.IO) {
                geocodingRepository.reverse(lat, lng)
            } ?: formatCoordinate(lat, lng)
            val current = _uiState.value ?: return@launch
            _uiState.value = current.copy(
                selectedPoint = MapPoint(lat = lat, lng = lng, label = label),
                isResolvingAddress = false,
                searchErrorMessage = null
            )
        }
    }

    /** Requirement 3 — a typed address resolves to a point the camera can navigate to. A blank
     * query never reaches [GeocodingRepository.forward] at all. An address that does not
     * resolve leaves [AlertMapUiState.selectedPoint] exactly as it was — in particular it never
     * becomes (0.0, 0.0) — and instead reports through [AlertMapUiState.searchErrorMessage]. */
    fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val point = withContext(Dispatchers.IO) {
                geocodingRepository.forward(trimmed)
            }
            val current = _uiState.value ?: return@launch
            _uiState.value = if (point == null) {
                current.copy(searchErrorMessage = "Não encontramos esse endereço. Tente buscar de outro jeito.")
            } else {
                current.copy(
                    selectedPoint = MapPoint(lat = point.lat, lng = point.lng, label = trimmed),
                    searchErrorMessage = null
                )
            }
        }
    }

    /**
     * Requirement 4 — turns the live `locationUpdates` subscription on or off. Missing
     * permission never throws and never leaves the switch stuck on: [AlertMapUiState.followError]
     * carries the explanation and [AlertMapUiState.isFollowingLocation] stays false, matching
     * the same "explain, never crash, never permanently disable" rule §10 already applies to
     * every other permission-gated control in this project.
     */
    fun setFollowingLocation(enabled: Boolean) {
        followJob?.cancel()
        followJob = null
        val current = _uiState.value ?: return

        if (!enabled) {
            _uiState.value = current.copy(isFollowingLocation = false)
            return
        }

        if (!locationProvider.hasPermission()) {
            _uiState.value = current.copy(
                isFollowingLocation = false,
                followError = "Permita o acesso à localização para acompanhar a posição no mapa."
            )
            return
        }

        _uiState.value = current.copy(isFollowingLocation = true, followError = null)
        followJob = viewModelScope.launch {
            locationProvider.locationUpdates(FOLLOW_INTERVAL_MILLIS).collect { point ->
                val latest = _uiState.value ?: return@collect
                _uiState.value = latest.copy(followedLocation = point)
            }
        }
    }

    /**
     * Requirement 7 — health places (hospital, pharmacy, clinic, doctor's office) near the point
     * a parent most likely cares about right now. Never called from [init]: the Overpass call
     * behind [NearbyHealthPlacesRepository] takes 5-15 seconds and has already proven unstable,
     * so gating it behind an explicit tap is what keeps opening this screen fast regardless of
     * that service's health.
     *
     * The anchor point is [AlertMapUiState.lastAlertLocation] when one exists — a parent looking
     * at this screen is usually here *because* of that alert, so "help near where the alert came
     * from" is the more useful default over "help near me right now". Only when no alert has ever
     * carried a location does this fall back to [LocationProvider.getCurrentLocation]. When
     * neither is available, [NearbyHealthPlacesRepository.findNearby] is never called at all —
     * there is nothing honest to search around.
     */
    fun searchNearbyHealthPlaces() {
        val started = _uiState.value ?: return
        _uiState.value = started.copy(isSearchingNearbyPlaces = true, nearbyPlacesMessage = null)
        viewModelScope.launch {
            val anchor = started.lastAlertLocation
                ?: withContext(Dispatchers.IO) { locationProvider.getCurrentLocation() }
                    ?.let { MapPoint(lat = it.lat, lng = it.lng, label = formatCoordinate(it.lat, it.lng)) }

            if (anchor == null) {
                val current = _uiState.value ?: return@launch
                _uiState.value = current.copy(
                    isSearchingNearbyPlaces = false,
                    nearbyPlacesMessage = "Não encontramos um alerta recente nem sua localização atual para buscar por perto."
                )
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                nearbyHealthPlacesRepository.findNearby(
                    lat = anchor.lat,
                    lng = anchor.lng,
                    radiusMeters = NEARBY_SEARCH_RADIUS_METERS
                )
            }
            val current = _uiState.value ?: return@launch
            _uiState.value = when (result) {
                is NetworkResult.Success -> if (result.data.isEmpty()) {
                    current.copy(
                        isSearchingNearbyPlaces = false,
                        nearbyPlacesMessage = "Nenhum lugar de saúde encontrado por perto."
                    )
                } else {
                    current.copy(
                        isSearchingNearbyPlaces = false,
                        nearbyHealthPlaces = result.data,
                        nearbyPlacesMessage = null
                    )
                }
                is NetworkResult.Failure.NoConnection -> current.copy(
                    isSearchingNearbyPlaces = false,
                    nearbyPlacesMessage = "Sem conexão com a internet. Verifique sua conexão e tente novamente."
                )
                is NetworkResult.Failure.ServiceUnavailable -> current.copy(
                    isSearchingNearbyPlaces = false,
                    nearbyPlacesMessage = "O serviço de busca está indisponível no momento. Tente novamente em alguns minutos."
                )
                is NetworkResult.Failure.UnreadableResponse -> current.copy(
                    isSearchingNearbyPlaces = false,
                    nearbyPlacesMessage = "Não conseguimos entender a resposta do serviço de busca. Tente novamente."
                )
            }
        }
    }

    /** Locale.US on purpose: a pt-BR locale would print the decimal separator as a comma,
     * which would collide with the comma this format already uses between lat and lng and
     * make the label ambiguous to read back. */
    private fun formatCoordinate(lat: Double, lng: Double): String =
        String.format(Locale.US, "%.5f, %.5f", lat, lng)

    companion object {
        /** Must match the literal fallback in `app/build.gradle.kts` exactly — that is the
         * string the build injects into [BuildConfig.MAPS_API_KEY] (and, from there, into
         * whatever `@Named("mapsApiKey")` provides) whenever `local.properties` has no real
         * `MAPS_API_KEY`. */
        const val MISSING_MAPS_API_KEY = "MISSING_MAPS_API_KEY"

        private const val FOLLOW_INTERVAL_MILLIS = 5_000L
        private const val NEARBY_SEARCH_RADIUS_METERS = 5_000
    }
}

/**
 * What the map screen (b-map) reads. Immutable, updated with `.copy()` — same pattern as every
 * other screen's `UiState` in this project (see [com.glicokids.prototype.presentation.kids.GlucoseAlertUiState]).
 */
data class AlertMapUiState(
    /** False when the build has no real Maps key ([AlertMapViewModel.MISSING_MAPS_API_KEY]) —
     * the Activity's cue to hide the `GoogleMap` fragment and explain instead of showing a mute
     * gray rectangle. */
    val hasMapsApiKey: Boolean,
    /** Requirement 1 — the last place a glucose alert was sent from. Null when no alert with a
     * location has ever been sent — an explicit empty state, never (0.0, 0.0). */
    val lastAlertLocation: MapPoint?,
    /** The point currently under discussion — from a tap (requirement 2) or a search
     * (requirement 3) — with the best label available for it. Untouched by a search that found
     * nothing, so it never becomes a wrong point on the map. */
    val selectedPoint: MapPoint?,
    /** True only while a tap's reverse-geocode is in flight; never true for a search, which has
     * no equivalent "in-between" state of its own to show. */
    val isResolvingAddress: Boolean,
    /** Requirement 3 — non-null right after a search whose query resolved to nothing. Cleared
     * by the next successful search or the next tap. */
    val searchErrorMessage: String?,
    /** Requirement 4 — mirrors the on/off toggle exactly; never true while [followError] is
     * non-null, since a missing permission never lets the subscription start. */
    val isFollowingLocation: Boolean,
    /** The most recent position received while following. Null until the first update arrives,
     * and left at its last value after `setFollowingLocation(false)` — turning follow back on
     * simply resumes updating it. */
    val followedLocation: GeoPoint?,
    /** Requirement 4 — non-null only when the last `setFollowingLocation(true)` could not start
     * because the permission is missing. Cleared as soon as follow starts successfully. */
    val followError: String?,
    /** Requirement 5 — one entry per technology [RawLocationDataSource.sampleEachProvider]
     * managed to read a last-known fix for. An empty list is a legitimate outcome, left to the
     * Activity to explain rather than a second flag here. */
    val technologyReadings: List<TechnologyReading>,
    /** Requirement 7 — true only while [AlertMapViewModel.searchNearbyHealthPlaces] has a call
     * in flight (resolving the current position and/or waiting on the Overpass lookup, which
     * alone can take 5-15 seconds). Defaults to false so existing tests that build this state
     * without mentioning the field keep passing unchanged. */
    val isSearchingNearbyPlaces: Boolean = false,
    /** Requirement 7 — the last successful, non-empty search result, ordered by distance same as
     * [NearbyHealthPlacesRepository.findNearby] returns it. Left untouched by a failed search, so
     * a transient network error never wipes a result the parent is already looking at. */
    val nearbyHealthPlaces: List<HealthPlace> = emptyList(),
    /** Requirement 7 — set for every outcome that is not "found at least one place": no
     * connection, service unavailable, unreadable response, an empty result, or no point to
     * search around at all. Each has its own pt-BR text; never a generic "something went wrong".
     * Cleared by the next search that finds something. */
    val nearbyPlacesMessage: String? = null
)
