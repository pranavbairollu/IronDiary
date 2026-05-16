package com.example.irondiary.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.irondiary.data.local.IronDiaryDatabase
import com.example.irondiary.util.NotificationHelper

/**
 * A background worker responsible for rescheduling all pending task reminders.
 * This is typically triggered after a device reboot or application update
 * to ensure that AlarmManager alarms (which are cleared on reboot) are restored.
 */
class RescheduleWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("RescheduleWorker", "Starting task reminder rescheduling...")
        
        return try {
            val database = IronDiaryDatabase.getDatabase(applicationContext)
            val taskDao = database.taskDao()
            
            // Fetch all pending tasks that have a reminder set
            // In a real scenario, we might want to filter only for future reminders,
            // but NotificationHelper.scheduleTaskReminder already handles past times by moving them to tomorrow.
            val pendingTasksWithReminders = taskDao.getPendingTasksWithReminders()
            
            Log.d("RescheduleWorker", "Found ${pendingTasksWithReminders.size} tasks to reschedule")
            
            pendingTasksWithReminders.forEach { task ->
                task.reminderTime?.let { time ->
                    NotificationHelper.scheduleTaskReminder(
                        applicationContext,
                        task.id,
                        task.description,
                        time
                    )
                }
            }
            
            Log.d("RescheduleWorker", "Rescheduling complete.")
            Result.success()
        } catch (e: Exception) {
            Log.e("RescheduleWorker", "Failed to reschedule tasks", e)
            Result.retry()
        }
    }
}
