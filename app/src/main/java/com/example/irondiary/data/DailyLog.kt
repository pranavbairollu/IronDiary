package com.example.irondiary.data

import com.google.firebase.firestore.PropertyName

data class DailyLog(
    val date: String = "",
    @get:PropertyName("attended_gym") @set:PropertyName("attended_gym") var attendedGym: Boolean = false,
    val weight: Float? = null,
    val notes: String? = null
)
