package com.example.irondiary.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Executes bidirectional background synchronization of the IronDiary offline databases
 * against the remote Firebase Firestore structure. Handles Conflict Resolution via specific timestamps.
 */
class SyncWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@withContext Result.failure()
        val db = IronDiaryDatabase.getDatabase(applicationContext)
        val firestore = FirebaseFirestore.getInstance()

        val tasksSuccess = syncTasks(db, firestore, userId)
        val studySuccess = syncStudySessions(db, firestore, userId)
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

            // 2. Process Local Changes (Push to Remote)
            val locals = taskDao.getUnsyncedTasks(userId)
            for (local in locals) {
                try {
                    when (local.syncState) {
                        SyncState.DELETED -> {
                            tasksCollection.document(local.id).delete().await()
                            taskDao.deleteById(local.id, userId)
                        }
                        SyncState.PENDING, SyncState.FAILED -> {
                            val remote = remoteTaskMap[local.id]
                            if (remote == null || local.localUpdatedAt >= remote.updatedAt.toDate().time) {
                                tasksCollection.document(local.id).set(local.toDomainModel(), SetOptions.merge()).await()
                                taskDao.update(local.copy(syncState = SyncState.SYNCED))
                            } else {
                                Log.d("SyncWorker", "Local task ${local.id} abandoned due to newer remote data.")
                                taskDao.update(remote.toEntity(userId, SyncState.SYNCED))
                            }
                        }
                        else -> Unit
                    }
                } catch (e: Exception) {
                    Log.e("SyncWorker", "Failed to sync task ${local.id}", e)
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

            val locals = studyDao.getUnsyncedSessions(userId)
            for (local in locals) {
                try {
                    when (local.syncState) {
                        SyncState.DELETED -> {
                            studyCollection.document(local.id).delete().await()
                            studyDao.deleteById(local.id, userId)
                        }
                        SyncState.PENDING, SyncState.FAILED -> {
                            val remote = remoteMap[local.id]
                            if (remote == null || local.localUpdatedAt >= remote.updatedAt.toDate().time) {
                                studyCollection.document(local.id).set(local.toDomainModel(), SetOptions.merge()).await()
                                studyDao.update(local.copy(syncState = SyncState.SYNCED))
                            } else {
                                studyDao.update(remote.toEntity(userId, SyncState.SYNCED))
                            }
                        }
                        else -> Unit
                    }
                } catch (e: Exception) {
                    Log.e("SyncWorker", "Failed to sync session ${local.id}", e)
                }
            }

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

            val locals = logDao.getUnsyncedLogs(userId)
            for (local in locals) {
                try {
                    when (local.syncState) {
                        SyncState.DELETED -> {
                            logsCollection.document(local.date).delete().await()
                            logDao.deleteById(local.id, userId)
                        }
                        SyncState.PENDING, SyncState.FAILED -> {
                            val remote = remoteMap[local.date]
                            if (remote == null || local.localUpdatedAt >= remote.updatedAt.toDate().time) {
                                logsCollection.document(local.date).set(local.toDomainModel(), SetOptions.merge()).await()
                                logDao.update(local.copy(syncState = SyncState.SYNCED))
                            } else {
                                logDao.update(remote.toEntity(userId, SyncState.SYNCED))
                            }
                        }
                        else -> Unit
                    }
                } catch (e: Exception) {
                    Log.e("SyncWorker", "Failed to sync log ${local.date}", e)
                }
            }

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
            true
        } catch (e: Exception) {
            Log.e("SyncWorker", "Error syncing daily logs", e)
            false
        }
    }
}
