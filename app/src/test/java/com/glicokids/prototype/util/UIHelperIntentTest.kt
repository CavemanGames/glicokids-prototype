package com.glicokids.prototype.util

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Module 6 — covers the two [UIHelper] functions that hand communication off to another
 * app (the default SMS app, an e-mail client) through an implicit [Intent].
 *
 * Kept as a separate class on purpose: [UIHelperTest] already has 9 tests of pure glucose-status
 * logic that have nothing to do with Intents. Switching that class to [RobolectricTestRunner]
 * would put all 9 at risk for no reason, so this class alone carries the Robolectric runner
 * and [UIHelperTest] stays exactly as it is.
 */
@RunWith(RobolectricTestRunner::class)
class UIHelperIntentTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `sendSmsViaMessagingApp starts an ACTION_SENDTO intent with the smsto uri and the body extra`() {
        val result = UIHelper.sendSmsViaMessagingApp(application, "11988776543", "GlicoKids: alerta")

        val started = shadowOf(application).nextStartedActivity
        assertThat(started).isNotNull()
        assertThat(started.action).isEqualTo(Intent.ACTION_SENDTO)
        assertThat(started.data).isEqualTo(Uri.parse("smsto:11988776543"))
        assertThat(started.getStringExtra("sms_body")).isEqualTo("GlicoKids: alerta")
        assertThat(result).isTrue()
    }

    @Test
    fun `sendSmsViaMessagingApp returns false without throwing when no app can handle the intent`() {
        // Forces Robolectric to validate resolution against the (empty) test package manager,
        // reproducing the real ActivityNotFoundException a device raises with no SMS app installed.
        shadowOf(application).checkActivities(true)

        val result = UIHelper.sendSmsViaMessagingApp(application, "11988776543", "GlicoKids: alerta")

        assertThat(result).isFalse()
    }

    @Test
    fun `sendEmailWithBody starts an ACTION_SENDTO intent with the mailto uri and the email extras`() {
        // Field regression: a generic ACTION_SEND chooser offered WhatsApp, Quick Share and
        // WhatsApp Business alongside Outlook and ignored EXTRA_EMAIL for every app that was
        // not an e-mail client. ACTION_SENDTO with a bare "mailto:" URI is the same technique
        // sendSmsViaMessagingApp already uses with "smsto:" — it filters the chooser down to
        // e-mail apps only and every one of them honours the recipient/subject/body extras.
        val recipients = arrayOf("ana@example.com", "beto@example.com")

        val result = UIHelper.sendEmailWithBody(
            application,
            recipients,
            "Relatorio GlicoKids - 7 dias",
            "Corpo do relatorio"
        )

        val started = shadowOf(application).nextStartedActivity
        assertThat(started).isNotNull()
        assertThat(started.action).isEqualTo(Intent.ACTION_SENDTO)
        assertThat(started.data).isEqualTo(Uri.parse("mailto:"))
        assertThat(started.getStringArrayExtra(Intent.EXTRA_EMAIL)).asList()
            .containsExactly("ana@example.com", "beto@example.com").inOrder()
        assertThat(started.getStringExtra(Intent.EXTRA_SUBJECT)).isEqualTo("Relatorio GlicoKids - 7 dias")
        assertThat(started.getStringExtra(Intent.EXTRA_TEXT)).isEqualTo("Corpo do relatorio")
        assertThat(result).isTrue()
    }

    @Test
    fun `sendEmailWithBody returns false without throwing when no app can handle the intent`() {
        shadowOf(application).checkActivities(true)

        val result = UIHelper.sendEmailWithBody(
            application,
            arrayOf("ana@example.com"),
            "Relatorio GlicoKids - 7 dias",
            "Corpo do relatorio"
        )

        assertThat(result).isFalse()
    }
}
