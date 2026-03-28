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
        
        val taskId = intent.getStringExtra("TASK_ID") ?: "unknown"
        val taskDescription = intent.getStringExtra("TASK_DESCRIPTION") ?: "You have a task to complete!"

        val notificationIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("FRAGMENT_TO_OPEN", "academics") // Optional: open tasks screen
        }
        
        val requestCode = taskId.hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Task Reminder")
            .setContentText(taskDescription)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(requestCode, notification)
        Log.d("NotificationReceiver", "Task reminder notification posted for $taskId: $taskDescription")
    }
}
