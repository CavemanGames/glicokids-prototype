package com.glicokids.prototype.domain.model

/**
 * Which side of the target range a glucose value falls on — the piece of information
 * [com.glicokids.prototype.domain.usecase.ShouldAutoAlertUseCase] needs to pick between
 * `hypoMode` and `hyperMode`. This is deliberately not [com.glicokids.prototype.util.UIHelper.GlucoseStatus]:
 * `FORA_DA_META` covers both sides and paints the same color for either, but an automatic
 * alert has to know which one it is.
 */
enum class AlertDirection {
    HYPO, HYPER, NONE;

    companion object {
        /** Never hardcode [rangeMin]/[rangeMax] here — they always come from the caller's prefs. */
        fun from(value: Int, rangeMin: Int, rangeMax: Int): AlertDirection = when {
            value < rangeMin -> HYPO
            value > rangeMax -> HYPER
            else -> NONE
        }
    }
}
