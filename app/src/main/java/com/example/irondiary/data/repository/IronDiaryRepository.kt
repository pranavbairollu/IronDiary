package com.example.irondiary.data.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.irondiary.data.local.IronDiaryDatabase
import com.example.irondiary.data.local.SyncState
import com.example.irondiary.data.local.mapper.toDomainModel
import com.example.irondiary.data.local.mapper.toEntity
import com.example.irondiary.data.model.Task
import com.example.irondiary.worker.SyncWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import com.google.firebase.Timestamp

/**
 * Single source of truth for the application's data. 
 * ViewModels should ONLY interact with this layer.
 */
class IronDiaryRepository(private val context: Context) {

    private val db = IronDiaryDatabase.getDatabase(context)
    private val taskDao = db.taskDao()

    // --------------------------------------------------------
    // TASKS
    // --------------------------------------------------------

    /**
     * Exposes a stream of local tasks, automatically updating UI when DB changes.
     */
    fun getTasks(userId: String): Flow<List<Task>> {
        return taskDao.getTasksForUser(userId).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    /**
     * Inserts task locally as PENDING and schedules sync.
     */
    suspend fun addTask(task: Task, userId: String) {
        val taskId = if (task.docId.isBlank()) UUID.randomUUID().toString() else task.docId
        val taskWithId = task.copy(
            docId = taskId, 
            updatedAt = Timestamp.now()
        )
        taskDao.insert(taskWithId.toEntity(userId = userId, syncState = SyncState.PENDING))
        enqueueSync()
    }

    /**
     * Updates task locally as PENDING and schedules sync.
     */
    suspend fun updateTask(task: Task, userId: String) {
        val updatedTask = task.copy(updatedAt = Timestamp.now())
        taskDao.insert(updatedTask.toEntity(userId = userId, syncState = SyncState.PENDING))
        enqueueSync()
    }

    /**
     * Soft-deletes task locally as DELETED and schedules sync.
     */
    suspend fun deleteTask(taskId: String, userId: String) {
        val existing = taskDao.getTaskById(taskId, userId)
        existing?.let {
            val deletedEntity = it.copy(
                syncState = SyncState.DELETED,
                localUpdatedAt = System.currentTimeMillis()
            )
            taskDao.update(deletedEntity)
            enqueueSync()
        }
    }

    /**
     * Triggers WorkManager to sync pending changes to Firebase.
     */
    fun enqueueSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()

        // REPLACE policy ensures rapid successive clicks don't spawn 100 workers, 
        // just restarts the sync timer.
        WorkManager.getInstance(context).enqueueUniqueWork(
            "IronDiarySyncWork",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }
}
