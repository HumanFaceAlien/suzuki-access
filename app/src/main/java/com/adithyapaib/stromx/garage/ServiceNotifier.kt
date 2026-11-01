package com.adithyapaib.stromx.garage

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.adithyapaib.stromx.MainActivity
import com.adithyapaib.stromx.R
import java.text.NumberFormat

/** Posts one notification when a service item becomes due soon or overdue. */
class ServiceNotifier(private val context: Context) {

    init {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Service reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When a service item on your bike is due, based on its odometer."
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(FUEL_CHANNEL_ID, "Low fuel", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "When the estimated range drops low, with a shortcut to nearby petrol pumps."
            }
        )
    }

    /** "About 35 km of fuel left", with a Maps search for petrol pumps near you. */
    fun notifyLowRange(rangeKm: Int, litres: Double) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val pumps = PendingIntent.getActivity(
            context,
            1,
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=petrol%20pump")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val open = PendingIntent.getActivity(
            context,
            2,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, FUEL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_service)
            .setContentTitle("About $rangeKm km of fuel left")
            .setContentText(String.format(java.util.Locale.getDefault(), "Estimated %.1f L in the tank.", litres))
            .setContentIntent(open)
            .addAction(0, "Nearest pump", pumps)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(FUEL_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post; nothing to do.
        }
    }

    fun notify(status: MaintenanceStatus) {
        val manager = NotificationManagerCompat.from(context)
        // Respects both the runtime permission and the rider switching the channel off.
        if (!manager.areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val title = if (status.state == DueState.Due) "${status.item.name} is due" else "${status.item.name} due soon"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_service)
            .setContentTitle(title)
            .setContentText(describe(status))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(NOTIFICATION_BASE + status.item.id.toInt(), notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post; nothing to do.
        }
    }

    companion object {
        private const val CHANNEL_ID = "service"
        private const val NOTIFICATION_BASE = 4_000
        private const val FUEL_CHANNEL_ID = "fuel"
        private const val FUEL_NOTIFICATION_ID = 3_999

        /** "Due in 240 km or 5 days", "Overdue by 120 km". Shared with the Service screen. */
        fun describe(status: MaintenanceStatus): String {
            val km = status.remainingKm
            val days = status.remainingDays
            val number = NumberFormat.getIntegerInstance()
            val parts = buildList {
                if (km != null) add(if (km < 0) "${number.format(-km)} km" else "${number.format(km)} km")
                if (days != null) add(if (days < 0) "${-days} days" else "$days days")
            }
            return when {
                status.state == DueState.NotStarted -> "Mark it done once to start the countdown."
                status.state == DueState.Due -> {
                    val over = buildList {
                        if (km != null && km < 0) add("${number.format(-km)} km")
                        if (days != null && days < 0) add("${-days} days")
                    }
                    if (over.isEmpty()) "Due now." else "Overdue by ${over.joinToString(" and ")}."
                }
                else -> "Due in ${parts.joinToString(" or ")}."
            }
        }
    }
}
