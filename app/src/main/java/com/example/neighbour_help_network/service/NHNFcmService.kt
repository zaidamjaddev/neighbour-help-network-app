package com.example.neighbour_help_network.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.neighbour_help_network.R
import com.example.neighbour_help_network.data.repository.AuthRepository
import com.example.neighbour_help_network.ui.main.MainActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.*

/**
 * NHNFcmService — Handles incoming Firebase Cloud Messages AND runs a
 * local Firestore listener for nearby help requests.
 *
 * Because the Firebase project is on the Spark (free) plan, Cloud Functions
 * are unavailable. Instead, each device runs a Firestore real-time listener
 * that detects NEW help requests within 10 km and shows a LOCAL notification.
 * When a request is accepted, the requester's device detects the status change
 * via its own Firestore listener and shows a local "accepted" notification.
 *
 * FCM is still used for server-sent notifications if Cloud Functions are later
 * enabled (Blaze plan upgrade), but local listeners act as a free fallback.
 */
class NHNFcmService : FirebaseMessagingService() {

    companion object {
        private const val TAG             = "NHNFcmService"
        const val CHANNEL_ID              = "neighbour_help_alerts"
        const val TYPE_NEW_REQUEST        = "new_request"
        const val TYPE_ACCEPTED           = "request_accepted"
        private const val RADIUS_KM       = 10.0

        /** Haversine distance between two lat/lng pairs in kilometres. */
        fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r    = 6371.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a    = sin(dLat / 2).pow(2) +
                       cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                       sin(dLon / 2).pow(2)
            return r * 2 * atan2(sqrt(a), sqrt(1 - a))
        }

        // Singleton listener references so we can remove them on logout/cleanup
        private var nearbyRequestsListener: ListenerRegistration? = null
        private var myRequestsListener:     ListenerRegistration? = null

        /**
         * Start listening for new nearby help requests and accepted-request events.
         * Call this once the user is logged in and location is known.
         *
         * @param userLat current user latitude
         * @param userLng current user longitude
         * @param context any application context
         */
        fun startLocalListeners(userLat: Double, userLng: Double, context: Context) {
            stopLocalListeners()

            val uid       = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val firestore = FirebaseFirestore.getInstance()

            // ── Listener 1: New open requests ─────────────────────────────────
            // We use a snapshot listener. On each snapshot we check whether
            // any document was ADDED since we started listening. To avoid
            // notifying for pre-existing requests, we track the listener start time.
            val startTime = System.currentTimeMillis()

            nearbyRequestsListener = firestore.collection("help_requests")
                .whereEqualTo("status", "open")
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener

                    for (change in snapshot.documentChanges) {
                        if (change.type != com.google.firebase.firestore.DocumentChange.Type.ADDED) continue

                        val doc        = change.document
                        val posterId   = doc.getString("userId") ?: continue
                        if (posterId == uid) continue            // don't notify self

                        // Only show notification for documents added AFTER we started
                        val ts = doc.getTimestamp("timestamp")?.toDate()?.time ?: 0L
                        if (ts < startTime - 5_000) continue    // 5 s grace window

                        val reqLat = doc.getDouble("latitude") ?: continue
                        val reqLng = doc.getDouble("longitude") ?: continue

                        val dist = haversineKm(userLat, userLng, reqLat, reqLng)
                        if (dist > RADIUS_KM) continue

                        val title = doc.getString("title") ?: "New Help Request"
                        val poster = doc.getString("userDisplayName") ?: "A neighbour"

                        showLocalNotification(
                            context  = context,
                            id       = doc.id.hashCode(),
                            title    = "🆘 New Help Request Nearby (${String.format("%.1f", dist)} km)",
                            body     = "$poster needs help: \"$title\"",
                            type     = TYPE_NEW_REQUEST
                        )
                    }
                }

            // ── Listener 2: My open requests getting accepted ──────────────────
            myRequestsListener = firestore.collection("help_requests")
                .whereEqualTo("userId", uid)
                .whereEqualTo("status", "accepted")
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener

                    for (change in snapshot.documentChanges) {
                        if (change.type != com.google.firebase.firestore.DocumentChange.Type.ADDED &&
                            change.type != com.google.firebase.firestore.DocumentChange.Type.MODIFIED) continue

                        val doc         = change.document
                        val acceptedBy  = doc.getString("acceptedBy") ?: continue
                        if (acceptedBy.isBlank()) continue

                        // Avoid duplicate notifications for same acceptance event
                        val docId = doc.id
                        val prefs = context.getSharedPreferences("nhn_notified", Context.MODE_PRIVATE)
                        val key   = "accepted_$docId"
                        if (prefs.getBoolean(key, false)) continue
                        prefs.edit().putBoolean(key, true).apply()

                        val title = doc.getString("title") ?: "Your request"

                        // Look up the acceptor's name
                        firestore.collection("users").document(acceptedBy)
                            .get()
                            .addOnSuccessListener { helperDoc ->
                                val helperName = helperDoc.getString("displayName") ?: "A volunteer"
                                showLocalNotification(
                                    context = context,
                                    id      = (docId + "_accepted").hashCode(),
                                    title   = "✅ Your Request Was Accepted!",
                                    body    = "$helperName is on their way to help you with \"$title\".",
                                    type    = TYPE_ACCEPTED
                                )
                            }
                    }
                }
        }

        /** Remove all active local Firestore listeners. Call on logout. */
        fun stopLocalListeners() {
            nearbyRequestsListener?.remove()
            nearbyRequestsListener = null
            myRequestsListener?.remove()
            myRequestsListener = null
        }

        /** Shows an Android system notification. */
        fun showLocalNotification(
            context: Context,
            id: Int,
            title: String,
            body: String,
            type: String = ""
        ) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Emergency & Help Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for nearby help requests and request updates"
                    enableLights(true)
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("notification_type", type)
            }
            val pendingIntent = PendingIntent.getActivity(
                context, id, intent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_sos)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .build()

            manager.notify(id, notification)
            Log.d(TAG, "Local notification shown: $title")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FCM message handler (for when Cloud Functions are enabled in future)
    // ─────────────────────────────────────────────────────────────────────────

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        val type = data["type"] ?: ""

        val (title, body) = when (type) {
            TYPE_NEW_REQUEST -> Pair(
                data["title"] ?: "🆘 New Help Request Nearby",
                data["body"]  ?: "A neighbour near you needs assistance!"
            )
            TYPE_ACCEPTED -> Pair(
                data["title"] ?: "✅ Your Request Was Accepted!",
                data["body"]  ?: "A volunteer is on their way to help you."
            )
            else -> Pair(
                remoteMessage.notification?.title ?: data["title"] ?: "Neighbour Help Network",
                remoteMessage.notification?.body  ?: data["body"]  ?: "You have a new notification."
            )
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("notification_type", type)
        }
        showLocalNotification(this, System.currentTimeMillis().toInt(), title, body, type)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Token refresh
    // ─────────────────────────────────────────────────────────────────────────

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed")
        if (FirebaseAuth.getInstance().currentUser == null) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AuthRepository().saveFcmToken(token)
                Log.d(TAG, "FCM token saved to Firestore")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save FCM token: ${e.message}")
            }
        }
    }
}
