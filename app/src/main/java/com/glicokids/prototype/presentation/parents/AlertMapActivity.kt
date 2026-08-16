package com.glicokids.prototype.presentation.parents

import android.Manifest
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import com.glicokids.prototype.R
import com.glicokids.prototype.databinding.ActivityAlertMapBinding
import com.glicokids.prototype.domain.model.LocationTech
import com.glicokids.prototype.util.UIHelper
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

/**
 * Module 7 — b-map. Purely the presentation layer: every decision (what the last alert was,
 * what a tap or search resolves to, whether follow is allowed) already lives in
 * [AlertMapViewModel] and is unit-tested there, since Robolectric has no shadow for [GoogleMap]
 * itself. This class only wires that state onto the screen.
 *
 * [getMapAsync] is only ever reached through [ensureMapSection] — never on any other path —
 * because calling it without a real Maps key is what triggers the authorization failure the
 * key check exists to avoid.
 */
@AndroidEntryPoint
class AlertMapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlertMapBinding
    private val viewModel: AlertMapViewModel by viewModels()

    private var googleMap: GoogleMap? = null
    private var mapFragmentAdded = false

    private var lastAlertMarker: Marker? = null
    private var selectedMarker: Marker? = null
    private var followMarker: Marker? = null
    private var renderedLastAlertPoint: MapPoint? = null
    private var renderedSelectedPoint: MapPoint? = null

    /**
     * Asked once, on open, same molde as [SupportNetworkActivity]'s SEND_SMS request — the
     * screen never re-prompts, and a denial only explains itself through a toast. Everything
     * else on the screen (search, tap, last-alert marker, technology diagnostics) works with no
     * location permission at all; only the follow toggle depends on it, and
     * [AlertMapViewModel.setFollowingLocation] already reports a missing permission through
     * [com.glicokids.prototype.presentation.parents.AlertMapUiState.followError] rather than
     * crashing or getting stuck.
     */
    private val requestLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                UIHelper.showToast(
                    this,
                    "Sem a permissão de localização o app não consegue seguir sua posição no mapa"
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlertMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)

        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnSearchAddress.setOnClickListener {
            viewModel.search(binding.etSearchAddress.text?.toString().orEmpty())
        }

        binding.cgMapType.setOnCheckedStateChangeListener { _, checkedIds ->
            val type = when (checkedIds.firstOrNull()) {
                binding.chipMapSatellite.id -> GoogleMap.MAP_TYPE_SATELLITE
                binding.chipMapHybrid.id -> GoogleMap.MAP_TYPE_HYBRID
                binding.chipMapTerrain.id -> GoogleMap.MAP_TYPE_TERRAIN
                else -> GoogleMap.MAP_TYPE_NORMAL
            }
            googleMap?.mapType = type
        }

        // Same idiom as AlertSettingsActivity's switches: the listener always forwards to the
        // view model, and observeViewModel only reassigns isChecked when it actually disagrees
        // with the state — so a programmatic correction (e.g. permission denied, switch snaps
        // back off) converges in one harmless extra round trip instead of looping.
        binding.swFollowLocation.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setFollowingLocation(isChecked)
        }
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(this) { state ->
            ensureMapSection(state.hasMapsApiKey)
            renderSelectedPoint(state)
            renderLastAlert(state)
            renderFollow(state)
            renderTechReadings(state.technologyReadings)
            renderMapContent(state)
        }
    }

    // ------------------------------------------------------------------
    // Map section — the only place in this class allowed to call getMapAsync.
    // ------------------------------------------------------------------

    private fun ensureMapSection(hasMapsApiKey: Boolean) {
        if (!hasMapsApiKey) {
            binding.cvMap.visibility = View.GONE
            binding.cgMapType.visibility = View.GONE
            binding.cvNoMapsKey.visibility = View.VISIBLE
            return
        }

        binding.cvMap.visibility = View.VISIBLE
        binding.cgMapType.visibility = View.VISIBLE
        binding.cvNoMapsKey.visibility = View.GONE

        if (mapFragmentAdded) return
        mapFragmentAdded = true

        val mapFragment = SupportMapFragment.newInstance()
        supportFragmentManager.beginTransaction()
            .replace(binding.flMapContainer.id, mapFragment)
            .commit()

        mapFragment.getMapAsync { map ->
            googleMap = map
            map.uiSettings.isZoomControlsEnabled = true
            map.setContentDescription(
                "Mapa mostrando o último alerta, o ponto selecionado e a posição seguida da criança"
            )
            map.setOnMapClickListener { latLng -> viewModel.onMapTapped(latLng.latitude, latLng.longitude) }
            renderMapContent(viewModel.uiState.value)
        }
    }

    private fun renderMapContent(state: AlertMapUiState?) {
        val map = googleMap ?: return
        state ?: return

        val alertPoint = state.lastAlertLocation
        if (alertPoint != null && alertPoint != renderedLastAlertPoint) {
            renderedLastAlertPoint = alertPoint
            lastAlertMarker?.remove()
            lastAlertMarker = map.addMarker(
                MarkerOptions()
                    .position(LatLng(alertPoint.lat, alertPoint.lng))
                    .title("Último alerta enviado")
                    .snippet(alertPoint.label)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )
        }

        val selected = state.selectedPoint
        if (selected != null && selected != renderedSelectedPoint) {
            renderedSelectedPoint = selected
            val target = LatLng(selected.lat, selected.lng)
            selectedMarker?.remove()
            selectedMarker = map.addMarker(
                MarkerOptions().position(target).title("Ponto selecionado").snippet(selected.label)
            )
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(target, DEFAULT_ZOOM))
        }

        // The marker follows isFollowingLocation, not followedLocation != null: the view model
        // keeps the last fix around after turning follow off on purpose (so a re-enable does
        // not blink), and the marker/text on screen stay consistent by hiding together.
        if (state.isFollowingLocation && state.followedLocation != null) {
            val target = LatLng(state.followedLocation.lat, state.followedLocation.lng)
            val marker = followMarker
            if (marker == null) {
                followMarker = map.addMarker(
                    MarkerOptions().position(target).title("Você")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                )
            } else {
                marker.position = target
            }
        } else {
            followMarker?.remove()
            followMarker = null
        }
    }

    // ------------------------------------------------------------------
    // Text panel below the map
    // ------------------------------------------------------------------

    private fun renderSelectedPoint(state: AlertMapUiState) {
        binding.rowResolvingAddress.visibility = if (state.isResolvingAddress) View.VISIBLE else View.GONE
        binding.tvSelectedPoint.visibility = if (state.isResolvingAddress) View.GONE else View.VISIBLE
        binding.tvSelectedPoint.text = state.selectedPoint?.label
            ?: "Toque no mapa ou busque um endereço para ver o ponto aqui."

        binding.tvSearchError.visibility = if (state.searchErrorMessage != null) View.VISIBLE else View.GONE
        binding.tvSearchError.text = state.searchErrorMessage.orEmpty()
    }

    private fun renderLastAlert(state: AlertMapUiState) {
        binding.tvLastAlertLocation.text = state.lastAlertLocation?.label
            ?: "Nenhum alerta com localização foi enviado ainda."
    }

    private fun renderFollow(state: AlertMapUiState) {
        if (binding.swFollowLocation.isChecked != state.isFollowingLocation) {
            binding.swFollowLocation.isChecked = state.isFollowingLocation
        }

        val showFollowedLocation = state.isFollowingLocation && state.followedLocation != null
        binding.tvFollowedLocation.visibility = if (showFollowedLocation) View.VISIBLE else View.GONE
        state.followedLocation?.let { geo ->
            binding.tvFollowedLocation.text =
                "Última posição: ${formatCoordinate(geo.lat, geo.lng)} (±${geo.accuracyMeters.toInt()} m)"
        }

        binding.tvFollowError.visibility = if (state.followError != null) View.VISIBLE else View.GONE
        binding.tvFollowError.text = state.followError.orEmpty()
    }

    /** Requirement 5 — same "build views in code" idiom as [ParentAreaFragment.renderWeekChart]:
     * the list length varies with how many providers answered, so a fixed set of rows in the
     * layout would either overflow or leave dead ones. */
    private fun renderTechReadings(readings: List<TechnologyReading>) {
        binding.llTechReadings.removeAllViews()
        binding.tvTechEmpty.visibility = if (readings.isEmpty()) View.VISIBLE else View.GONE

        val density = resources.displayMetrics.density
        readings.forEach { reading ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (6 * density).toInt(), 0, (6 * density).toInt())
            }
            val label = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                text = techLabel(reading.tech)
                setTextColor(ResourcesCompat.getColor(resources, R.color.ink, null))
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
            }
            val value = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                text = "±${reading.accuracyMeters.toInt()} m"
                setBackgroundResource(R.drawable.bg_chip_teal_soft)
                setPadding((12 * density).toInt(), (5 * density).toInt(), (12 * density).toInt(), (5 * density).toInt())
                setTextColor(Color.parseColor("#00785B"))
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
            }
            row.addView(label)
            row.addView(value)
            binding.llTechReadings.addView(row)
        }
    }

    private fun techLabel(tech: LocationTech): String = when (tech) {
        LocationTech.GPS -> "GPS"
        LocationTech.NETWORK -> "Rede (torres/Wi-Fi)"
        LocationTech.FUSED -> "Fusão (GPS + Rede)"
        LocationTech.PASSIVE -> "Passivo (outro app)"
        LocationTech.UNKNOWN -> "Desconhecida"
    }

    /** Locale.US on purpose — same reasoning as [AlertMapViewModel]'s own formatter: a pt-BR
     * locale's comma decimal separator would collide with the comma already separating lat/lng. */
    private fun formatCoordinate(lat: Double, lng: Double): String =
        String.format(Locale.US, "%.5f, %.5f", lat, lng)

    companion object {
        private const val DEFAULT_ZOOM = 15f
    }
}
