package com.example.irondiary.util

import com.example.irondiary.data.DailyLog
import com.example.irondiary.data.model.StudySession
import com.example.irondiary.data.model.Task
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class IntelligenceStressTest {

    @Test
    fun prEngine_mixedUnits_normalizesToKg() {
        val logs = listOf(
            DailyLog(date = "2026-05-15", notes = "Bench Press 100kg"),
            DailyLog(date = "2026-05-16", notes = "Bench Press 225lbs") // ~102kg
        )
        
        val allPRs = IronIntelligenceEngine.getAllPersonalRecords(logs)
        
        // 225lbs is higher than 100kg
        assertEquals(225.0, allPRs["bench press"]?.first ?: 0.0, 0.01)
        assertEquals("lbs", allPRs["bench press"]?.second)
    }

    @Test
    fun prEngine_fuzzyMatching_preventsFalsePositives() {
        // "Row" is in our map (part of "Rows" usually, but let's assume we have a short name)
        // If the user has "Rows" in map and writes "Rowing" it shouldn't match if it's <= 4 chars.
        
        val logs = listOf(
            DailyLog(date = "2026-05-16", notes = "Rowing for 20 mins at 50kg")
        )
        
        // Let's check how "Rows" is defined in our map (at line 86 it's "rows")
        val allPRs = IronIntelligenceEngine.getAllPersonalRecords(logs)
        
        // "Rowing" contains "rows" but word boundary should prevent match if we use Regex(\b)
        // Note: exerciseToMuscleMap contains "rows" (4 chars).
        assertTrue("Rows should NOT match Rowing", !allPRs.containsKey("rows"))
    }

    @Test
    fun forecasting_velocity_predictsGoalDate() {
        val logs = listOf(
            DailyLog(date = "2026-05-01", weight = 100f),
            DailyLog(date = "2026-05-04", weight = 99.5f),
            DailyLog(date = "2026-05-08", weight = 99f) // 1kg loss in 7 days total
        )
        
        val response = IronIntelligenceEngine.processQuery("When will I reach 90kg?", LocalDataBundle(logs, emptyList(), emptyList()))
        // Rate: -1kg over 7 days = -1/7 per day.
        // target: 90. current: 99. remaining: -9.
        // days: -9 / (-1/7) = 63.
        assertTrue("Should mention reaching 90: ${response.text}", response.text.contains("90"))
        assertTrue("Should mention about 63 days: ${response.text}", response.text.contains("63 days"))
    }

    @Test
    fun forecasting_movingAwayFromGoal_detectsCorrectly() {
        val logs = listOf(
            DailyLog(date = "2026-05-01", weight = 90f),
            DailyLog(date = "2026-05-04", weight = 90.5f),
            DailyLog(date = "2026-05-08", weight = 91f) // Gaining weight
        )
        
        val response = IronIntelligenceEngine.processQuery("When will I reach 85kg?", LocalDataBundle(logs, emptyList(), emptyList()))
        assertTrue("Should detect moving away: ${response.text}", response.text.lowercase().contains("away"))
    }

    @Test
    fun correlationEngine_gymVsStudy_identifiesLink() {
        // 5 Gym Days with 4 hours study
        val logs = listOf(
            DailyLog(date = "2026-05-10", attendedGym = true),
            DailyLog(date = "2026-05-11", attendedGym = true),
            DailyLog(date = "2026-05-12", attendedGym = true)
        )
        
        val sessions = listOf(
            StudySession(date = Timestamp(Date(126, 4, 10)), duration = 4.0), // May 10
            StudySession(date = Timestamp(Date(126, 4, 11)), duration = 4.0), // May 11
            StudySession(date = Timestamp(Date(126, 4, 12)), duration = 4.0), // May 12
            StudySession(date = Timestamp(Date(126, 4, 13)), duration = 1.0)  // May 13 (Rest Day)
        )
        
        val bundle = LocalDataBundle(logs, emptyList(), sessions)
        val response = IronIntelligenceEngine.processQuery("Does gym affect study?", bundle)
        
        // 4.0 avg on gym days vs 1.0 avg on rest days
        assertTrue("Should identify positive correlation", response.text.contains("study 300% longer on days you hit the gym"))
    }

    @Test
    fun trainingDirector_recovery_suggestsOldestMuscle() {
        val logs = listOf(
            DailyLog(date = "2026-05-10", notes = "Chest Day"),
            DailyLog(date = "2026-05-11", notes = "Back Day"),
            DailyLog(date = "2026-05-12", notes = "Legs Day"),
            DailyLog(date = "2026-05-13", notes = "Shoulders Day"),
            DailyLog(date = "2026-05-14", notes = "Arms Day"),
            DailyLog(date = "2026-05-15", notes = "Abs Day")
        )
        
        val rec = IronIntelligenceEngine.getNextWorkoutRecommendation(logs)
        
        // Chest was trained longest ago (May 10)
        assertTrue("Should suggest Chest", rec.contains("Chest", ignoreCase = true))
    }

    @Test
    fun weightParsing_commas_handledInQuery() {
        val logs = listOf(
            DailyLog(date = "2026-05-01", weight = 80f),
            DailyLog(date = "2026-05-04", weight = 79.5f),
            DailyLog(date = "2026-05-08", weight = 79f)
        )
        
        val response = IronIntelligenceEngine.processQuery("When will I reach 75,5kg?", LocalDataBundle(logs, emptyList(), emptyList()))
        assertTrue("Should correctly parse 75.5: ${response.text}", response.text.contains("75.5") || response.text.contains("75,5"))
    }
}
