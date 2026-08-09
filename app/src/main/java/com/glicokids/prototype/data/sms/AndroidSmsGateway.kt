package com.glicokids.prototype.data.sms

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import com.glicokids.prototype.domain.repository.SmsGateway
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Module 6 — requirement 1: sends the alert/summary SMS through the device's own
 * [SmsManager], the native SMS API the requirement asks for (no third-party app).
 *
 * [SmsManager.getDefault] is deprecated in favor of a subscription-aware overload aimed
 * at multi-SIM devices; that overload needs a subscription id this app has no meaningful
 * way to pick, and Robolectric's shadow only mirrors the deprecated call anyway. A single
 * code path that works across this project's whole SDK range — and that the test suite
 * can actually exercise — is worth more here than silencing a deprecation warning, so this
 * stays on [SmsManager.getDefault] on purpose instead of branching on `Build.VERSION.SDK_INT`.
 *
 * Same defensive style as `ReportStorage.saveReportExternally`: never let a failed send
 * escape as an exception, log it and hand the caller a plain `false`.
 */
@Singleton
class AndroidSmsGateway @Inject constructor(
    @ApplicationContext private val context: Context
) : SmsGateway {

    override fun sendTextMessage(phone: String, message: String): Boolean {
        return try {
            @Suppress("DEPRECATION")
            SmsManager.getDefault().sendTextMessage(phone, null, message, null, null)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send SMS", e)
            false
        }
    }

    companion object {
        private const val TAG = "GlicoKids_Sms"
    }
}
