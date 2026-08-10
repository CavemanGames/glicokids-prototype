package com.glicokids.prototype.domain.usecase

import com.glicokids.prototype.data.model.GlucoseReading
import javax.inject.Inject

/** What the home dashboard's trend chip (`tvTrend`, b7) should show. */
data class GlucoseTrendDisplay(val label: String, val hasEnoughData: Boolean)

/** Empty label — [hasEnoughData] is false, so [KidsDashboardFragment][com.glicokids.prototype.presentation.kids.KidsDashboardFragment] hides the chip instead of rendering it. */
private const val NO_TREND_LABEL = ""

/**
 * Module 6 — field defect: the home screen's trend chip was hardcoded to "→ estável" in the
 * layout and nothing ever wrote to it, so a brand-new install with zero glucose readings still
 * claimed the trend was stable. This use case is the single place that decides the chip's
 * label, comparing the two most recent readings — mirroring how
 * [GetDashboardGlucoseDisplayUseCase] already keeps that kind of decision out of the
 * untestable Fragment.
 *
 * With fewer than two readings there is nothing to compare, so [hasEnoughData] is false and
 * the caller must hide the chip rather than render an empty or misleading label.
 */
class GetGlucoseTrendUseCase @Inject constructor() {

    fun execute(previousReading: GlucoseReading?, currentReading: GlucoseReading?): GlucoseTrendDisplay {
        if (previousReading == null || currentReading == null) {
            return GlucoseTrendDisplay(label = NO_TREND_LABEL, hasEnoughData = false)
        }

        val label = when {
            currentReading.valueMgdl > previousReading.valueMgdl -> "↑ subindo"
            currentReading.valueMgdl < previousReading.valueMgdl -> "↓ descendo"
            else -> "→ estável"
        }
        return GlucoseTrendDisplay(label = label, hasEnoughData = true)
    }
}
