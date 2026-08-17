package com.glicokids.prototype.data.sms

import android.app.Activity
import android.os.Looper
import android.telephony.SmsManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Module 6 — proves [AndroidSmsGateway] hands the exact phone number and text to
 * [SmsManager] and never lets a failed send reach the caller as an exception (same
 * defensive style as `ReportStorage.saveReportExternally`: catch, log, return false).
 *
 * The failure cases below do not need a hand-rolled shadow: [org.robolectric.shadows.ShadowSmsManager]
 * already mirrors the real `SmsManager` contract and throws `IllegalArgumentException` for
 * an empty destination address or an empty message body — the same failure a real device
 * raises for a malformed send.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidSmsGatewayTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val gateway = AndroidSmsGateway(context)

    // sendTextMessage suspends and waits for the sentIntent broadcast, which
    // ShadowSmsManager records but never fires on its own (unlike a real device). This launch
    // uses Dispatchers.Unconfined the same way GlucoseAlertViewModelTest does for its own send
    // path: it runs eagerly on this thread up to the first real suspension point — which lands
    // right after SmsManager.sendTextMessage records the params below — then hands control
    // straight back here so the test can inspect those params and simulate the carrier's reply.
    /**
     * The gateway asks the system service for its [SmsManager] from API 31 onward, since the
     * legacy accessor cannot target a subscription on a device with more than one SIM.
     * Robolectric runs at the project's target SDK, so the assertions have to look at the manager
     * the gateway actually used, not the legacy one.
     */
    private fun sentThrough(): SmsManager = context.getSystemService(SmsManager::class.java)

    private fun launchSend(phone: String, message: String, onResult: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.Unconfined).launch {
            onResult(gateway.sendTextMessage(phone, message))
        }
    }

    @Test
    fun `sends the exact phone number and message text through SmsManager and returns true`() {
        var result: Boolean? = null
        launchSend("11988776543", "GlicoKids: Lucas M. esta com 54 mg-dL") { result = it }

        val params = shadowOf(sentThrough()).lastSentTextMessageParams
        assertThat(params).isNotNull()
        assertThat(params.destinationAddress).isEqualTo("11988776543")
        assertThat(params.text).isEqualTo("GlicoKids: Lucas M. esta com 54 mg-dL")

        // Simulate the carrier confirming delivery through the sentIntent — the real signal
        // AndroidSmsGateway waits for; the shadow above only records the intent, it never
        // fires it. The receiver is registered on the main looper, which Robolectric leaves
        // paused by default, so the broadcast only actually reaches it once idled.
        params.sentIntent.send(Activity.RESULT_OK)
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(result).isTrue()
    }

    @Test
    fun `returns false without throwing when SmsManager rejects an empty destination address`() {
        // SmsManager throws synchronously here, before any broadcast is ever coming, so the
        // coroutine resolves without a real suspension — a plain runBlocking is enough.
        val result = runBlocking { gateway.sendTextMessage("", "GlicoKids: alerta") }

        assertThat(result).isFalse()
    }

    @Test
    fun `returns false without throwing when SmsManager rejects an empty message body`() {
        val result = runBlocking { gateway.sendTextMessage("11988776543", "") }

        assertThat(result).isFalse()
    }

    // --- Field regression found on a physical device: it showed "SMS ENVIADO" and
    // wrote the throttle for a message the carrier silently dropped. `sendTextMessage` is
    // asynchronous: no exception at the call site only means the request reached the radio,
    // not that the carrier accepted it. A carrier rejection, no-service state or invalid PDU
    // is reported later through `sentIntent`, never as a thrown exception. Passing `null` for
    // `sentIntent` (today's code) throws that signal away and forces the caller to treat
    // "no exception" as "delivered", which is exactly the false positive the field test found.

    @Test
    fun `registers a non-null sentIntent so a carrier failure can be reported back instead of assumed away`() {
        launchSend("11988776543", "GlicoKids: alerta") { }

        val params = shadowOf(sentThrough()).lastSentTextMessageParams
        assertThat(params.sentIntent).isNotNull()

        // Resolve the pending send instead of leaving it suspended past the end of the test.
        params.sentIntent.send(Activity.RESULT_OK)
        shadowOf(Looper.getMainLooper()).idle()
    }
}
