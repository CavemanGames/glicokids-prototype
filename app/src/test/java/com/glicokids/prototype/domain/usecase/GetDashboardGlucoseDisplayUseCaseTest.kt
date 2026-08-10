package com.glicokids.prototype.domain.usecase

import com.glicokids.prototype.data.model.GlucoseReading
import com.glicokids.prototype.domain.model.ReadingSource
import com.glicokids.prototype.util.UIHelper
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Module 6 — field defect: the home screen (b7) showed a hardcoded "112 mg/dL · Na meta!"
 * no matter what the child had actually logged. A device run proved it: the child registered
 * 220 mg/dL and the dashboard still read 112 on the next visit. [KidsDashboardFragment] has
 * no JVM test infrastructure, so this use case is where the decision now lives — it takes the
 * most recent [GlucoseReading] straight out of [com.glicokids.prototype.data.local.GlicoKidsDbHelper.getLastGlucoseReading]
 * and the range currently configured in preferences, and returns what the card should show.
 * Never reads or replicates the status colour rule itself — that stays [UIHelper.glucoseStatus]'s
 * job alone, exactly as it already is for [com.glicokids.prototype.presentation.kids.GlucoseLogActivity].
 */
class GetDashboardGlucoseDisplayUseCaseTest {

    private val useCase = GetDashboardGlucoseDisplayUseCase()

    private fun reading(value: Int, createdAt: Long = 1_700_000_000_000L) = GlucoseReading(
        id = 1,
        valueMgdl = value,
        status = UIHelper.GlucoseStatus.NA_META, // stored status from save time — must be ignored, see below
        source = ReadingSource.SENSOR,
        createdAt = createdAt
    )

    @Test
    fun `no reading yet returns an empty state instead of any glucose value`() {
        val result = useCase.execute(lastReading = null, rangeMin = 70, rangeMax = 180)

        assertThat(result.hasReading).isFalse()
        assertThat(result.valueMgdl).isNull()
        assertThat(result.status).isNull()
    }

    @Test
    fun `the field regression - a 220 reading is shown as 220 and out of range, never as a fixed 112`() {
        val result = useCase.execute(lastReading = reading(220), rangeMin = 70, rangeMax = 180)

        assertThat(result.hasReading).isTrue()
        assertThat(result.valueMgdl).isEqualTo(220)
        assertThat(result.valueMgdl).isNotEqualTo(112)
        assertThat(result.status).isEqualTo(UIHelper.GlucoseStatus.FORA_DA_META)
    }

    @Test
    fun `a reading inside the current range is shown as NA_META`() {
        val result = useCase.execute(lastReading = reading(120), rangeMin = 70, rangeMax = 180)

        assertThat(result.hasReading).isTrue()
        assertThat(result.valueMgdl).isEqualTo(120)
        assertThat(result.status).isEqualTo(UIHelper.GlucoseStatus.NA_META)
    }

    @Test
    fun `status is recomputed from the range passed in, not from the reading's stored status`() {
        // Saved as NA_META (see reading() above) under whatever range was active back then;
        // called here with a range that puts the very same value outside the target — editing
        // the range in the Area dos Pais must recolour the home card, same as GlucoseLogActivity.
        val result = useCase.execute(lastReading = reading(120), rangeMin = 130, rangeMax = 200)

        assertThat(result.status).isEqualTo(UIHelper.GlucoseStatus.FORA_DA_META)
    }

    @Test
    fun `statusLabel matches the three states already used on the glucose log screen`() {
        assertThat(useCase.execute(reading(120), 70, 180).statusLabel).isEqualTo("Na meta!")
        assertThat(useCase.execute(reading(75), 70, 180).statusLabel).isEqualTo("Atenção")
        assertThat(useCase.execute(reading(220), 70, 180).statusLabel).isEqualTo("Fora da meta")
    }

    @Test
    fun `empty state label does not claim any clinical status`() {
        val result = useCase.execute(lastReading = null, rangeMin = 70, rangeMax = 180)

        assertThat(result.statusLabel).isEqualTo("Sem leitura ainda")
    }

