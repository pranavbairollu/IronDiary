package com.example.irondiary.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.irondiary.data.local.SyncState

@Entity(tableName = "study_sessions")
data class StudySessionEntity(
    @PrimaryKey val id: String, // Matches Firestore docId
    val userId: String,
    val subject: String,
    val date: Long, // timestamp
    val duration: Double,
    val localUpdatedAt: Long,
    val syncState: SyncState
)
