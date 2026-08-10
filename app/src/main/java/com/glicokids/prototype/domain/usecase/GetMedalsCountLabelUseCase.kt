package com.glicokids.prototype.domain.usecase

import com.glicokids.prototype.data.model.MedalRecord
import javax.inject.Inject

/**
 * Module 6 — field defect: the home screen's "★ Medalhas" button was hardcoded to
 * "12 conquistadas" no matter how many medals the child had actually unlocked. This use case
 * is the single place that decides the label, mirroring how [GetDashboardGlucoseDisplayUseCase]
 * already keeps that kind of decision out of the untestable Fragment.
 */
class GetMedalsCountLabelUseCase @Inject constructor() {

    fun execute(medals: List<MedalRecord>): String {
        val unlockedCount = medals.count { it.unlocked }
        val suffix = if (unlockedCount == 1) "conquistada" else "conquistadas"
        return "$unlockedCount $suffix"
    }
}
