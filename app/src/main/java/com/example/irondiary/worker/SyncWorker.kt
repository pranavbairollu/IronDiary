package com.example.irondiary.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.irondiary.data.DailyLog
import com.example.irondiary.data.local.IronDiaryDatabase
import com.example.irondiary.data.local.SyncState
import com.example.irondiary.data.local.mapper.toDomainModel
import com.example.irondiary.data.local.mapper.toEntity
import com.example.irondiary.data.model.Task
import com.example.irondiary.data.model.StudySession
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * A background worker that synchronizes local Room data with Firebase Firestore.
 *
 * The sync process is split into three isolated domains: Tasks, Study Sessions, and Daily Logs.
 * It uses a timestamp-based conflict resolution strategy (Last Write Wins) with a safe-guard for
 * newer local changes.
 */
class SyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    /**
     * Entry point for the work manager. Executes the sync tasks sequentially for all domains.
     */
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Log.w("SyncWorker", "User not authenticated. Sync aborted.")
            return@withContext Result.failure()
        }
        val db = IronDiaryDatabase.getDatabase(applicationContext)
        val firestore = FirebaseFirestore.getInstance()

        setProgress(workDataOf("status" to "Syncing tasks..."))
        val tasksSuccess = syncTasks(db, firestore, userId)
        
        setProgress(workDataOf("status" to "Syncing study sessions..."))
        val studySuccess = syncStudySessions(db, firestore, userId)
        
        setProgress(workDataOf("status" to "Syncing daily logs..."))
        val logsSuccess = syncDailyLogs(db, firestore, userId)

        if (tasksSuccess && studySuccess && logsSuccess) {
            Result.success()
        } else {
            Result.retry()
        }
    }

    private suspend fun syncTasks(db: IronDiaryDatabase, firestore: FirebaseFirestore, userId: String): Boolean {
        val taskDao = db.taskDao()
        val tasksCollection = firestore.collection("users").document(userId).collection("tasks")

        return try {
            // 1. Fetch Remote
            val remoteTasks = tasksCollection.get().await().toObjects(Task::class.java)
            val remoteTaskMap = remoteTasks.associateBy { it.docId }

            // 2. Process Local Changes (Push to Remote with Batching - Chunked to 500 ops)
            val locals = taskDao.getUnsyncedTasks(userId)
            if (locals.isNotEmpty()) {
                val chunks = locals.chunked(500)
                for (chunk in chunks) {
                    val batch = firestore.batch()
                    val pushedLocals = mutableListOf<com.example.irondiary.data.local.entity.TaskEntity>()
                    val localDeletesToPush = mutableListOf<com.example.irondiary.data.local.entity.TaskEntity>()

                    for (local in chunk) {
                        try {
                            when (local.syncState) {
                                SyncState.DELETED -> {
                                    batch.delete(tasksCollection.document(local.id))
                                    localDeletesToPush.add(local)
                                }
                                SyncState.PENDING, SyncState.FAILED, SyncState.SYNCED -> { 
                                    // PENDING/FAILED are new/edited. 
                                    // SYNCED is here if we ever need to force a re-push, but usually it's not in getUnsyncedTasks
                                    val remote = remoteTaskMap[local.id]
                                    if (remote == null || local.localUpdatedAt >= remote.updatedAt.toDate().time) {
                                        batch.set(tasksCollection.document(local.id), local.toDomainModel(), SetOptions.merge())
                                        pushedLocals.add(local.copy(syncState = SyncState.SYNCED))
                                    } else {
                                        Log.d("SyncWorker", "Local task ${local.id} abandoned due to newer remote data.")
                                        taskDao.update(remote.toEntity(userId, SyncState.SYNCED))
                                    }
                                }
                                else -> Unit
                            }
                        } catch (e: Exception) {
                            Log.e("SyncWorker", "Failed to stage task ${local.id} for batch", e)
                        }
                    }
                    
                    // Commit Firestore Batch for this chunk
                    batch.commit().await()
                    
                    // Atomic Local Room Updates for this chunk
                    if (pushedLocals.isNotEmpty()) taskDao.updateAll(pushedLocals)
                    if (localDeletesToPush.isNotEmpty()) taskDao.deleteAll(localDeletesToPush)
                }
            }

            // 3. Process Remote Injections (Pull to Local)
            for (remote in remoteTasks) {
                val local = taskDao.getTaskById(remote.docId, userId)
                if (local == null) {
                    taskDao.insert(remote.toEntity(userId, SyncState.SYNCED))
                } else if (local.syncState == SyncState.SYNCED) {
                    if (remote.updatedAt.toDate().time > local.localUpdatedAt) {
                        taskDao.update(remote.toEntity(userId, SyncState.SYNCED))
                    }
                }
            }

            // 4. Remote Deletion Mirroring (Pruning)
            val allLocals = taskDao.getTasksImmediate(userId)
            val nodesToRemove = allLocals.filter { 
                it.syncState == SyncState.SYNCED && !remoteTaskMap.containsKey(it.id) 
            }
            if (nodesToRemove.isNotEmpty()) {
                Log.d("SyncWorker", "Pruning ${nodesToRemove.size} tasks deleted remotely.")
                taskDao.deleteAll(nodesToRemove)
            }
            true
        } catch (e: Exception) {
            Log.e("SyncWorker", "Error syncing tasks", e)
            false
        }
    }

    private suspend fun syncStudySessions(db: IronDiaryDatabase, firestore: FirebaseFirestore, userId: String): Boolean {
        val studyDao = db.studySessionDao()
        val studyCollection = firestore.collection("users").document(userId).collection("study_sessions")

        return try {
            val remoteSessions = studyCollection.get().await().toObjects(StudySession::class.java)
            val remoteMap = remoteSessions.associateBy { it.docId }

            // 2. Process Local Changes (Push to Remote with Batching - Chunked to 500)
            val locals = studyDao.getUnsyncedSessions(userId)
            if (locals.isNotEmpty()) {
                val chunks = locals.chunked(500)
                for (chunk in chunks) {
                    val batch = firestore.batch()
                    val pushedLocals = mutableListOf<com.example.irondiary.data.local.entity.StudySessionEntity>()
                    val localDeletesToPush = mutableListOf<com.example.irondiary.data.local.entity.StudySessionEntity>()

                    for (local in chunk) {
                        try {
                            when (local.syncState) {
                                SyncState.DELETED -> {
                                    batch.delete(studyCollection.document(local.id))
                                    localDeletesToPush.add(local)
                                }
                                SyncState.PENDING, SyncState.FAILED, SyncState.SYNCED -> {
                                    val remote = remoteMap[local.id]
                                    if (remote == null || local.localUpdatedAt >= remote.updatedAt.toDate().time) {
                                        batch.set(studyCollection.document(local.id), local.toDomainModel(), SetOptions.merge())
                                        pushedLocals.add(local.copy(syncState = SyncState.SYNCED))
                                    } else {
                                        studyDao.update(remote.toEntity(userId, SyncState.SYNCED))
                                    }
                                }
                                else -> Unit
                            }
                        } catch (e: Exception) {
                            Log.e("SyncWorker", "Failed to stage session ${local.id} for batch", e)
                        }
                    }
                    batch.commit().await()
                    if (pushedLocals.isNotEmpty()) studyDao.updateAll(pushedLocals)
                    if (localDeletesToPush.isNotEmpty()) studyDao.deleteAll(localDeletesToPush)
                }
            }

            // 3. Process Remote Injections (Pull to Local)
            for (remote in remoteSessions) {
                val local = studyDao.getSessionById(remote.docId, userId)
                if (local == null) {
                    studyDao.insert(remote.toEntity(userId, SyncState.SYNCED))
                } else if (local.syncState == SyncState.SYNCED) {
                    if (remote.updatedAt.toDate().time > local.localUpdatedAt) {
                        studyDao.update(remote.toEntity(userId, SyncState.SYNCED))
                    }
                }
            }

            // 4. Remote Deletion Mirroring (Pruning)
            val allLocals = studyDao.getSessionsImmediate(userId)
            val nodesToRemove = allLocals.filter { 
                it.syncState == SyncState.SYNCED && !remoteMap.containsKey(it.id) 
            }
            if (nodesToRemove.isNotEmpty()) {
                Log.d("SyncWorker", "Pruning ${nodesToRemove.size} sessions deleted remotely.")
                studyDao.deleteAll(nodesToRemove)
            }
            true
        } catch (e: Exception) {
            Log.e("SyncWorker", "Error syncing study sessions", e)
            false
        }
    }

    private suspend fun syncDailyLogs(db: IronDiaryDatabase, firestore: FirebaseFirestore, userId: String): Boolean {
        val logDao = db.dailyLogDao()
        val logsCollection = firestore.collection("users").document(userId).collection("daily_logs")

        return try {
            val remoteLogs = logsCollection.get().await().toObjects(DailyLog::class.java)
            val remoteMap = remoteLogs.associateBy { it.date }

            // 2. Process Local Changes (Push to Remote with Batching - Chunked to 500)
            val locals = logDao.getUnsyncedLogs(userId)
            if (locals.isNotEmpty()) {
                val chunks = locals.chunked(500)
                for (chunk in chunks) {
                    val batch = firestore.batch()
                    val pushedLocals = mutableListOf<com.example.irondiary.data.local.entity.DailyLogEntity>()
                    val localDeletesToPush = mutableListOf<com.example.irondiary.data.local.entity.DailyLogEntity>()

                    for (local in chunk) {
                        try {
                            when (local.syncState) {
                                SyncState.DELETED -> {
                                    batch.delete(logsCollection.document(local.date))
                                    localDeletesToPush.add(local)
                                }
                                SyncState.PENDING, SyncState.FAILED, SyncState.SYNCED -> {
                                    val remote = remoteMap[local.date]
                                    if (remote == null || local.localUpdatedAt >= remote.updatedAt.toDate().time) {
                                        batch.set(logsCollection.document(local.date), local.toDomainModel(), SetOptions.merge())
                                        pushedLocals.add(local.copy(syncState = SyncState.SYNCED))
                                    } else {
                                        logDao.update(remote.toEntity(userId, SyncState.SYNCED))
                                    }
                                }
                                else -> Unit
                            }
                        } catch (e: Exception) {
                            Log.e("SyncWorker", "Failed to stage log ${local.date} for batch", e)
                        }
                    }
                    batch.commit().await()
                    if (pushedLocals.isNotEmpty()) logDao.updateAll(pushedLocals)
                    if (localDeletesToPush.isNotEmpty()) logDao.deleteAll(localDeletesToPush)
                }
            }

            // 3. Process Remote Injections (Pull to Local)
            for (remote in remoteLogs) {
                val local = logDao.getLogById("${userId}_${remote.date}", userId)
                if (local == null) {
                    logDao.insert(remote.toEntity(userId, SyncState.SYNCED))
                } else if (local.syncState == SyncState.SYNCED) {
                    if (remote.updatedAt.toDate().time > local.localUpdatedAt) {
                        logDao.update(remote.toEntity(userId, SyncState.SYNCED))
                    }
                }
            }

            // 4. Remote Deletion Mirroring (Pruning)
            val allLocals = logDao.getLogsImmediate(userId)
            val nodesToRemove = allLocals.filter { 
                it.syncState == SyncState.SYNCED && !remoteMap.containsKey(it.date) 
            }
            if (nodesToRemove.isNotEmpty()) {
                Log.d("SyncWorker", "Pruning ${nodesToRemove.size} logs deleted remotely.")
                logDao.deleteAll(nodesToRemove)
            }
            true
        } catch (e: Exception) {
            Log.e("SyncWorker", "Error syncing daily logs", e)
            false
        }
    }
}
