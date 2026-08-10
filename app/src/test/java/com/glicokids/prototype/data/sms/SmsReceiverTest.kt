package com.glicokids.prototype.data.sms

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.glicokids.prototype.data.local.GlicoKidsDbHelper
import com.glicokids.prototype.data.model.Contact
import com.glicokids.prototype.util.NotificationHelper
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Module 6 — b23, the incoming SMS flow. [SmsReceiver.onReceive] is not exercised here —
 * fabricating a `SMS_RECEIVED` PDU is out of scope for a unit test — only the function it
 * delegates to, [SmsReceiver.handleIncomingMessage], which is what actually writes to
 * `received_messages`, resolves the sender against [GlicoKidsDbHelper.findContactByPhone]
 * and posts the notification.
 */
@RunWith(RobolectricTestRunner::class)
class SmsReceiverTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val manager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private lateinit var dbHelper: GlicoKidsDbHelper
    private val receiver = SmsReceiver()

    private val receivedAt = 1_700_000_000_000L

    private data class ReceivedMessageRow(
        val senderPhone: String,
        val contactId: Long?,
        val body: String,
        val receivedAt: Long
    )

    @Before
    fun setup() {
        dbHelper = GlicoKidsDbHelper(context)
        NotificationHelper.createChannel(context)
    }

    @After
    fun tearDown() {
        dbHelper.close()
    }

    private fun insertContact(name: String, relationship: String, phone: String): Long =
        dbHelper.insertContact(
            Contact(
                name = name,
                relationship = relationship,
                phone = phone,
                email = null,
                receivesAlert = true,
                receivesReport = false,
                isPrimary = false,
                createdAt = receivedAt
            )
        )

    /** Reads `received_messages` straight from SQLite — no dbHelper method exists for it yet. */
    private fun queryReceivedMessages(): List<ReceivedMessageRow> {
        val out = mutableListOf<ReceivedMessageRow>()
        dbHelper.readableDatabase.rawQuery(
            "SELECT sender_phone, contact_id, body, received_at FROM received_messages",
            null
        ).use { c ->
            while (c.moveToNext()) {
                out += ReceivedMessageRow(
                    senderPhone = c.getString(0),
                    contactId = if (c.isNull(1)) null else c.getLong(1),
                    body = c.getString(2),
                    receivedAt = c.getLong(3)
                )
            }
        }
        return out
    }

    private fun notificationText(notification: Notification): String =
        "${notification.extras.getString(Notification.EXTRA_TITLE)} " +
            "${notification.extras.getString(Notification.EXTRA_TEXT)}"

    @Test
    fun `records the incoming message in received_messages`() {
        receiver.handleIncomingMessage(context, "11988776543", "Tudo bem por ai?", receivedAt)

        val rows = queryReceivedMessages()
        assertThat(rows).hasSize(1)
        assertThat(rows.single().senderPhone).isEqualTo("11988776543")
        assertThat(rows.single().body).isEqualTo("Tudo bem por ai?")
        assertThat(rows.single().receivedAt).isEqualTo(receivedAt)
    }

    @Test
    fun `known sender resolves name and relationship into the notification`() {
        insertContact("Ana", "Mae", "11988776543")

        receiver.handleIncomingMessage(context, "11988776543", "Cheguei em casa", receivedAt)

        val posted = shadowOf(manager).allNotifications.single()
        val text = notificationText(posted)
        assertThat(text).contains("Ana")
        assertThat(text).contains("Mae")
    }

    @Test
    fun `known sender links the stored contact_id`() {
        val contactId = insertContact("Ana", "Mae", "11988776543")

        receiver.handleIncomingMessage(context, "11988776543", "Cheguei em casa", receivedAt)

        assertThat(queryReceivedMessages().single().contactId).isEqualTo(contactId)
    }

    @Test
    fun `unknown sender stores a null contact_id and still notifies`() {
        receiver.handleIncomingMessage(context, "11900000000", "Quem e voce?", receivedAt)

        assertThat(queryReceivedMessages().single().contactId).isNull()
        assertThat(shadowOf(manager).allNotifications).hasSize(1)
    }

    @Test
    fun `posts the notification on the glicokids_comms channel`() {
        receiver.handleIncomingMessage(context, "11988776543", "Oi", receivedAt)

        val posted = shadowOf(manager).allNotifications.single()
        assertThat(posted.channelId).isEqualTo(NotificationHelper.CHANNEL_ID)
    }

    @Test
    fun `matches the stored contact even when the incoming phone is formatted differently`() {
        insertContact("Beto", "Pai", "(11) 98877-6543")

        receiver.handleIncomingMessage(context, "+5511988776543", "Chegando", receivedAt)

        assertThat(queryReceivedMessages().single().contactId).isNotNull()
        val posted = shadowOf(manager).allNotifications.single()
        assertThat(notificationText(posted)).contains("Beto")
    }
}
