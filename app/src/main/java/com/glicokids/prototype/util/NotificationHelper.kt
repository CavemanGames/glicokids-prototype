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

    /**
     * Posts the "message received" notification (b23) on [CHANNEL_ID], tapping it opens
     * [destination]. The screen to open is not this object's call — whichever caller
     * receives the message (SMS, in-app event) is the one who knows where the user should
     * land, so it passes the target in the same way [UIHelper.navigateTo] does.
     */
    fun notifyMessageReceived(
        context: Context,
        destination: Class<*>,
        notificationId: Int,
        title: String,
        body: String
    ) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_signal)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(destinationPendingIntent(context, destination, notificationId))
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
    }

    private fun destinationPendingIntent(
        context: Context,
        destination: Class<*>,
        requestCode: Int
    ): PendingIntent {
        val openDestination = Intent(context, destination)
        return PendingIntent.getActivity(
            context,
            requestCode,
            openDestination,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
