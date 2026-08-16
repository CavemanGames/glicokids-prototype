package com.glicokids.prototype.presentation.kids

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Module 6 — real camera capture on b9.
 *
 * `NewMealActivity` decides, in its `TakePicturePreview()` callback, whether `ivMealPhoto`
 * should be shown/announced and whether `ivPhotoPlaceholder` should be visible. That decision
 * is pulled out into [MealPhotoUiState] on purpose: the project has no Hilt+Robolectric
 * harness for instantiating an `@AndroidEntryPoint` Activity in a JVM unit test yet (checked —
 * no `HiltAndroidRule`/custom test runner anywhere in `app/src`), so the Activity wiring itself
 * (registerForActivityResult callbacks, binding.ivMealPhoto.setImageBitmap, view visibility)
 * stays uncovered here and is only verifiable on device per SPEC §8/§12. This class covers the
 * one piece of the change that *is* a pure rule: what state each view should end up in.
 */
class MealPhotoUiStateTest {

    @Test
    fun `with no photo yet, the placeholder is visible and the photo view is decorative`() {
        val state = MealPhotoUiState.forPhoto(hasPhoto = false)

        assertThat(state.placeholderVisible).isTrue()
        assertThat(state.photoImportantForAccessibility).isFalse()
        assertThat(state.photoContentDescription).isNull()
    }

    @Test
    fun `with a captured photo, the placeholder hides and the photo view announces its state`() {
        val state = MealPhotoUiState.forPhoto(hasPhoto = true)

        assertThat(state.placeholderVisible).isFalse()
        assertThat(state.photoImportantForAccessibility).isTrue()
        assertThat(state.photoContentDescription).isEqualTo("Foto do prato tirada")
    }
}
