package com.example.irondiary.data.local.mapper

import com.example.irondiary.data.local.SyncState
import com.example.irondiary.data.local.entity.TaskEntity
import com.example.irondiary.data.model.Task
import com.google.firebase.Timestamp

fun TaskEntity.toDomainModel(): Task {
    return Task(
        docId = id,
        description = description,
        completed = completed,
        createdDate = Timestamp(createdDate / 1000, ((createdDate % 1000) * 1000000).toInt()),
        completedDate = completedDate?.let { Timestamp(it / 1000, ((it % 1000) * 1000000).toInt()) },
        updatedAt = Timestamp(localUpdatedAt / 1000, ((localUpdatedAt % 1000) * 1000000).toInt()),
        syncState = syncState
    )
}

fun Task.toEntity(userId: String, syncState: SyncState): TaskEntity {
    return TaskEntity(
        id = docId,
        userId = userId,
        description = description,
        completed = completed,
        createdDate = createdDate.toDate().time,
        completedDate = completedDate?.toDate()?.time,
        localUpdatedAt = updatedAt.toDate().time,
        syncState = syncState
    )
}
