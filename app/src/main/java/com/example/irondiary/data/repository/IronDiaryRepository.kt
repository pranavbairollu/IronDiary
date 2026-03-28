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
    suspend fun addTask(userId: String, description: String, reminderTime: Long? = null): String {
        val trimmedDesc = description.trim()
        if (trimmedDesc.isBlank()) throw IllegalArgumentException("Task description cannot be empty")
        if (trimmedDesc.length > 500) throw IllegalArgumentException("Task description too long")

        val taskId = UUID.randomUUID().toString()
        val task = Task(
            docId = taskId,
            description = trimmedDesc,
            createdDate = Timestamp.now(),
            reminderTime = reminderTime,
            updatedAt = Timestamp.now()
        )
        taskDao.insert(task.toEntity(userId, SyncState.PENDING))
        enqueueSync()
        return taskId
    }

    /**
     * Updates task locally as PENDING and schedules sync.
     */
    suspend fun updateTask(task: Task, userId: String) {
        val trimmedDesc = task.description.trim()
        if (trimmedDesc.isBlank()) throw IllegalArgumentException("Task description cannot be empty")
        if (trimmedDesc.length > 500) throw IllegalArgumentException("Task description too long")
        
        val updatedTask = task.copy(description = trimmedDesc, updatedAt = Timestamp.now())
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
        
        // Update all identified entities in a single Room transaction
        taskDao.updateAll(deletedEntities)
        enqueueSync()
    }

    suspend fun deleteAllTasks(userId: String) {
        val tasks = taskDao.getTasksImmediate(userId)
        if (tasks.isEmpty()) return
        
        val deletedEntities = tasks.map {
            it.copy(syncState = SyncState.DELETED, localUpdatedAt = System.currentTimeMillis())
        }
        
        taskDao.updateAll(deletedEntities)
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
        val trimmedSubject = session.subject.trim()
        if (trimmedSubject.isBlank()) throw IllegalArgumentException("Subject cannot be empty")
        if (trimmedSubject.length > 100) throw IllegalArgumentException("Subject cannot exceed 100 characters")
        if (session.duration.isNaN() || session.duration.isInfinite() || session.duration <= 0 || session.duration > 24) {
            throw IllegalArgumentException("Duration must be between 0 and 24 hours")
        }

        val docId = if (session.docId.isBlank()) UUID.randomUUID().toString() else session.docId
        val sessionWithId = session.copy(
            subject = trimmedSubject,
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
        // Repository level validation as a safety net
        if (log.weight != null && (log.weight <= 0 || log.weight > 500)) {
            throw IllegalArgumentException("Weight must be between 0 and 500 kg")
        }
        if (log.notes != null && log.notes.length > 2000) {
            throw IllegalArgumentException("Notes too long")
        }

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

    /**
     * Completely wipes the local Room database and SharedPreferences.
     * This is intended for explicit user sign-out to guarantee no personal data
     * is left lingering on a potentially shared device.
     */
    suspend fun clearAllLocalData() {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            db.clearAllTables()
            context.getSharedPreferences("IronDiaryPrefs", Context.MODE_PRIVATE).edit().clear().apply()
        }
    }
}
