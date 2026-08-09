package com.glicokids.prototype.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

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
     * Module 6 — requirement 4: hands the report off to an e-mail client via `ACTION_SEND`,
     * wrapped in a chooser so the user always picks which app opens it. Plain-text body only —
     * no `FileProvider` attachment, that is out of scope for this module.
     */
    fun sendEmailWithBody(context: Context, recipients: Array<String>, subject: String, body: String): Boolean {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, recipients)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        return startExternalActivity(context, Intent.createChooser(intent, null))
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

    fun getStatusColor(status: GlucoseStatus): Int {
        return when (status) {
            GlucoseStatus.NA_META -> android.graphics.Color.parseColor("#00E5B0") // Teal
            GlucoseStatus.ATENCAO -> android.graphics.Color.parseColor("#FFD54F") // Gold
            GlucoseStatus.FORA_DA_META -> android.graphics.Color.parseColor("#C62828") // Danger
        }
    }
}