    // --- Field defect (RED, session of 09/08/2026): the b7 card holds two more elements the
    // Fragment never varies, on top of the hardcoded "112" this file already exists for.
    //
    // 1.1 — the secondary caption ("Glico está orbitando feliz") never changes, even with a
    // 220 mg/dL reading and the label right next to it already reading "Fora da meta". Neither
    // `GlicoKids Design.dc.html` (id="b7") nor `handoff-android.md` ("Home da criança (b7 / f7)")
    // define per-status text here — the mock only ever shows the happy/"Na meta!" composition,
    // which is what a static mockup of one representative state always looks like. Since there
    // is no spec to read, the strings below are this session's PROPOSAL — sober, no treatment
    // instruction (same discipline already applied to GlucoseAlertViewModel.imOkButtonText for
    // b22) — and still need a human sign-off on the literal wording before GREEN locks them in.
    //
    // 1.2 — the big value (`tvGlucoseValue`) stays in a fixed `teal_glow`; only the card border
    // and the trend chip already follow `UIHelper.getStatusColor` (KidsDashboardFragment.
    // renderGlucoseDisplay computes `color` and applies it to cardGlucose.strokeColor/tvTrend,
    // never to tvGlucoseValue). The b7 prose is silent on the value's colour, so read alone that
    // would be "the design, not a defect" — but GlucoseLogActivity.kt:137 and
    // NewMealActivity.kt:98 already call `tvGlucoseValue.setTextColor(UIHelper.getStatusColor(status))`
    // for the exact same kind of value on other screens, and the project convention fixes
    // glucose-state colour as coming ONLY from UIHelper.glucoseStatus/getStatusColor. That
    // precedent — not the b7 paragraph — is why this is a defect.
    private val teal = 0xFF00E5B0.toInt() // UIHelper.getStatusColor(NA_META)
    private val gold = 0xFFFFD54F.toInt() // UIHelper.getStatusColor(ATENCAO)
    private val danger = 0xFFC62828.toInt() // UIHelper.getStatusColor(FORA_DA_META)
    private val neutralGrey = 0xFF8A8299.toInt() // same fallback KidsDashboardFragment already uses for "no status yet"

    @Test
    fun `the field regression - a 220 reading out of range gets a sober out-of-range caption, never the happy one`() {
        val result = useCase.execute(lastReading = reading(220), rangeMin = 70, rangeMax = 180)

        assertThat(result.secondaryMessage).isNotEqualTo("Glico está orbitando feliz")
    }

    @Test
    fun `secondaryMessage follows the three glucose states instead of one fixed caption`() {
        val onTarget = useCase.execute(reading(120), 70, 180).secondaryMessage
        val attention = useCase.execute(reading(75), 70, 180).secondaryMessage
        val outOfRange = useCase.execute(reading(220), 70, 180).secondaryMessage

        assertThat(onTarget).isEqualTo("Glico está orbitando feliz")
        assertThat(attention).isNotEqualTo("Glico está orbitando feliz")
        assertThat(outOfRange).isNotEqualTo("Glico está orbitando feliz")
        // ATENCAO and FORA_DA_META must read differently from each other too, not share a fallback
        assertThat(attention).isNotEqualTo(outOfRange)
    }

    @Test
    fun `empty state secondary message does not claim any mood, same discipline as statusLabel`() {
        val result = useCase.execute(lastReading = null, rangeMin = 70, rangeMax = 180)

        assertThat(result.secondaryMessage).isNotEqualTo("Glico está orbitando feliz")
    }

    @Test
    fun `the field regression - a 220 reading colours the big value red, not the fixed teal it shows today`() {
        val result = useCase.execute(lastReading = reading(220), rangeMin = 70, rangeMax = 180)

        assertThat(result.valueColorArgb).isEqualTo(danger)
        assertThat(result.valueColorArgb).isNotEqualTo(teal)
    }

    @Test
    fun `valueColorArgb follows the same three states the border and trend chip already do`() {
        assertThat(useCase.execute(reading(120), 70, 180).valueColorArgb).isEqualTo(teal)
        assertThat(useCase.execute(reading(75), 70, 180).valueColorArgb).isEqualTo(gold)
        assertThat(useCase.execute(reading(220), 70, 180).valueColorArgb).isEqualTo(danger)
    }

    @Test
    fun `empty state value colour uses the same neutral grey KidsDashboardFragment already falls back to`() {
        val result = useCase.execute(lastReading = null, rangeMin = 70, rangeMax = 180)

        assertThat(result.valueColorArgb).isEqualTo(neutralGrey)
    }
}
