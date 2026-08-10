package com.glicokids.prototype.domain.usecase

import com.glicokids.prototype.data.model.MedalRecord
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Module 6 — field defect: the home screen (b7) "★ Medalhas" button
 * (`fragment_kids_dashboard.xml:185`, `android:id="@+id/btnMedals"`) is hardcoded to
 * "★ Medalhas\n12 conquistadas". [com.glicokids.prototype.presentation.kids.KidsDashboardFragment]
 * never writes to it. On a brand-new install — `GlicoKidsDbHelper.seedMedals`
 * (`GlicoKidsDbHelper.kt:172`-`193`) seeds exactly 6 medals, 4 of them unlocked, as the comment
 * there already documents — the button still claims "12 conquistadas": a mockup value shipped
 * as fact, the same pattern already fixed once for the glucose card's hardcoded "112".
 *
 * [com.glicokids.prototype.data.local.GlicoKidsDbHelper.getMedals] already returns the real
 * `unlocked` flag per medal (used correctly by
 * [com.glicokids.prototype.presentation.kids.GalleryActivity]), so the data needed to compute
 * the true count exists today — only the label decision is missing. This use case is a pure
 * function of the medal list, mirroring how [GetDashboardGlucoseDisplayUseCase] already keeps
 * that kind of decision out of the untestable Fragment.
 *
 * `GetMedalsCountLabelUseCase` does not exist yet — this file does not compile until GREEN adds
 * it, which is the expected RED state for this defect.
 *
 * NOTE: neither `GlicoKids Design.dc.html` (b7) nor `handoff-android.md` define the singular
 * wording ("1 conquistada" vs "1 conquistadas") — PATCHES.md does not mention this button's
 * text as dynamic at all. The singular-form assertion below is this session's PROPOSAL
 * (standard pt-BR grammar) and still needs a human sign-off before GREEN locks it in.
 */
class GetMedalsCountLabelUseCaseTest {

    private val useCase = GetMedalsCountLabelUseCase()

    private fun medal(unlocked: Boolean) = MedalRecord(
        id = 1,
        code = "codigo",
        name = "Medalha",
        rarity = "OURO",
        unlocked = unlocked,
        unlockedAt = null
    )

    @Test
    fun `the field regression - the fresh install seed of 6 medals, 4 unlocked, reads 4 conquistadas, never the fixed 12`() {
        val medals = listOf(
            medal(unlocked = true),
            medal(unlocked = true),
            medal(unlocked = true),
            medal(unlocked = true),
            medal(unlocked = false),
            medal(unlocked = false)
        )

        val result = useCase.execute(medals)

        assertThat(result).isEqualTo("4 conquistadas")
        assertThat(result).isNotEqualTo("12 conquistadas")
    }

    @Test
    fun `zero unlocked medals reads 0 conquistadas, not the fixed 12`() {
        val medals = listOf(medal(unlocked = false), medal(unlocked = false))

        val result = useCase.execute(medals)

        assertThat(result).isEqualTo("0 conquistadas")
    }

    @Test
    fun `an empty medal list reads 0 conquistadas instead of crashing or showing the fixed 12`() {
        val result = useCase.execute(emptyList())

        assertThat(result).isEqualTo("0 conquistadas")
    }

    @Test
    fun `exactly one unlocked medal uses the singular conquistada`() {
        val medals = listOf(medal(unlocked = true), medal(unlocked = false))

        val result = useCase.execute(medals)

        assertThat(result).isEqualTo("1 conquistada")
    }
}
