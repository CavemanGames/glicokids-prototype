package com.glicokids.prototype.data.sms

import android.telephony.SmsManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
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

    @Test
    fun `sends the exact phone number and message text through SmsManager and returns true`() {
        val result = gateway.sendTextMessage("11988776543", "GlicoKids: Lucas M. esta com 54 mg-dL")

        val params = shadowOf(SmsManager.getDefault()).lastSentTextMessageParams
        assertThat(params).isNotNull()
        assertThat(params.destinationAddress).isEqualTo("11988776543")
        assertThat(params.text).isEqualTo("GlicoKids: Lucas M. esta com 54 mg-dL")
        assertThat(result).isTrue()
    }

    @Test
    fun `returns false without throwing when SmsManager rejects an empty destination address`() {
        val result = gateway.sendTextMessage("", "GlicoKids: alerta")

        assertThat(result).isFalse()
    }

    @Test
    fun `returns false without throwing when SmsManager rejects an empty message body`() {
        val result = gateway.sendTextMessage("11988776543", "")

        assertThat(result).isFalse()
    }
}
