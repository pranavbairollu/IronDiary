package com.example.irondiary.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class Task(
    @DocumentId val docId: String = "",
    val description: String = "",
    val createdDate: Timestamp = Timestamp.now(),
    var completedDate: Timestamp? = null,
    var completed: Boolean = false
)
