package com.example.irondiary.data.local

import androidx.room.TypeConverter

class RoomTypeConverters {
    @TypeConverter
    fun fromSyncState(state: SyncState): String {
        return state.name
    }

    @TypeConverter
    fun toSyncState(stateString: String): SyncState {
        return try {
            SyncState.valueOf(stateString)
        } catch (e: IllegalArgumentException) {
            SyncState.PENDING // Default fallback
        }
    }
}
