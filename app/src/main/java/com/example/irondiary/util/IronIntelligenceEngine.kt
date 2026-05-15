package com.example.irondiary.util

import com.example.irondiary.data.DailyLog
import com.example.irondiary.data.model.Task
import com.example.irondiary.data.model.StudySession
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class LocalDataBundle(
    val logs: List<DailyLog>,
    val tasks: List<Task>,
    val sessions: List<StudySession>
)

data class IntelligenceResponse(
    val text: String,
    val graphData: List<Float>? = null
)

object IronIntelligenceEngine {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    
    private val muscleGroups = listOf(
        "back", "chest", "shoulders", "legs", "arms", "abs", "core", "cardio", "glutes", "triceps", "biceps", "forearms", "calves", "quads", "hamstrings"
    )

    private val muscleHierarchy = mapOf(
        "triceps" to listOf("triceps", "arms"),
        "biceps" to listOf("biceps", "arms"),
        "quads" to listOf("quads", "legs"),
        "hamstrings" to listOf("hamstrings", "legs"),
        "calves" to listOf("calves", "legs"),
        "abs" to listOf("abs", "core"),
        "glutes" to listOf("glutes", "legs")
    )

    fun getWelcomeMessage(bundle: LocalDataBundle): String {
        val validLogs = bundle.logs.filter { it.weight != null && it.weight > 0 }.sortedBy { it.date }
        val gymLogs = bundle.logs.filter { it.attendedGym }
        val studyHours = bundle.sessions.sumOf { it.duration }
        
        val last7Days = LocalDate.now().minusDays(7)
        val gymCount = gymLogs.count { 
            try { LocalDate.parse(it.date, dateFormatter).isAfter(last7Days) } catch(e: Exception) { false }
        }

        return when {
            validLogs.size >= 3 && gymCount >= 3 -> {
                "Welcome back! You've hit the gym $gymCount times this week and your weight trend is looking solid. You're in peak momentum—what's the plan for today?"
            }
            studyHours > 10 -> {
                "Hi there! You've logged over 10 hours of study recently. Don't forget that a quick gym session could boost your focus even further!"
            }
            validLogs.size >= 2 -> {
                val first = validLogs.first().weight!!
                val last = validLogs.last().weight!!
                val diff = last - first
                if (diff < 0) {
                    "Great to see you! You've lost ${String.format("%.1f", -diff)} kg since you started. You're making real progress—ready to keep going?"
                } else {
                    "Welcome back! I'm tracking your progress. Ask me anything about your weight trends or gym history to see how you're doing."
                }
            }
            else -> "Hi! I'm your Iron Assistant. Log your weight and workouts to see your intelligence dashboard grow!"
        }
    }

