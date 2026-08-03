package g.p.cbb.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import g.p.cbb.MainActivity
import g.p.cbb.R

object NotificationHelper {
    private const val CHANNEL_ID = "cloud_updates_channel"
    private const val CHANNEL_NAME = "Cloud Sync & Updates"
    private const val SYNC_NOTIFICATION_ID = 1001

    private var lastMessage: String? = null
    private var updateCount: Int = 0
    private val activeMessages = mutableListOf<String>()

    @Synchronized
    fun showSyncUpdateNotification(context: Context, title: String, message: String) {
        val prefs = context.getSharedPreferences("cbb_settings", Context.MODE_PRIVATE)
        val notificationsEnabled = prefs.getBoolean("notifications_enabled", true)
        if (!notificationsEnabled) return

        val cleanMessage = message.trim()
        if (cleanMessage.isEmpty()) return

        // Deduplication: Suppress re-sending identical notification message
        if (cleanMessage == lastMessage) {
            return
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for database updates synced from collaborators"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Clubbing: Increment count and append message to active summary list
        updateCount++
        lastMessage = cleanMessage
        if (activeMessages.size >= 5) {
            activeMessages.removeAt(0)
        }
        activeMessages.add(cleanMessage)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val displayTitle = if (updateCount > 1) "Udaari Sync ($updateCount updates)" else title

        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle(displayTitle)
        activeMessages.forEach { msg ->
            inboxStyle.addLine(msg)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(displayTitle)
            .setContentText(cleanMessage)
            .setNumber(updateCount)
            .setOnlyAlertOnce(true)
            .setStyle(inboxStyle)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(SYNC_NOTIFICATION_ID, notification)
    }

    @Synchronized
    fun resetNotificationCount(context: Context? = null) {
        lastMessage = null
        updateCount = 0
        activeMessages.clear()
        context?.let {
            val notificationManager = it.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(SYNC_NOTIFICATION_ID)
        }
    }
}
