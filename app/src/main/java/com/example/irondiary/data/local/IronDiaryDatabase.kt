package com.example.irondiary.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.irondiary.data.local.dao.TaskDao
import com.example.irondiary.data.local.dao.StudySessionDao
import com.example.irondiary.data.local.dao.DailyLogDao
import com.example.irondiary.data.local.entity.TaskEntity
import com.example.irondiary.data.local.entity.StudySessionEntity
import com.example.irondiary.data.local.entity.DailyLogEntity

@Database(
    entities = [
        TaskEntity::class, 
        StudySessionEntity::class, 
        DailyLogEntity::class
    ], 
    version = 3, 
    exportSchema = false
)
@TypeConverters(RoomTypeConverters::class)
abstract class IronDiaryDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun studySessionDao(): StudySessionDao
    abstract fun dailyLogDao(): DailyLogDao

    companion object {
        @Volatile
        private var INSTANCE: IronDiaryDatabase? = null
        
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tasks ADD COLUMN reminderTime INTEGER")
            }
        }

        fun getDatabase(context: Context): IronDiaryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    IronDiaryDatabase::class.java,
                    "iron_diary_database"
                )
                // In a production scenario, provide destructive migration fallback 
                // or specific version migrations here.
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
