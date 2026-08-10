package com.glicokids.prototype.domain.usecase

import com.glicokids.prototype.data.model.GlucoseReading
import com.glicokids.prototype.domain.model.ReadingSource
import com.glicokids.prototype.util.UIHelper
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Module 6 — field defect: the home screen (b7) trend chip (`tvTrend`) is hardcoded to
 * "→ estável" in `fragment_kids_dashboard.xml:122` and [com.glicokids.prototype.presentation.kids.KidsDashboardFragment]
 * never writes to it — only the chip's background colour is derived from
 * [com.glicokids.prototype.domain.usecase.GetDashboardGlucoseDisplayUseCase]. A brand-new
 * install, with zero glucose readings ever logged, still greets the child with a chip
 * claiming the trend is stable. There is nothing stable to report yet.
 *
 * `GlicoKidsDbHelper` already stores every reading with `created_at`
 * ([com.glicokids.prototype.data.local.GlicoKidsDbHelper.getGlucoseReadingsSince] /
 * [com.glicokids.prototype.data.local.GlicoKidsDbHelper.getLastGlucoseReading]), so comparing
 * the two most recent readings is possible — but no query returns "the second-to-last
 * reading" today, and no use case decides what the chip should say. Both are GREEN's job.
 * This test targets the decision alone: [GetGlucoseTrendUseCase] is a pure function of the
 * two most recent readings, mirroring how [GetDashboardGlucoseDisplayUseCase] already decides
 * the rest of the card without touching the database itself.
 *
 * `GetGlucoseTrendUseCase` does not exist yet — this file does not compile until GREEN adds
 * it, which is the expected RED state for this defect.
 *
 * NOTE — same discipline as the caption ("Glico está orbitando feliz") and colour precedent in
 * GetDashboardGlucoseDisplayUseCaseTest: neither `GlicoKids Design.dc.html` (b7) nor
 * `handoff-android.md` define any wording besides the single "→ estável" mock state, and
 * PATCHES.md does not mention this chip's text as dynamic at all. The labels asserted below
 * (`"↑ subindo"`, `"↓ descendo"`, the two "no data yet" cases) are this session's PROPOSAL and
 * still need a human sign-off on the literal wording before GREEN locks them in — the tests
 * only pin down that the chip must never claim "→ estável" without two comparable readings,
 * and that rising/falling/stable must each read differently from one another.
 */
class GetGlucoseTrendUseCaseTest {

    private val useCase = GetGlucoseTrendUseCase()

    private fun reading(value: Int, createdAt: Long) = GlucoseReading(
        id = 1,
        valueMgdl = value,
        status = UIHelper.GlucoseStatus.NA_META,
        source = ReadingSource.SENSOR,
        createdAt = createdAt
    )

    @Test
    fun `the field regression - a brand new install with zero readings must not claim estavel`() {
        val result = useCase.execute(previousReading = null, currentReading = null)

        assertThat(result.hasEnoughData).isFalse()
        assertThat(result.label).isNotEqualTo("→ estável")
    }

    @Test
    fun `a single logged reading with no previous one to compare must not claim estavel either`() {
        val result = useCase.execute(previousReading = null, currentReading = reading(120, 1_000L))

        assertThat(result.hasEnoughData).isFalse()
        assertThat(result.label).isNotEqualTo("→ estável")
    }

    @Test
    fun `a rising second reading is reported as rising, not the fixed stable chip`() {
        val result = useCase.execute(
            previousReading = reading(110, 1_000L),
            currentReading = reading(160, 2_000L)
        )

        assertThat(result.hasEnoughData).isTrue()
        assertThat(result.label).isNotEqualTo("→ estável")
    }

    @Test
    fun `a falling second reading is reported as falling, not the fixed stable chip`() {
        val result = useCase.execute(
            previousReading = reading(160, 1_000L),
            currentReading = reading(110, 2_000L)
        )

        assertThat(result.hasEnoughData).isTrue()
        assertThat(result.label).isNotEqualTo("→ estável")
    }

    @Test
    fun `rising and falling must read differently from each other, not share a fallback label`() {
        val rising = useCase.execute(reading(110, 1_000L), reading(160, 2_000L)).label
        val falling = useCase.execute(reading(160, 1_000L), reading(110, 2_000L)).label

        assertThat(rising).isNotEqualTo(falling)
    }

    @Test
    fun `two equal readings is the only case honestly reported as stable`() {
        val result = useCase.execute(
            previousReading = reading(120, 1_000L),
            currentReading = reading(120, 2_000L)
        )

        assertThat(result.hasEnoughData).isTrue()
        assertThat(result.label).isEqualTo("→ estável")
    }
}