    fun processQuery(query: String, bundle: LocalDataBundle): IntelligenceResponse {
        val q = query.lowercase(Locale.getDefault()).trim()

        // Detect Muscle Group Queries
        val detectedMuscle = muscleGroups.find { q.contains(it) }
        val isAskingHistory = q.contains("day") || q.contains("when") || q.contains("last") || q.contains("list") || q.contains("train") || q.contains("workout")
        
        if (detectedMuscle != null && isAskingHistory) {
            val aliases = muscleHierarchy[detectedMuscle] ?: listOf(detectedMuscle)
            return IntelligenceResponse(getWorkoutHistoryByMuscleGroup(detectedMuscle, aliases, bundle.logs))
        }

        return when {
            // Predictions & Goals
            q.contains("reach") || q.contains("goal") || q.contains("prediction") || q.contains("forecast") ->
                IntelligenceResponse(getWeightPrediction(q, bundle.logs))

            // Weight Stats
            q.contains("highest weight") || q.contains("max weight") -> IntelligenceResponse(getHighestWeight(bundle.logs))
            q.contains("lowest weight") || q.contains("min weight") -> IntelligenceResponse(getLowestWeight(bundle.logs))
            q.contains("average weight") || q.contains("avg weight") -> IntelligenceResponse(getAverageWeight(bundle.logs))
            
            // Weight Trends
            q.contains("weight loss") || q.contains("lost") || q.contains("gain") || q.contains("trend") -> {
                val text = getWeightTrend(bundle.logs)
                val graphData = bundle.logs
                    .filter { it.weight != null && it.weight > 0 }
                    .sortedBy { it.date }
                    .takeLast(7)
                    .map { it.weight!! }
                IntelligenceResponse(text, if (graphData.size >= 2) graphData else null)
            }
            
            // Weight History (Specific Dates)
            q.contains("weight on") || q.contains("weight for") -> IntelligenceResponse(getWeightOnDate(q, bundle.logs))
            
            // Correlation Insights
            q.contains("link") || q.contains("correlation") || q.contains("compare") || q.contains("affect") ->
                IntelligenceResponse(getCorrelationInsights(bundle))

            // Study Stats
            q.contains("study") || q.contains("subject") || q.contains("hours") || q.contains("productive") ->
                IntelligenceResponse(getStudyStats(q, bundle.sessions))

            // Gym Stats
            q.contains("gym") || q.contains("workout") -> IntelligenceResponse(getGymStats(q, bundle.logs))
            
            // Task Stats
            q.contains("task") || q.contains("todo") -> IntelligenceResponse(getTaskStats(bundle.tasks))
            
            // General Greeting / Help
            q.contains("hi") || q.contains("hello") || q.contains("help") -> 
                IntelligenceResponse("I can answer questions about your weight, gym split, and study habits. Try asking 'How much did I study this week?' or 'Does the gym affect my studying?'.")
            
            else -> IntelligenceResponse("I'm not sure about that yet. Try asking about your weight, gym progress, or study sessions!")
        }
    }

    private fun getStudyStats(query: String, sessions: List<StudySession>): String {
        if (sessions.isEmpty()) return "I don't see any study sessions logged yet. Start a focus session in the Study tab to track your progress!"

        val last7Days = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
        val recentSessions = sessions.filter { it.updatedAt.toDate().time > last7Days }
        val totalHours = recentSessions.sumOf { it.duration }

        return when {
            query.contains("subject") -> {
                val topSubject = sessions.groupBy { it.subject }
                    .maxByOrNull { it.value.sumOf { s -> s.duration } }?.key
                "Your most studied subject is '$topSubject'. You've put in some serious work there!"
            }
            query.contains("week") || query.contains("recent") -> {
                "In the last 7 days, you've studied for ${String.format("%.1f", totalHours)} hours across ${recentSessions.size} sessions."
            }
            else -> {
                val totalEver = sessions.sumOf { it.duration }
                "You've logged a total of ${String.format("%.1f", totalEver)} hours of deep work since you started using Iron Diary. Impressive!"
            }
        }
    }

    private fun getCorrelationInsights(bundle: LocalDataBundle): String {
        val gymDays = bundle.logs.filter { it.attendedGym }.map { it.date }.toSet()
        if (gymDays.isEmpty() || bundle.sessions.isEmpty()) {
            return "I need a bit more data on both your gym sessions and study focus to find correlations. Keep logging for a few more days!"
        }

        val sessionsOnGymDays = bundle.sessions.filter { 
            val sessionDate = it.date.toDate().toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
            gymDays.contains(sessionDate)
        }
        
        val sessionsOnRestDays = bundle.sessions.filter { 
            val sessionDate = it.date.toDate().toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
            !gymDays.contains(sessionDate)
        }

        val avgDurationGym = if (sessionsOnGymDays.isNotEmpty()) sessionsOnGymDays.map { it.duration }.average() else 0.0
        val avgDurationRest = if (sessionsOnRestDays.isNotEmpty()) sessionsOnRestDays.map { it.duration }.average() else 0.0

        return if (avgDurationGym > avgDurationRest) {
            val percent = ((avgDurationGym - avgDurationRest) / avgDurationRest * 100).toInt()
            "Iron Insight: You study $percent% longer on days you hit the gym! Your physical activity seems to be fueling your focus."
        } else if (avgDurationRest > avgDurationGym) {
            "Iron Insight: You tend to have longer study sessions on your rest days. It looks like you're using that extra recovery time to dive deep into your books."
        } else {
            "Iron Insight: Your study duration is remarkably consistent regardless of whether you hit the gym or not. That's some high-level discipline!"
        }
    }

