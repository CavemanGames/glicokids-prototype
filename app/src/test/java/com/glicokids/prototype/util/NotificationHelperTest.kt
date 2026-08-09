package com.glicokids.prototype.util

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.glicokids.prototype.presentation.MainActivity
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Module 6 — covers [NotificationHelper.notifyMessageReceived]. The destination Activity is
 * now a typed parameter instead of a class name resolved at runtime, so what matters here is
 * proving the [android.content.Intent] inside the posted notification's `contentIntent`
 * actually points at the class the caller passed in. [MainActivity] stands in for whichever
 * real destination a caller supplies — the function does not care which Activity it is.
 */
@RunWith(RobolectricTestRunner::class)
class NotificationHelperTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val manager =
        application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Before
    fun setup() {
        NotificationHelper.createChannel(application)
    }

    @Test
    fun `notifyMessageReceived posts the notification on the glicokids_comms channel`() {
        NotificationHelper.notifyMessageReceived(
            application,
            MainActivity::class.java,
            1,
            "Nova mensagem",
            "Ana enviou uma mensagem"
        )

        val posted = shadowOf(manager).getNotification(1)

        assertThat(posted).isNotNull()
        assertThat(posted.channelId).isEqualTo(NotificationHelper.CHANNEL_ID)
    }

    @Test
    fun `notifyMessageReceived points the content intent at the class passed as destination`() {
        NotificationHelper.notifyMessageReceived(
            application,
            MainActivity::class.java,
            2,
            "Nova mensagem",
            "Ana enviou uma mensagem"
        )

        val posted = shadowOf(manager).getNotification(2)
        val savedIntent = shadowOf(posted.contentIntent).savedIntent

        assertThat(savedIntent.component?.className).isEqualTo(MainActivity::class.java.name)
    }

    @Test
    fun `notifyMessageReceived carries the title and body into the notification`() {
        NotificationHelper.notifyMessageReceived(
            application,
            MainActivity::class.java,
            3,
            "Nova mensagem",
            "Ana enviou uma mensagem"
        )

        val extras = shadowOf(manager).getNotification(3).extras

        assertThat(extras.getString(Notification.EXTRA_TITLE)).isEqualTo("Nova mensagem")
        assertThat(extras.getString(Notification.EXTRA_TEXT)).isEqualTo("Ana enviou uma mensagem")
    }
}
