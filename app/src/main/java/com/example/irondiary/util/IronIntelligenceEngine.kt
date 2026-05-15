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
    
    enum class GoalType { LOSS, GAIN, NEUTRAL }

    private fun detectGoalType(logs: List<DailyLog>, query: String? = null): GoalType {
        val validLogs = logs.filter { it.weight != null && it.weight > 0 }.sortedBy { it.date }
        if (validLogs.isEmpty()) return GoalType.NEUTRAL
        
        val lastWeight = validLogs.last().weight!!
        
        // If query has a number, assume it's a target weight
        if (query != null && (query.contains("reach") || query.contains("goal") || query.contains("target"))) {
            val target = query.split(Regex("[^0-9.]")).mapNotNull { it.toFloatOrNull() }.firstOrNull()
            if (target != null) {
                return if (target > lastWeight) GoalType.GAIN else GoalType.LOSS
            }
        }
        
        // Fallback: Check if they are consistently gaining or losing over the last 14 days
        val recentLogs = validLogs.takeLast(14)
        if (recentLogs.size >= 3) {
            val diff = recentLogs.last().weight!! - recentLogs.first().weight!!
            if (diff > 1.5f) return GoalType.GAIN
            if (diff < -1.5f) return GoalType.LOSS
        }

        return GoalType.NEUTRAL
    }

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

    private val exerciseToMuscleMap = mapOf(
        "bench press" to "chest",
        "chest press" to "chest",
        "flyes" to "chest",
        "pushups" to "chest",
        "deadlift" to "back",
        "pullups" to "back",
        "rows" to "back",
        "lat pulldown" to "back",
        "squats" to "legs",
        "leg press" to "legs",
        "lunges" to "legs",
        "curls" to "arms",
        "skull crushers" to "arms",
        "shoulder press" to "shoulders",
        "lateral raises" to "shoulders"
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

    fun getNextWorkoutRecommendation(logs: List<DailyLog>): String {
        if (logs.isEmpty()) return "I don't have enough history yet to suggest a workout. Start by logging your first session!"

        val muscleLastTrained = mutableMapOf<String, LocalDate>()
        
        // We only care about parent muscle groups for high-level split
        val mainMuscles = listOf("chest", "back", "legs", "shoulders", "arms", "abs")
        
        logs.sortedBy { it.date }.forEach { log ->
            val notes = log.notes?.lowercase(Locale.getDefault()) ?: ""
            val date = try { LocalDate.parse(log.date, dateFormatter) } catch(e: Exception) { null }
            
            if (date != null) {
                mainMuscles.forEach { muscle ->
                    val aliases = (muscleHierarchy.filter { it.value.contains(muscle) }.keys + muscle).toSet()
                    val relatedExercises = exerciseToMuscleMap.filter { it.value == muscle }.keys
                    if (aliases.any { notes.contains(it) } || relatedExercises.any { notes.contains(it) }) {
                        muscleLastTrained[muscle] = date
                    }
                }
            }
        }
        
        if (muscleLastTrained.isEmpty()) {
            return "I see your gym attendance, but your notes don't mention specific muscle groups. Try adding 'Chest Day' or 'Legs' to your daily notes so I can track your split!"
        }
        
        // Find the muscle trained longest ago, or never trained
        val neverTrained = mainMuscles.filter { !muscleLastTrained.containsKey(it) }
        
        return if (neverTrained.isNotEmpty()) {
            val muscle = neverTrained.first()
            "I don't see any record of you training ${muscle.uppercase()} yet! Why not kick things off with a solid ${muscle.replaceFirstChar { it.uppercase() }} session today?"
        } else {
            val oldestEntry = mainMuscles
                .filter { muscleLastTrained.containsKey(it) }
                .minByOrNull { muscleLastTrained[it]!! }
            
            if (oldestEntry != null) {
                val lastDate = muscleLastTrained[oldestEntry]!!
                val daysSince = java.time.temporal.ChronoUnit.DAYS.between(lastDate, LocalDate.now())
                
                when {
                    daysSince >= 7 -> "You haven't trained $oldestEntry in over a week ($daysSince days)! It should be fully recovered. How about a ${oldestEntry.replaceFirstChar { it.uppercase() }} session today?"
                    daysSince >= 4 -> "It's been $daysSince days since your last $oldestEntry session. This is a great time to hit them again."
                    else -> "Your muscle groups are looking well-balanced! Your $oldestEntry session was the furthest back ($daysSince days ago). If you're feeling recovered, that's your best bet today."
                }
            } else {
                "Start logging your specific muscle groups in your notes to get personalized workout recommendations!"
            }
        }
    }

    fun processVoiceQuery(matches: List<String>, bundle: LocalDataBundle): IntelligenceResponse {
        // 1. Try to find a high-confidence match in the list of candidates
        for (match in matches) {
            val q = match.lowercase(Locale.getDefault()).trim()
            
            // Check for specific keywords using word boundaries to avoid false positives (like 'pr' in 'press')
            val keywords = listOf("pr", "max", "trend", "weight", "study", "gym")
            val hasGymKeyword = keywords.any { Regex("\\b$it\\b").containsMatchIn(q) }
            val hasExercise = exerciseToMuscleMap.keys.any { q.contains(it) }
            val hasMuscle = muscleGroups.any { Regex("\\b$it\\b").containsMatchIn(q) }
            
            if (hasGymKeyword || hasExercise || hasMuscle) {
                return processQuery(q, bundle)
            }
        }
        
        // 2. Fallback to the first match if no high-confidence gym match found
        return processQuery(matches[0], bundle)
    }

    private fun match(q: String, vararg keywords: String): Boolean {
        return keywords.any { Regex("\\b$it\\b").containsMatchIn(q) }
    }

    fun processQuery(query: String, bundle: LocalDataBundle): IntelligenceResponse {
        val q = query.lowercase(Locale.getDefault()).trim()

        // 1. Detect Multi-Intents
        val isAskingPR = match(q, "pr", "max", "best", "record", "highest weight")
        val isAskingHistory = match(q, "when", "last", "history", "many", "how", "list", "train", "workout")
        val isAskingTrend = match(q, "trend", "loss", "lost", "gain", "gained", "prediction", "forecast")
        
        // 2. Resolve Subject (Muscle or Exercise)
        val detectedMuscle = muscleGroups.find { match(q, it) }
        
        // Fuzzy match for exercises: only if no muscle group matched or it's an exact exercise match
        val detectedExercise = exerciseToMuscleMap.keys.find { q.contains(it) } ?: run {
            if (detectedMuscle == null) {
                exerciseToMuscleMap.keys.find { it.contains(" ") && match(q, it.split(" ")[0]) }
            } else null
        }

        // 3. Handle PR + Muscle/Exercise (The "Chest Max" Stress Test)
        if (isAskingPR) {
            if (detectedExercise != null) {
                return IntelligenceResponse(getPersonalRecord(detectedExercise, bundle.logs))
            } else if (detectedMuscle != null) {
                // Find top PR across all exercises for this muscle
                val relatedExercises = exerciseToMuscleMap.filter { it.value == detectedMuscle }.keys
                val allPRs = getAllPersonalRecords(bundle.logs).filter { relatedExercises.contains(it.key) }
                
                return if (allPRs.isNotEmpty()) {
                    val topPR = allPRs.maxByOrNull { it.value.first }!!
                    val daysAgo = java.time.temporal.ChronoUnit.DAYS.between(
                        LocalDate.parse(topPR.value.third, dateFormatter), 
                        LocalDate.now()
                    )
                    val daysText = if (daysAgo == 0L) "today" else "$daysAgo days ago"
                    IntelligenceResponse("Your $detectedMuscle max is on ${topPR.key}: ${topPR.value.first} ${topPR.value.second}, achieved $daysText. 💪")
                } else {
                    IntelligenceResponse("I couldn't find any recorded PRs for $detectedMuscle exercises. Try logging something like 'Bench Press 100kg'!")
                }
            } else if (q.split(" ").size <= 3) {
                val allPRs = getAllPersonalRecords(bundle.logs)
                if (allPRs.isEmpty()) return IntelligenceResponse("I don't see any PRs in your notes yet. Try logging one like 'Bench Press 100kg'!")
                val top3 = allPRs.entries.sortedByDescending { it.value.first }.take(3)
                val text = "Your Top 3 PRs are: " + top3.joinToString(", ") { "${it.key}: ${it.value.first} ${it.value.second}" }
                return IntelligenceResponse(text)
            }
        }

        // 4. Handle History + Muscle/Exercise
        if (isAskingHistory) {
            if (detectedExercise != null) {
                return IntelligenceResponse(getExerciseHistory(detectedExercise, bundle.logs))
            } else if (detectedMuscle != null) {
                val aliases = muscleHierarchy[detectedMuscle] ?: listOf(detectedMuscle)
                return IntelligenceResponse(getWorkoutHistoryByMuscleGroup(detectedMuscle, aliases, bundle.logs))
            }
        }

        // 5. Handle Trends & Stats
        return when {
            match(q, "stats", "progress", "doing") ->
                IntelligenceResponse(getTopInsight(bundle))

            match(q, "reach", "goal", "prediction", "forecast") ->
                IntelligenceResponse(getWeightPrediction(q, bundle.logs))

            match(q, "highest weight", "max weight") -> IntelligenceResponse(getHighestWeight(bundle.logs))
            match(q, "lowest weight", "min weight") -> IntelligenceResponse(getLowestWeight(bundle.logs))
            match(q, "average weight", "avg weight") -> IntelligenceResponse(getAverageWeight(bundle.logs))
            
            isAskingTrend -> {
                val text = getWeightTrend(bundle.logs)
                val graphData = bundle.logs
                    .filter { it.weight != null && it.weight > 0 }
                    .sortedBy { it.date }
                    .takeLast(7)
                    .map { it.weight!! }
                IntelligenceResponse(text, if (graphData.size >= 2) graphData else null)
            }
            
            match(q, "weight on", "weight for") -> IntelligenceResponse(getWeightOnDate(q, bundle.logs))
            
            match(q, "link", "correlation", "compare", "affect") ->
                IntelligenceResponse(getCorrelationInsights(bundle))

            match(q, "study", "subject", "hours", "productive") ->
                IntelligenceResponse(getStudyStats(q, bundle.sessions))

            match(q, "gym", "workout", "train") -> {
                if (match(q, "should", "suggest", "next")) {
                    IntelligenceResponse(getNextWorkoutRecommendation(bundle.logs))
                } else {
                    IntelligenceResponse(getGymStats(q, bundle.logs))
                }
            }
            
            match(q, "task", "tasks", "todo", "todos") -> IntelligenceResponse(getTaskStats(bundle.tasks))
            
            match(q, "hi", "hello", "help") -> 
                IntelligenceResponse("I can answer questions about your weight, gym split, and study habits. Try asking 'How much did I study this week?' or 'Does the gym affect my studying?'.")
            
            else -> {
                // Smart Fallback Suggestor
                val recentExercise = bundle.logs.sortedByDescending { it.date }.firstOrNull { !it.notes.isNullOrBlank() }?.notes?.split(" ")?.firstOrNull()
                val suggestText = if (recentExercise != null) " I see you've been working on $recentExercise lately. Want to see your progress there?" else ""
                IntelligenceResponse("I'm not sure about that yet. Try asking about your weight trends, gym PRs, or study sessions!$suggestText")
            }
        }
    }

    fun getTopInsight(bundle: LocalDataBundle): String {
        val rec = getNextWorkoutRecommendation(bundle.logs)
        if (rec.contains("haven't trained") || rec.contains("record of you training")) {
            return rec
        }
        
        val corr = getCorrelationInsights(bundle)
        if (corr.contains("longer on days you hit the gym")) {
            return corr
        }
        
        val validLogs = bundle.logs.filter { it.weight != null && it.weight > 0 }.sortedBy { it.date }
        if (validLogs.size >= 2) {
            val last = validLogs.last().weight!!
            val first = validLogs.first().weight!!
            val diff = last - first
            val goal = detectGoalType(bundle.logs)
            
            if (Math.abs(diff) > 2.0) {
                return when {
                    goal == GoalType.GAIN && diff > 0 -> "You've gained ${String.format("%.1f", diff)} kg! Your bulk is working—keep those calories high and sets heavy! 💪"
                    goal == GoalType.LOSS && diff < 0 -> "You've lost ${String.format("%.1f", -diff)} kg! Your cut is looking sharp. Discipline is paying off! 🔥"
                    diff < 0 -> "You've lost ${String.format("%.1f", -diff)} kg since you started. Great work! (If you're bulking, try increasing your surplus!)"
                    else -> "You've gained ${String.format("%.1f", diff)} kg since you started. (If you're cutting, keep an eye on your caloric intake!)"
                }
            }
        }
        
        return "Keep logging your sessions and weight to unlock more deep insights from your Iron Coach!"
    }

    fun getAllPersonalRecords(logs: List<DailyLog>): Map<String, Triple<Double, String, String>> {
        val weightRegex = Regex("""(\d+(?:\.\d+)?)\s*(kg|lbs)""", RegexOption.IGNORE_CASE)
        val allRecords = mutableMapOf<String, Triple<Double, String, String>>()

        exerciseToMuscleMap.keys.forEach { exercise ->
            val records = logs.mapNotNull { log ->
                val notes = log.notes?.lowercase(Locale.getDefault()) ?: ""
                if (notes.contains(exercise)) {
                    val match = weightRegex.find(notes)
                    if (match != null) {
                        val weightValue = match.groupValues[1].toDouble()
                        val unit = match.groupValues[2].lowercase()
                        Triple(weightValue, unit, log.date)
                    } else null
                } else null
            }.sortedByDescending { it.first }

            if (records.isNotEmpty()) {
                val (weight, unit, date) = records.first()
                allRecords[exercise] = Triple(weight, unit, date)
            }
        }
        return allRecords
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
        val relatedExercises = exerciseToMuscleMap.filter { it.value == muscle }.keys
        val allAliases = (aliases + relatedExercises).toSet()
        
        val matchingLogs = logs.filter { log ->
            val notes = log.notes?.lowercase(Locale.getDefault()) ?: ""
            allAliases.any { notes.contains(it) }
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

    private fun getExerciseHistory(exercise: String, logs: List<DailyLog>): String {
        val matchingLogs = logs.filter { log ->
            val notes = log.notes?.lowercase(Locale.getDefault()) ?: ""
            notes.contains(exercise)
        }.sortedByDescending { it.date }

        return if (matchingLogs.isNotEmpty()) {
            val dates = matchingLogs.take(3).joinToString(", ") { formatDisplayDate(it.date) }
            val count = matchingLogs.size
            val muscle = exerciseToMuscleMap[exercise]
            "You last performed $exercise on: $dates. (Total: $count times). Since it targets your $muscle, I've factored this into your split recommendations!"
        } else {
            "I couldn't find any logs where you specifically mentioned '$exercise'. Try adding it to your notes!"
        }
    }

    private fun getPersonalRecord(exercise: String, logs: List<DailyLog>): String {
        val weightRegex = Regex("""(\d+(?:\.\d+)?)\s*(kg|lbs)""", RegexOption.IGNORE_CASE)
        
        val records = logs.mapNotNull { log ->
            val notes = log.notes?.lowercase(Locale.getDefault()) ?: ""
            if (notes.contains(exercise)) {
                val match = weightRegex.find(notes)
                if (match != null) {
                    val weightValue = match.groupValues[1].toDouble()
                    val unit = match.groupValues[2].lowercase()
                    Triple(weightValue, unit, log.date)
                } else null
            } else null
        }.sortedByDescending { it.first }

        return if (records.isNotEmpty()) {
            val (weight, unit, date) = records.first()
            "Your Personal Record for $exercise is $weight $unit, achieved on ${formatDisplayDate(date)}. Boom! 💥"
        } else {
            "I couldn't find any recorded weights for $exercise in your notes. Try logging your sets like '$exercise 100kg'!"
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
        val goal = detectGoalType(logs)

        return when {
            diff < 0 && goal == GoalType.GAIN -> "Your weight is down ${String.format("%.1f", -diff)} kg. To reach your bulk goal, you might need more fuel!"
            diff < 0 -> "You've lost ${String.format("%.1f", -diff)} kg since your first entry on ${formatDisplayDate(first.date)}. Sharp progress!"
            diff > 0 && goal == GoalType.LOSS -> "Your weight is up ${String.format("%.1f", diff)} kg. Stay disciplined on your cut!"
            diff > 0 -> "You've gained ${String.format("%.1f", diff)} kg since your first entry on ${formatDisplayDate(first.date)}. Solid gains!"
            else -> "Your weight has remained stable since your first entry."
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
