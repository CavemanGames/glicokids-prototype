package com.glicokids.prototype.data.sms

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import com.glicokids.prototype.domain.repository.SmsGateway
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Module 6 — requirement 1: sends the alert/summary SMS through the device's own
 * [SmsManager], the native SMS API the requirement asks for (no third-party app).
 *
 * From API 31 the [SmsManager] comes from the system service rather than [SmsManager.getDefault],
 * which is deprecated precisely because it cannot say which subscription should carry a message on
 * a device with more than one active SIM. Older releases keep the legacy accessor, where it is the
 * only option available.
 *
 * To be accurate about why: this was changed while chasing a send that failed with
 * `RESULT_ERROR_GENERIC_FAILURE` on a dual-SIM device, and **it did not fix that failure** — the
 * same error persisted afterwards, so the cause lies elsewhere, outside this class. The change was
 * kept anyway because targeting a subscription explicitly is the correct API on every release this
 * app supports, not because it repaired anything.
 *
 * A physical device showed "SMS ENVIADO" and wrote the throttle
 * for a message the carrier silently dropped. `SmsManager.sendTextMessage` only confirms the
 * request reached the radio — a carrier rejection, no-service state or invalid PDU is
 * reported later, through the `sentIntent` broadcast, never as a thrown exception. Passing
 * `null` for `sentIntent` (the old code) threw that signal away. This version registers a
 * one-shot receiver under a request-specific action, suspends until that receiver reports
 * the real [Activity.RESULT_OK] (or any other result, treated as failure), and treats a
 * timeout the same as a carrier failure — never as success. Same defensive style as
 * `ReportStorage.saveReportExternally` otherwise: never let a failed send escape as an
 * exception, log it and hand the caller a plain `false`.
 */
@Singleton
class AndroidSmsGateway @Inject constructor(
    @ApplicationContext private val context: Context
) : SmsGateway {

    private val nextRequestId = AtomicInteger(0)

    override suspend fun sendTextMessage(phone: String, message: String): Boolean {
        return try {
            withTimeoutOrNull(SEND_TIMEOUT_MILLIS) {
                awaitSendResult(phone, message)
            } ?: false
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send SMS", e)
            false
        }
    }

    private suspend fun awaitSendResult(phone: String, message: String): Boolean =
        suspendCancellableCoroutine { continuation ->
            // A unique action per send: recipients are sent back-to-back and, with a shared
            // action, the sentIntent broadcast for one send could be picked up by the
            // receiver waiting on a different (unrelated) send.
            val action = "$ACTION_SMS_SENT.${nextRequestId.getAndIncrement()}"

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(receiverContext: Context, intent: Intent) {
                    runCatching { context.unregisterReceiver(this) }
                    // A failed send throws no exception — the carrier's reason only shows
                    // up here, in the sentIntent resultCode. Without logging it, every
                    // failure (no SMS plan, carrier rejection, radio off, no service...)
                    // looks identical and the user only ever sees "could not confirm send".
                    if (resultCode != Activity.RESULT_OK) {
                        // The framework delivers the carrier's own error code under this
                        // key. There is no public constant for it on SmsManager, so the
                        // literal is the only way to read it, and it is absent on most
                        // devices — hence the sentinel default rather than a plain -1.
                        val extraErrorCode = intent.getIntExtra("errorCode", Int.MIN_VALUE)
                        val extraSuffix = if (extraErrorCode != Int.MIN_VALUE) {
                            ", extraErrorCode=$extraErrorCode"
                        } else {
                            ""
                        }
                        Log.w(TAG, "SMS send failed: resultCode=${describeResultCode(resultCode)}$extraSuffix")
                    }
                    if (continuation.isActive) {
                        continuation.resume(resultCode == Activity.RESULT_OK)
                    }
                }
            }

            val filter = IntentFilter(action)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(receiver, filter)
            }

            // Unregister on every exit path this coroutine can take, including the timeout
            // above cancelling it — a leaked receiver here is a leak on every glucose alert.
            continuation.invokeOnCancellation {
                runCatching { context.unregisterReceiver(receiver) }
            }

            try {
                val sentIntent = PendingIntent.getBroadcast(
                    context,
                    REQUEST_CODE,
                    Intent(action).setPackage(context.packageName),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
                )

                smsManager().sendTextMessage(phone, null, message, sentIntent, null)
            } catch (e: Exception) {
                // SmsManager rejects a malformed send (empty address/body) synchronously,
                // before any broadcast is ever coming — nothing will call onReceive to
                // unregister this receiver, so it has to happen here instead.
                runCatching { context.unregisterReceiver(receiver) }
                if (continuation.isActive) {
                    continuation.resumeWithException(e)
                }
            }
        }

    /**
     * From API 31 the system service is bound to whichever subscription the user set as their
     * default for messaging, which is the only way to get this right on a phone with two active
     * SIMs — see this class's own notes on the failure that made this necessary. Below that
     * release the legacy accessor is the only one available.
     */
    private fun smsManager(): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

    // Maps the sentIntent resultCode to its constant name, for logging. SmsManager only
    // exposes these as raw ints, so without this translation every log line would read
    // "resultCode=2" with no indication of what actually went wrong.
    private fun describeResultCode(resultCode: Int): String = when (resultCode) {
        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "RESULT_ERROR_GENERIC_FAILURE"
        SmsManager.RESULT_ERROR_NO_SERVICE -> "RESULT_ERROR_NO_SERVICE"
        SmsManager.RESULT_ERROR_NULL_PDU -> "RESULT_ERROR_NULL_PDU"
        SmsManager.RESULT_ERROR_RADIO_OFF -> "RESULT_ERROR_RADIO_OFF"
        SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "RESULT_ERROR_LIMIT_EXCEEDED"
        else -> "UNKNOWN($resultCode)"
    }

    companion object {
        private const val TAG = "GlicoKids_Sms"
        private const val ACTION_SMS_SENT = "com.glicokids.prototype.action.SMS_SENT"
        private const val REQUEST_CODE = 0

        // How long to wait for the carrier to confirm a send before treating it as a
        // failure. b22 sends to several recipients back-to-back, so this has to be short
        // enough not to stall the whole alert on one unresponsive contact, while staying
        // generous enough to survive a normal carrier round-trip under load: 15s.
        private const val SEND_TIMEOUT_MILLIS = 15_000L
    }
}
