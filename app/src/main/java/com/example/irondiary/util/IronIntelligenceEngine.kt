package com.example.irondiary.util

import com.example.irondiary.data.DailyLog
import com.example.irondiary.data.model.Task
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class LocalDataBundle(
    val logs: List<DailyLog>,
    val tasks: List<Task>
)

object IronIntelligenceEngine {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun processQuery(query: String, bundle: LocalDataBundle): String {
        val q = query.lowercase(Locale.getDefault()).trim()

        return when {
            // Weight Stats
            q.contains("highest weight") || q.contains("max weight") -> getHighestWeight(bundle.logs)
            q.contains("lowest weight") || q.contains("min weight") -> getLowestWeight(bundle.logs)
            q.contains("average weight") || q.contains("avg weight") -> getAverageWeight(bundle.logs)
            
            // Weight Trends
            q.contains("weight loss") || q.contains("lost") || q.contains("gain") -> getWeightTrend(bundle.logs)
            
            // Weight History (Specific Dates)
            q.contains("weight on") || q.contains("weight for") -> getWeightOnDate(q, bundle.logs)
            
            // Gym Stats
            q.contains("gym") || q.contains("workout") -> getGymStats(q, bundle.logs)
            
            // Task Stats
            q.contains("task") || q.contains("todo") -> getTaskStats(bundle.tasks)
            
            // General Greeting / Help
            q.contains("hi") || q.contains("hello") || q.contains("help") -> 
                "I can answer questions about your weight trends, gym attendance, and pending tasks. Try asking 'What's my average weight?' or 'Gym stats this month'."
            
            else -> "I'm not sure about that yet. Try asking about your weight trends, gym attendance, or tasks!"
        }
    }

    private fun getHighestWeight(logs: List<DailyLog>): String {
        val max = logs.filter { it.weight != null }.maxByOrNull { it.weight!! }
        return if (max != null) {
            "Your highest weight recorded was ${max.weight} kg on ${formatDisplayDate(max.date)}."
        } else {
            "I don't have enough weight records to find a maximum."
        }
    }

    private fun getLowestWeight(logs: List<DailyLog>): String {
        val min = logs.filter { it.weight != null && it.weight > 0 }.minByOrNull { it.weight!! }
        return if (min != null) {
            "Your lowest weight recorded was ${min.weight} kg on ${formatDisplayDate(min.date)}."
        } else {
            "I don't have enough weight records to find a minimum."
        }
    }

    private fun getAverageWeight(logs: List<DailyLog>): String {
        val weights = logs.filter { it.weight != null && it.weight > 0 }.map { it.weight!! }
        return if (weights.isNotEmpty()) {
            val avg = weights.average()
            "Your average weight across ${weights.size} entries is ${String.format("%.1f", avg)} kg."
        } else {
            "I don't have any weight records to calculate an average."
        }
    }

    private fun getWeightTrend(logs: List<DailyLog>): String {
        val validLogs = logs.filter { it.weight != null && it.weight > 0 }.sortedBy { it.date }
        if (validLogs.size < 2) return "I need at least two weight entries to show a trend."

        val first = validLogs.first()
        val last = validLogs.last()
        val diff = last.weight!! - first.weight!!

        return if (diff < 0) {
            "You've lost ${String.format("%.1f", -diff)} kg since your first entry on ${formatDisplayDate(first.date)}."
        } else if (diff > 0) {
            "You've gained ${String.format("%.1f", diff)} kg since your first entry on ${formatDisplayDate(first.date)}."
        } else {
            "Your weight has remained stable since your first entry."
        }
    }

    private fun getWeightOnDate(query: String, logs: List<DailyLog>): String {
        // Simple today/yesterday check
        val targetDate = when {
            query.contains("today") -> LocalDate.now()
            query.contains("yesterday") -> LocalDate.now().minusDays(1)
            else -> null
        }

        if (targetDate != null) {
            val dateStr = targetDate.format(dateFormatter)
            val log = logs.find { it.date == dateStr }
            return if (log?.weight != null) {
                "Your weight on ${formatDisplayDate(dateStr)} was ${log.weight} kg."
            } else {
                "I don't have a weight record for ${formatDisplayDate(dateStr)}."
            }
        }

        return "Try specifying a date like 'today' or 'yesterday'. (I'm still learning to parse complex dates!)"
    }

    private fun getGymStats(query: String, logs: List<DailyLog>): String {
        val gymLogs = logs.filter { it.attendedGym }
        val last30Days = LocalDate.now().minusDays(30)
        val recentGymLogs = gymLogs.filter { 
            LocalDate.parse(it.date, dateFormatter).isAfter(last30Days)
        }

        return if (query.contains("month") || query.contains("recent")) {
            "You've hit the gym ${recentGymLogs.size} times in the last 30 days. Keep it up!"
        } else {
            "Total gym sessions recorded: ${gymLogs.size}. You're making progress!"
        }
    }

    private fun getTaskStats(tasks: List<Task>): String {
        val pending = tasks.count { !it.completed }
        val completed = tasks.count { it.completed }
        
        return if (pending > 0) {
            "You have $pending pending tasks and you've completed $completed so far. What's next on the list?"
        } else if (completed > 0) {
            "All tasks completed! You've finished $completed tasks today."
        } else {
            "No tasks found for today."
        }
    }

    private fun formatDisplayDate(dateStr: String): String {
        return try {
            val date = LocalDate.parse(dateStr, dateFormatter)
            date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
        } catch (e: Exception) {
            dateStr
        }
    }
}
