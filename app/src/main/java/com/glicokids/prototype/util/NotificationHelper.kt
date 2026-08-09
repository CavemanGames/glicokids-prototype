package com.glicokids.prototype.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.glicokids.prototype.R

/**
 * Module 6 — requirement 3: posts the "message received" notification (b23). Kept apart
 * from [UIHelper], which hands communication off to other apps — the notification channel
 * and the incoming-message alert are a different responsibility.
 */
object NotificationHelper {

    const val CHANNEL_ID = "glicokids_comms"

    /** Call once, from `GlicoKidsApplication.onCreate()` — creating an existing channel is a no-op. */
    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Mensagens da Rede de Apoio",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Avisos de mensagens recebidas da Rede de Apoio"
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    fun notifyMessageReceived(context: Context, notificationId: Int, title: String, body: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_signal)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(receivedMessagesPendingIntent(context, notificationId))
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
    }

    /**
     * `ReceivedMessagesActivity` (b23) does not exist yet, so it cannot be referenced as a
     * Kotlin class here without breaking today's build. Targeting it by its fully-qualified
     * name through [Intent.setClassName] compiles regardless — the Intent is only resolved
     * at launch time, not at compile time — so this line needs no change the day that
     * Activity is added under this exact package; it just starts resolving.
     */
    private fun receivedMessagesPendingIntent(context: Context, requestCode: Int): PendingIntent {
        val openMessages = Intent().setClassName(context.packageName, RECEIVED_MESSAGES_ACTIVITY)
        return PendingIntent.getActivity(
            context,
            requestCode,
            openMessages,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private const val RECEIVED_MESSAGES_ACTIVITY =
        "com.glicokids.prototype.presentation.parents.ReceivedMessagesActivity"
}
