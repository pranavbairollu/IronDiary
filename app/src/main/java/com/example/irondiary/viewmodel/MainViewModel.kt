package com.example.irondiary.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.irondiary.data.DailyLog
import com.example.irondiary.data.Resource
import com.example.irondiary.data.model.StudySession
import com.example.irondiary.data.model.Task
import com.google.firebase.Timestamp
import com.example.irondiary.data.repository.IronDiaryRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date

data class TaskTemplate(
    val category: String,
    val title: String,
    val emoji: String
)

/**
 * The primary ViewModel for managing UI state and business logic in the IronDiary app.
 *
 * It acts as a bridge between the [IronDiaryRepository] and the Compose UI,
 * exposing data as reactive [StateFlow]s and handling user interactions.
 */
class MainViewModel(private val repository: IronDiaryRepository) : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val sharedPreferences = repository.context.getSharedPreferences("IronDiaryPrefs", Context.MODE_PRIVATE)

    private val _dailyLogs = MutableStateFlow<Resource<Map<String, DailyLog>>>(Resource.Success(emptyMap()))
    val dailyLogs: StateFlow<Resource<Map<String, DailyLog>>> = _dailyLogs.asStateFlow()

    private val _weightData = MutableStateFlow<Resource<List<DailyLog>>>(Resource.Success(emptyList()))
    val weightData: StateFlow<Resource<List<DailyLog>>> = _weightData.asStateFlow()

    private val _studySessions = MutableStateFlow<Resource<List<StudySession>>>(Resource.Success(emptyList()))
    val studySessions: StateFlow<Resource<List<StudySession>>> = _studySessions.asStateFlow()

    private val _tasks = MutableStateFlow<Resource<List<Task>>>(Resource.Success(emptyList()))
    val tasks: StateFlow<Resource<List<Task>>> = _tasks.asStateFlow()

    private val _gymStreak = MutableStateFlow(0)
    val gymStreak: StateFlow<Int> = _gymStreak.asStateFlow()

    private val _totalWorkouts = MutableStateFlow(0)
    val totalWorkouts: StateFlow<Int> = _totalWorkouts.asStateFlow()

    private val _isDailyReminderEnabled = MutableStateFlow(false)
    val isDailyReminderEnabled: StateFlow<Boolean> = _isDailyReminderEnabled.asStateFlow()

    private val _saveStatus = MutableStateFlow<Resource<Unit>?>(null)
    val saveStatus: StateFlow<Resource<Unit>?> = _saveStatus.asStateFlow()

    private val _templateStatus = MutableStateFlow<Resource<Unit>?>(null)
    val templateStatus: StateFlow<Resource<Unit>?> = _templateStatus.asStateFlow()

    private val _customTemplates = MutableStateFlow<List<TaskTemplate>>(emptyList())
    
    private val defaultTemplates = listOf(
        TaskTemplate("Health", "Drink water, keep healthy", "💧"),
        TaskTemplate("Health", "Go to bed early", "🌙"),
        TaskTemplate("Health", "Get up early", "🌅"),
        TaskTemplate("Health", "Medication reminder", "💊"),
        TaskTemplate("Health", "Take a break", "☕"),
        TaskTemplate("Health", "Eat fruits", "🍎"),
        TaskTemplate("Life", "Clean house", "🧹"),
        TaskTemplate("Life", "Skin care", "🧖‍♀️"),
        TaskTemplate("Life", "Go shopping", "🛒"),
        TaskTemplate("Life", "Feed pets", "🐾"),
        TaskTemplate("Life", "Study", "👩‍🎓"),
        TaskTemplate("Life", "Keep reading", "📚"),
        TaskTemplate("Life", "Learn a foreign language", "🗣️"),
        TaskTemplate("Life", "Learn instruments", "🎸"),
        TaskTemplate("Life", "Keep in touch with family", "📞"),
        TaskTemplate("Sports", "Go exercising", "🏃‍♂️"),
        TaskTemplate("Sports", "Stretch", "🤸"),
        TaskTemplate("Sports", "Swimming", "🏊‍♂️"),
        TaskTemplate("Sports", "Practice Yoga", "🧘‍♀️"),
        TaskTemplate("Sports", "Cycling", "🚴"),
        TaskTemplate("Mind", "Meditation", "🪷"),
        TaskTemplate("Mind", "Be grateful for what you have", "🙏"),
        TaskTemplate("Mind", "Pray", "🤲"),
        TaskTemplate("Mind", "Practice smiling and be happy", "😊"),
        TaskTemplate("Quit", "Eat less sugar", "🍬"),
        TaskTemplate("Quit", "Less time on your phone", "📱"),
        TaskTemplate("Quit", "Play less game", "🎮")
    )

    private val _categorizedTemplates = MutableStateFlow<Map<String, List<TaskTemplate>>>(emptyMap())
    val categorizedTemplates: StateFlow<Map<String, List<TaskTemplate>>> = _categorizedTemplates.asStateFlow()

    private var dailyLogsJob: Job? = null
    private var weightDataJob: Job? = null
    private var studySessionsJob: Job? = null
    private var tasksJob: Job? = null
    
    // Map to keep track of active toggle jobs per taskId to prevent rapid UI toggling floods
    private val activeToggleJobs = mutableMapOf<String, Job>()
    
    // Map to keep track of active save jobs per date to prevent redundant save floods
    private val activeSaveJobs = mutableMapOf<String, Job>()

    init {
        fetchDailyLogs()
        fetchWeightData()
        fetchStudySessions()
        fetchTasks()
        loadTemplates()
        
        // Trigger background sync on app startup (Safeguard #4)
        repository.enqueueSync()
    }


    fun fetchDailyLogs() {
        dailyLogsJob?.cancel()
        dailyLogsJob = viewModelScope.launch {
            _dailyLogs.value = Resource.Loading
            val userId = auth.currentUser?.uid
            if (userId == null) {
                _dailyLogs.value = Resource.Error("User not logged in.")
                return@launch
            }

            try {
                repository.getDailyLogs(userId).collect { logsMap ->
                    _dailyLogs.value = Resource.Success(logsMap)
                    calculateStats(logsMap)
                }
            } catch (e: Exception) {
                _dailyLogs.value = Resource.Error("Error loading logs: ${e.message}")
            }
        }
    }

    fun fetchWeightData() {
        weightDataJob?.cancel()
        weightDataJob = viewModelScope.launch {
            _weightData.value = Resource.Loading
            val userId = auth.currentUser?.uid
            if (userId == null) {
                _weightData.value = Resource.Error("User not logged in.")
                return@launch
            }

            try {
                repository.getWeightData(userId).collect { data ->
                    _weightData.value = Resource.Success(data)
                }
            } catch (e: Exception) {
                _weightData.value = Resource.Error("Error loading weight data: ${e.message}")
            }
        }
    }

    fun fetchStudySessions() {
        studySessionsJob?.cancel()
        studySessionsJob = viewModelScope.launch {
            _studySessions.value = Resource.Loading
            val userId = auth.currentUser?.uid
            if (userId == null) {
                _studySessions.value = Resource.Error("User not logged in.")
                return@launch
            }

            try {
                repository.getStudySessions(userId).collect { sessions ->
                    _studySessions.value = Resource.Success(sessions)
                }
            } catch (e: Exception) {
                _studySessions.value = Resource.Error("Error loading study sessions: ${e.message}")
            }
        }
    }

    fun fetchTasks() {
        tasksJob?.cancel()
        tasksJob = viewModelScope.launch {
            _tasks.value = Resource.Loading
            val userId = auth.currentUser?.uid
            if (userId == null) {
                _tasks.value = Resource.Error("You must be logged in to see your data.")
                return@launch
            }

            try {
                repository.getTasks(userId).collect { taskList ->
                    _tasks.value = Resource.Success(taskList)
                }
            } catch (e: Exception) {
                _tasks.value = Resource.Error("Error loading tasks: ${e.message}")
            }
        }
    }

    fun saveDailyLog(log: DailyLog) {
        val userId = auth.currentUser?.uid ?: return
        
        // Validation: Ensure weight is within a realistic range and notes aren't too long
        val weight = log.weight
        if (weight != null && (weight <= 0 || weight > 500)) {
            _saveStatus.value = Resource.Error("Weight must be between 0 and 500 kg.")
            return
        }
        
        val notes = log.notes
        if (notes != null && notes.length > 2000) {
            _saveStatus.value = Resource.Error("Notes cannot exceed 2000 characters.")
            return
        }

        _saveStatus.value = Resource.Loading
        
        // Cancel any existing save job for this specific date
        activeSaveJobs[log.date]?.cancel()
        
        activeSaveJobs[log.date] = viewModelScope.launch {
            try {
                repository.saveDailyLog(log, userId)
                _saveStatus.value = Resource.Success(Unit)
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    _saveStatus.value = Resource.Error("Failed to save daily log: ${e.message}")
                }
            } finally {
                activeSaveJobs.remove(log.date)
            }
        }
    }

    fun addStudySession(subject: String, duration: Float) {
        val trimmedSubject = subject.trim()
        if (trimmedSubject.isEmpty()) {
            _saveStatus.value = Resource.Error("Subject cannot be empty.")
            return
        }
        if (trimmedSubject.length > 100) {
            _saveStatus.value = Resource.Error("Subject cannot exceed 100 characters.")
            return
        }
        if (duration.isNaN() || duration.isInfinite() || duration <= 0f || duration > 24f) {
            _saveStatus.value = Resource.Error("Duration must be between 0 and 24 hours.")
            return
        }

        val userId = auth.currentUser?.uid ?: return
        _saveStatus.value = Resource.Loading
        
        activeSaveJobs["study_session_save"]?.cancel()
        
        activeSaveJobs["study_session_save"] = viewModelScope.launch {
            try {
                val session = StudySession(subject = trimmedSubject, date = com.google.firebase.Timestamp(Date()), duration = duration.toDouble())
                repository.addStudySession(session, userId)
                _saveStatus.value = Resource.Success(Unit)
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    _saveStatus.value = Resource.Error("Failed to add study session: ${e.message}")
                }
            } finally {
                activeSaveJobs.remove("study_session_save")
            }
        }
    }

    fun deleteStudySession(session: StudySession) {
        val docId = session.docId
        if (docId.isEmpty()) {
            Log.e("MainViewModel", "Cannot delete study session with empty docId: ${session.subject}")
            _saveStatus.value = Resource.Error("Logic Error: Missing Session ID. Please refresh.")
            return
        }

        val userId = auth.currentUser?.uid ?: return
        _saveStatus.value = Resource.Loading
        viewModelScope.launch {
            try {
                Log.d("MainViewModel", "Initiating deletion of session $docId for user $userId")
                repository.deleteStudySession(docId, userId)
                _saveStatus.value = Resource.Success(Unit)
                Log.d("MainViewModel", "Successfully marked session $docId as deleted")
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to delete study session $docId", e)
                _saveStatus.value = Resource.Error("Failed to delete study session: ${e.message}")
            }
        }
    }

    fun addTask(description: String, reminderTime: Long? = null) {
        val trimmedDesc = description.trim()
        if (trimmedDesc.isEmpty()) {
            _saveStatus.value = Resource.Error("Task description cannot be empty.")
            return
        }
        if (trimmedDesc.length > 500) {
            _saveStatus.value = Resource.Error("Task description is too long.")
            return
        }

        val userId = auth.currentUser?.uid
        if (userId == null) {
            _saveStatus.value = Resource.Error("Must be logged in.")
            return
        }

        _saveStatus.value = Resource.Loading
        viewModelScope.launch {
            try {
                val taskId = repository.addTask(userId, trimmedDesc, reminderTime)
                
                // Schedule high-priority task reminder if set
                reminderTime?.let { time ->
                    com.example.irondiary.util.NotificationHelper.scheduleTaskReminder(
                        repository.context,
                        taskId,
                        trimmedDesc,
                        time
                    )
                }
                
                _saveStatus.value = Resource.Success(Unit)
            } catch (e: Exception) {
                _saveStatus.value = Resource.Error("Failed to save task: ${e.message}")
            }
        }
    }

    fun toggleTaskCompletion(task: Task) {
        val userId = auth.currentUser?.uid ?: return
        val docId = task.docId
        if (docId.isEmpty()) return
        
        // Cancel any existing toggle job for this specific task
        activeToggleJobs[docId]?.cancel()
        
        // Debounce: Wait 300ms before pushing to repository
        activeToggleJobs[docId] = viewModelScope.launch {
            try {
                delay(300)
                
                val newCompletedStatus = !task.completed
                val updatedTask = task.copy(
                    completed = newCompletedStatus,
                    completedDate = if (newCompletedStatus) com.google.firebase.Timestamp.now() else null
                )
                repository.updateTask(updatedTask, userId)
                // Success is handled by the Flow observer in the ViewModel
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.e("MainViewModel", "Failed to toggle task", e)
                }
            } finally {
                activeToggleJobs.remove(docId)
            }
        }
    }

    fun updateTaskDescription(task: Task, newDescription: String) {
        val trimmedDesc = newDescription.trim()
        if (trimmedDesc.isEmpty()) {
            _saveStatus.value = Resource.Error("Task description cannot be empty.")
            return
        }
        if (trimmedDesc.length > 500) {
            _saveStatus.value = Resource.Error("Task description is too long.")
            return
        }

        val userId = auth.currentUser?.uid
        if (userId == null) {
            _saveStatus.value = Resource.Error("Must be logged in.")
            return
        }

        _saveStatus.value = Resource.Loading
        val updatedTask = task.copy(description = trimmedDesc)

        viewModelScope.launch {
            try {
                repository.updateTask(updatedTask, userId)
                _saveStatus.value = Resource.Success(Unit)
            } catch (e: Exception) {
                _saveStatus.value = Resource.Error("Failed to update task description: ${e.message}")
            }
        }
    }

    fun deleteTask(task: Task) {
        val docId = task.docId
        if (docId.isEmpty()) return

        val userId = auth.currentUser?.uid ?: return

        _saveStatus.value = Resource.Loading
        viewModelScope.launch {
            try {
                repository.deleteTask(docId, userId)
                _saveStatus.value = Resource.Success(Unit)
            } catch (e: Exception) {
                _saveStatus.value = Resource.Error("Failed to delete task: ${e.message}")
            }
        }
    }

    fun clearCompletedTasks() {
        val userId = auth.currentUser?.uid ?: return
        _saveStatus.value = Resource.Loading
        
        viewModelScope.launch {
            try {
                repository.clearCompletedTasks(userId)
                _saveStatus.value = Resource.Success(Unit)
            } catch (e: Exception) {
                _saveStatus.value = Resource.Error("Failed to clear completed: ${e.message}")
            }
        }
    }

    fun deleteAllTasks() {
        val userId = auth.currentUser?.uid ?: return
        _saveStatus.value = Resource.Loading
        
        viewModelScope.launch {
            try {
                repository.deleteAllTasks(userId)
                _saveStatus.value = Resource.Success(Unit)
            } catch (e: Exception) {
                _saveStatus.value = Resource.Error("Failed to delete all tasks: ${e.message}")
            }
        }
    }

    fun toggleGymAttendance(date: java.time.LocalDate) {
        val dateId = date.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
        val currentLogs = (dailyLogs.value as? Resource.Success)?.data ?: emptyMap()
        val existingLog = currentLogs[dateId] ?: DailyLog(date = dateId)
        
        saveDailyLog(existingLog.copy(attendedGym = !existingLog.attendedGym))
    }

    private fun calculateStats(logsMap: Map<String, DailyLog>) {
        val today = java.time.LocalDate.now()
        var streak = 0
        var checkDate = today
        
        // Count total workouts
        _totalWorkouts.value = logsMap.values.count { it.attendedGym }
        
        // Calculate current streak
        // If user didn't work out today, streak might still be active from yesterday
        if (logsMap[checkDate.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)]?.attendedGym != true) {
            checkDate = checkDate.minusDays(1)
        }
        
        while (true) {
            val dateStr = checkDate.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
            if (logsMap[dateStr]?.attendedGym == true) {
                streak++
                checkDate = checkDate.minusDays(1)
            } else {
                break
            }
        }
        _gymStreak.value = streak
    }


    fun resetSaveStatus() {
        _saveStatus.value = null
    }

    fun resetTemplateStatus() {
        _templateStatus.value = null
    }

    fun updateTaskReminder(task: Task, newReminderTime: Long?) {
        val userId = auth.currentUser?.uid ?: return
        _saveStatus.value = Resource.Loading
        
        viewModelScope.launch {
            try {
                val updatedTask = task.copy(reminderTime = newReminderTime, updatedAt = com.google.firebase.Timestamp.now())
                repository.updateTask(updatedTask, userId)
                
                if (newReminderTime != null) {
                    com.example.irondiary.util.NotificationHelper.scheduleTaskReminder(
                        repository.context,
                        task.docId,
                        task.description,
                        newReminderTime
                    )
                } else {
                    com.example.irondiary.util.NotificationHelper.cancelTaskReminder(
                        repository.context,
                        task.docId
                    )
                }
                
                _saveStatus.value = Resource.Success(Unit)
            } catch (e: Exception) {
                _saveStatus.value = Resource.Error("Failed to update reminder: ${e.message}")
            }
        }
    }

    fun toggleDailyReminder(enabled: Boolean, context: Context) {
        // Deprecated: Moving to per-task high-priority reminders.
        sharedPreferences.edit().putBoolean("daily_reminders_enabled", enabled).apply()
        _isDailyReminderEnabled.value = enabled
    }

    private fun loadTemplates() {
        val templatesJson = sharedPreferences.getString("task_templates", "[]") ?: "[]"
        try {
            val jsonArray = org.json.JSONArray(templatesJson)
            val list = mutableListOf<TaskTemplate>()
            for (i in 0 until jsonArray.length()) {
                try {
                    val item = jsonArray.get(i)
                    if (item is String) {
                        list.add(TaskTemplate("Custom", item, "✨"))
                    } else if (item is org.json.JSONObject) {
                        list.add(TaskTemplate(
                            category = "Custom",
                            title = item.optString("title", "Unknown Task"),
                            emoji = item.optString("emoji", "✨")
                        ))
                    }
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Skipping corrupted template entry at index $i", e)
                }
            }
            _customTemplates.value = list.take(100) // Hardware limit for UI stability
            updateCategorizedTemplates()
        } catch (e: Exception) {
            Log.e("MainViewModel", "Critical failure parsing templates JSON", e)
            _customTemplates.value = emptyList()
            updateCategorizedTemplates()
        }
    }

    private fun updateCategorizedTemplates() {
        // Custom templates already have "Custom" category but we ensure it here
        val customTaskTemplates = _customTemplates.value
        val allTemplates = customTaskTemplates + defaultTemplates
        
        // Group and maintain order: Custom first, then Health, Life, Sports, Mind, Quit
        val order = listOf("Custom", "Health", "Life", "Sports", "Mind", "Quit")
        
        val grouped = allTemplates.groupBy { it.category }
        val sortedMap = LinkedHashMap<String, List<TaskTemplate>>()
        
        for (category in order) {
            grouped[category]?.let { sortedMap[category] = it }
        }
        
        _categorizedTemplates.value = sortedMap
    }

    fun addTemplate(title: String, emoji: String = "✨") {
        val trimmedTitle = title.trim()
        val trimmedEmoji = emoji.trim().ifEmpty { "✨" }
        
        // Strict Validation (Second Line of Defense)
        if (trimmedTitle.isEmpty()) {
            _templateStatus.value = Resource.Error("Template title cannot be empty.")
            return
        }
        if (trimmedTitle.length > 100) {
            _templateStatus.value = Resource.Error("Template title too long (max 100).")
            return
        }
        if (trimmedEmoji.length > 8) { // Allow for some multi-byte emojis but cap it
            _templateStatus.value = Resource.Error("Emoji too long.")
            return
        }

        var alreadyExists = false
        var limitReached = false

        _customTemplates.update { currentList ->
            if (currentList.size >= 100) {
                limitReached = true
                currentList
            } else if (currentList.any { it.title.equals(trimmedTitle, ignoreCase = true) }) {
                alreadyExists = true
                currentList
            } else {
                val newList = currentList + TaskTemplate("Custom", trimmedTitle, trimmedEmoji)
                saveTemplatesToPrefs(newList)
                updateCategorizedTemplates()
                newList
            }
        }

        if (limitReached) {
            _templateStatus.value = Resource.Error("Maximum of 100 custom templates allowed.")
        } else if (alreadyExists) {
            _templateStatus.value = Resource.Error("Template '$trimmedTitle' already exists.")
        } else {
            _templateStatus.value = Resource.Success(Unit)
        }
    }

    fun removeTemplate(templateTitle: String) {
        val currentList = _customTemplates.value
        val newList = currentList.filterNot { it.title == templateTitle }
        
        if (newList.size != currentList.size) {
            _customTemplates.value = newList
            saveTemplatesToPrefs(newList)
            updateCategorizedTemplates()
            _templateStatus.value = Resource.Success(Unit)
        }
    }

    private fun saveTemplatesToPrefs(templatesList: List<TaskTemplate>) {
        val jsonArray = org.json.JSONArray()
        templatesList.forEach {
            val obj = org.json.JSONObject()
            obj.put("title", it.title)
            obj.put("emoji", it.emoji)
            jsonArray.put(obj)
        }
        sharedPreferences.edit().putString("task_templates", jsonArray.toString()).apply()
    }

    fun addTemplateToToday(template: TaskTemplate) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            _templateStatus.value = Resource.Error("Must be logged in.")
            return
        }

        _templateStatus.value = Resource.Loading
        viewModelScope.launch {
            try {
                repository.addTask(userId, template.title)
                _templateStatus.value = Resource.Success(Unit)
            } catch (e: Exception) {
                _templateStatus.value = Resource.Error("Failed to add task: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        dailyLogsJob?.cancel()
        weightDataJob?.cancel()
        studySessionsJob?.cancel()
        tasksJob?.cancel()
        activeToggleJobs.values.forEach { it.cancel() }
        activeSaveJobs.values.forEach { it.cancel() }
    }
}
