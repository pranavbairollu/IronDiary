package com.example.irondiary.data.model

import com.google.firebase.Timestamp

import com.google.firebase.firestore.DocumentId

data class StudySession(
    @DocumentId val docId: String = "",
    val subject: String = "",
    val date: Timestamp = Timestamp.now(),
    val duration: Double = 0.0,
    val updatedAt: Timestamp = Timestamp.now()
)