    private fun getWeightPrediction(query: String, logs: List<DailyLog>): String {
        val validLogs = logs.filter { it.weight != null && it.weight > 0 }.sortedBy { it.date }
        if (validLogs.size < 3) return "I need at least 3 weight entries over time to calculate your velocity and give you a prediction."

        val recentLogs = validLogs.takeLast(14)
        val first = recentLogs.first()
        val last = recentLogs.last()
        
        val daysBetween = java.time.temporal.ChronoUnit.DAYS.between(
            LocalDate.parse(first.date, dateFormatter),
            LocalDate.parse(last.date, dateFormatter)
        ).coerceAtLeast(1)
        
        val totalDelta = last.weight!! - first.weight!!
        val dailyDelta = totalDelta / daysBetween
        val weeklyRate = dailyDelta * 7

        // Extract a number from the query as the target weight
        val targetWeight = query.split(Regex("[^0-9.]")).mapNotNull { it.toFloatOrNull() }.firstOrNull()
        
        return if (targetWeight != null) {
            val currentWeight = last.weight!!
            val remaining = targetWeight - currentWeight
            
            if (Math.abs(dailyDelta) < 0.001) {
                return "Your weight has been very stable lately. At this rate, it's hard to predict when you'll reach $targetWeight kg!"
            }

            if ((remaining > 0 && dailyDelta > 0) || (remaining < 0 && dailyDelta < 0)) {
                val daysToGoal = Math.round((remaining / dailyDelta).toDouble())
                val goalDate = LocalDate.now().plusDays(daysToGoal)
                "At your current rate of ${String.format("%.1f", weeklyRate)} kg/week, you'll reach $targetWeight kg in about $daysToGoal days (${goalDate.format(DateTimeFormatter.ofPattern("MMM dd"))})."
            } else if (Math.abs(remaining) < 0.1f) {
                "You're already at your goal! Current weight: $currentWeight kg."
            } else {
                "Based on your recent trend (${String.format("%.1f", weeklyRate)} kg/week), you are currently moving ${if (dailyDelta > 0) "up" else "down"}, which is away from your goal of $targetWeight kg."
            }
        } else {
            "You are currently ${if (weeklyRate < 0) "losing" else "gaining"} about ${String.format("%.1f", Math.abs(weeklyRate))} kg per week. Try asking 'When will I reach 75kg?' for a specific projection!"
        }
    }

    private fun getWorkoutHistoryByMuscleGroup(muscle: String, aliases: List<String>, logs: List<DailyLog>): String {
        val matchingLogs = logs.filter { log ->
            val notes = log.notes?.lowercase(Locale.getDefault()) ?: ""
            aliases.any { notes.contains(it) }
        }.sortedByDescending { it.date }

        return if (matchingLogs.isNotEmpty()) {
            val dates = matchingLogs.take(5).joinToString(", ") { formatDisplayDate(it.date) }
            val count = matchingLogs.size
            val prefix = if (count > 5) "Your last 5 sessions for $muscle were on: " else "You trained $muscle on: "
            "$prefix$dates. (Total: $count sessions found)"
        } else {
            val aliasMsg = if (aliases.size > 1) " (I also checked for ${aliases.last()})" else ""
            "I couldn't find any logs where you mentioned training $muscle$aliasMsg. Make sure to add it to your daily notes!"
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
