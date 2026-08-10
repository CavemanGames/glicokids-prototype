package com.glicokids.prototype.domain.usecase

import com.glicokids.prototype.domain.model.AlertDirection
import com.glicokids.prototype.domain.model.AlertMode
import com.glicokids.prototype.domain.model.ReadingSource
import javax.inject.Inject

/**
 * Module 6 — the single, named rule that decides whether an out-of-range glucose reading
 * is allowed to fire an alert on its own, without a screen or a repository duplicating it.
 */
class ShouldAutoAlertUseCase @Inject constructor() {

    fun execute(
        source: ReadingSource,
        direction: AlertDirection,
        hypoMode: AlertMode,
        hyperMode: AlertMode,
        lastAlertAt: Long,
        now: Long,
        throttleMin: Int
    ): Boolean {
        // A MANUAL reading means the child picked up the meter and typed the number in —
        // she is awake, conscious and already interacting with the app. There is no
        // emergency to page a caregiver about, so the app can at most *suggest* telling
        // someone; it must never page anyone on the child's behalf. This is the safety
        // rule the rest of the module is built around: never relax it to add a feature.
        if (source == ReadingSource.MANUAL) return false

        // NONE covers every value inside the configured range (both NA_META and ATENCAO
        // in UIHelper terms) — an automatic alert only makes sense for a value that is
        // genuinely outside the range.
        if (direction == AlertDirection.NONE) return false

        val mode = when (direction) {
            AlertDirection.HYPO -> hypoMode
            AlertDirection.HYPER -> hyperMode
            AlertDirection.NONE -> return false // unreachable, guarded above
        }
        if (mode != AlertMode.AUTO) return false // SUGGEST and OFF never fire on their own

        val elapsedMillis = now - lastAlertAt
        if (elapsedMillis < throttleMin * 60_000L) return false

        return true
    }
}
