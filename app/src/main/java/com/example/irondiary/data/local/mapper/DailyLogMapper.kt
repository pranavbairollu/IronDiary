package com.example.irondiary.data.local.mapper

import com.example.irondiary.data.DailyLog
import com.example.irondiary.data.local.SyncState
import com.example.irondiary.data.local.entity.DailyLogEntity
import com.google.firebase.Timestamp
import java.util.Date

fun DailyLogEntity.toDomainModel(): DailyLog {
    return DailyLog(
        date = date,
        attendedGym = attendedGym,
        weight = weight,
        notes = notes,
        isRestDay = isRestDay,
        updatedAt = Timestamp(Date(localUpdatedAt)),
        syncState = syncState
    )
}

fun DailyLog.toEntity(userId: String, syncState: SyncState): DailyLogEntity {
    return DailyLogEntity(
        id = "${userId}_$date",
        userId = userId,
        date = date,
        attendedGym = attendedGym,
        weight = weight,
        notes = notes,
        isRestDay = isRestDay,
        localUpdatedAt = updatedAt.toDate().time,
        syncState = syncState
    )
}
