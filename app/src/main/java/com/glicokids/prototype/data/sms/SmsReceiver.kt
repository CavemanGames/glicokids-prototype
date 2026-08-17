package com.glicokids.prototype.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.glicokids.prototype.data.local.GlicoKidsDbHelper
import com.glicokids.prototype.data.model.ReceivedMessage
import com.glicokids.prototype.presentation.parents.ReceivedMessagesActivity
import com.glicokids.prototype.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint

/**
 * Module 6 — b23: the incoming SMS flow. Declared statically in the manifest, not
 * registered at runtime — a dynamic registration stops listening the moment the process
 * dies, defeating the point of an SMS inbox. Guarded by BROADCAST_SMS in the manifest so
 * no other app can forge the SMS_RECEIVED broadcast.
 *
 * [onReceive] only extracts sender and body — [Telephony.Sms.Intents.getMessagesFromIntent]
 * already reassembles a multi-part SMS into the right set of PDUs — and hands off
 * immediately to [handleIncomingMessage], which does the actual work.
 */
@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages.first().originatingAddress ?: return
        val body = messages.joinToString(separator = "") { it.messageBody }

        handleIncomingMessage(context, sender, body, System.currentTimeMillis())
    }

    /**
     * Persists the message, resolves [sender] against [GlicoKidsDbHelper.findContactByPhone]
     * and posts the "message received" notification. Takes [context] explicitly instead of
     * relying on Hilt field injection so it can be exercised directly by a unit test without
     * fabricating a PDU.
     */
    internal fun handleIncomingMessage(context: Context, sender: String, body: String, receivedAt: Long) {
        val dbHelper = GlicoKidsDbHelper(context)
        val contact = dbHelper.findContactByPhone(sender)

        dbHelper.insertReceivedMessage(
            ReceivedMessage(
                senderPhone = sender,
                contactId = contact?.id,
                body = body,
                receivedAt = receivedAt
            )
        )

        val title = if (contact != null) "${contact.name} · ${contact.relationship}" else "Novo número"
        NotificationHelper.notifyMessageReceived(
            context = context,
            destination = ReceivedMessagesActivity::class.java,
            notificationId = receivedAt.toInt(),
            title = title,
            body = body
        )
    }
}
