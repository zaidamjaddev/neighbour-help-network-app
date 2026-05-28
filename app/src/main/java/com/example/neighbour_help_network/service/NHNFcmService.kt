package com.example.neighbour_help_network.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.neighbour_help_network.R
import com.example.neighbour_help_network.ui.main.MainActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * NHNFcmService — Handles incoming Firebase Cloud Messages.
 *
 * This service receives notifications even when the app is in the background
 * or closed. It manages the notification channel creation and display.
 */
class NHNFcmService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Handle data payload or notification payload
        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "New Help Request"
        val body = remoteMessage.notification?.body ?: remoteMessage.data["body"] ?: "A neighbour needs assistance near you."

        showNotification(title, body)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Here you would typically send the token to your backend/Firestore
        // to target this specific device for notifications.
    }

    private fun showNotification(title: String, body: String) {
        val channelId = "neighbour_help_alerts"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create the NotificationChannel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Emergency & Help Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for nearby help requests and SOS alerts"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_sos) // Using existing SOS icon
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}
