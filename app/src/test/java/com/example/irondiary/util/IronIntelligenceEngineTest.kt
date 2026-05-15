package com.example.irondiary.util

import com.example.irondiary.data.DailyLog
import com.example.irondiary.data.model.Task
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
        val bundle = LocalDataBundle(logs, emptyList())
        val response = IronIntelligenceEngine.processQuery("What is my highest weight?", bundle)
        assertTrue("Response should contain 85.0", response.contains("85.0"))
    }

    @Test
    fun testAverageWeightQuery() {
        val logs = listOf(
            DailyLog(date = "2023-01-01", weight = 70f),
            DailyLog(date = "2023-01-02", weight = 80f)
        )
        val bundle = LocalDataBundle(logs, emptyList())
        val response = IronIntelligenceEngine.processQuery("average weight", bundle)
        assertTrue("Response should contain 75.0", response.contains("75.0"))
    }

    @Test
    fun testGymStatsQuery() {
        val logs = listOf(
            DailyLog(date = "2023-01-01", attendedGym = true),
            DailyLog(date = "2023-01-02", attendedGym = false),
            DailyLog(date = "2023-01-03", attendedGym = true)
        )
        val bundle = LocalDataBundle(logs, emptyList())
        val response = IronIntelligenceEngine.processQuery("how many gym sessions", bundle)
        assertTrue("Response should contain 2", response.contains("2"))
    }

    @Test
    fun testTaskStatsQuery() {
        val tasks = listOf(
            Task(description = "Task 1", completed = true),
            Task(description = "Task 2", completed = false)
        )
        val bundle = LocalDataBundle(emptyList(), tasks)
        val response = IronIntelligenceEngine.processQuery("how many tasks pending", bundle)
        assertTrue("Response should mention 1 pending task", response.contains("1 pending"))
    }
}
