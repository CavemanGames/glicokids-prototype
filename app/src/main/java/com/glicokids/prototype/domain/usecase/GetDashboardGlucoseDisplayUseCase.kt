package com.glicokids.prototype.domain.usecase

import com.glicokids.prototype.data.model.GlucoseReading
import com.glicokids.prototype.util.UIHelper
import javax.inject.Inject

/** What the home dashboard card (b7) should show for the most recent glucose reading. */
data class DashboardGlucoseDisplay(
    val hasReading: Boolean,
    val valueMgdl: Int?,
    val status: UIHelper.GlucoseStatus?,
    val statusLabel: String,
    val secondaryMessage: String,
    val valueColorArgb: Int
)

/** Fallback for when there is no clinical status to show yet — same neutral grey
 * [com.glicokids.prototype.presentation.kids.KidsDashboardFragment] already used before this
 * use case existed. */
private const val NEUTRAL_GREY_ARGB = 0xFF8A8299.toInt()

/**
 * Module 6 — field defect: the home card showed a hardcoded "112 mg/dL · Na meta!" no matter
 * what the child had actually logged. This use case is the single place that decides what the
 * card shows, taking the most recent [GlucoseReading] straight from
 * [com.glicokids.prototype.data.local.GlicoKidsDbHelper.getLastGlucoseReading] and the range
 * currently configured in preferences.
 *
 * Never reads the [GlucoseReading.status] stored on the reading itself — that was computed
 * against whatever range was active at save time. The status shown here is always recomputed
 * through [UIHelper.glucoseStatus] against the range passed in, exactly like
 * [com.glicokids.prototype.presentation.kids.GlucoseLogActivity] already does.
 */
class GetDashboardGlucoseDisplayUseCase @Inject constructor() {

    fun execute(lastReading: GlucoseReading?, rangeMin: Int, rangeMax: Int): DashboardGlucoseDisplay {
        if (lastReading == null) {
            return DashboardGlucoseDisplay(
                hasReading = false,
                valueMgdl = null,
                status = null,
                statusLabel = "Sem leitura ainda",
                secondaryMessage = "Registre uma leitura para acompanhar o Glico",
                valueColorArgb = NEUTRAL_GREY_ARGB
            )
        }

        val status = UIHelper.glucoseStatus(lastReading.valueMgdl, rangeMin, rangeMax)
        return DashboardGlucoseDisplay(
            hasReading = true,
            valueMgdl = lastReading.valueMgdl,
            status = status,
            statusLabel = when (status) {
                UIHelper.GlucoseStatus.NA_META -> "Na meta!"
                UIHelper.GlucoseStatus.ATENCAO -> "Atenção"
                UIHelper.GlucoseStatus.FORA_DA_META -> "Fora da meta"
            },
            secondaryMessage = when (status) {
                UIHelper.GlucoseStatus.NA_META -> "Glico está orbitando feliz"
                UIHelper.GlucoseStatus.ATENCAO -> "Glico está de olho, quase saindo da rota"
                UIHelper.GlucoseStatus.FORA_DA_META -> "Glico saiu da rota — bora chamar um adulto?"
            },
            valueColorArgb = UIHelper.getStatusColor(status)
        )
    }
}
