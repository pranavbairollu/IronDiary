package com.example.irondiary.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.example.irondiary.data.local.entity.DailyLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyLogDao : BaseDao<DailyLogEntity> {

    @Query("SELECT * FROM daily_logs WHERE userId = :userId AND syncState != 'DELETED' ORDER BY date DESC")
    fun getAllLogs(userId: String): Flow<List<DailyLogEntity>>

    @Query("SELECT * FROM daily_logs WHERE userId = :userId AND syncState != 'DELETED'")
    suspend fun getLogsImmediate(userId: String): List<DailyLogEntity>

    @Query("SELECT * FROM daily_logs WHERE date = :date AND userId = :userId AND syncState != 'DELETED' LIMIT 1")
    fun getLogByDate(date: String, userId: String): Flow<DailyLogEntity?>

    @Query("SELECT * FROM daily_logs WHERE userId = :userId AND weight > 0 AND syncState != 'DELETED' ORDER BY date ASC")
    fun getWeightLogs(userId: String): Flow<List<DailyLogEntity>>

    @Query("SELECT * FROM daily_logs WHERE id = :id AND userId = :userId")
    suspend fun getLogById(id: String, userId: String): DailyLogEntity?

    @Query("SELECT * FROM daily_logs WHERE userId = :userId AND (syncState = 'PENDING' OR syncState = 'DELETED' OR syncState = 'FAILED')")
    suspend fun getUnsyncedLogs(userId: String): List<DailyLogEntity>

    @Query("DELETE FROM daily_logs WHERE id = :id AND userId = :userId")
    suspend fun deleteById(id: String, userId: String)
}
