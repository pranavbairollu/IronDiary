package com.example.irondiary.util

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.irondiary.MainActivity
import com.example.irondiary.R
import android.util.Log

class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!NotificationHelper.hasNotificationPermission(context)) {
            Log.w("NotificationReceiver", "Notification permission not granted. Skipping reminder.")
            return
        }

        // Ensure channel is created/exists right before posting
        NotificationHelper.createNotificationChannel(context)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notificationIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Log Your Day")
            .setContentText("Don't forget to log your activities for the day!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NotificationHelper.NOTIFICATION_ID, notification)
        Log.d("NotificationReceiver", "Daily reminder notification posted.")

        // Reschedule the alarm for the next day, crucial for exact alarms that don't repeat automatically
        NotificationHelper.scheduleDailyReminder(context)
    }
}
