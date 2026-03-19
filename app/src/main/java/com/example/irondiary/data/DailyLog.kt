package com.example.irondiary.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

data class DailyLog(
    val date: String = "",
    @get:PropertyName("attended_gym") @set:PropertyName("attended_gym") var attendedGym: Boolean = false,
    val weight: Float? = null,
    val notes: String? = null,
    val updatedAt: Timestamp = Timestamp.now(),
    val syncState: com.example.irondiary.data.local.SyncState = com.example.irondiary.data.local.SyncState.SYNCED
)
