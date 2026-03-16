package com.example.irondiary.data.model

import com.google.firebase.Timestamp

data class StudySession(
    val subject: String = "",
    val date: Timestamp = Timestamp.now(),
    val duration: Double = 0.0
)
