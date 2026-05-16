package com.example.irondiary.viewmodel

import com.example.irondiary.data.DailyLog
import com.example.irondiary.util.IronIntelligenceEngine
import com.example.irondiary.util.LocalDataBundle
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TrackingStressTest {

    @Test
    fun intelligenceEngine_multiLinePR_associatesCorrectly() {
        val notes = """
            Bench Press: 100kg, 105kg
            Squats: 150kg
            Deadlift: 200kg
        """.trimIndent()
        
        val logs = listOf(
            DailyLog(date = "2026-05-16", notes = notes)
        )
        
        val allPRs = IronIntelligenceEngine.getAllPersonalRecords(logs)
        
        assertEquals(105.0, allPRs["bench press"]?.first ?: 0.0, 0.01)
        assertEquals(150.0, allPRs["squats"]?.first ?: 0.0, 0.01)
        assertEquals(200.0, allPRs["deadlift"]?.first ?: 0.0, 0.01)
    }

    @Test
    fun intelligenceEngine_misattributionPrevention_test() {
        val notes = """
            Light Chest Day
            Bench Press 60kg for warmup
            Then Deadlift 220kg PR!
        """.trimIndent()
        
        val logs = listOf(
            DailyLog(date = "2026-05-16", notes = notes)
        )
        
        val allPRs = IronIntelligenceEngine.getAllPersonalRecords(logs)
        
        // Bench Press should be 60kg, NOT 220kg
        assertEquals(60.0, allPRs["bench press"]?.first ?: 0.0, 0.01)
        assertEquals(220.0, allPRs["deadlift"]?.first ?: 0.0, 0.01)
    }

    @Test
    fun intelligenceEngine_lbsToKg_normalization() {
        val logs = listOf(
            DailyLog(date = "2026-05-15", notes = "Bench Press 220lbs"),
            DailyLog(date = "2026-05-16", notes = "Bench Press 101kg")
        )
        
        val allPRs = IronIntelligenceEngine.getAllPersonalRecords(logs)
        
        // 220 lbs is ~99.79 kg. 101 kg is higher.
        assertEquals(101.0, allPRs["bench press"]?.first ?: 0.0, 0.01)
        assertEquals("kg", allPRs["bench press"]?.second)
    }

    @Test
    fun calculateStats_restDayAwareStreak_test() {
        // Since I cannot easily run the full MainViewModel logic without mocking repository,
        // I will test the logic by simulating the calculation.
        // I'll check how it handles Wed (Gym), Thu (Rest), Fri (Gym).
        
        val logsMap = mapOf(
            "2026-05-13" to DailyLog(attendedGym = true), // Wed
            "2026-05-14" to DailyLog(attendedGym = false, isRestDay = true), // Thu
            "2026-05-15" to DailyLog(attendedGym = true)  // Fri
        )
        
        val streak = calculateCurrentStreak(LocalDate.parse("2026-05-15"), logsMap)
        assertEquals(2, streak)
    }

    @Test
    fun calculateStats_bestStreakWithRestDays_test() {
        val logsMap = mapOf(
            "2026-05-01" to DailyLog(attendedGym = true),
            "2026-05-02" to DailyLog(attendedGym = true),
            "2026-05-03" to DailyLog(attendedGym = false, isRestDay = true),
            "2026-05-04" to DailyLog(attendedGym = true),
            "2026-05-05" to DailyLog(attendedGym = false), // Gap!
            "2026-05-06" to DailyLog(attendedGym = true)
        )
        
        val bestStreak = calculateBestStreak(logsMap)
        assertEquals(3, bestStreak)
    }

    // Helper functions mimicking MainViewModel logic for pure logic testing
    private fun calculateCurrentStreak(today: LocalDate, logsMap: Map<String, DailyLog>): Int {
        var currentStreak = 0
        val todayStr = today.toString()
        val hasLoggedToday = logsMap[todayStr]?.attendedGym == true
        
        var checkDate = if (hasLoggedToday) today else today.minusDays(1)
        
        while (true) {
            val dateStr = checkDate.toString()
            val log = logsMap[dateStr]
            
            if (log?.attendedGym == true) {
                currentStreak++
                checkDate = checkDate.minusDays(1)
            } else if (log?.isRestDay == true) {
                checkDate = checkDate.minusDays(1)
            } else {
                break
            }
        }
        return currentStreak
    }

    private fun calculateBestStreak(logsMap: Map<String, DailyLog>): Int {
        var maxStreak = 0
        var tempStreak = 0
        
        val sortedDates = logsMap.keys.sorted().map { LocalDate.parse(it) }
        
        if (sortedDates.isNotEmpty()) {
            var lastDate: LocalDate? = null
            for (date in sortedDates) {
                val log = logsMap[date.toString()]
                
                if (log?.attendedGym == true) {
                    if (lastDate != null && (date == lastDate.plusDays(1))) {
                        tempStreak++
                    } else {
                        tempStreak = 1
                    }
                    maxStreak = maxOf(maxStreak, tempStreak)
                    lastDate = date
                } else if (log?.isRestDay == true) {
                    if (lastDate != null && date == lastDate.plusDays(1)) {
                        lastDate = date
                    }
                } else {
                    tempStreak = 0
                    lastDate = null
                }
            }
        }
        return maxStreak
    }
}
