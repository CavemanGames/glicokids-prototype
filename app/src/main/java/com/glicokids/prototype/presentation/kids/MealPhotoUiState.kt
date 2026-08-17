package com.glicokids.prototype.presentation.kids

/**
 * Real camera capture on b9.
 *
 * The view state `ivMealPhoto`/`ivPhotoPlaceholder` must end up in once a photo is (or is not)
 * present. Pulled out of `NewMealActivity` so the rule is covered by a plain JUnit test even
 * though the Activity wiring around it (registerForActivityResult, setImageBitmap, visibility)
 * has no Robolectric/Hilt harness yet to exercise it directly.
 */
data class MealPhotoAccessibilityState(
    val placeholderVisible: Boolean,
    val photoImportantForAccessibility: Boolean,
    val photoContentDescription: String?
)

object MealPhotoUiState {

    private const val PHOTO_TAKEN_DESCRIPTION = "Foto do prato tirada"

    fun forPhoto(hasPhoto: Boolean): MealPhotoAccessibilityState {
        return if (hasPhoto) {
            MealPhotoAccessibilityState(
                placeholderVisible = false,
                photoImportantForAccessibility = true,
                photoContentDescription = PHOTO_TAKEN_DESCRIPTION
            )
        } else {
            MealPhotoAccessibilityState(
                placeholderVisible = true,
                photoImportantForAccessibility = false,
                photoContentDescription = null
            )
        }
    }
}
