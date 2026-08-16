package com.glicokids.prototype.util

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.ContextCompat

object UIHelper {

    enum class GlucoseStatus {
        NA_META, ATENCAO, FORA_DA_META
    }

    fun showToast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun navigateTo(context: Context, destination: Class<*>) {
        val intent = Intent(context, destination)
        context.startActivity(intent)
    }

    /**
     * Module 6 — requirement 2: hands the 7-day summary off to whatever SMS app the user
     * has, through `ACTION_SENDTO` (no `SEND_SMS` permission needed for this path). Returns
     * `false` instead of throwing when the device has no app that can handle it — the
     * caller decides whether/how to tell the user.
     */
    fun sendSmsViaMessagingApp(context: Context, phone: String, message: String): Boolean {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone")).apply {
            putExtra("sms_body", message)
        }
        return startExternalActivity(context, intent)
    }

    /**
     * Module 6 — requirement 4: hands the report off to an e-mail client through
     * `ACTION_SENDTO` with a bare `mailto:` URI — the same technique [sendSmsViaMessagingApp]
     * already uses with `smsto:`. Field regression: a generic `ACTION_SEND` chooser offered
     * WhatsApp, Quick Share and WhatsApp Business alongside Outlook, and every one of those
     * non-mail apps ignored `EXTRA_EMAIL`. `mailto:` filters the chooser down to e-mail
     * clients only, and every one of them honours the recipient/subject/body extras. Plain-text
     * body only — no `FileProvider` attachment, that is out of scope for this module.
     */
    fun sendEmailWithBody(context: Context, recipients: Array<String>, subject: String, body: String): Boolean {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            putExtra(Intent.EXTRA_EMAIL, recipients)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        return startExternalActivity(context, intent)
    }

    /**
     * b22 — dials the caregiver's own phone app for the primary contact's number via
     * `ACTION_DIAL`, which needs no runtime permission because the user still presses
     * call inside the dialer. Returns `false` instead of throwing on a device with no
     * dialer app.
     */
    fun dialPhone(context: Context, phone: String): Boolean {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
        return startExternalActivity(context, intent)
    }

    /**
     * Both communication hand-offs above may be called with a non-[android.app.Activity]
     * context (a `Service`, the `Application` itself) — Android refuses `startActivity`
     * from those without `FLAG_ACTIVITY_NEW_TASK`, so it is added whenever the context
     * given is not already an Activity. Returns `false` instead of throwing when no app
     * on the device can handle the intent.
     */
    private fun startExternalActivity(context: Context, intent: Intent): Boolean {
        return try {
            if (context !is android.app.Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    /**
     * Real camera capture on b9.
     * `NewMealActivity` checks this before launching `TakePicturePreview()`; a denied
     * permission never re-prompts, it just falls back to a toast.
     */
    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * Same reasoning [dialPhone]/[sendSmsViaMessagingApp]
     * already use for external hand-offs: check `resolveActivity` before launching so a
     * device with no camera app gets a toast instead of an `ActivityNotFoundException`.
     */
    fun hasCameraAppAvailable(context: Context): Boolean {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        return intent.resolveActivity(context.packageManager) != null
    }

    fun glucoseStatus(value: Int, min: Int, max: Int): GlucoseStatus {
        return when {
            value < min || value > max -> GlucoseStatus.FORA_DA_META
            value in min..(min + 15) || value in (max - 15)..max -> GlucoseStatus.ATENCAO
            else -> GlucoseStatus.NA_META
        }
    }

    /** Same status colour with reduced opacity — for chip and bar backgrounds. */
    fun withAlpha(color: Int, alpha: Float): Int =
        android.graphics.Color.argb(
            (alpha.coerceIn(0f, 1f) * 255).toInt(),
            android.graphics.Color.red(color),
            android.graphics.Color.green(color),
            android.graphics.Color.blue(color)
        )

    /**
     * Module 6 — field defect fix: [GetDashboardGlucoseDisplayUseCase] lives in `domain/`,
     * a pure-Kotlin layer with no Android runtime, and its plain-JUnit test asserts on this
     * function's output directly. `Color.parseColor` needs a real (or Robolectric-shadowed)
     * Android environment and throws "not mocked" otherwise, so the three status colours are
     * literal ARGB ints instead — numerically identical to `Color.parseColor("#RRGGBB")`
     * (alpha 0xFF prepended), just resolved at compile time rather than parsed at runtime.
     */
    fun getStatusColor(status: GlucoseStatus): Int {
        return when (status) {
            GlucoseStatus.NA_META -> 0xFF00E5B0.toInt() // Teal
            GlucoseStatus.ATENCAO -> 0xFFFFD54F.toInt() // Gold
            GlucoseStatus.FORA_DA_META -> 0xFFC62828.toInt() // Danger
        }
    }
}
