package com.example.irondiary.util

import com.example.irondiary.data.DailyLog
import org.junit.Test
import org.junit.Assert.*

class IntelligenceDebug {
    
    @Test
    fun debugBackHistory() {
        val logs = listOf(
            DailyLog(date = "2026-05-13", notes = "Back", attendedGym = true),
            DailyLog(date = "2026-05-08", notes = "Back session", attendedGym = true),
            DailyLog(date = "2026-03-28", notes = "Heavy back day", attendedGym = true),
            DailyLog(date = "2025-12-31", notes = "Back workout", attendedGym = true),
            DailyLog(date = "2025-12-02", notes = "Back", attendedGym = true)
        )
        
        val bundle = LocalDataBundle(logs = logs, tasks = emptyList(), sessions = emptyList())
        val response = IronIntelligenceEngine.processQuery("How many times I've trained back", bundle)
        
        println("Response: ${response.text}")
        
        // The user says it only shows 3 sessions (Mar 28, Dec 31, Dec 02)
        // We expect 5 sessions (including May 13 and May 08)
        assertTrue(response.text.contains("Total: 5 sessions found"))
        assertTrue(response.text.contains("May 13, 2026"))
        assertTrue(response.text.contains("May 08, 2026"))
    }
}
