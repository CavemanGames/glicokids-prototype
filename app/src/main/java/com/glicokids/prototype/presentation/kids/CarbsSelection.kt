package com.glicokids.prototype.presentation.kids

/**
 * Module 7 — where the carbohydrate value on b9 came from. Replaces the old
 * `carbsFromPhoto: Boolean` in [NewMealActivity]: a single flag stopped being enough once a
 * value can also come from the local foods table or an online search, and the UI needs to tell
 * all four apart (the source chip text differs, and [LocalFood]/[OnlineFood] carry the food
 * name so that chip can name what was picked instead of just saying "a food was picked").
 *
 * [Manual] and [Photo] carry no extra data — same as before, just promoted from a bare boolean
 * to a case of this type — and are constructed by [NewMealActivity] itself; this ViewModel only
 * ever produces [LocalFood]/[OnlineFood], returned from [NewMealViewModel.applyLocalFood] and
 * [NewMealViewModel.applyOnlineFood].
 *
 * That split is intentional and not missing wiring. Typing a value and capturing a photo never
 * went through a ViewModel on this screen, and routing them through one now would mean touching
 * the most used flow in the app to gain nothing.
 */
sealed class CarbsOrigin {
    object Manual : CarbsOrigin()
    object Photo : CarbsOrigin()
    data class LocalFood(val name: String) : CarbsOrigin()
    data class OnlineFood(val name: String) : CarbsOrigin()
}

/**
 * Module 7 — the result of picking a food off b9's search dialog: the carbohydrate value to put
 * in `etCarbohydrates` and the [CarbsOrigin] that produced it. Returned directly from
 * [NewMealViewModel.applyLocalFood]/[NewMealViewModel.applyOnlineFood] rather than folded into
 * [NewMealUiState] — a LiveData field would keep replaying the last pick on every unrelated state
 * emission (e.g. after [NewMealViewModel.calculate]), which would silently overwrite whatever the
 * child had since typed by hand.
 */
data class CarbsSelection(val carbs: Double, val origin: CarbsOrigin)
