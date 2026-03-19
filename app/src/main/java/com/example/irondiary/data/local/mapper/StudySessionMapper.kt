package com.example.irondiary.data.local.mapper

import com.example.irondiary.data.local.SyncState
import com.example.irondiary.data.local.entity.StudySessionEntity
import com.example.irondiary.data.model.StudySession
import com.google.firebase.Timestamp
import java.util.Date

fun StudySessionEntity.toDomainModel(): StudySession {
    return StudySession(
        docId = id,
        subject = subject,
        date = Timestamp(Date(date)),
        duration = duration,
        updatedAt = Timestamp(Date(localUpdatedAt))
    )
}

fun StudySession.toEntity(userId: String, syncState: SyncState): StudySessionEntity {
    return StudySessionEntity(
        id = docId,
        userId = userId,
        subject = subject,
        date = date.toDate().time,
        duration = duration,
        localUpdatedAt = updatedAt.toDate().time,
        syncState = syncState
    )
}
