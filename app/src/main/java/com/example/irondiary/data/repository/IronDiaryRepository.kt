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
import com.example.irondiary.data.model.StudySession
import com.example.irondiary.data.DailyLog
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
    private val studyDao = db.studySessionDao()
    private val logDao = db.dailyLogDao()

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

    suspend fun clearCompletedTasks(userId: String) {
        val tasks = taskDao.getTasksImmediate(userId).filter { it.completed }
        if (tasks.isEmpty()) return
        
        val deletedEntities = tasks.map {
            it.copy(syncState = SyncState.DELETED, localUpdatedAt = System.currentTimeMillis())
        }
        
        // Iterative update acts effectively as batch-update thanks to Room transactions
        deletedEntities.forEach { taskDao.update(it) }
        enqueueSync()
    }

    suspend fun deleteAllTasks(userId: String) {
        val tasks = taskDao.getTasksImmediate(userId)
        if (tasks.isEmpty()) return
        
        val deletedEntities = tasks.map {
            it.copy(syncState = SyncState.DELETED, localUpdatedAt = System.currentTimeMillis())
        }
        
        deletedEntities.forEach { taskDao.update(it) }
        enqueueSync()
    }

    // --------------------------------------------------------
    // STUDY SESSIONS
    // --------------------------------------------------------

    fun getStudySessions(userId: String): Flow<List<StudySession>> {
        return studyDao.getSessionsForUser(userId).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    suspend fun addStudySession(session: StudySession, userId: String) {
        val docId = if (session.docId.isBlank()) UUID.randomUUID().toString() else session.docId
        val sessionWithId = session.copy(
            docId = docId,
            updatedAt = Timestamp.now()
        )
        studyDao.insert(sessionWithId.toEntity(userId, SyncState.PENDING))
        enqueueSync()
    }

    suspend fun deleteStudySession(docId: String, userId: String) {
        val existing = studyDao.getSessionById(docId, userId)
        existing?.let {
            studyDao.update(it.copy(syncState = SyncState.DELETED, localUpdatedAt = System.currentTimeMillis()))
            enqueueSync()
        }
    }

    // --------------------------------------------------------
    // DAILY LOGS
    // --------------------------------------------------------

    fun getDailyLogs(userId: String): Flow<Map<String, DailyLog>> {
        return logDao.getAllLogs(userId).map { entities ->
            entities.associate { it.date to it.toDomainModel() }
        }
    }

    fun getDailyLogForDate(date: String, userId: String): Flow<DailyLog?> {
        return logDao.getLogByDate(date, userId).map { it?.toDomainModel() }
    }

    suspend fun saveDailyLog(log: DailyLog, userId: String) {
        val logWithUpdate = log.copy(updatedAt = Timestamp.now())
        logDao.insert(logWithUpdate.toEntity(userId, SyncState.PENDING))
        enqueueSync()
    }

    fun getWeightData(userId: String): Flow<List<DailyLog>> {
        return logDao.getWeightLogs(userId).map { entities ->
            entities.map { it.toDomainModel() }
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
        android.util.Log.d("IronDiaryRepo", "Enqueuing SyncWorker (ExistingWorkPolicy.REPLACE)")
        WorkManager.getInstance(context).enqueueUniqueWork(
            "IronDiarySyncWork",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }
}
