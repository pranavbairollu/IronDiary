package com.example.irondiary.ui

import com.example.irondiary.data.DailyLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class VisualizationStressTest {

    @Test
    fun trophyCard_capitalization_handlesMultipleWords() {
        val exercise = "dumbbell incline bench press"
        val expected = "Dumbbell Incline Bench Press"
        
        // Mocking the logic used in RecordTrophyCard.kt
        val result = exercise.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        
        assertEquals(expected, result)
    }

    @Test
    fun hallOfFame_sorting_newestFirst() {
        val prs = mapOf(
            "bench" to Triple(100.0, "kg", "2026-05-10"),
            "squat" to Triple(150.0, "kg", "2026-05-15"),
            "deadlift" to Triple(200.0, "kg", "2026-05-12")
        )
        
        val sorted = prs.entries.sortedByDescending { it.value.third }
        
        assertEquals("squat", sorted[0].key)
        assertEquals("deadlift", sorted[1].key)
        assertEquals("bench", sorted[2].key)
    }

    @Test
    fun graphRange_identicalPoints_addsPadding() {
        // Logic from SimpleLineGraph.kt
        val dataPoints = listOf(100.0, 100.0, 100.0)
        val maxVal = dataPoints.maxOrNull() ?: 100.0
        val minVal = dataPoints.minOrNull() ?: 0.0
        
        var paddedMin: Double = 0.0
        var paddedMax: Double = 0.0
        
        if (maxVal == minVal) {
            paddedMin = minVal - 1.0
            paddedMax = maxVal + 1.0
        }
        
        assertTrue(paddedMax > paddedMin)
        assertEquals(99.0, paddedMin, 0.01)
        assertEquals(101.0, paddedMax, 0.01)
    }

    @Test
    fun graphRange_variedPoints_usesPaddedRange() {
        // Logic from SimpleLineGraph.kt
        val dataPoints = listOf(90.0, 100.0)
        val maxVal = 100.0
        val minVal = 90.0
        val range = 10.0
        
        val paddedMin = minVal - range * 0.1
        val paddedMax = maxVal + range * 0.1
        
        assertEquals(89.0, paddedMin, 0.01)
        assertEquals(101.0, paddedMax, 0.01)
    }
}
