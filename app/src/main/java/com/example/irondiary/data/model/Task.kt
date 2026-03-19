package com.example.irondiary.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.example.irondiary.data.local.SyncState

data class Task(
    @DocumentId val docId: String = "",
    val description: String = "",
    val createdDate: Timestamp = Timestamp.now(),
    var completedDate: Timestamp? = null,
    var completed: Boolean = false,
    var updatedAt: Timestamp = Timestamp.now(),
    val syncState: SyncState = SyncState.SYNCED
)
