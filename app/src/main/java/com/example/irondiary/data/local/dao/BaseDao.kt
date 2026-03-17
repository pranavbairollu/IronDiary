package com.example.irondiary.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Update

/**
 * Base DAO providing common CRUD ops for all Room entities.
 */
@Dao
interface BaseDao<T> {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: T)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<T>)

    @Update
    suspend fun update(entity: T)

    @Delete
    suspend fun delete(entity: T)
}
