package com.example.irondiary.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.irondiary.data.local.SyncState

/**
 * Single Source of Truth for a Task, persisted using Room DB.
 */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey
    val id: String,
    
    // User scoping to prevent data leaks across accounts on shared device
    val userId: String,
    
    val description: String,
    val completed: Boolean,
    
    // Original creation date as Unix timestamp
    val createdDate: Long,
    
    // When the task was marked completed
    val completedDate: Long? = null,
    
    // Tracks when the record was last modified locally. Crucial for resolving write conflicts.
    val localUpdatedAt: Long,
    
    // Current synchronization status with Firebase
    val syncState: SyncState = SyncState.PENDING
)
