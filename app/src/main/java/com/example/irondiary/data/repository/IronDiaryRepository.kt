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
 * The central repository that mediates between the local Room database (SSOT)
 * and the remote Firestore sync layer.
 *
 * All data operations for Tasks, StudySessions, and DailyLogs are routed through
 * this class to ensure consistent offline-first behavior and proper sync status tracking.
 */
class IronDiaryRepository(val context: android.content.Context) {

    private val db = com.example.irondiary.data.local.IronDiaryDatabase.getDatabase(context)
    private val taskDao = db.taskDao()
    private val studySessionDao = db.studySessionDao()
    private val dailyLogDao = db.dailyLogDao()

    // --------------------------------------------------------
    // TASKS
    // --------------------------------------------------------

    /**
     * Retrieves all tasks for the given user, ordered by creation date descending.
     * Observed as a reactive Flow from the local database.
     */
    fun getTasks(userId: String): Flow<List<Task>> = taskDao.getTasksForUser(userId).map { entities ->
        entities.map { it.toDomainModel() }
    }

    /**
     * Inserts a new task locally with PENDING sync status and triggers a sync work.
     */
    suspend fun addTask(userId: String, description: String) {
        val task = Task(
            docId = UUID.randomUUID().toString(),
            description = description,
            createdDate = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
        taskDao.insert(task.toEntity(userId, SyncState.PENDING))
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
        return studySessionDao.getSessionsForUser(userId).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    suspend fun addStudySession(session: StudySession, userId: String) {
        val docId = if (session.docId.isBlank()) UUID.randomUUID().toString() else session.docId
        val sessionWithId = session.copy(
            docId = docId,
            updatedAt = Timestamp.now()
        )
        studySessionDao.insert(sessionWithId.toEntity(userId, SyncState.PENDING))
        enqueueSync()
    }

    suspend fun deleteStudySession(docId: String, userId: String) {
        val existing = studySessionDao.getSessionById(docId, userId)
        existing?.let {
            studySessionDao.update(it.copy(syncState = SyncState.DELETED, localUpdatedAt = System.currentTimeMillis()))
            enqueueSync()
        }
    }

    // --------------------------------------------------------
    // DAILY LOGS
    // --------------------------------------------------------

    fun getDailyLogs(userId: String): Flow<Map<String, DailyLog>> {
        return dailyLogDao.getAllLogs(userId).map { entities ->
            entities.associate { it.date to it.toDomainModel() }
        }
    }

    fun getDailyLogForDate(date: String, userId: String): Flow<DailyLog?> {
        return dailyLogDao.getLogByDate(date, userId).map { it?.toDomainModel() }
    }

    suspend fun saveDailyLog(log: DailyLog, userId: String) {
        val logWithUpdate = log.copy(updatedAt = Timestamp.now())
        dailyLogDao.insert(logWithUpdate.toEntity(userId, SyncState.PENDING))
        enqueueSync()
    }

    fun getWeightData(userId: String): Flow<List<DailyLog>> {
        return dailyLogDao.getWeightLogs(userId).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }


    /**
     * Triggers the background [SyncWorker] to process pending local changes.
     */
    fun enqueueSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "IronDiarySync",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }
}
