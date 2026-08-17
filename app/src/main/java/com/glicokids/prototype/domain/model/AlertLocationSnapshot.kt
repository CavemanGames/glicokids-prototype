package com.glicokids.prototype.domain.model

/**
 * Module 7 — what [com.glicokids.prototype.domain.usecase.ResolveAlertLocationUseCase] hands
 * back when it manages to resolve a location for a glucose alert inside its own time budget:
 * the raw fix, kept for a future map screen to plot, and the best-effort reverse-geocoded
 * label to drop into the SMS body. [label] can be null even when [point] resolved — reverse
 * geocoding is a second, independent best-effort step, same golden "never blocks the alert"
 * rule as [com.glicokids.prototype.domain.repository.GeocodingRepository] itself.
 */
data class AlertLocationSnapshot(
    val point: GeoPoint,
    val label: String?
)
