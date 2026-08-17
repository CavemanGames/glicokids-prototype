package com.glicokids.prototype.domain.usecase

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Module 6 — assembles the pt-BR alert text sent to the support network.
 * Only primitives in the signature: the caller already anonymized the child's name
 * (`ReportStorage.anonymizedName`, same LGPD rule as the 7-day report) before calling this.
 *
 * Module 7: [locationHint] is a defaulted parameter — every existing call site and test keeps
 * compiling and behaving byte-for-byte the same when it is left null. When present, it is folded
 * in as an extra sentence, after the origin note and before the closing call to action, so
 * direction and range stay exactly where they already were.
 */
class BuildAlertMessageUseCase @Inject constructor() {

    fun execute(
        partialChildName: String,
        value: Int,
        timestampMillis: Long,
        rangeMin: Int,
        rangeMax: Int,
        isHypo: Boolean,
        fromSensor: Boolean,
        locationHint: String? = null
    ): String {
        // A new formatter per call — SimpleDateFormat is not thread-safe, so it is never
        // shared as a static/companion field (same convention as ReportStorage.buildReport).
        val time = SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date(timestampMillis))
        val directionText = if (isHypo) "abaixo da faixa" else "acima da faixa"
        val originText = if (fromSensor) {
            "Leitura do sensor, sem confirmação da criança."
        } else {
            "Leitura feita pela criança, já confirmada."
        }

        val locationText = if (locationHint != null) " Localização aproximada: $locationHint." else ""

        return "GlicoKids: $partialChildName está com $value mg/dL às $time — " +
            "$directionText ($rangeMin–$rangeMax). $originText$locationText Ligue se puder."
    }
}
