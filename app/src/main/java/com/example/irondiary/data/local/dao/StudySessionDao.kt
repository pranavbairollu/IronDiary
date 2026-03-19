package com.example.irondiary.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.example.irondiary.data.local.entity.StudySessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudySessionDao : BaseDao<StudySessionEntity> {

    @Query("SELECT * FROM study_sessions WHERE userId = :userId AND syncState != 'DELETED' ORDER BY date DESC")
    fun getSessionsForUser(userId: String): Flow<List<StudySessionEntity>>

    @Query("SELECT * FROM study_sessions WHERE userId = :userId AND syncState != 'DELETED'")
    suspend fun getSessionsImmediate(userId: String): List<StudySessionEntity>

    @Query("SELECT * FROM study_sessions WHERE id = :id AND userId = :userId")
    suspend fun getSessionById(id: String, userId: String): StudySessionEntity?

    @Query("SELECT * FROM study_sessions WHERE userId = :userId AND (syncState = 'PENDING' OR syncState = 'DELETED' OR syncState = 'FAILED')")
    suspend fun getUnsyncedSessions(userId: String): List<StudySessionEntity>

    @Query("DELETE FROM study_sessions WHERE id = :id AND userId = :userId")
    suspend fun deleteById(id: String, userId: String)
}
