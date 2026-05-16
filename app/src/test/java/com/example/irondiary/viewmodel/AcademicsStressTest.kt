package com.example.irondiary.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class AcademicsStressTest {

    @Test
    fun studySession_decimalParsing_handlesCommasAndPeriods() {
        val testInputs = listOf("1.5", "1,5", "2", "0,75", "1.25")
        val expectedOutputs = listOf(1.5f, 1.5f, 2.0f, 0.75f, 1.25f)
        
        testInputs.forEachIndexed { index, input ->
            val parsed = input.replace(',', '.').toFloatOrNull()
            assertEquals("Failed for input: $input", expectedOutputs[index], parsed ?: 0f, 0.001f)
        }
    }

    @Test
    fun reminderLogic_pastTime_movesToTomorrow() {
        val now = Calendar.getInstance()
        
        // Create a time that is 1 hour in the past
        val pastTime = Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, -1)
        }
        
        val reminderTimeMillis = pastTime.timeInMillis
        val currentTimeMillis = now.timeInMillis
        
        var scheduleTime = reminderTimeMillis
        
        // Mimic NotificationHelper logic
        if (scheduleTime <= currentTimeMillis) {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = scheduleTime
                add(Calendar.DAY_OF_YEAR, 1)
            }
            scheduleTime = calendar.timeInMillis
        }
        
        // Should be exactly 24 hours after the pastTime, which is 23 hours from "now"
        val diffHours = (scheduleTime - currentTimeMillis) / (1000 * 60 * 60)
        assertEquals(23, diffHours)
    }

    @Test
    fun reminderLogic_futureTime_staysSameDay() {
        val now = Calendar.getInstance()
        
        // Create a time that is 1 hour in the future
        val futureTime = Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, 1)
        }
        
        val reminderTimeMillis = futureTime.timeInMillis
        val currentTimeMillis = now.timeInMillis
        
        var scheduleTime = reminderTimeMillis
        
        // Mimic NotificationHelper logic
        if (scheduleTime <= currentTimeMillis) {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = scheduleTime
                add(Calendar.DAY_OF_YEAR, 1)
            }
            scheduleTime = calendar.timeInMillis
        }
        
        assertEquals(futureTime.timeInMillis, scheduleTime)
    }

    @Test
    fun studyDuration_validation_preventsInvalidValues() {
        val invalidValues = listOf(-1f, 0f, 25f, Float.NaN, Float.POSITIVE_INFINITY)
        
        invalidValues.forEach { d ->
            val isInvalid = d.isNaN() || d.isInfinite() || d <= 0f || d > 24f
            assertEquals("Value $d should be invalid", true, isInvalid)
        }
        
        val validValues = listOf(0.1f, 1f, 12f, 24f)
        validValues.forEach { d ->
            val isInvalid = d.isNaN() || d.isInfinite() || d <= 0f || d > 24f
            assertEquals("Value $d should be valid", false, isInvalid)
        }
    }
}
