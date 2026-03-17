package com.example.irondiary.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.example.irondiary.data.local.entity.TaskEntity
import com.example.irondiary.data.local.SyncState
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao : BaseDao<TaskEntity> {
    
    /**
     * Gets all non-deleted tasks for a specific user, ordered by creation date descending.
     * Use this Flow as the SSOT for the UI.
     */
    @Query("SELECT * FROM tasks WHERE userId = :userId AND syncState != 'DELETED' ORDER BY createdDate DESC")
    fun getTasksForUser(userId: String): Flow<List<TaskEntity>>

    /**
     * Gets a specific task by ID. Returns null if not found.
     */
    @Query("SELECT * FROM tasks WHERE id = :taskId AND userId = :userId")
    suspend fun getTaskById(taskId: String, userId: String): TaskEntity?

    /**
     * Retrieves tasks that need syncing to the server (PENDING or DELETED)
     */
    @Query("SELECT * FROM tasks WHERE syncState IN ('PENDING', 'DELETED', 'FAILED') AND userId = :userId")
    suspend fun getUnsyncedTasks(userId: String): List<TaskEntity>
    
    /**
     * Hard delete a task (only called after successful Firebase deletion).
     */
    @Query("DELETE FROM tasks WHERE id = :taskId AND userId = :userId")
    suspend fun deleteById(taskId: String, userId: String)
    
    /**
     * Delete all tasks for a particular user. Use sparingly (e.g., account reset).
     */
    @Query("DELETE FROM tasks WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}
