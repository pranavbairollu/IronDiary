package com.example.irondiary.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar
import com.example.irondiary.worker.RescheduleWorker

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || 
            action == Intent.ACTION_TIME_CHANGED || 
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            
            // NotificationHelper.scheduleDailyReminder(context) // Removed global daily reminders
            
            val workRequest = androidx.work.OneTimeWorkRequestBuilder<RescheduleWorker>()
                .setConstraints(androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.NOT_REQUIRED)
                    .build())
                .build()
            
            androidx.work.WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}
