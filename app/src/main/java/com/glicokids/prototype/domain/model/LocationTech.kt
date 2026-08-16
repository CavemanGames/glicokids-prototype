package com.glicokids.prototype.domain.model

/**
 * Module 7 — which positioning technology produced a [GeoPoint].
 *
 * The academic requirement asks the app to demonstrate a fix obtained through GPS, Cell ID
 * and Wi-Fi. Google's fused provider blends all three internally and only ever reports back
 * as "fused" — the split has to come from `android.location.LocationManager` instead, which
 * exposes [GPS] ([android.location.LocationManager.GPS_PROVIDER], satellite) and [NETWORK]
 * ([android.location.LocationManager.NETWORK_PROVIDER], cell tower **and** Wi-Fi together —
 * the Android public API does not separate those two). This enum is what lets the UI show
 * the difference in precision between them instead of a single opaque "located" state.
 *
 * [PASSIVE] mirrors [android.location.LocationManager.PASSIVE_PROVIDER] (a fix obtained by
 * another app on the device, observed passively). [UNKNOWN] is the safe fallback for a
 * provider string the platform reports that this enum does not recognize — never a reason
 * to throw, matching the rest of this contract.
 */
enum class LocationTech { GPS, NETWORK, FUSED, PASSIVE, UNKNOWN }
