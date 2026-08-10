package com.glicokids.prototype.util

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Module 6 (Etapa D) — the two pure checks `NewMealActivity` needs before it may launch the
 * system camera (`.artifacts/SPEC-camera-captura-real.md` §3b/§7): `CAMERA` permission granted,
 * and some app on the device resolving the image-capture intent. Both are extracted into
 * [UIHelper] instead of living in the Activity so they are testable without the Activity
 * harness the project does not have (see [MealPhotoUiStateTest] for that same reasoning).
 *
 * Kept apart from [UIHelperTest] on purpose — same reasoning [UIHelperIntentTest] already
 * documents: only this class carries the [RobolectricTestRunner], so [UIHelperTest]'s plain
 * JUnit tests are never put at risk by it.
 */
@RunWith(RobolectricTestRunner::class)
class UIHelperCameraTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `hasCameraPermission returns true once CAMERA is granted`() {
        shadowOf(application).grantPermissions(Manifest.permission.CAMERA)

        assertThat(UIHelper.hasCameraPermission(application)).isTrue()
    }

    @Test
    fun `hasCameraPermission returns false before the user grants CAMERA`() {
        shadowOf(application).denyPermissions(Manifest.permission.CAMERA)

        assertThat(UIHelper.hasCameraPermission(application)).isFalse()
    }

    @Test
    fun `hasCameraAppAvailable returns true when an app resolves the image capture intent`() {
        // The Robolectric sandbox starts with no packages installed besides the app under
        // test, so a fake camera app is registered here to simulate the real-device case
        // where some camera app resolves MediaStore.ACTION_IMAGE_CAPTURE.
        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "com.example.camera"
                name = "CameraActivity"
                applicationInfo = ApplicationInfo().apply { packageName = "com.example.camera" }
            }
        }
        shadowOf(application.packageManager)
            .addResolveInfoForIntent(Intent(MediaStore.ACTION_IMAGE_CAPTURE), resolveInfo)

        assertThat(UIHelper.hasCameraAppAvailable(application)).isTrue()
    }

    @Test
    fun `hasCameraAppAvailable returns false when no app resolves the image capture intent`() {
        // Same technique UIHelperIntentTest uses for "no SMS/e-mail app installed": forces
        // Robolectric to validate resolution against the (empty) test package manager.
        shadowOf(application).checkActivities(true)

        assertThat(UIHelper.hasCameraAppAvailable(application)).isFalse()
    }
}
