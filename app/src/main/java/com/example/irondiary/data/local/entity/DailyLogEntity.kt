package com.example.irondiary.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.irondiary.data.local.SyncState

@Entity(
    tableName = "daily_logs",
    indices = [
        androidx.room.Index(value = ["userId"]),
        androidx.room.Index(value = ["date"])
    ]
)
data class DailyLogEntity(
    @PrimaryKey val id: String, // Format: ${userId}_${date}
    val userId: String,
    val date: String, // Format: YYYY-MM-DD
    val attendedGym: Boolean,
    val weight: Float?,
    val notes: String?,
    val localUpdatedAt: Long,
    val syncState: SyncState
)
