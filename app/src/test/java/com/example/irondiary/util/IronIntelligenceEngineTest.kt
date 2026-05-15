package com.example.irondiary.util

import com.example.irondiary.data.DailyLog
import com.example.irondiary.data.model.Task
import com.example.irondiary.data.model.StudySession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.time.LocalDate
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

    @Test
    fun testWorkoutRecommendation() {
        val today = LocalDate.now()
        val logs = listOf(
            DailyLog(date = today.minusDays(10).toString(), notes = "Chest Day"),
            DailyLog(date = today.minusDays(8).toString(), notes = "Legs session"),
            DailyLog(date = today.minusDays(6).toString(), notes = "Back and biceps"),
            DailyLog(date = today.minusDays(4).toString(), notes = "Abs and core"),
            DailyLog(date = today.minusDays(2).toString(), notes = "Shoulders and arms")
        )
        val bundle = LocalDataBundle(logs, emptyList(), emptyList())
        val response = IronIntelligenceEngine.processQuery("What should I train next?", bundle)
        
        assertTrue("Recommendation should suggest chest", response.text.contains("chest", ignoreCase = true))
        assertTrue("Recommendation should mention days since", response.text.contains("10 days"))
    }

    @Test
    fun testExerciseMapping() {
        val logs = listOf(
            DailyLog(date = "2023-01-01", notes = "Did some heavy bench press and rows"),
            DailyLog(date = "2023-01-05", notes = "Squats and lunges")
        )
        val bundle = LocalDataBundle(logs, emptyList(), emptyList())
        
        // 1. Direct exercise query
        val response1 = IronIntelligenceEngine.processQuery("When did I last do bench press?", bundle)
        assertTrue("Should find bench press history", response1.text.contains("Jan 01, 2023"))
        assertTrue("Should mention chest", response1.text.contains("chest"))

        // 2. Muscle group query picking up exercises
        val response2 = IronIntelligenceEngine.processQuery("When did I train legs?", bundle)
        assertTrue("Should find legs history via squats", response2.text.contains("Jan 05, 2023"))
    }

    @Test
    fun testPersonalRecordTracking() {
        val logs = listOf(
            DailyLog(date = "2023-01-01", notes = "Bench Press 80kg x 5"),
            DailyLog(date = "2023-01-10", notes = "Bench Press 90kg x 3"),
            DailyLog(date = "2023-01-15", notes = "Bench Press 85kg x 8")
        )
        val bundle = LocalDataBundle(logs, emptyList(), emptyList())
        
        val response = IronIntelligenceEngine.processQuery("What is my bench press PR?", bundle)
        assertTrue("Should identify 90kg as PR", response.text.contains("90.0 kg"))
        assertTrue("Should mention the date", response.text.contains("Jan 10, 2023"))
    }

    @Test
    fun testTopInsightPrioritization() {
        val today = LocalDate.now()
        // Case 1: Stale muscles should take priority
        val logs = listOf(
            DailyLog(date = today.minusDays(10).toString(), notes = "Chest Day"),
            DailyLog(date = today.minusDays(1).toString(), notes = "Back Day")
        )
        val bundle = LocalDataBundle(logs, emptyList(), emptyList())
        val insight = IronIntelligenceEngine.getTopInsight(bundle)
        assertTrue("Should prioritize workout recommendation", insight.contains("record of you training LEGS", ignoreCase = true))
    }

    @Test
    fun testGoalRecognitionAndResponse() {
        val today = LocalDate.now()
        // Case: Bulking goal (detected by trend or target query)
        val logs = listOf(
            DailyLog(date = today.minusDays(14).toString(), weight = 70.0f),
            DailyLog(date = today.minusDays(7).toString(), weight = 71.0f),
            DailyLog(date = today.toString(), weight = 72.0f)
        )
        val bundle = LocalDataBundle(logs, emptyList(), emptyList())
        
        // 1. Detect Bulk Goal via trend
        val trend = IronIntelligenceEngine.processQuery("How is my weight trend?", bundle)
        assertTrue("Should acknowledge gains positively for bulk", trend.text.contains("Solid gains"))

        // 2. Detect Bulk Goal via explicit query
        val prediction = IronIntelligenceEngine.processQuery("When will I reach 80kg?", bundle)
        assertTrue("Should recognize target weight higher than current as bulk", prediction.text.contains("reach 80.0 kg"))
    }

    @Test
    fun testHallOfFameExtraction() {
        val logs = listOf(
            DailyLog(date = "2023-01-01", notes = "Bench Press 80kg"),
            DailyLog(date = "2023-01-10", notes = "Bench Press 90kg"),
            DailyLog(date = "2023-01-15", notes = "Squats 100kg")
        )
        val records = IronIntelligenceEngine.getAllPersonalRecords(logs)
        
        assertEquals(2, records.size)
        assertEquals(90.0, records["bench press"]?.first)
        assertEquals(100.0, records["squats"]?.first)
    }

    @Test
    fun testVoiceQueryMultiMatchRouting() {
        val logs = listOf(
            DailyLog(date = "2023-01-01", weight = 80.0f),
            DailyLog(date = "2023-01-02", weight = 80.0f)
        )
        val bundle = LocalDataBundle(logs, emptyList(), emptyList())
        
        // Multi-match scenario: Top result is noisy/unrelated, second is gym-related
        val matches = listOf("french press is cool", "what is my weight trend", "bench press")
        val response = IronIntelligenceEngine.processVoiceQuery(matches, bundle)
        
        assertTrue("Should identify weight trend from multi-match", response.text.contains("stable"))
    }

    @Test
    fun testFuzzyExerciseMatching() {
        val logs = listOf(
            DailyLog(date = "2023-01-01", notes = "Bench Press 100kg")
        )
        val bundle = LocalDataBundle(logs, emptyList(), emptyList())
        
        // User just says "Bench" instead of "Bench Press"
        val response = IronIntelligenceEngine.processQuery("How is my bench PR?", bundle)
        assertTrue("Should match 'bench' to 'bench press'", response.text.contains("100.0 kg"))
    }

    @Test
    fun testNakedPRQuery() {
        val logs = listOf(
            DailyLog(date = "2023-01-01", notes = "Bench Press 100kg"),
            DailyLog(date = "2023-01-05", notes = "Squats 150kg")
        )
        val bundle = LocalDataBundle(logs, emptyList(), emptyList())
        
        // User just says "PR?"
        val response = IronIntelligenceEngine.processQuery("PR?", bundle)
        assertTrue("Should show top PRs", response.text.contains("squats: 150.0") && response.text.contains("bench press: 100.0"))
    }

    @Test
    fun testShortStatusQuery() {
        val logs = listOf(DailyLog(date = "2023-01-01", weight = 80.0f))
        val bundle = LocalDataBundle(logs, emptyList(), emptyList())
        
        // User says "How am I doing?"
        val response = IronIntelligenceEngine.processQuery("how am i doing", bundle)
        assertTrue("Should trigger insight engine", response.text.contains("Iron Coach") || response.text.contains("insight"))
    }

    @Test
    fun testComplexMultiIntentQuery() {
        val today = LocalDate.now()
        val logs = listOf(
            DailyLog(date = today.minusDays(10).toString(), notes = "Bench Press 100kg"),
            DailyLog(date = today.minusDays(5).toString(), notes = "Chest Press 80kg")
        )
        val bundle = LocalDataBundle(logs, emptyList(), emptyList())
        
        // Query: "How many days ago did I hit my chest max?"
        // Should find Bench Press 100kg (since it's higher than 80kg) and calculate 10 days ago.
        val response = IronIntelligenceEngine.processQuery("How many days ago did I hit my chest max?", bundle)
        assertTrue("Should identify chest max", response.text.contains("chest max", ignoreCase = true))
        assertTrue("Should mention bench press", response.text.contains("bench press", ignoreCase = true))
        assertTrue("Should calculate 10 days ago", response.text.contains("10 days ago"))
    }

    @Test
    fun testFalsePositivePrevention() {
        val logs = listOf(DailyLog(date = "2023-01-01", weight = 80.0f))
        val bundle = LocalDataBundle(logs, emptyList(), emptyList())
        
        // 1. "Preach" should NOT match "reach" (weight goal)
        val response1 = IronIntelligenceEngine.processQuery("I need to preach more", bundle)
        assertTrue("Should not match weight goal", response1.text.contains("not sure"))

        // 2. "Cluster" should NOT match "lost" (weight loss)
        val response2 = IronIntelligenceEngine.processQuery("This is a cluster", bundle)
        assertTrue("Should not match weight loss", response2.text.contains("not sure"))
    }

    @Test
    fun testSmartFallback() {
        val logs = listOf(
            DailyLog(date = "2023-01-01", notes = "Squats 100kg")
        )
        val bundle = LocalDataBundle(logs, emptyList(), emptyList())
        
        val response = IronIntelligenceEngine.processQuery("gibberish", bundle)
        assertTrue("Should mention squats in suggestion", response.text.contains("squats", ignoreCase = true))
    }

    @Test
    fun testMultiSetPRExtraction() {
        // Stress Test: Multiple weights in one note
        val logs = listOf(
            DailyLog(date = "2023-01-01", notes = "Bench Press: 100kg, 110kg, 105kg")
        )
        val records = IronIntelligenceEngine.getAllPersonalRecords(logs)
        
        assertEquals(110.0, records["bench press"]?.first)
    }

    @Test
    fun testMixedUnitComparison() {
        // Stress Test: Comparing kg and lbs
        val logs = listOf(
            DailyLog(date = "2023-01-01", notes = "Squats 100kg"), // 100kg
            DailyLog(date = "2023-01-02", notes = "Squats 250lbs") // ~113.4kg (Should Win)
        )
        val records = IronIntelligenceEngine.getAllPersonalRecords(logs)
        
        assertEquals(250.0, records["squats"]?.first)
        assertEquals("lbs", records["squats"]?.second)
    }
}
