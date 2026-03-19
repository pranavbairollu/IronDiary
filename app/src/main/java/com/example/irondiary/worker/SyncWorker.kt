package com.example.irondiary.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.irondiary.data.local.IronDiaryDatabase
import com.example.irondiary.data.local.SyncState
import com.example.irondiary.data.local.mapper.toDomainModel
import com.example.irondiary.data.local.mapper.toEntity
import com.example.irondiary.data.model.Task
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
class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
        
        // Critical: User Isolation
        if (user == null) {
            return@withContext Result.success()
        }
        val userId = user.uid

        val db = IronDiaryDatabase.getDatabase(applicationContext)
        val taskDao = db.taskDao()
        val firestore = FirebaseFirestore.getInstance()
        val tasksCollection = firestore.collection("users").document(userId).collection("tasks")

        try {
            // STEP 1: Fetch remote snapshot
            val querySnapshot = tasksCollection.get().await()
            val remoteTasks = querySnapshot.toObjects(Task::class.java)
            val remoteTaskMap = remoteTasks.associateBy { it.docId }

            // STEP 2: Push local outstanding operations (PENDING, FAILED, DELETED)
            val unsyncedTasks = taskDao.getUnsyncedTasks(userId)
            
            for (localTask in unsyncedTasks) {
                try {
                    when (localTask.syncState) {
                        SyncState.DELETED -> {
                            Log.d("SyncWorker", "Syncing DELETION for task: ${localTask.id}")
                            // Execute Cloud Deletion, then Hard Delete locally
                            tasksCollection.document(localTask.id).delete().await()
                            taskDao.deleteById(localTask.id, userId)
                            Log.d("SyncWorker", "Task ${localTask.id} permanently removed from Room after successful remote delete.")
                        }
                        SyncState.PENDING, SyncState.FAILED -> {
                            Log.d("SyncWorker", "Syncing PENDING/FAILED task: ${localTask.id}")
                            val remoteVersion = remoteTaskMap[localTask.id]
                            
                            // CONFLICT RESOLUTION: Last Write Wins, Timestamp Based
                            if (remoteVersion != null) {
                                val remoteTime = remoteVersion.updatedAt.toDate().time
                                if (remoteTime > localTask.localUpdatedAt) {
                                    // Remote mapping is newer than the unsynced local edit. Surrender.
                                    Log.d("SyncWorker", "Local PENDING task ${localTask.id} abandoned due to newer remote timestamp.")
                                    continue 
                                }
                            }
                            
                            // Local data is newer, push to cloud idempotenly
                            val domainModel = localTask.toDomainModel()
                            tasksCollection.document(localTask.id).set(domainModel, SetOptions.merge()).await()
                            taskDao.update(localTask.copy(syncState = SyncState.SYNCED))
                            Log.d("SyncWorker", "Task ${localTask.id} successfully synced to Firestore.")
                        }
                        else -> Unit
                    }
                } catch (e: Exception) {
                    Log.e("SyncWorker", "Failed to push specific task ${localTask.id}", e)
                    // If it was PENDING/FAILED, keep it as FAILED. If DELETED, keep it as DELETED so it retries as a deletion.
                    if (localTask.syncState == SyncState.PENDING) {
                        taskDao.update(localTask.copy(syncState = SyncState.FAILED))
                    }
                }
            }

            // STEP 3: Apply Remote Snapshot down to local storage
            for (remoteTask in remoteTasks) {
                // By providing a blank docId fallback in Firebase UI previously, some items may have blank IDs remotely
                // We generate or fix IDs before mapping them directly.
                val safeDocId = remoteTask.docId
                if (safeDocId.isBlank()) continue
                
                val localTask = taskDao.getTaskById(safeDocId, userId)
                
                if (localTask == null) {
                    // Safe injection of brand-new remote element
                    Log.d("SyncWorker", "Injecting new remote task to Room: ${remoteTask.docId}")
                    taskDao.insert(remoteTask.toEntity(userId, SyncState.SYNCED))
                } else if (localTask.syncState == SyncState.SYNCED) {
                    // CONFLICT RESOLUTION
                    val remoteTime = remoteTask.updatedAt.toDate().time
                    if (remoteTime > localTask.localUpdatedAt) {
                        // Safe overwrite of out-of-date local element with synced element
                        Log.d("SyncWorker", "Updating local task ${localTask.id} with newer remote version.")
                        taskDao.update(remoteTask.toEntity(userId, SyncState.SYNCED))
                    }
                }
            }
            
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Global Sync execution failed completely due to exception", e)
            Result.retry() // Implements exponential back-off safely within WorkManager constraints
        }
    }
}
