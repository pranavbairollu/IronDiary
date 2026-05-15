package com.example.irondiary.util

import com.example.irondiary.data.DailyLog
import com.example.irondiary.data.model.Task
import com.example.irondiary.data.model.StudySession
import org.junit.Assert.assertTrue
import org.junit.Test

class IronIntelligenceEngineTest {

    @Test
    fun testHighestWeightQuery() {
        val logs = listOf(
            DailyLog(date = "2023-01-01", weight = 80f),
            DailyLog(date = "2023-01-02", weight = 85f),
            DailyLog(date = "2023-01-03", weight = 82f)
        )
        val bundle = LocalDataBundle(logs, emptyList(), emptyList())
        val response = IronIntelligenceEngine.processQuery("What is my highest weight?", bundle)
        assertTrue("Response should contain 85.0", response.text.contains("85.0"))
    }

    @Test
    fun testAverageWeightQuery() {
        val logs = listOf(
            DailyLog(date = "2023-01-01", weight = 70f),
            DailyLog(date = "2023-01-02", weight = 80f)
        )
        val bundle = LocalDataBundle(logs, emptyList(), emptyList())
        val response = IronIntelligenceEngine.processQuery("average weight", bundle)
        assertTrue("Response should contain 75.0", response.text.contains("75.0"))
    }

    @Test
    fun testGymStatsQuery() {
        val logs = listOf(
            DailyLog(date = "2023-01-01", attendedGym = true),
            DailyLog(date = "2023-01-02", attendedGym = false),
            DailyLog(date = "2023-01-03", attendedGym = true)
        )
        val bundle = LocalDataBundle(logs, emptyList(), emptyList())
        val response = IronIntelligenceEngine.processQuery("how many gym sessions", bundle)
        assertTrue("Response should contain 2", response.text.contains("2"))
    }

    @Test
    fun testTaskStatsQuery() {
        val tasks = listOf(
            Task(description = "Task 1", completed = true),
            Task(description = "Task 2", completed = false)
        )
        val bundle = LocalDataBundle(emptyList(), tasks, emptyList())
        val response = IronIntelligenceEngine.processQuery("how many tasks pending", bundle)
        assertTrue("Response should mention 1 pending task", response.text.contains("1 pending"))
    }

    @Test
    fun testMuscleGroupQuery() {
        val logs = listOf(
            DailyLog(date = "2023-01-01", notes = "Heavy Chest Day", attendedGym = true),
            DailyLog(date = "2023-01-05", notes = "Back and Biceps", attendedGym = true),
            DailyLog(date = "2023-01-10", notes = "Chest and Tris", attendedGym = true)
        )
        val bundle = LocalDataBundle(logs, emptyList(), emptyList())
        val response = IronIntelligenceEngine.processQuery("When did I train chest?", bundle)
        assertTrue("Response should contain Jan 10", response.text.contains("Jan 10"))
        assertTrue("Response should contain Jan 01", response.text.contains("Jan 01"))
        assertTrue("Response should mention 2 sessions", response.text.contains("2 sessions"))
    }

    @Test
    fun testMuscleHierarchyQuery() {
        val logs = listOf(
            DailyLog(date = "2023-01-01", notes = "Chest and Arms day", attendedGym = true),
            DailyLog(date = "2023-01-05", notes = "Triceps isolation", attendedGym = true)
        )
        val bundle = LocalDataBundle(logs, emptyList(), emptyList())
        
        // Querying for triceps should find both "Arms" and "Triceps"
        val response = IronIntelligenceEngine.processQuery("When did I last do triceps?", bundle)
        assertTrue("Response should contain Jan 05", response.text.contains("Jan 05"))
        assertTrue("Response should contain Jan 01", response.text.contains("Jan 01"))
        assertTrue("Response should mention 2 sessions", response.text.contains("2 sessions"))
    }

    @Test
    fun testWeightPredictionQuery() {
        val logs = listOf(
            DailyLog(date = "2023-01-01", weight = 80f),
            DailyLog(date = "2023-01-08", weight = 79f), // 1kg loss in 7 days
            DailyLog(date = "2023-01-15", weight = 78f)  // another 1kg loss
        )
        val bundle = LocalDataBundle(logs, emptyList(), emptyList())
        
        // Target 76kg (should take 2 more weeks)
        val response = IronIntelligenceEngine.processQuery("When will I reach 76kg?", bundle)
        assertTrue("Response should mention -1.0 kg/week", response.text.contains("-1.0 kg/week"))
        assertTrue("Response should mention about 14 days", response.text.contains("14 days"))
    }

    @Test
    fun testCorrelationQuery() {
        val logs = listOf(
            DailyLog(date = "2023-01-01", attendedGym = true),
            DailyLog(date = "2023-01-02", attendedGym = false)
        )
        
        val utcZone = java.util.TimeZone.getTimeZone("UTC")
        val cal1 = java.util.GregorianCalendar(utcZone).apply { 
            set(2023, 0, 1, 12, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val cal2 = java.util.GregorianCalendar(utcZone).apply { 
            set(2023, 0, 2, 12, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }

        val sessions = listOf(
            // Gym Day Session (2 hours)
            StudySession(subject = "Math", duration = 2.0, date = com.google.firebase.Timestamp(cal1.time)),
            // Rest Day Session (1 hour)
            StudySession(subject = "Art", duration = 1.0, date = com.google.firebase.Timestamp(cal2.time))
        )
        val bundle = LocalDataBundle(logs, emptyList(), sessions)
        
        val response = IronIntelligenceEngine.processQuery("Does the gym affect my studying?", bundle)
        org.junit.Assert.assertEquals("Iron Insight: You study 100% longer on days you hit the gym! Your physical activity seems to be fueling your focus.", response.text)
    }

    @Test
    fun testWelcomeMessage() {
        val logs = listOf(
            DailyLog(date = "2023-01-01", weight = 80.0f),
            DailyLog(date = "2023-01-10", weight = 78.0f)
        )
        val bundle = LocalDataBundle(logs, emptyList(), emptyList())
        val welcome = IronIntelligenceEngine.getWelcomeMessage(bundle)
        
        assertTrue("Welcome should mention weight loss", welcome.contains("lost 2.0 kg"))
    }
}
